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
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLException;

class SearchClient {
    private static final SearchOptions FAST_OPTIONS = new SearchOptions(8000, 12000, 2500, 3500, 0);
    private static final SearchOptions DEEP_OPTIONS = new SearchOptions(15000, 25000, 7000, 10000, 3);
    private static final String DUCK_DUCK_GO_ENDPOINT = "https://duckduckgo.com/html/?q={query}";
    private static final String BING_ENDPOINT = "https://cn.bing.com/search?q={query}&count={count}";
    private static final Pattern DUCK_RESULT_PATTERN = Pattern.compile(
            "(?is)<a[^>]+class=\"[^\"]*result__a[^\"]*\"[^>]+href=\"([^\"]+)\"[^>]*>(.*?)</a>"
    );
    private static final Pattern DUCK_SNIPPET_PATTERN = Pattern.compile(
            "(?is)<(?:a|div)[^>]+class=\"[^\"]*result__snippet[^\"]*\"[^>]*>(.*?)</(?:a|div)>"
    );
    private static final Pattern BING_RESULT_PATTERN = Pattern.compile(
            "(?is)<li\\s+class=\"[^\"]*b_algo[^\"]*\"[^>]*>.*?<h2[^>]*>\\s*<a[^>]+href=\"([^\"]+)\"[^>]*>(.*?)</a>.*?</h2>(.*?)(?=<li\\s+class=\"[^\"]*b_algo|</ol>|</main>|$)"
    );
    private static final Pattern BING_SNIPPET_PATTERN = Pattern.compile(
            "(?is)<p[^>]*>(.*?)</p>"
    );
    private static final Pattern FALLBACK_LINK_PATTERN = Pattern.compile(
            "(?is)<a[^>]+href=\"([^\"]+)\"[^>]*>(.*?)</a>"
    );
    private static final Pattern TITLE_PATTERN = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern META_DESCRIPTION_PATTERN = Pattern.compile(
            "(?is)<meta[^>]+(?:name|property)=[\"'](?:description|og:description)[\"'][^>]+content=[\"']([^\"']+)[\"'][^>]*>"
    );

    static List<SearchResult> search(
            String endpoint,
            String authMode,
            String apiKey,
            int count,
            String query,
            OpenAiClient.CancelToken cancelToken
    ) throws Exception {
        return search(endpoint, authMode, apiKey, count, query, cancelToken, FAST_OPTIONS);
    }

    static List<SearchResult> search(
            String endpoint,
            String authMode,
            String apiKey,
            int count,
            String query,
            OpenAiClient.CancelToken cancelToken,
            SearchOptions options
    ) throws Exception {
        SearchOptions timing = options == null ? FAST_OPTIONS : options;
        String cleanEndpoint = endpoint == null ? "" : endpoint.trim();
        String cleanQuery = query == null ? "" : query.trim();
        if (cleanQuery.isEmpty()) {
            throw new IOException("搜索问题为空");
        }

        int limit = Math.max(1, Math.min(10, count));
        if (cleanEndpoint.isEmpty() || "builtin".equalsIgnoreCase(cleanEndpoint) || "auto".equalsIgnoreCase(cleanEndpoint)) {
            return searchBuiltIn(limit, cleanQuery, cancelToken, timing);
        }
        if ("duckduckgo".equalsIgnoreCase(cleanEndpoint) || "builtin:duckduckgo".equalsIgnoreCase(cleanEndpoint)) {
            return searchDuckDuckGo(limit, cleanQuery, cancelToken, timing);
        }
        if ("bing".equalsIgnoreCase(cleanEndpoint) || "builtin:bing".equalsIgnoreCase(cleanEndpoint)) {
            return searchBing(limit, cleanQuery, cancelToken, timing);
        }
        Request request = buildRequest(cleanEndpoint, authMode, apiKey, limit, cleanQuery);
        Object response = request.method.equals("GET")
                ? getJson(request.url, authMode, apiKey, cancelToken)
                : postJson(request.url, authMode, apiKey, request.body, cancelToken);
        return extractResults(response, limit);
    }

