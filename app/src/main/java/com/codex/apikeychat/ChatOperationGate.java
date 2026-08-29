package com.codex.apikeychat;

final class ChatOperationGate {
    private boolean restoring;

    synchronized boolean tryBeginRestore() {
        if (restoring) {
            return false;
        }
        restoring = true;
        return true;
    }

    synchronized void endRestore() {
        restoring = false;
    }

    synchronized boolean allowsChatMutation() {
        return !restoring;
    }
}
