package com.codex.apikeychat;

final class BaseUrlNormalizer {
    private BaseUrlNormalizer() {
    }

    static String normalize(String value, String fallback) {
        String fallbackValue = fallback == null ? "" : fallback.trim();
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            normalized = fallbackValue;
        }
        if (normalized.isEmpty()) {
            return "";
        }

        String lower = normalized.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            normalized = "https://" + normalized;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        normalized = stripEndpointSuffix(normalized);
        if (normalized.isEmpty()) {
            return fallbackValue;
        }

        // OpenAI-compatible providers normally expose their API below /v1.
        // When the user enters only a domain, make that common form explicit.
        if (hasOnlyOrigin(normalized)) {
            normalized += "/v1";
        }
        return normalized;
    }

    private static boolean hasOnlyOrigin(String value) {
        int schemeEnd = value.indexOf("://");
        if (schemeEnd < 0) {
            return false;
        }
        int pathStart = value.indexOf('/', schemeEnd + 3);
        return pathStart < 0 && value.indexOf('?', schemeEnd + 3) < 0 && value.indexOf('#', schemeEnd + 3) < 0;
    }

    private static String stripEndpointSuffix(String value) {
        String lower = value.toLowerCase();
        String[] suffixes = {"/chat/completions", "/images/generations", "/responses", "/models"};
        for (String suffix : suffixes) {
            if (lower.endsWith(suffix)) {
                return value.substring(0, value.length() - suffix.length());
            }
        }
        return value;
    }
}
