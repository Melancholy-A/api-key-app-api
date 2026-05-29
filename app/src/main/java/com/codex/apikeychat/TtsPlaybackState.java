package com.codex.apikeychat;

final class TtsPlaybackState {
    private int activeMessageIndex = -1;
    private String activeUtteranceId = "";

    boolean shouldStopCurrent(int messageIndex, boolean engineSpeaking) {
        return engineSpeaking && activeMessageIndex >= 0 && activeMessageIndex == messageIndex;
    }

    void markStarted(int messageIndex, String utteranceId) {
        activeMessageIndex = messageIndex;
        activeUtteranceId = utteranceId == null ? "" : utteranceId;
    }

    boolean matchesUtterance(String utteranceId) {
        return activeMessageIndex >= 0 && activeUtteranceId.equals(utteranceId == null ? "" : utteranceId);
    }

    void clear() {
        activeMessageIndex = -1;
        activeUtteranceId = "";
    }
}
