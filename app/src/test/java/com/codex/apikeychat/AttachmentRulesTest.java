package com.codex.apikeychat;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AttachmentRulesTest {
    @Test
    public void summarizesImageFileAndTotalSize() {
        List<AttachmentRules.Entry> entries = Arrays.asList(
                new AttachmentRules.Entry("image-1", "photo.jpg", 2L * 1024L * 1024L, true, false),
                new AttachmentRules.Entry("file-1", "notes.txt", 512L * 1024L, false, false)
        );

        AttachmentRules.Summary summary = AttachmentRules.summarize(entries);

        assertEquals(2, summary.count);
        assertEquals(1, summary.imageCount);
        assertEquals(1, summary.fileCount);
        assertEquals(2L * 1024L * 1024L + 512L * 1024L, summary.totalBytes);
        assertEquals(0, summary.unknownSizeCount);
    }

    @Test
    public void rejectsDuplicateAndOversizedAttachments() {
        List<AttachmentRules.Entry> entries = Arrays.asList(
                new AttachmentRules.Entry("same", "photo.jpg", 1L, true, false),
                new AttachmentRules.Entry("same", "photo.jpg", AttachmentRules.MAX_ATTACHMENT_BYTES + 1L, true, false)
        );

        AttachmentRules.Validation validation = AttachmentRules.validate(entries);

        assertFalse(validation.isValid());
        assertEquals(2, validation.errors.size());
    }

    @Test
    public void allowsOfficeAttachmentUpToOfficeLimit() {
        AttachmentRules.Entry entry = new AttachmentRules.Entry(
                "office", "deck.pptx", AttachmentRules.MAX_ATTACHMENT_BYTES + 1L, false, true
        );

        assertTrue(AttachmentRules.validate(Collections.singletonList(entry)).isValid());
    }

    @Test
    public void removesOnlySelectedEntries() {
        AttachmentRules.Entry first = new AttachmentRules.Entry("first", "a.txt", 1L, false, false);
        AttachmentRules.Entry second = new AttachmentRules.Entry("second", "b.txt", 1L, false, false);

        List<AttachmentRules.Entry> remaining = AttachmentRules.removeSelected(
                Arrays.asList(first, second), new HashSet<>(Collections.singletonList("second"))
        );

        assertEquals(1, remaining.size());
        assertEquals("first", remaining.get(0).id);
    }

    @Test
    public void rejectsAggregateInlinePayloadBeforeEncoding() {
        long oversizedTotal = AttachmentRules.MAX_INLINE_REQUEST_BYTES;
        List<AttachmentRules.Entry> entries = Arrays.asList(
                new AttachmentRules.Entry("a", "a.bin", oversizedTotal / 2, false, false),
                new AttachmentRules.Entry("b", "b.bin", oversizedTotal / 2, false, false)
        );

        assertFalse(AttachmentRules.inlinePayloadWithinBudget(entries));
    }

    @Test
    public void acceptsOneMaximumSizeOrdinaryAttachment() {
        List<AttachmentRules.Entry> entries = Collections.singletonList(
                new AttachmentRules.Entry(
                        "large", "large.bin", AttachmentRules.MAX_ATTACHMENT_BYTES, false, false
                )
        );

        assertTrue(AttachmentRules.inlinePayloadWithinBudget(entries));
    }

    @Test
    public void officeFilesDoNotConsumeInlinePayloadBudget() {
        List<AttachmentRules.Entry> entries = Collections.singletonList(
                new AttachmentRules.Entry(
                        "office", "large.pptx", AttachmentRules.MAX_OFFICE_ATTACHMENT_BYTES, false, true
                )
        );

        assertTrue(AttachmentRules.inlinePayloadWithinBudget(entries));
    }
}
