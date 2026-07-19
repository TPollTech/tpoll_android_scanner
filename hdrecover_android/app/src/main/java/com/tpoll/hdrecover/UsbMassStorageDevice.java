package com.tpoll.hdrecover;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Leitor USB Mass Storage Bulk-Only Transport. Todos os comandos usados aqui
 * são somente de consulta/leitura: INQUIRY, TEST UNIT READY, READ CAPACITY e READ.
 */
final class UsbMassStorageDevice implements AutoCloseable {
    private static final int CBW_SIGNATURE = 0x43425355;
    private static final int CSW_SIGNATURE = 0x53425355;
    private static final int IO_TIMEOUT_MS = 15000;
    private static final int MAX_USB_TRANSFER = 128 * 1024;

    private final UsbManager manager;
    private final UsbDevice device;
    private final AtomicInteger nextTag = new AtomicInteger(1);

    private UsbDeviceConnection connection;
    private UsbInterface massStorageInterface;
    private UsbEndpoint bulkIn;
    private UsbEndpoint bulkOut;
    private int blockSize = 512;
    private long blockCount = 0;
    private boolean useRead16 = false;

    UsbMassStorageDevice(UsbManager manager, UsbDevice device) {
        this.manager = manager;
        this.device = device;
    }

    void open() throws IOException {
        locateInterface();
        connection = manager.openDevice(device);
        if (connection == null) {
            throw new IOException("O Android não conseguiu abrir o dispositivo USB.");
        }
        if (!connection.claimInterface(massStorageInterface, true)) {
            close();
            throw new IOException("Não foi possível assumir o controle da interface USB Mass Storage.");
        }

        try {
            testUnitReady();
        } catch (IOException ignored) {
            // Alguns adaptadores retornam CHECK CONDITION no primeiro comando.
            requestSenseQuietly();
            testUnitReady();
        }
        readCapacity();
        if (blockSize < 512 || blockSize > 1024 * 1024 || blockCount <= 0) {
            throw new IOException("Geometria inválida informada pelo dispositivo USB.");
        }
    }

