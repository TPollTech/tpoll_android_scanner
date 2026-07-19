package com.tpoll.hdrecover;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class SafOutput {
    static final class Target {
        final Uri uri;
        final OutputStream stream;

        Target(Uri uri, OutputStream stream) {
            this.uri = uri;
            this.stream = stream;
        }
    }

    private final ContentResolver resolver;
    private final Uri sessionDirectory;
    private final Map<String, Uri> categoryDirectories = new HashMap<>();
    private final String sessionName;

    SafOutput(ContentResolver resolver, Uri treeUri) throws Exception {
        this.resolver = resolver;
        this.sessionName = "HdRecover_" + new SimpleDateFormat(
                "yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
        String rootId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri rootDocument = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId);
        sessionDirectory = DocumentsContract.createDocument(
                resolver,
                rootDocument,
                DocumentsContract.Document.MIME_TYPE_DIR,
                sessionName);
        if (sessionDirectory == null) {
            throw new FileNotFoundException("Não foi possível criar a pasta da recuperação.");
        }
    }

    String getSessionName() {
        return sessionName;
    }

    synchronized Target create(String category, String fileName, String mimeType) throws Exception {
        Uri folder = categoryDirectories.get(category);
        if (folder == null) {
            folder = DocumentsContract.createDocument(
                    resolver,
                    sessionDirectory,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    category);
            if (folder == null) throw new FileNotFoundException(
                    "Não foi possível criar a pasta " + category + ".");
            categoryDirectories.put(category, folder);
        }
        Uri file = DocumentsContract.createDocument(resolver, folder, mimeType, fileName);
        if (file == null) throw new FileNotFoundException(
                "Não foi possível criar o arquivo " + fileName + ".");
        OutputStream stream = resolver.openOutputStream(file, "w");
        if (stream == null) {
            try {
                DocumentsContract.deleteDocument(resolver, file);
            } catch (Exception ignored) {
            }
            throw new FileNotFoundException("Não foi possível abrir o arquivo de destino.");
        }
        return new Target(file, stream);
    }

    void delete(Uri uri) {
        try {
            DocumentsContract.deleteDocument(resolver, uri);
        } catch (Exception ignored) {
        }
    }
}
