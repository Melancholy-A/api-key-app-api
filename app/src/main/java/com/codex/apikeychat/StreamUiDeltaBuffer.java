package com.codex.apikeychat;

/** Collects stream fragments until the UI flushes them, without replaying old text. */
final class StreamUiDeltaBuffer {
    private final StringBuilder text = new StringBuilder();
    private final StringBuilder reasoning = new StringBuilder();

    synchronized void appendText(String value) {
        if (value != null && !value.isEmpty()) {
            text.append(value);
        }
    }

    synchronized void appendReasoning(String value) {
        if (value != null && !value.isEmpty()) {
            reasoning.append(value);
        }
    }

    synchronized Snapshot drain() {
        Snapshot snapshot = new Snapshot(text.toString(), reasoning.toString());
        text.setLength(0);
        reasoning.setLength(0);
        return snapshot;
    }

    static final class Snapshot {
        final String textDelta;
        final String reasoningDelta;

        Snapshot(String textDelta, String reasoningDelta) {
            this.textDelta = textDelta == null ? "" : textDelta;
            this.reasoningDelta = reasoningDelta == null ? "" : reasoningDelta;
        }
    }
}
