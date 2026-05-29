package com.codex.apikeychat;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TtsPlaybackStateTest {
    @Test
    public void sameMessageClickWhileSpeakingRequestsStop() {
        TtsPlaybackState state = new TtsPlaybackState();

        state.markStarted(7, "utt-7");

        assertTrue(state.shouldStopCurrent(7, true));
    }

    @Test
    public void differentMessageClickWhileSpeakingStartsNewPlayback() {
        TtsPlaybackState state = new TtsPlaybackState();

        state.markStarted(7, "utt-7");

        assertFalse(state.shouldStopCurrent(8, true));
    }

    @Test
    public void clearedStateDoesNotRequestStop() {
        TtsPlaybackState state = new TtsPlaybackState();

        state.markStarted(7, "utt-7");
        state.clear();

        assertFalse(state.shouldStopCurrent(7, true));
    }
}
