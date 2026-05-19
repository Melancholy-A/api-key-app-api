package com.codex.apikeychat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.SSLException;

class UpdateClient {
    static final String RELEASES_URL = "https://github.com/Melancholy-A/api-key-app-api/releases";
    private static final String LATEST_RELEASE_API = "https://api.github.com/repos/Melancholy-A/api-key-app-api/releases/latest";
    private static final String APK_NAME = "CodexMobile-debug.apk";

    static UpdateInfo fetchLatest(OpenAiClient.CancelToken cancelToken) throws Exception {
        JSONObject root = getJson(LATEST_RELEASE_API, cancelToken);
        String tagName = root.optString("tag_name", "");
        String name = root.optString("name", tagName);
        String htmlUrl = root.optString("html_url", RELEASES_URL);
        String assetUrl = "";
        long size = 0;
        JSONArray assets = root.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) {
                    continue;
                }
                String assetName = asset.optString("name", "");
                if (APK_NAME.equals(assetName) || assetName.endsWith(".apk")) {
                    assetUrl = asset.optString("browser_download_url", "");
                    size = asset.optLong("size", 0);
                    break;
                }
            }
        }
        return new UpdateInfo(tagName, name, htmlUrl, assetUrl, size);
    }

    static boolean isNewer(UpdateInfo info, String currentVersionName, int currentVersionCode) {
        if (info == null || !info.hasApk()) {
            return false;
        }
        int compare = compareVersions(info.normalizedVersion(), currentVersionName);
        return compare > 0;
    }

    private static JSONObject getJson(String endpoint, OpenAiClient.CancelToken cancelToken) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        if (cancelToken != null) {
            cancelToken.attach(connection);
        }
        try {
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "CodexMobile/" + BuildConfig.VERSION_NAME);
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(60000);
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String text = readAll(stream);
            if (code < 200 || code >= 300) {
                throw new IOException("更新接口 HTTP " + code + ": " + text);
            }
            return new JSONObject(text);
        } catch (SSLException e) {
            throw new IOException("检查更新 HTTPS/TLS 连接失败: " + e.getMessage(), e);
        } finally {
            if (cancelToken != null) {
                cancelToken.clear(connection);
            }
        }
    }

    private static int compareVersions(String left, String right) {
        int[] a = versionParts(left);
        int[] b = versionParts(right);
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int av = i < a.length ? a[i] : 0;
            int bv = i < b.length ? b[i] : 0;
            if (av != bv) {
                return Integer.compare(av, bv);
            }
        }
        return 0;
    }

    private static int[] versionParts(String value) {
        String cleaned = value == null ? "" : value.trim().replaceFirst("^[vV]", "");
        String[] raw = cleaned.split("[^0-9]+");
        int count = 0;
        for (String part : raw) {
            if (!part.isEmpty()) {
                count++;
            }
        }
        int[] parts = new int[count];
        int index = 0;
        for (String part : raw) {
            if (part.isEmpty()) {
                continue;
            }
            try {
                parts[index++] = Integer.parseInt(part);
            } catch (Exception ignored) {
                parts[index++] = 0;
            }
        }
        return parts;
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

    static class UpdateInfo {
        final String tagName;
        final String name;
        final String htmlUrl;
        final String assetUrl;
        final long assetSize;

        UpdateInfo(String tagName, String name, String htmlUrl, String assetUrl, long assetSize) {
            this.tagName = tagName == null ? "" : tagName;
            this.name = name == null ? "" : name;
            this.htmlUrl = htmlUrl == null ? RELEASES_URL : htmlUrl;
            this.assetUrl = assetUrl == null ? "" : assetUrl;
            this.assetSize = assetSize;
        }

        boolean hasApk() {
            return !assetUrl.isEmpty();
        }

        String normalizedVersion() {
            return tagName.isEmpty() ? name : tagName;
        }
    }
}
