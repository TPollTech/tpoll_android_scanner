package com.tpoll.hdrecover;

import android.net.Uri;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class RecoveryEngine {
    interface Listener {
        boolean isCancelled();
        void onLog(String message);
        void onProgress(String phase, double fraction, long scannedBytes,
                        double bytesPerSecond, int candidates, int recovered);
    }

    static final class Result {
        final boolean cancelled;
        final int candidates;
        final int recovered;
        final String sessionName;

        Result(boolean cancelled, int candidates, int recovered, String sessionName) {
            this.cancelled = cancelled;
            this.candidates = candidates;
            this.recovered = recovered;
            this.sessionName = sessionName;
        }
    }

    private enum Kind {
        JPG, PNG, PDF, ZIP, BMP, RIFF, SQLITE, ISO, GIF
    }

    private static final class Candidate {
        final long offset;
        final Kind kind;

        Candidate(long offset, Kind kind) {
            this.offset = offset;
            this.kind = kind;
        }
    }

    private static final class FileInfo {
        final long length;
        final String extension;
        final String category;
        final String mime;

        FileInfo(long length, String extension, String category, String mime) {
            this.length = length;
            this.extension = extension;
            this.category = category;
            this.mime = mime;
        }
    }

    private static final byte[] SIG_JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] SIG_PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] SIG_PDF = {0x25, 0x50, 0x44, 0x46, 0x2D};
    private static final byte[] SIG_ZIP = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] SIG_BMP = {0x42, 0x4D};
    private static final byte[] SIG_RIFF = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] SIG_SQLITE = "SQLite format 3\u0000".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SIG_FTYP = {0x66, 0x74, 0x79, 0x70};
    private static final byte[] SIG_GIF87 = "GIF87a".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SIG_GIF89 = "GIF89a".getBytes(StandardCharsets.US_ASCII);

    private static final byte[] END_JPG = {(byte) 0xFF, (byte) 0xD9};
    private static final byte[] END_PNG = {0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82};
    private static final byte[] END_PDF = {0x25, 0x25, 0x45, 0x4F, 0x46};
    private static final byte[] END_ZIP = {0x50, 0x4B, 0x05, 0x06};
    private static final byte[] END_GIF = {0x3B};

    private static final int SCAN_CHUNK = 4 * 1024 * 1024;
    private static final int IO_CHUNK = 1024 * 1024;
    private static final int OVERLAP = 32;
    private static final int MAX_CANDIDATES = 200_000;

    private final UsbMassStorageDevice storage;
    private final SafOutput output;
    private final Listener listener;

    RecoveryEngine(UsbMassStorageDevice storage, SafOutput output, Listener listener) {
        this.storage = storage;
        this.output = output;
        this.listener = listener;
    }

    Result run() throws Exception {
        List<Candidate> candidates = scanSignatures();
        if (listener.isCancelled()) {
            return new Result(true, candidates.size(), 0, output.getSessionName());
        }
        candidates.sort(Comparator.comparingLong(c -> c.offset));
        listener.onLog("Etapa 1 concluída: " + candidates.size() + " candidato(s) localizado(s).");
        listener.onLog("Etapa 2/2: validando tamanhos e salvando arquivos em ordem física.");

        int recovered = 0;
        long passStart = System.nanoTime();
        long extractedBytes = 0;
        for (int i = 0; i < candidates.size(); i++) {
            if (listener.isCancelled()) {
                return new Result(true, candidates.size(), recovered, output.getSessionName());
            }
            Candidate candidate = candidates.get(i);
            try {
                FileInfo info = inspect(candidate);
                if (info == null || info.length <= 0) continue;
                String name = String.format(Locale.US,
                        "recuperado_%06d_offset_%012X.%s",
                        recovered + 1, candidate.offset, info.extension);
                SafOutput.Target target = output.create(info.category, name, info.mime);
                boolean success = false;
                try (OutputStream stream = target.stream) {
                    writeRange(candidate.offset, info.length, stream);
                    success = !listener.isCancelled();
                } catch (Exception writeError) {
                    output.delete(target.uri);
                    throw writeError;
                }
                if (success) {
                    recovered++;
                    extractedBytes += info.length;
                    if (recovered <= 10 || recovered % 25 == 0) {
                        listener.onLog("Recuperado: " + name + " (" + human(info.length) + ")");
                    }
                } else {
                    output.delete(target.uri);
                }
            } catch (Exception candidateError) {
                if (i < 20 || i % 100 == 0) {
                    listener.onLog("Candidato ignorado em 0x"
                            + Long.toHexString(candidate.offset).toUpperCase(Locale.US)
                            + ": " + safeMessage(candidateError));
                }
            }

            double elapsed = Math.max(0.001, (System.nanoTime() - passStart) / 1_000_000_000.0);
            double speed = extractedBytes / elapsed;
            double secondFraction = candidates.isEmpty() ? 1.0 : (i + 1.0) / candidates.size();
            listener.onProgress(
                    "Etapa 2/2 — extraindo arquivos",
                    0.70 + secondFraction * 0.30,
                    extractedBytes,
                    speed,
                    candidates.size(),
                    recovered);
        }
        return new Result(false, candidates.size(), recovered, output.getSessionName());
    }

    private List<Candidate> scanSignatures() throws Exception {
        long capacity = storage.getCapacityBytes();
        int blockSize = storage.getBlockSize();
        int blocksPerChunk = Math.max(1, SCAN_CHUNK / blockSize);
        long totalBlocks = storage.getBlockCount();
        List<Candidate> candidates = new ArrayList<>();
        Set<Long> unique = new HashSet<>();
        byte[] tail = new byte[0];
        long startNanos = System.nanoTime();
        long lastUpdateNanos = 0;

        listener.onLog("Etapa 1/2: leitura sequencial de " + human(capacity) + ".");
        for (long lba = 0; lba < totalBlocks; lba += blocksPerChunk) {
            if (listener.isCancelled()) break;
            int count = (int) Math.min(blocksPerChunk, totalBlocks - lba);
            byte[] data = storage.readBlocks(lba, count);
            long chunkOffset = lba * (long) blockSize;
            byte[] combined = new byte[tail.length + data.length];
            System.arraycopy(tail, 0, combined, 0, tail.length);
            System.arraycopy(data, 0, combined, tail.length, data.length);
            long combinedOffset = chunkOffset - tail.length;

            find(combined, combinedOffset, SIG_JPG, Kind.JPG, 0, candidates, unique);
            find(combined, combinedOffset, SIG_PNG, Kind.PNG, 0, candidates, unique);
            find(combined, combinedOffset, SIG_PDF, Kind.PDF, 0, candidates, unique);
            find(combined, combinedOffset, SIG_ZIP, Kind.ZIP, 0, candidates, unique);
            find(combined, combinedOffset, SIG_BMP, Kind.BMP, 0, candidates, unique);
            find(combined, combinedOffset, SIG_RIFF, Kind.RIFF, 0, candidates, unique);
            find(combined, combinedOffset, SIG_SQLITE, Kind.SQLITE, 0, candidates, unique);
            find(combined, combinedOffset, SIG_GIF87, Kind.GIF, 0, candidates, unique);
            find(combined, combinedOffset, SIG_GIF89, Kind.GIF, 0, candidates, unique);
            find(combined, combinedOffset, SIG_FTYP, Kind.ISO, -4, candidates, unique);

            if (candidates.size() >= MAX_CANDIDATES) {
                listener.onLog("Limite de " + MAX_CANDIDATES
                        + " candidatos alcançado. Iniciando validação para preservar a memória do celular.");
                break;
            }

            int tailLength = Math.min(OVERLAP, combined.length);
            tail = Arrays.copyOfRange(combined, combined.length - tailLength, combined.length);

            long scanned = Math.min(capacity, chunkOffset + data.length);
            long now = System.nanoTime();
            if (now - lastUpdateNanos >= 350_000_000L || scanned >= capacity) {
                double elapsed = Math.max(0.001, (now - startNanos) / 1_000_000_000.0);
                double fraction = capacity == 0 ? 0 : scanned / (double) capacity;
                listener.onProgress(
                        "Etapa 1/2 — procurando assinaturas",
                        Math.min(0.70, fraction * 0.70),
                        scanned,
                        scanned / elapsed,
                        candidates.size(),
                        0);
                lastUpdateNanos = now;
            }
        }
        return candidates;
    }

    private void find(byte[] data, long baseOffset, byte[] pattern, Kind kind, int adjustment,
                      List<Candidate> outputList, Set<Long> unique) {
        int limit = data.length - pattern.length;
        for (int i = 0; i <= limit; i++) {
            if (data[i] != pattern[0]) continue;
            boolean matches = true;
            for (int j = 1; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    matches = false;
                    break;
                }
            }
            if (!matches) continue;
            long offset = baseOffset + i + adjustment;
            if (offset < 0 || offset >= storage.getCapacityBytes()) continue;
            long key = Long.rotateLeft(offset, 8) ^ kind.ordinal();
            if (unique.add(key)) outputList.add(new Candidate(offset, kind));
            if (outputList.size() >= MAX_CANDIDATES) return;
        }
    }

    private FileInfo inspect(Candidate candidate) throws Exception {
        return switch (candidate.kind) {
            case JPG -> markerFile(candidate.offset, END_JPG, 256,
                    256L * 1024 * 1024, 2, "jpg", "Imagens", "image/jpeg");
            case PNG -> markerFile(candidate.offset, END_PNG, 64,
                    512L * 1024 * 1024, END_PNG.length, "png", "Imagens", "image/png");
            case PDF -> markerFile(candidate.offset, END_PDF, 128,
                    1024L * 1024 * 1024, END_PDF.length, "pdf", "Documentos", "application/pdf");
            case GIF -> markerFile(candidate.offset, END_GIF, 64,
                    256L * 1024 * 1024, 1, "gif", "Imagens", "image/gif");
            case ZIP -> inspectZip(candidate.offset);
            case BMP -> inspectBmp(candidate.offset);
            case RIFF -> inspectRiff(candidate.offset);
            case SQLITE -> inspectSqlite(candidate.offset);
            case ISO -> inspectIso(candidate.offset);
        };
    }

    private FileInfo markerFile(long offset, byte[] marker, long minLength, long maxLength,
                                int markerLength, String extension, String category, String mime)
            throws Exception {
        long end = findMarkerEnd(offset, marker, minLength, maxLength, markerLength);
        if (end <= offset) return null;
        return new FileInfo(end - offset, extension, category, mime);
    }

    private FileInfo inspectZip(long offset) throws Exception {
        long end = findZipEnd(offset, 2L * 1024 * 1024 * 1024);
        if (end <= offset) return null;
        int sampleLength = (int) Math.min(2L * 1024 * 1024, end - offset);
        byte[] sample = storage.readBytes(offset, sampleLength);
        String ext = "zip";
        String category = "Compactados";
        String mime = "application/zip";
        if (containsAscii(sample, "word/")) {
            ext = "docx";
            category = "Documentos";
            mime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else if (containsAscii(sample, "xl/")) {
            ext = "xlsx";
            category = "Planilhas";
            mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else if (containsAscii(sample, "ppt/")) {
            ext = "pptx";
            category = "Apresentacoes";
            mime = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        }
        return new FileInfo(end - offset, ext, category, mime);
    }

    private FileInfo inspectBmp(long offset) throws Exception {
        byte[] header = storage.readBytes(offset, 18);
        if (header.length < 18 || header[0] != 'B' || header[1] != 'M') return null;
        long size = getLe32(header, 2);
        if (size < 54 || size > 1024L * 1024 * 1024) return null;
        return new FileInfo(size, "bmp", "Imagens", "image/bmp");
    }

    private FileInfo inspectRiff(long offset) throws Exception {
        byte[] header = storage.readBytes(offset, 16);
        if (header.length < 12 || !asciiEquals(header, 0, "RIFF")) return null;
        long size = getLe32(header, 4) + 8L;
        if (size < 12 || size > 4L * 1024 * 1024 * 1024) return null;
        if (asciiEquals(header, 8, "WAVE")) {
            return new FileInfo(size, "wav", "Audios", "audio/wav");
        }
        if (asciiEquals(header, 8, "AVI ")) {
            return new FileInfo(size, "avi", "Videos", "video/x-msvideo");
        }
        return null;
    }

    private FileInfo inspectSqlite(long offset) throws Exception {
        byte[] header = storage.readBytes(offset, 100);
        if (header.length < 100 || !asciiEquals(header, 0, "SQLite format 3\u0000")) return null;
        int pageSize = ((header[16] & 0xFF) << 8) | (header[17] & 0xFF);
        if (pageSize == 1) pageSize = 65536;
        long pages = getBe32(header, 28);
        if (pageSize < 512 || pages <= 0) return null;
        long size;
        try {
            size = Math.multiplyExact((long) pageSize, pages);
        } catch (ArithmeticException overflow) {
            return null;
        }
        if (size < 512 || size > 8L * 1024 * 1024 * 1024) return null;
        return new FileInfo(size, "sqlite", "Bancos_de_dados", "application/vnd.sqlite3");
    }

    private FileInfo inspectIso(long offset) throws Exception {
        long capacity = storage.getCapacityBytes();
        long cursor = offset;
        long total = 0;
        int boxes = 0;
        String firstBrand = "";
        while (cursor + 8 <= capacity && total < 8L * 1024 * 1024 * 1024 && boxes < 100_000) {
            byte[] header = storage.readBytes(cursor, 24);
            if (header.length < 8) break;
            long boxSize = getBe32(header, 0);
            String type = new String(header, 4, 4, StandardCharsets.US_ASCII);
            int headerSize = 8;
            if (!isBoxType(type)) break;
            if (boxSize == 1) {
                if (header.length < 16) break;
                boxSize = getBe64(header, 8);
                headerSize = 16;
            } else if (boxSize == 0) {
                break;
            }
            if (boxSize < headerSize || boxSize > capacity - cursor) break;
            if (boxes == 0) {
                if (!"ftyp".equals(type) || header.length < 12) return null;
                firstBrand = new String(header, 8, 4, StandardCharsets.US_ASCII);
            }
            total += boxSize;
            cursor += boxSize;
            boxes++;
        }
        if (boxes < 1 || total < 16) return null;
        String brand = firstBrand.toLowerCase(Locale.US);
        if (brand.startsWith("hei") || brand.equals("mif1") || brand.equals("msf1")) {
            return new FileInfo(total, "heic", "Imagens", "image/heic");
        }
        if (brand.startsWith("avi")) {
            return new FileInfo(total, "avif", "Imagens", "image/avif");
        }
        if (brand.startsWith("qt")) {
            return new FileInfo(total, "mov", "Videos", "video/quicktime");
        }
        return new FileInfo(total, "mp4", "Videos", "video/mp4");
    }

    private long findMarkerEnd(long offset, byte[] marker, long minLength,
                               long maxLength, int markerLength) throws Exception {
        long capacity = storage.getCapacityBytes();
        long limit = Math.min(capacity, offset + maxLength);
        long cursor = offset;
        byte[] tail = new byte[0];
        while (cursor < limit && !listener.isCancelled()) {
            int amount = (int) Math.min(IO_CHUNK, limit - cursor);
            byte[] data = storage.readBytes(cursor, amount);
            if (data.length == 0) break;
            byte[] combined = new byte[tail.length + data.length];
            System.arraycopy(tail, 0, combined, 0, tail.length);
            System.arraycopy(data, 0, combined, tail.length, data.length);
            long base = cursor - tail.length;
            int from = (int) Math.max(0, minLength - (base - offset));
            int position = indexOf(combined, marker, from);
            if (position >= 0) {
                long end = base + position + markerLength;
                if (end - offset >= minLength) return end;
            }
            int tailSize = Math.min(marker.length - 1, combined.length);
            tail = Arrays.copyOfRange(combined, combined.length - tailSize, combined.length);
            cursor += data.length;
        }
        return -1;
    }

    private long findZipEnd(long offset, long maxLength) throws Exception {
        long capacity = storage.getCapacityBytes();
        long limit = Math.min(capacity, offset + maxLength);
        long cursor = offset;
        byte[] tail = new byte[0];
        while (cursor < limit && !listener.isCancelled()) {
            int amount = (int) Math.min(IO_CHUNK, limit - cursor);
            byte[] data = storage.readBytes(cursor, amount);
            if (data.length == 0) break;
            byte[] combined = new byte[tail.length + data.length];
            System.arraycopy(tail, 0, combined, 0, tail.length);
            System.arraycopy(data, 0, combined, tail.length, data.length);
            long base = cursor - tail.length;
            int search = 0;
            while (true) {
                int position = indexOf(combined, END_ZIP, search);
                if (position < 0) break;
                long eocd = base + position;
                byte[] record = storage.readBytes(eocd, 22);
                if (record.length == 22) {
                    int commentLength = (record[20] & 0xFF) | ((record[21] & 0xFF) << 8);
                    long end = eocd + 22L + commentLength;
                    if (end <= limit && end > offset + 22) return end;
                }
                search = position + 1;
            }
            int tailSize = Math.min(END_ZIP.length - 1, combined.length);
            tail = Arrays.copyOfRange(combined, combined.length - tailSize, combined.length);
            cursor += data.length;
        }
        return -1;
    }

    private void writeRange(long offset, long length, OutputStream stream) throws Exception {
        long written = 0;
        while (written < length) {
            if (listener.isCancelled()) return;
            int amount = (int) Math.min(IO_CHUNK, length - written);
            byte[] data = storage.readBytes(offset + written, amount);
            if (data.length == 0) throw new IllegalStateException("Fim inesperado durante a extração.");
            stream.write(data);
            written += data.length;
        }
        stream.flush();
    }

    private static int indexOf(byte[] data, byte[] pattern, int from) {
        if (pattern.length == 0) return Math.max(0, from);
        int limit = data.length - pattern.length;
        for (int i = Math.max(0, from); i <= limit; i++) {
            if (data[i] != pattern[0]) continue;
            boolean found = true;
            for (int j = 1; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    found = false;
                    break;
                }
            }
            if (found) return i;
        }
        return -1;
    }

    private static boolean containsAscii(byte[] data, String needle) {
        return indexOf(data, needle.getBytes(StandardCharsets.US_ASCII), 0) >= 0;
    }

    private static boolean asciiEquals(byte[] data, int offset, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        if (offset < 0 || offset + bytes.length > data.length) return false;
        for (int i = 0; i < bytes.length; i++) {
            if (data[offset + i] != bytes[i]) return false;
        }
        return true;
    }

    private static boolean isBoxType(String value) {
        if (value.length() != 4) return false;
        for (int i = 0; i < 4; i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c > 0x7E) return false;
        }
        return true;
    }

    private static long getLe32(byte[] data, int offset) {
        return (data[offset] & 0xFFL)
                | ((data[offset + 1] & 0xFFL) << 8)
                | ((data[offset + 2] & 0xFFL) << 16)
                | ((data[offset + 3] & 0xFFL) << 24);
    }

    private static long getBe32(byte[] data, int offset) {
        return ((data[offset] & 0xFFL) << 24)
                | ((data[offset + 1] & 0xFFL) << 16)
                | ((data[offset + 2] & 0xFFL) << 8)
                | (data[offset + 3] & 0xFFL);
    }

    private static long getBe64(byte[] data, int offset) {
        long value = 0;
        for (int i = 0; i < 8; i++) value = (value << 8) | (data[offset + i] & 0xFFL);
        return value;
    }

    private static String safeMessage(Exception error) {
        String value = error.getMessage();
        return value == null || value.isBlank() ? error.getClass().getSimpleName() : value;
    }

    private static String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int index = -1;
        do {
            value /= 1024.0;
            index++;
        } while (value >= 1024.0 && index < units.length - 1);
        return String.format(Locale.US, value >= 10 ? "%.1f %s" : "%.2f %s", value, units[index]);
    }
}
