package com.codex.apikeychat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLException;

class SearchClient {
    private static final int CONNECT_TIMEOUT_MS = 30000;
    private static final int READ_TIMEOUT_MS = 60000;

    static List<SearchResult> search(
            String endpoint,
            String authMode,
            String apiKey,
            int count,
            String query,
            OpenAiClient.CancelToken cancelToken
    ) throws Exception {
        String cleanEndpoint = endpoint == null ? "" : endpoint.trim();
        String cleanQuery = query == null ? "" : query.trim();
        if (cleanEndpoint.isEmpty()) {
            throw new IOException("未配置搜索接口地址");
        }
        if (cleanQuery.isEmpty()) {
            throw new IOException("搜索问题为空");
        }

        int limit = Math.max(1, Math.min(10, count));
        Request request = buildRequest(cleanEndpoint, authMode, apiKey, limit, cleanQuery);
        Object response = request.method.equals("GET")
                ? getJson(request.url, authMode, apiKey, cancelToken)
                : postJson(request.url, authMode, apiKey, request.body, cancelToken);
        return extractResults(response, limit);
    }

    static JSONArray toJsonArray(List<SearchResult> results) {
        JSONArray array = new JSONArray();
        if (results == null) {
            return array;
        }
        for (SearchResult result : results) {
            array.put(result.toJson());
        }
        return array;
    }

    private static Request buildRequest(
            String endpoint,
            String authMode,
            String apiKey,
            int count,
            String query
    ) throws Exception {
        boolean templated = endpoint.contains("{query}") || endpoint.contains("{q}") || endpoint.contains("{count}");
        if (templated || endpoint.contains("?")) {
            String url = endpoint;
            if (templated) {
                url = url.replace("{query}", encode(query))
                        .replace("{q}", encode(query))
                        .replace("{count}", String.valueOf(count));
                if (!endpoint.contains("{count}")) {
                    url = appendQueryParam(url, "count", String.valueOf(count));
                }
            } else {
                url = appendQueryParam(url, "q", query);
                url = appendQueryParam(url, "count", String.valueOf(count));
            }
            if (ApiKeyStore.SEARCH_AUTH_QUERY_API_KEY.equals(authMode) && apiKey != null && !apiKey.trim().isEmpty()) {
                url = appendQueryParam(url, "api_key", apiKey.trim());
            }
            return new Request("GET", url, null);
        }

        JSONObject body = new JSONObject();
        body.put("query", query);
        body.put("q", query);
        body.put("count", count);
        body.put("num", count);
        body.put("max_results", count);
        if (ApiKeyStore.SEARCH_AUTH_QUERY_API_KEY.equals(authMode) && apiKey != null && !apiKey.trim().isEmpty()) {
            body.put("api_key", apiKey.trim());
        }
        return new Request("POST", endpoint, body);
    }

