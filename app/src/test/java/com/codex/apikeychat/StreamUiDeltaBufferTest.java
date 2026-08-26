package com.codex.apikeychat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StreamUiDeltaBufferTest {
    @Test
    public void drainsEachPendingDeltaOnce() {
        StreamUiDeltaBuffer buffer = new StreamUiDeltaBuffer();

        buffer.appendText("第一");
        buffer.appendText("段");
        buffer.appendReasoning("思");
        buffer.appendReasoning("考");

        StreamUiDeltaBuffer.Snapshot first = buffer.drain();
        StreamUiDeltaBuffer.Snapshot second = buffer.drain();

        assertEquals("第一段", first.textDelta);
        assertEquals("思考", first.reasoningDelta);
        assertEquals("", second.textDelta);
        assertEquals("", second.reasoningDelta);
    }

    @Test
    public void keepsTextAndReasoningIndependent() {
        StreamUiDeltaBuffer buffer = new StreamUiDeltaBuffer();

        buffer.appendText("answer");

        StreamUiDeltaBuffer.Snapshot snapshot = buffer.drain();

        assertEquals("answer", snapshot.textDelta);
        assertEquals("", snapshot.reasoningDelta);
    }
}
