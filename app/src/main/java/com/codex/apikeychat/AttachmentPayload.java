package com.codex.apikeychat;

class AttachmentPayload {
    final String filename;
    final String dataUrl;
    final String extractedText;
    final boolean image;

    AttachmentPayload(String filename, String dataUrl, boolean image) {
        this(filename, dataUrl, "", image);
    }

    AttachmentPayload(String filename, String dataUrl, String extractedText, boolean image) {
        this.filename = filename;
        this.dataUrl = dataUrl;
        this.extractedText = extractedText == null ? "" : extractedText;
        this.image = image;
    }
}