    private void locateInterface() throws IOException {
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface intf = device.getInterface(i);
            if (intf.getInterfaceClass() != UsbConstants.USB_CLASS_MASS_STORAGE) continue;
            UsbEndpoint in = null;
            UsbEndpoint out = null;
            for (int e = 0; e < intf.getEndpointCount(); e++) {
                UsbEndpoint endpoint = intf.getEndpoint(e);
                if (endpoint.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) continue;
                if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) in = endpoint;
                if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) out = endpoint;
            }
            if (in != null && out != null) {
                massStorageInterface = intf;
                bulkIn = in;
                bulkOut = out;
                return;
            }
        }
        throw new IOException("O dispositivo não oferece uma interface USB Mass Storage compatível.");
    }

    long getCapacityBytes() {
        if (blockCount > Long.MAX_VALUE / blockSize) return Long.MAX_VALUE;
        return blockCount * (long) blockSize;
    }

    int getBlockSize() {
        return blockSize;
    }

    long getBlockCount() {
        return blockCount;
    }

    byte[] readBlocks(long lba, int count) throws IOException {
        if (count <= 0) return new byte[0];
        if (lba < 0 || lba >= blockCount) throw new IOException("LBA fora do dispositivo.");
        long remaining = blockCount - lba;
        if (count > remaining) count = (int) Math.min(Integer.MAX_VALUE, remaining);
        if (count <= 0) return new byte[0];

        int maxByMemory = Math.max(1, (8 * 1024 * 1024) / blockSize);
        if (count > maxByMemory) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(count * blockSize);
            long cursor = lba;
            int left = count;
            while (left > 0) {
                int batch = Math.min(left, maxByMemory);
                byte[] part = readBlocks(cursor, batch);
                out.write(part, 0, part.length);
                cursor += batch;
                left -= batch;
            }
            return out.toByteArray();
        }

        int dataLength;
        try {
            dataLength = Math.multiplyExact(count, blockSize);
        } catch (ArithmeticException overflow) {
            throw new IOException("Bloco de leitura grande demais.");
        }
        byte[] data = new byte[dataLength];

        if (!useRead16 && lba <= 0xFFFFFFFFL && count <= 0xFFFF) {
            byte[] cdb = new byte[10];
            cdb[0] = 0x28; // READ(10)
            putBe32(cdb, 2, lba);
            cdb[7] = (byte) ((count >>> 8) & 0xFF);
            cdb[8] = (byte) (count & 0xFF);
            command(cdb, data);
        } else {
            byte[] cdb = new byte[16];
            cdb[0] = (byte) 0x88; // READ(16)
            putBe64(cdb, 2, lba);
            putBe32(cdb, 10, count & 0xFFFFFFFFL);
            command(cdb, data);
        }
        return data;
    }

    byte[] readBytes(long offset, int length) throws IOException {
        if (length <= 0) return new byte[0];
        long capacity = getCapacityBytes();
        if (offset < 0 || offset >= capacity) return new byte[0];
        long end = Math.min(capacity, offset + (long) length);
        long startLba = offset / blockSize;
        long endLba = (end + blockSize - 1L) / blockSize;
        long countLong = endLba - startLba;
        if (countLong > Integer.MAX_VALUE) throw new IOException("Leitura solicitada grande demais.");
        byte[] aligned = readBlocks(startLba, (int) countLong);
        int start = (int) (offset - startLba * blockSize);
        int wanted = (int) Math.min(end - offset, aligned.length - start);
        if (wanted <= 0) return new byte[0];
        return Arrays.copyOfRange(aligned, start, start + wanted);
    }

    private void testUnitReady() throws IOException {
        command(new byte[]{0x00, 0, 0, 0, 0, 0}, null);
    }

    private void requestSenseQuietly() {
        try {
            byte[] sense = new byte[18];
            byte[] cdb = new byte[]{0x03, 0, 0, 0, 18, 0};
            command(cdb, sense);
        } catch (Exception ignored) {
        }
    }

    private void readCapacity() throws IOException {
        byte[] data = new byte[8];
        command(new byte[]{0x25, 0, 0, 0, 0, 0, 0, 0, 0, 0}, data);
        long lastLba = getBe32(data, 0);
        long reportedBlockSize = getBe32(data, 4);
        if (lastLba != 0xFFFFFFFFL) {
            blockCount = lastLba + 1L;
            blockSize = (int) reportedBlockSize;
            useRead16 = false;
            return;
        }

        byte[] data16 = new byte[32];
        byte[] cdb = new byte[16];
        cdb[0] = (byte) 0x9E;
        cdb[1] = 0x10; // READ CAPACITY(16)
        cdb[13] = 32;
        command(cdb, data16);
        long lastLba16 = getBe64(data16, 0);
        long blockSize16 = getBe32(data16, 8);
        if (lastLba16 < 0 || lastLba16 == Long.MAX_VALUE) {
            throw new IOException("Dispositivo maior que o limite suportado nesta versão.");
        }
        blockCount = lastLba16 + 1L;
        blockSize = (int) blockSize16;
        useRead16 = true;
    }

    private synchronized void command(byte[] cdb, byte[] input) throws IOException {
        if (connection == null) throw new IOException("Dispositivo USB fechado.");
        if (cdb.length == 0 || cdb.length > 16) throw new IOException("Comando SCSI inválido.");

        int tag = nextTag.getAndIncrement();
        int dataLength = input == null ? 0 : input.length;
        ByteBuffer cbw = ByteBuffer.allocate(31).order(ByteOrder.LITTLE_ENDIAN);
        cbw.putInt(CBW_SIGNATURE);
        cbw.putInt(tag);
        cbw.putInt(dataLength);
        cbw.put((byte) (dataLength > 0 ? 0x80 : 0x00));
        cbw.put((byte) 0); // LUN
        cbw.put((byte) cdb.length);
        cbw.put(cdb);
        while (cbw.position() < 31) cbw.put((byte) 0);

        int sent = connection.bulkTransfer(bulkOut, cbw.array(), 31, IO_TIMEOUT_MS);
        if (sent != 31) throw new IOException("Falha ao enviar comando para o dispositivo USB.");

        if (input != null && input.length > 0) {
            bulkInExact(input, input.length);
        }

        byte[] csw = new byte[13];
        bulkInExact(csw, csw.length);
        ByteBuffer status = ByteBuffer.wrap(csw).order(ByteOrder.LITTLE_ENDIAN);
        int signature = status.getInt();
        int returnedTag = status.getInt();
        status.getInt(); // residue
        int commandStatus = status.get() & 0xFF;
        if (signature != CSW_SIGNATURE || returnedTag != tag) {
            throw new IOException("Resposta USB Mass Storage inválida.");
        }
        if (commandStatus != 0) {
            throw new IOException("O dispositivo recusou uma operação de leitura SCSI (status "
                    + commandStatus + ").");
        }
    }

    private void bulkInExact(byte[] target, int expected) throws IOException {
        int offset = 0;
        int emptyReads = 0;
        while (offset < expected) {
            int amount = Math.min(expected - offset, MAX_USB_TRANSFER);
            int read = connection.bulkTransfer(
                    bulkIn, target, offset, amount, IO_TIMEOUT_MS);
            if (read > 0) {
                offset += read;
                emptyReads = 0;
            } else {
                emptyReads++;
                if (emptyReads >= 3) {
                    throw new IOException("Tempo esgotado durante a leitura USB em "
                            + offset + " de " + expected + " bytes.");
                }
            }
        }
    }

    private static long getBe32(byte[] data, int offset) {
        return ((long) (data[offset] & 0xFF) << 24)
                | ((long) (data[offset + 1] & 0xFF) << 16)
                | ((long) (data[offset + 2] & 0xFF) << 8)
                | (long) (data[offset + 3] & 0xFF);
    }

    private static long getBe64(byte[] data, int offset) {
        long value = 0;
        for (int i = 0; i < 8; i++) value = (value << 8) | (data[offset + i] & 0xFFL);
        return value;
    }

    private static void putBe32(byte[] data, int offset, long value) {
        data[offset] = (byte) ((value >>> 24) & 0xFF);
        data[offset + 1] = (byte) ((value >>> 16) & 0xFF);
        data[offset + 2] = (byte) ((value >>> 8) & 0xFF);
        data[offset + 3] = (byte) (value & 0xFF);
    }

    private static void putBe64(byte[] data, int offset, long value) {
        for (int i = 7; i >= 0; i--) {
            data[offset + i] = (byte) (value & 0xFF);
            value >>>= 8;
        }
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                if (massStorageInterface != null) connection.releaseInterface(massStorageInterface);
            } catch (Exception ignored) {
            }
            connection.close();
            connection = null;
        }
    }
}
