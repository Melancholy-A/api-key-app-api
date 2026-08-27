package com.codex.apikeychat;

import java.util.List;
import java.util.Locale;

final class ConnectionDiagnostics {
    private ConnectionDiagnostics() {
    }

    static Result success(
            String baseUrl,
            String fallbackUrl,
            long elapsedMs,
            List<String> modelIds,
            String selectedModel
    ) {
        String normalized = BaseUrlNormalizer.normalize(baseUrl, fallbackUrl);
        int modelCount = modelIds == null ? 0 : modelIds.size();
        boolean listed = containsModel(modelIds, selectedModel);
        String modelNote = selectedModel == null || selectedModel.trim().isEmpty()
                ? "未选择聊天模型"
                : (listed ? "当前模型已在列表中" : "当前模型未出现在列表中，可检查模型 ID 或服务商权限");
        String summary = "连接正常\n"
                + "地址：" + normalized + "\n"
                + "HTTP 200 · " + elapsedMs + " ms\n"
                + "模型：" + modelCount + " 个 · " + modelNote;
        return new Result(normalized, 200, elapsedMs, modelCount, listed, false, summary);
    }

    static Result failure(
            String baseUrl,
            String fallbackUrl,
            int httpStatus,
            long elapsedMs,
            String error
    ) {
        String normalized = BaseUrlNormalizer.normalize(baseUrl, fallbackUrl);
        String message = redactSensitiveError(error);
        int status = httpStatus > 0 ? httpStatus : statusFromMessage(message);
        String summary = "连接失败\n"
                + "地址：" + normalized + "\n"
                + (status > 0 ? "HTTP " + status : "未取得 HTTP 响应")
                + " · " + elapsedMs + " ms\n"
                + explain(message, status);
        return new Result(
                normalized,
                status,
                elapsedMs,
                0,
                false,
                RecoverableRequestRules.isRetryable(message, false),
                summary
        );
    }

    private static boolean containsModel(List<String> modelIds, String selectedModel) {
        if (modelIds == null || selectedModel == null || selectedModel.trim().isEmpty()) {
            return false;
        }
        String expected = selectedModel.trim();
        for (String modelId : modelIds) {
            if (expected.equals(modelId)) {
                return true;
            }
        }
        return false;
    }

    private static int statusFromMessage(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        int start = lower.indexOf("http ");
        if (start < 0 || start + 8 > lower.length()) {
            return -1;
        }
        try {
            return Integer.parseInt(lower.substring(start + 5, start + 8));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static String redactSensitiveError(String error) {
        String value = error == null || error.trim().isEmpty() ? "请求失败" : error.trim();
        value = value.replaceAll(
                "(?i)(authorization\\s*[:=]?\\s*bearer\\s+)[^\\s,;]+",
                "$1[已隐藏]"
        );
        value = value.replaceAll(
                "(?i)(api[ _-]?key\\s*[:=]\\s*)[^\\s,;]+",
                "$1[已隐藏]"
        );
        return value.replaceAll("\\bsk-[A-Za-z0-9_-]{8,}\\b", "[已隐藏]");
    }

    private static String explain(String message, int status) {
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("tls") || lower.contains("ssl")) {
            return "TLS/证书握手失败。请检查域名、HTTPS 证书链和服务商是否支持 Android/HarmonyOS 的 TLS 1.2/1.3。";
        }
        if (status == 401) {
            return "鉴权失败。请检查 API key 是否正确、是否已保存，以及该 key 是否仍有效。";
        }
        if (status == 403) {
            if (lower.contains("country") || lower.contains("region") || lower.contains("territory")) {
                return "地区或服务商策略拒绝了请求。请向服务商确认当前地区、分组和出口网络是否受支持。";
            }
            return "服务商拒绝访问。请检查 API key 权限、模型分组和访问策略。";
        }
        if (status == 429) {
            return "请求过于频繁或余额/配额受限。请稍后重试，或检查服务商的额度和并发限制。";
        }
        if (status == 500 || status == 502 || status == 503 || status == 504 || status == 524) {
            return "服务商或上游暂时不可用。可稍后重试；若持续出现，请向服务商确认通道状态。";
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "连接或读取超时。请检查网络稳定性，或稍后重试。";
        }
        return "原始错误：" + message;
    }

    static final class Result {
        final String normalizedBaseUrl;
        final int httpStatus;
        final long elapsedMs;
        final int modelCount;
        final boolean selectedModelListed;
        final boolean retryable;
        final String summary;

        Result(
                String normalizedBaseUrl,
                int httpStatus,
                long elapsedMs,
                int modelCount,
                boolean selectedModelListed,
                boolean retryable,
                String summary
        ) {
            this.normalizedBaseUrl = normalizedBaseUrl;
            this.httpStatus = httpStatus;
            this.elapsedMs = elapsedMs;
            this.modelCount = modelCount;
            this.selectedModelListed = selectedModelListed;
            this.retryable = retryable;
            this.summary = summary;
        }
    }
}
