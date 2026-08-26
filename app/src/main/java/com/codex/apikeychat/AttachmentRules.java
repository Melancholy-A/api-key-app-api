package com.codex.apikeychat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Shared attachment limits and validation used before an attachment is queued or sent. */
final class AttachmentRules {
    static final long MAX_ATTACHMENT_BYTES = 20L * 1024L * 1024L;
    static final long MAX_OFFICE_ATTACHMENT_BYTES = 120L * 1024L * 1024L;
    /** Upper bound for non-Office inline data after Base64 encoding. */
    static final long MAX_INLINE_REQUEST_BYTES = 28L * 1024L * 1024L;
    static final int MAX_ATTACHMENTS = 6;

    private AttachmentRules() {
    }

    static final class Entry {
        final String id;
        final String name;
        final long sizeBytes;
        final boolean image;
        final boolean office;

        Entry(String id, String name, long sizeBytes, boolean image, boolean office) {
            this.id = id == null ? "" : id;
            this.name = name == null || name.isEmpty() ? "附件" : name;
            this.sizeBytes = sizeBytes;
            this.image = image;
            this.office = office;
        }
    }

    static final class Summary {
        final int count;
        final int imageCount;
        final int fileCount;
        final long totalBytes;
        final int unknownSizeCount;

        Summary(int count, int imageCount, int fileCount, long totalBytes, int unknownSizeCount) {
            this.count = count;
            this.imageCount = imageCount;
            this.fileCount = fileCount;
            this.totalBytes = totalBytes;
            this.unknownSizeCount = unknownSizeCount;
        }
    }

    static final class Validation {
        final List<String> errors;

        Validation(List<String> errors) {
            this.errors = errors;
        }

        boolean isValid() {
            return errors.isEmpty();
        }
    }

    static Summary summarize(List<Entry> entries) {
        int count = 0;
        int imageCount = 0;
        int fileCount = 0;
        int unknownSizeCount = 0;
        long totalBytes = 0L;
        if (entries != null) {
            for (Entry entry : entries) {
                if (entry == null) {
                    continue;
                }
                count++;
                if (entry.image) {
                    imageCount++;
                } else {
                    fileCount++;
                }
                if (entry.sizeBytes < 0) {
                    unknownSizeCount++;
                } else {
                    totalBytes += entry.sizeBytes;
                }
            }
        }
        return new Summary(count, imageCount, fileCount, totalBytes, unknownSizeCount);
    }

    static Validation validate(List<Entry> entries) {
        ArrayList<String> errors = new ArrayList<>();
        if (entries == null || entries.isEmpty()) {
            return new Validation(errors);
        }
        if (entries.size() > MAX_ATTACHMENTS) {
            errors.add("最多选择 " + MAX_ATTACHMENTS + " 个附件");
        }
        Set<String> ids = new HashSet<>();
        for (Entry entry : entries) {
            if (entry == null) {
                continue;
            }
            if (!entry.id.isEmpty() && !ids.add(entry.id)) {
                errors.add("重复附件: " + entry.name);
            }
            long maxBytes = entry.office ? MAX_OFFICE_ATTACHMENT_BYTES : MAX_ATTACHMENT_BYTES;
            if (entry.sizeBytes > maxBytes) {
                errors.add(entry.name + " 超过 " + (entry.office ? "120MB" : "20MB"));
            }
        }
        return new Validation(errors);
    }

    static boolean inlinePayloadWithinBudget(List<Entry> entries) {
        return estimatedInlinePayloadBytes(entries) <= MAX_INLINE_REQUEST_BYTES;
    }

    private static long estimatedInlinePayloadBytes(List<Entry> entries) {
        long total = 0L;
        if (entries == null) {
            return 0L;
        }
        for (Entry entry : entries) {
            if (entry == null || entry.office || entry.image || entry.sizeBytes < 0) {
                continue;
            }
            long encoded = ((entry.sizeBytes + 2L) / 3L) * 4L;
            total = saturatingAdd(total, saturatingAdd(encoded, 128L));
            if (total > MAX_INLINE_REQUEST_BYTES) {
                return total;
            }
        }
        return total;
    }

    private static long saturatingAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    static List<Entry> removeSelected(List<Entry> entries, Set<String> selectedIds) {
        ArrayList<Entry> remaining = new ArrayList<>();
        if (entries == null) {
            return remaining;
        }
        for (Entry entry : entries) {
            if (entry == null || selectedIds == null || !selectedIds.contains(entry.id)) {
                if (entry != null) {
                    remaining.add(entry);
                }
            }
        }
        return remaining;
    }
}
