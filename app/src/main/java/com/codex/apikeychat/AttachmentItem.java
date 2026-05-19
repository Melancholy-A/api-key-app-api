package com.codex.apikeychat;

import android.net.Uri;

class AttachmentItem {
    final Uri uri;
    final String name;
    final String mimeType;
    final long sizeBytes;
    final boolean image;

    AttachmentItem(Uri uri, String name, String mimeType, long sizeBytes, boolean image) {
        this.uri = uri;
        this.name = name;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.image = image;
    }

    String displayLine() {
        String kind = image ? "图片" : "文件";
        String size = sizeBytes > 0 ? " · " + readableSize(sizeBytes) : "";
        return kind + ": " + name + size;
    }

    private static String readableSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        return String.format("%.1f MB", kb / 1024.0);
    }
}
