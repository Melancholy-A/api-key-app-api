package com.codex.apikeychat;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;
import java.util.Locale;

final class RecoverableRequestRules {
    private RecoverableRequestRules() {
    }

    static boolean isRetryable(String error, boolean canceled) {
        if (canceled) {
            return false;
        }
        String value = error == null ? "" : error.trim();
        if (value.isEmpty() || value.contains("请求已停止")) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("http 401") || lower.contains("http 403")) {
            return false;
        }
        return lower.contains("http 429")
                || lower.contains("http 500")
                || lower.contains("http 502")
                || lower.contains("http 503")
                || lower.contains("http 504")
                || lower.contains("http 524")
                || lower.contains("tls")
                || lower.contains("ssl")
                || lower.contains("timeout")
                || lower.contains("timed out")
                || lower.contains("socketexception")
                || lower.contains("unknownhost")
                || lower.contains("network is unreachable")
                || lower.contains("connection reset")
                || lower.contains("connection refused");
    }

    static boolean isRetryable(Throwable error, boolean canceled) {
        if (canceled || error == null) {
            return false;
        }
        StringBuilder messages = new StringBuilder();
        boolean networkException = false;
        Throwable current = error;
        int depth = 0;
        while (current != null && depth++ < 8) {
            if (messages.length() > 0) {
                messages.append(' ');
            }
            if (current.getMessage() != null) {
                messages.append(current.getMessage());
            }
            if (current instanceof UnknownHostException
                    || current instanceof SocketTimeoutException
                    || current instanceof ConnectException
                    || current instanceof SocketException
                    || current instanceof SSLException) {
                networkException = true;
            }
            current = current.getCause();
        }
        String combined = messages.toString();
        String lower = combined.toLowerCase(Locale.ROOT);
        if (lower.contains("请求已停止") || lower.contains("http 401") || lower.contains("http 403")) {
            return false;
        }
        return networkException || isRetryable(combined, false);
    }
}