    private static List<SearchResult> searchBuiltIn(
            int count,
            String query,
            OpenAiClient.CancelToken cancelToken,
            SearchOptions options
    ) throws Exception {
        ArrayList<SearchResult> results = new ArrayList<>();
        ArrayList<String> errors = new ArrayList<>();
        String requestedSource = requestedSource(query);
        SearchResult portal = sourcePortalResult(query);
        if (!requestedSource.isEmpty() && portal != null) {
            addUniqueResult(results, portal, count);
        }
        for (String searchQuery : buildSearchQueries(query)) {
            try {
                addUniqueResults(results, filterBySource(searchBing(count, searchQuery, cancelToken, options), requestedSource), count);
            } catch (Exception e) {
                checkCanceled(cancelToken);
                errors.add("Bing(" + searchQuery + "): " + e.getMessage());
            }
            if (results.size() >= count) {
                break;
            }
            try {
                addUniqueResults(results, filterBySource(searchDuckDuckGo(count, searchQuery, cancelToken, options), requestedSource), count);
            } catch (Exception e) {
                checkCanceled(cancelToken);
                errors.add("DuckDuckGo(" + searchQuery + "): " + e.getMessage());
            }
            if (results.size() >= count) {
                break;
            }
        }
        if (requestedSource.isEmpty() && portal != null && results.size() < count) {
            addUniqueResult(results, portal, count);
        }
        if (!results.isEmpty()) {
            enrichResultSnippets(results, count, cancelToken, options);
            return limitResults(results, count);
        }
        throw new IOException("内置搜索源都连接失败。可在设置里填写自定义搜索接口地址。\n" + joinErrors(errors));
    }

    private static List<SearchResult> searchDuckDuckGo(
            int count,
            String query,
            OpenAiClient.CancelToken cancelToken,
            SearchOptions options
    ) throws Exception {
        String endpoint = DUCK_DUCK_GO_ENDPOINT.replace("{query}", encode(query));
        String html = getText(endpoint, cancelToken, options.connectTimeoutMs, options.readTimeoutMs);
        ArrayList<SearchResult> results = parseDuckDuckGoResults(html, count);
        if (results.isEmpty()) {
            throw new IOException("内置 DuckDuckGo 搜索没有返回可解析结果。可以在设置里填写自定义搜索接口地址。");
        }
        return results;
    }

