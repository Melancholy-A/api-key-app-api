package com.codex.apikeychat;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ChatOperationGateTest {
    @Test
    public void restoreBlocksAllChatMutationsUntilItCompletes() throws Exception {
        ChatOperationGate gate = newGate();

        assertTrue(gate.tryBeginRestore());
        assertFalse(gate.allowsChatMutation());
        assertFalse(gate.tryBeginRestore());

        gate.endRestore();

        assertTrue(gate.allowsChatMutation());
        assertTrue(gate.tryBeginRestore());
    }

    private ChatOperationGate newGate() {
        try {
            return new ChatOperationGate();
        } catch (NoClassDefFoundError error) {
            fail("ChatOperationGate must serialize restore against chat mutations");
            throw error;
        }
    }
}