    private static Object getJson(
            String endpoint,
            String authMode,
            String apiKey,
            OpenAiClient.CancelToken cancelToken
    ) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        if (cancelToken != null) {
            cancelToken.attach(connection);
        }
        try {
            checkCanceled(cancelToken);
            connection.setRequestMethod("GET");
            applyAuth(connection, authMode, apiKey);
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            return parseResponse(connection, cancelToken);
        } catch (SSLException e) {
            throw new IOException(tlsFailureMessage(endpoint, e), e);
        } finally {
            if (cancelToken != null) {
                cancelToken.clear(connection);
            }
        }
    }

    private static Object postJson(
            String endpoint,
            String authMode,
            String apiKey,
            JSONObject body,
            OpenAiClient.CancelToken cancelToken
    ) throws Exception {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        if (cancelToken != null) {
            cancelToken.attach(connection);
        }
        try {
            checkCanceled(cancelToken);
            connection.setRequestMethod("POST");
            applyAuth(connection, authMode, apiKey);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = connection.getOutputStream()) {
                checkCanceled(cancelToken);
                out.write(bytes);
            }
            return parseResponse(connection, cancelToken);
        } catch (SSLException e) {
            throw new IOException(tlsFailureMessage(endpoint, e), e);
        } finally {
            if (cancelToken != null) {
                cancelToken.clear(connection);
            }
        }
    }

    private static void applyAuth(HttpURLConnection connection, String authMode, String apiKey) {
        String key = apiKey == null ? "" : apiKey.trim();
        if (key.isEmpty()) {
            return;
        }
        if (ApiKeyStore.SEARCH_AUTH_BEARER.equals(authMode)) {
            connection.setRequestProperty("Authorization", "Bearer " + key);
        } else if (ApiKeyStore.SEARCH_AUTH_X_API_KEY.equals(authMode)) {
            connection.setRequestProperty("X-API-Key", key);
        }
    }

    private static Object parseResponse(HttpURLConnection connection, OpenAiClient.CancelToken cancelToken) throws Exception {
        checkCanceled(cancelToken);
        int code = connection.getResponseCode();
        checkCanceled(cancelToken);
        InputStream stream = code >= 200 && code < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String text = readAll(stream);
        if (code < 200 || code >= 300) {
            throw new IOException("搜索接口 HTTP " + code + ": " + extractErrorMessage(text));
        }
        if (text == null || text.trim().isEmpty()) {
            return new JSONObject();
        }
        return new JSONTokener(text).nextValue();
    }

    private static List<SearchResult> extractResults(Object response, int limit) {
        JSONArray items = findResultArray(response);
        ArrayList<SearchResult> results = new ArrayList<>();
        if (items == null) {
            if (response instanceof JSONObject) {
                SearchResult single = toResult((JSONObject) response);
                if (!single.isEmpty()) {
                    results.add(single);
                }
            }
            return results;
        }

        for (int i = 0; i < items.length() && results.size() < limit; i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            SearchResult result = toResult(item);
            if (!result.isEmpty()) {
                results.add(result);
            }
        }
        return results;
    }

    private static JSONArray findResultArray(Object response) {
        if (response instanceof JSONArray) {
            return (JSONArray) response;
        }
        if (!(response instanceof JSONObject)) {
            return null;
        }
        JSONObject root = (JSONObject) response;
        String[] directKeys = {
                "results",
                "data",
                "items",
                "organic",
                "news",
                "web",
                "documents"
        };
        for (String key : directKeys) {
            JSONArray array = root.optJSONArray(key);
            if (array != null) {
                return array;
            }
            JSONObject nested = root.optJSONObject(key);
            if (nested != null) {
                JSONArray nestedResults = firstNestedArray(nested);
                if (nestedResults != null) {
                    return nestedResults;
                }
            }
        }

        JSONObject webPages = root.optJSONObject("webPages");
        if (webPages != null) {
            JSONArray value = webPages.optJSONArray("value");
            if (value != null) {
                return value;
            }
        }

        JSONObject search = root.optJSONObject("search");
        if (search != null) {
            JSONArray results = search.optJSONArray("results");
            if (results != null) {
                return results;
            }
        }
        return null;
    }

    private static JSONArray firstNestedArray(JSONObject object) {
        String[] keys = {"results", "items", "value", "data"};
        for (String key : keys) {
            JSONArray array = object.optJSONArray(key);
            if (array != null) {
                return array;
            }
        }
        return null;
    }

    private static SearchResult toResult(JSONObject item) {
        String title = firstNonEmpty(item, "title", "name", "headline");
        String snippet = firstNonEmpty(item, "snippet", "content", "description", "text", "body");
        String url = firstNonEmpty(item, "url", "link", "href");
        String publishedAt = firstNonEmpty(item, "publishedAt", "published_at", "publishedDate", "date", "lastUpdated", "last_updated");
        if (title.isEmpty() && !url.isEmpty()) {
            title = url;
        }
        return new SearchResult(limitText(title, 180), limitText(snippet, 900), url, limitText(publishedAt, 80));
    }

    private static String firstNonEmpty(JSONObject item, String... keys) {
        for (String key : keys) {
            String value = item.optString(key, "");
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String limitText(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxChars) {
            return trimmed;
        }
        return trimmed.substring(0, maxChars - 3) + "...";
    }

    private static void checkCanceled(OpenAiClient.CancelToken cancelToken) throws InterruptedIOException {
        if (cancelToken != null && cancelToken.isCanceled()) {
            throw new InterruptedIOException("请求已停止");
        }
    }

    private static String extractErrorMessage(String responseText) {
        try {
            Object parsed = new JSONTokener(responseText == null ? "" : responseText).nextValue();
            if (parsed instanceof JSONObject) {
                JSONObject root = (JSONObject) parsed;
                JSONObject error = root.optJSONObject("error");
                if (error != null) {
                    return error.optString("message", responseText);
                }
                String message = root.optString("message", "");
                if (!message.isEmpty()) {
                    return message;
                }
            }
        } catch (Exception ignored) {
        }
        return responseText == null ? "" : responseText;
    }

    private static String tlsFailureMessage(String endpoint, SSLException e) {
        return "搜索接口 HTTPS/TLS 连接失败。当前请求地址: " + endpoint
                + "\n\n请检查搜索服务是否支持 Android/HarmonyOS 的 TLS 1.2/1.3、证书链是否有效、域名是否正确且不要用 IP 地址直连。"
                + "\n\n原始错误: " + e.getMessage();
    }

    private static String appendQueryParam(String url, String key, String value) throws Exception {
        String separator = url.contains("?")
                ? (url.endsWith("?") || url.endsWith("&") ? "" : "&")
                : "?";
        return url + separator + encode(key) + "=" + encode(value == null ? "" : value);
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name());
    }

    private static String readAll(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        try (InputStream in = inputStream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    static class SearchResult {
        final String title;
        final String snippet;
        final String url;
        final String publishedAt;

        SearchResult(String title, String snippet, String url, String publishedAt) {
            this.title = title == null ? "" : title;
            this.snippet = snippet == null ? "" : snippet;
            this.url = url == null ? "" : url;
            this.publishedAt = publishedAt == null ? "" : publishedAt;
        }

        boolean isEmpty() {
            return title.isEmpty() && snippet.isEmpty() && url.isEmpty();
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("title", title);
                json.put("snippet", snippet);
                json.put("url", url);
                json.put("publishedAt", publishedAt);
            } catch (Exception ignored) {
            }
            return json;
        }
    }

    private static class Request {
        final String method;
        final String url;
        final JSONObject body;

        Request(String method, String url, JSONObject body) {
            this.method = method;
            this.url = url;
            this.body = body;
        }
    }
}
