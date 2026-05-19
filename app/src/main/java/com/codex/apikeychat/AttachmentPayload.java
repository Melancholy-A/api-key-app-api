package com.codex.apikeychat;

class AttachmentPayload {
    final String filename;
    final String dataUrl;
    final boolean image;

    AttachmentPayload(String filename, String dataUrl, boolean image) {
        this.filename = filename;
        this.dataUrl = dataUrl;
        this.image = image;
    }
}