    private static List<SearchResult> searchBing(
            int count,
            String query,
            OpenAiClient.CancelToken cancelToken,
            SearchOptions options
    ) throws Exception {
        String endpoint = BING_ENDPOINT
                .replace("{query}", encode(query))
                .replace("{count}", String.valueOf(count));
        String html = getText(endpoint, cancelToken, options.connectTimeoutMs, options.readTimeoutMs);
        ArrayList<SearchResult> results = parseBingResults(html, count);
        if (results.isEmpty()) {
            throw new IOException("内置 Bing 搜索没有返回可解析结果。可以在设置里填写自定义搜索接口地址。");
        }
        return results;
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
            connection.setRequestProperty("User-Agent", "CodexMobile/1.1 Android");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(FAST_OPTIONS.connectTimeoutMs);
            connection.setReadTimeout(FAST_OPTIONS.readTimeoutMs);
            return parseResponse(connection, cancelToken);
        } catch (SSLException e) {
            throw new IOException(tlsFailureMessage(endpoint, e), e);
        } finally {
            if (cancelToken != null) {
                cancelToken.clear(connection);
            }
        }
    }

    private static String getText(
            String endpoint,
            OpenAiClient.CancelToken cancelToken
    ) throws Exception {
        return getText(endpoint, cancelToken, FAST_OPTIONS.connectTimeoutMs, FAST_OPTIONS.readTimeoutMs);
    }

    private static String getText(
            String endpoint,
            OpenAiClient.CancelToken cancelToken,
            int connectTimeoutMs,
            int readTimeoutMs
    ) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        if (cancelToken != null) {
            cancelToken.attach(connection);
        }
        try {
            checkCanceled(cancelToken);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) CodexMobile/1.1");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);
            Object response = parseTextResponse(connection, cancelToken);
            return response == null ? "" : response.toString();
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
            connection.setRequestProperty("User-Agent", "CodexMobile/1.1 Android");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(FAST_OPTIONS.connectTimeoutMs);
            connection.setReadTimeout(FAST_OPTIONS.readTimeoutMs);
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

    private static Object parseTextResponse(HttpURLConnection connection, OpenAiClient.CancelToken cancelToken) throws Exception {
        checkCanceled(cancelToken);
        int code = connection.getResponseCode();
        checkCanceled(cancelToken);
        InputStream stream = code >= 200 && code < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String text = readAll(stream);
        if (code < 200 || code >= 300) {
            throw new IOException("内置搜索 HTTP " + code + ": " + limitText(text, 400));
        }
        return text;
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

    private static ArrayList<SearchResult> parseDuckDuckGoResults(String html, int limit) {
        ArrayList<SearchResult> results = new ArrayList<>();
        if (html == null || html.isEmpty()) {
            return results;
        }
        Matcher matcher = DUCK_RESULT_PATTERN.matcher(html);
        while (matcher.find() && results.size() < limit) {
            String url = cleanDuckUrl(matcher.group(1));
            String title = cleanHtml(matcher.group(2));
            String snippet = snippetAfter(html, matcher.end());
            SearchResult result = new SearchResult(limitText(title, 180), limitText(snippet, 900), url, "");
            if (!result.isEmpty() && !isDuplicate(results, result.url)) {
                results.add(result);
            }
        }
        if (!results.isEmpty()) {
            return results;
        }

        Matcher fallback = FALLBACK_LINK_PATTERN.matcher(html);
        while (fallback.find() && results.size() < limit) {
            String url = cleanDuckUrl(fallback.group(1));
            String title = cleanHtml(fallback.group(2));
            if (url.isEmpty() || title.isEmpty() || url.contains("duckduckgo.com/y.js")) {
                continue;
            }
            SearchResult result = new SearchResult(limitText(title, 180), "", url, "");
            if (!result.isEmpty() && !isDuplicate(results, result.url)) {
                results.add(result);
            }
        }
        return results;
    }

    private static ArrayList<String> buildSearchQueries(String query) {
        ArrayList<String> queries = new ArrayList<>();
        String terms = stripSearchInstructions(query);
        String lower = query == null ? "" : query.toLowerCase();
        boolean sourceSpecific = false;
        if (lower.contains("万方") || lower.contains("wanfang")) {
            sourceSpecific = true;
            addQuery(queries, "site:wanfangdata.com.cn " + terms);
            addQuery(queries, "site:s.wanfangdata.com.cn " + terms);
            addQuery(queries, terms + " 万方数据");
        }
        if (lower.contains("知网") || lower.contains("cnki")) {
            sourceSpecific = true;
            addQuery(queries, "site:cnki.net " + terms);
            addQuery(queries, terms + " 中国知网");
        }
        if (lower.contains("维普") || lower.contains("cqvip")) {
            sourceSpecific = true;
            addQuery(queries, "site:cqvip.com " + terms);
            addQuery(queries, terms + " 维普");
        }
        if (!sourceSpecific && containsAny(query, "文献", "论文", "期刊", "学术")) {
            addQuery(queries, terms + " 文献 论文");
        }
        if (!sourceSpecific) {
            addQuery(queries, query);
        }
        return queries;
    }

    private static String requestedSource(String query) {
        String lower = query == null ? "" : query.toLowerCase();
        if (lower.contains("万方") || lower.contains("wanfang")) {
            return "wanfang";
        }
        if (lower.contains("知网") || lower.contains("cnki")) {
            return "cnki";
        }
        if (lower.contains("维普") || lower.contains("cqvip")) {
            return "cqvip";
        }
        return "";
    }

    private static List<SearchResult> filterBySource(List<SearchResult> results, String requestedSource) {
        if (requestedSource == null || requestedSource.isEmpty() || results == null) {
            return results;
        }
        ArrayList<SearchResult> filtered = new ArrayList<>();
        for (SearchResult result : results) {
            if (matchesRequestedSource(result.url, requestedSource)) {
                filtered.add(result);
            }
        }
        return filtered;
    }

    private static boolean matchesRequestedSource(String url, String requestedSource) {
        String value = url == null ? "" : url.toLowerCase();
        if ("wanfang".equals(requestedSource)) {
            return value.contains("wanfangdata.com.cn") || value.contains("wf.pub");
        }
        if ("cnki".equals(requestedSource)) {
            return value.contains("cnki.net") || value.contains("cnki.com.cn");
        }
        if ("cqvip".equals(requestedSource)) {
            return value.contains("cqvip.com") || value.contains("cqvip.com.cn");
        }
        return true;
    }

    private static String stripSearchInstructions(String query) {
        String value = query == null ? "" : query;
        String[] removable = {
                "帮我", "请", "麻烦", "给我", "搜索", "搜一下", "搜", "检索", "查找", "查询", "查一下", "查",
                "在万方上", "在万方", "万方上", "万方", "万方数据",
                "在知网上", "在知网", "中国知网", "知网",
                "在维普上", "在维普", "维普",
                "相关文献", "相关论文", "有关文献", "有关论文", "文献", "论文", "结果", "直接"
        };
        for (String item : removable) {
            value = value.replace(item, " ");
        }
        value = value.replaceAll("(?i)\\b(search|find|lookup|look up|paper|papers|literature|article|articles)\\b", " ");
        value = value.replaceAll("[，。！？?！：:；;、（）()【】\\[\\]\"“”'`]", " ");
        value = value.replaceAll("\\s+", " ").trim();
        return value.isEmpty() ? (query == null ? "" : query.trim()) : value;
    }

    private static void addQuery(ArrayList<String> queries, String query) {
        String value = query == null ? "" : query.replaceAll("\\s+", " ").trim();
        if (value.isEmpty()) {
            return;
        }
        for (String existing : queries) {
            if (existing.equalsIgnoreCase(value)) {
                return;
            }
        }
        queries.add(value);
    }

    private static SearchResult sourcePortalResult(String query) {
        String lower = query == null ? "" : query.toLowerCase();
        String terms = stripSearchInstructions(query);
        try {
            if (lower.contains("万方") || lower.contains("wanfang")) {
                return new SearchResult(
                        "万方站内检索入口",
                        "这是按用户问题生成的万方站内检索入口，不是具体论文条目；打开后可在万方继续筛选实时结果。",
                        "https://apps.wanfangdata.com.cn/s?q=" + encode(terms),
                        ""
                );
            }
            if (lower.contains("知网") || lower.contains("cnki")) {
                return new SearchResult(
                        "中国知网站内检索入口",
                        "这是按用户问题生成的知网站内检索入口，不是具体论文条目；打开后可继续筛选实时结果。",
                        "https://kns.cnki.net/kns8/defaultresult/index?kw=" + encode(terms),
                        ""
                );
            }
            if (lower.contains("维普") || lower.contains("cqvip")) {
                return new SearchResult(
                        "维普站内检索入口",
                        "这是按用户问题生成的维普站内检索入口，不是具体论文条目；打开后可继续筛选实时结果。",
                        "https://www.cqvip.com/search?k=" + encode(terms),
                        ""
                );
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void enrichResultSnippets(
            ArrayList<SearchResult> results,
            int limit,
            OpenAiClient.CancelToken cancelToken,
            SearchOptions options
    ) {
        int maxFetches = Math.min(Math.min(results.size(), limit), options.maxPageFetches);
        for (int i = 0; i < maxFetches; i++) {
            SearchResult result = results.get(i);
            if (result.url.isEmpty() || result.snippet.length() >= 260 || !isFetchablePage(result.url)) {
                continue;
            }
            try {
                String summary = fetchPageSummary(result.url, cancelToken, options);
                if (!summary.isEmpty() && !summary.equals(result.snippet)) {
                    String snippet = result.snippet.isEmpty()
                            ? summary
                            : result.snippet + " 页面内容: " + summary;
                    results.set(i, new SearchResult(result.title, limitText(snippet, 1200), result.url, result.publishedAt));
                }
            } catch (Exception ignored) {
            }
        }
    }

    static String fetchPageSummary(String url, OpenAiClient.CancelToken cancelToken) throws Exception {
        return fetchPageSummary(url, cancelToken, FAST_OPTIONS);
    }

    static String fetchPageSummary(String url, OpenAiClient.CancelToken cancelToken, SearchOptions options) throws Exception {
        SearchOptions timing = options == null ? FAST_OPTIONS : options;
        String html = getText(url, cancelToken, timing.pageFetchConnectTimeoutMs, timing.pageFetchReadTimeoutMs);
        String meta = firstMatch(META_DESCRIPTION_PATTERN, html);
        String title = firstMatch(TITLE_PATTERN, html);
        String body = cleanHtml(html)
                .replaceAll("(?i)\\b(JavaScript|CSS|cookie|privacy policy)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
        StringBuilder builder = new StringBuilder();
        if (!title.isEmpty()) {
            builder.append("标题: ").append(title).append("。");
        }
        if (!meta.isEmpty()) {
            builder.append("摘要: ").append(meta).append("。");
        }
        if (body.length() > 120 && !body.equals(title) && !body.equals(meta)) {
            builder.append("正文片段: ").append(limitText(body, 700));
        }
        return limitText(builder.toString(), 1000);
    }

    private static String firstMatch(Pattern pattern, String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        Matcher matcher = pattern.matcher(html);
        return matcher.find() ? cleanHtml(matcher.group(1)) : "";
    }

    private static ArrayList<SearchResult> limitResults(List<SearchResult> results, int limit) {
        ArrayList<SearchResult> limited = new ArrayList<>();
        for (SearchResult result : results) {
            if (limited.size() >= limit) {
                break;
            }
            if (!result.isEmpty() && !isDuplicate(limited, result.url)) {
                limited.add(result);
            }
        }
        return limited;
    }

    private static void addUniqueResults(ArrayList<SearchResult> target, List<SearchResult> incoming, int limit) {
        if (incoming == null) {
            return;
        }
        for (SearchResult result : incoming) {
            addUniqueResult(target, result, limit);
            if (target.size() >= limit) {
                break;
            }
        }
    }

    private static void addUniqueResult(ArrayList<SearchResult> target, SearchResult result, int limit) {
        if (result == null || result.isEmpty() || target.size() >= limit) {
            return;
        }
        if (!isDuplicate(target, result.url)) {
            target.add(result);
        }
    }

    private static ArrayList<SearchResult> parseBingResults(String html, int limit) {
        ArrayList<SearchResult> results = new ArrayList<>();
        if (html == null || html.isEmpty()) {
            return results;
        }
        Matcher matcher = BING_RESULT_PATTERN.matcher(html);
        while (matcher.find() && results.size() < limit) {
            String url = decodeHtml(matcher.group(1)).trim();
            String title = cleanHtml(matcher.group(2));
            String block = matcher.group(3);
            String snippet = "";
            Matcher snippetMatcher = BING_SNIPPET_PATTERN.matcher(block);
            if (snippetMatcher.find()) {
                snippet = cleanHtml(snippetMatcher.group(1));
            }
            SearchResult result = new SearchResult(limitText(title, 180), limitText(snippet, 900), url, "");
            if (!result.isEmpty() && !isDuplicate(results, result.url)) {
                results.add(result);
            }
        }
        if (!results.isEmpty()) {
            return results;
        }

        Matcher fallback = FALLBACK_LINK_PATTERN.matcher(html);
        while (fallback.find() && results.size() < limit) {
            String url = decodeHtml(fallback.group(1)).trim();
            String title = cleanHtml(fallback.group(2));
            if (!isUsefulWebUrl(url) || title.isEmpty()) {
                continue;
            }
            SearchResult result = new SearchResult(limitText(title, 180), "", url, "");
            if (!result.isEmpty() && !isDuplicate(results, result.url)) {
                results.add(result);
            }
        }
        return results;
    }

    private static String snippetAfter(String html, int start) {
        int end = Math.min(html.length(), start + 2500);
        Matcher snippet = DUCK_SNIPPET_PATTERN.matcher(html.substring(start, end));
        if (snippet.find()) {
            return cleanHtml(snippet.group(1));
        }
        return "";
    }

    private static String cleanDuckUrl(String value) {
        if (value == null) {
            return "";
        }
        String url = decodeHtml(value.trim());
        if (url.startsWith("//")) {
            url = "https:" + url;
        }
        int uddg = url.indexOf("uddg=");
        if (uddg >= 0) {
            int start = uddg + 5;
            int end = url.indexOf('&', start);
            String encoded = end >= 0 ? url.substring(start, end) : url.substring(start);
            try {
                return URLDecoder.decode(encoded, StandardCharsets.UTF_8.name());
            } catch (Exception ignored) {
                return encoded;
            }
        }
        if (url.startsWith("/l/?")) {
            return "";
        }
        return url;
    }

    private static String cleanHtml(String html) {
        if (html == null) {
            return "";
        }
        String withoutTags = html.replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ");
        return decodeHtml(withoutTags).replaceAll("\\s+", " ").trim();
    }

    private static String decodeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ");
    }

    private static boolean isDuplicate(List<SearchResult> results, String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        for (SearchResult result : results) {
            if (url.equals(result.url)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUsefulWebUrl(String url) {
        if (url == null) {
            return false;
        }
        String value = url.trim().toLowerCase();
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            return false;
        }
        return !value.contains("bing.com")
                && !value.contains("microsoft.com")
                && !value.contains("go.microsoft.com")
                && !value.contains("javascript:");
    }

    private static boolean isFetchablePage(String url) {
        String value = url == null ? "" : url.trim().toLowerCase();
        return isUsefulWebUrl(value)
                && !value.endsWith(".pdf")
                && !value.endsWith(".doc")
                && !value.endsWith(".docx")
                && !value.endsWith(".xls")
                && !value.endsWith(".xlsx")
                && !value.endsWith(".ppt")
                && !value.endsWith(".pptx")
                && !value.endsWith(".zip")
                && !value.contains("/download");
    }

    private static boolean containsAny(String value, String... needles) {
        if (value == null) {
            return false;
        }
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String joinErrors(List<String> errors) {
        StringBuilder builder = new StringBuilder();
        for (String error : errors) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append("- ").append(error == null ? "" : error);
        }
        return builder.toString();
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

    static class SearchOptions {
        final int connectTimeoutMs;
        final int readTimeoutMs;
        final int pageFetchConnectTimeoutMs;
        final int pageFetchReadTimeoutMs;
        final int maxPageFetches;

        SearchOptions(int connectTimeoutMs, int readTimeoutMs, int pageFetchConnectTimeoutMs, int pageFetchReadTimeoutMs, int maxPageFetches) {
            this.connectTimeoutMs = connectTimeoutMs;
            this.readTimeoutMs = readTimeoutMs;
            this.pageFetchConnectTimeoutMs = pageFetchConnectTimeoutMs;
            this.pageFetchReadTimeoutMs = pageFetchReadTimeoutMs;
            this.maxPageFetches = Math.max(0, maxPageFetches);
        }

        static SearchOptions fast() {
            return FAST_OPTIONS;
        }

        static SearchOptions deep() {
            return DEEP_OPTIONS;
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
