package com.codex.apikeychat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.SSLException;

class OpenAiClient {
    static ChatResult sendMessage(
            String apiMode,
            String baseUrl,
            String apiKey,
            String model,
            String prompt,
            List<AttachmentPayload> attachments,
            String previousResponseId,
            CancelToken cancelToken
    ) throws Exception {
        if (ApiKeyStore.MODE_CHAT_COMPLETIONS.equals(apiMode)) {
            return sendChatCompletions(baseUrl, apiKey, model, prompt, attachments, cancelToken);
        }
        return sendResponses(baseUrl, apiKey, model, prompt, attachments, previousResponseId, cancelToken);
    }

    static List<String> fetchModels(String baseUrl, String apiKey) throws Exception {
        JSONObject root = getJson(endpoint(baseUrl, "models"), apiKey, null);
        JSONArray data = root.optJSONArray("data");
        ArrayList<String> ids = new ArrayList<>();
        if (data == null) {
            return ids;
        }
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String id = item.optString("id", "");
            if (isLikelyChatModel(id)) {
                ids.add(id);
            }
        }
        Collections.sort(ids, Collections.reverseOrder());
        return ids;
    }

    static ImageResult generateImage(
            String baseUrl,
            String apiKey,
            String model,
            String prompt,
            String size,
            CancelToken cancelToken
    ) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("prompt", prompt);
        body.put("size", size == null || size.isEmpty() ? "1024x1024" : size);
        body.put("n", 1);

        JSONObject response = postJson(endpoint(baseUrl, "images/generations"), apiKey, body, cancelToken);
        JSONArray data = response.optJSONArray("data");
        if (data == null || data.length() == 0) {
            throw new IOException("生图接口没有返回图片数据: " + response);
        }
        JSONObject item = data.optJSONObject(0);
        if (item == null) {
            throw new IOException("生图接口返回格式无法识别: " + response);
        }
        String b64 = item.optString("b64_json", "");
        String url = item.optString("url", "");
        String revisedPrompt = item.optString("revised_prompt", "");
        String imageSource;
        if (!b64.isEmpty()) {
            imageSource = "data:image/png;base64," + b64;
        } else if (!url.isEmpty()) {
            imageSource = url;
        } else {
            throw new IOException("生图接口没有返回 b64_json 或 url: " + response);
        }
        return new ImageResult(imageSource, revisedPrompt);
    }

    static ImageResult generateImageViaResponsesTool(
            String baseUrl,
            String apiKey,
            String model,
            String prompt,
            String size,
            CancelToken cancelToken
    ) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("input", "Generate an image from this prompt:\n" + prompt);

        JSONObject tool = new JSONObject();
        tool.put("type", "image_generation");
        if (size != null && !size.trim().isEmpty()) {
            tool.put("size", size.trim());
        }
        JSONArray tools = new JSONArray();
        tools.put(tool);
        body.put("tools", tools);

        JSONObject response = postJson(endpoint(baseUrl, "responses"), apiKey, body, cancelToken);
        ImageResult result = extractResponsesImage(response);
        if (!result.imageSource.isEmpty()) {
            return result;
        }
        throw new IOException("Responses 生图工具没有返回 image_generation_call.result。你的第三方接口可能没有开通 /v1/responses 的 image_generation 工具: " + response);
    }

    private static ChatResult sendResponses(
            String baseUrl,
            String apiKey,
            String model,
            String prompt,
            List<AttachmentPayload> attachments,
            String previousResponseId,
            CancelToken cancelToken
    ) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);

        JSONArray input = new JSONArray();
        JSONObject user = new JSONObject();
        user.put("role", "user");

        JSONArray content = new JSONArray();
        if (prompt != null && !prompt.trim().isEmpty()) {
            JSONObject text = new JSONObject();
            text.put("type", "input_text");
            text.put("text", prompt.trim());
            content.put(text);
        }

        for (AttachmentPayload attachment : attachments) {
            JSONObject item = new JSONObject();
            if (attachment.image) {
                item.put("type", "input_image");
                item.put("image_url", attachment.dataUrl);
            } else {
                item.put("type", "input_file");
                item.put("filename", attachment.filename);
                item.put("file_data", attachment.dataUrl);
            }
            content.put(item);
        }

        user.put("content", content);
        input.put(user);
        body.put("input", input);

        if (previousResponseId != null && !previousResponseId.isEmpty()) {
            body.put("previous_response_id", previousResponseId);
        }

        JSONObject response = postJson(endpoint(baseUrl, "responses"), apiKey, body, cancelToken);
        String id = response.optString("id", previousResponseId == null ? "" : previousResponseId);
        return new ChatResult(id, extractResponsesText(response), extractResponsesReasoning(response));
    }

    private static ChatResult sendChatCompletions(
            String baseUrl,
            String apiKey,
            String model,
            String prompt,
            List<AttachmentPayload> attachments,
            CancelToken cancelToken
    ) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);

        JSONArray messages = new JSONArray();
        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", buildChatContent(prompt, attachments));
        messages.put(user);
        body.put("messages", messages);

        JSONObject response = postJson(endpoint(baseUrl, "chat/completions"), apiKey, body, cancelToken);
        ChatParts parts = extractChatCompletionParts(response);
        return new ChatResult(response.optString("id", ""), parts.text, parts.reasoning);
    }

    private static JSONArray buildChatContent(String prompt, List<AttachmentPayload> attachments) throws Exception {
        JSONArray content = new JSONArray();
        StringBuilder text = new StringBuilder();
        if (prompt != null && !prompt.trim().isEmpty()) {
            text.append(prompt.trim());
        }

        for (AttachmentPayload attachment : attachments) {
            if (attachment.image) {
                JSONObject image = new JSONObject();
                image.put("type", "image_url");
                JSONObject imageUrl = new JSONObject();
                imageUrl.put("url", attachment.dataUrl);
                image.put("image_url", imageUrl);
                content.put(image);
            } else {
                if (text.length() > 0) {
                    text.append("\n\n");
                }
                text.append("文件附件: ").append(attachment.filename).append("\n");
                text.append(attachment.dataUrl);
            }
        }

        if (text.length() > 0 || content.length() == 0) {
            JSONObject textPart = new JSONObject();
            textPart.put("type", "text");
            textPart.put("text", text.length() == 0 ? "" : text.toString());
            JSONArray ordered = new JSONArray();
            ordered.put(textPart);
            for (int i = 0; i < content.length(); i++) {
                ordered.put(content.get(i));
            }
            return ordered;
        }
        return content;
    }

    private static boolean isLikelyChatModel(String id) {
        return id.startsWith("gpt-") || id.startsWith("o") || id.startsWith("claude-") || id.startsWith("gemini-");
    }

    private static String endpoint(String baseUrl, String path) {
        String value = baseUrl == null || baseUrl.trim().isEmpty()
                ? "https://api.openai.com/v1"
                : baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        value = stripEndpointSuffix(value);
        return value + "/" + path;
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

    private static JSONObject getJson(String endpoint, String apiKey, CancelToken cancelToken) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        if (cancelToken != null) {
            cancelToken.attach(connection);
        }
        try {
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(60000);
            return parseResponse(connection, cancelToken);
        } catch (SSLException e) {
            throw new IOException(tlsFailureMessage(endpoint, e), e);
        } finally {
            if (cancelToken != null) {
                cancelToken.clear(connection);
            }
        }
    }

    private static JSONObject postJson(String endpoint, String apiKey, JSONObject body, CancelToken cancelToken) throws Exception {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        if (cancelToken != null) {
            cancelToken.attach(connection);
        }
        try {
            checkCanceled(cancelToken);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(180000);
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

    private static JSONObject parseResponse(HttpURLConnection connection, CancelToken cancelToken) throws Exception {
        checkCanceled(cancelToken);
        int code = connection.getResponseCode();
        checkCanceled(cancelToken);
        InputStream stream = code >= 200 && code < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String text = readAll(stream);
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + ": " + extractErrorMessage(text));
        }
        return new JSONObject(text);
    }

    private static void checkCanceled(CancelToken cancelToken) throws InterruptedIOException {
        if (cancelToken != null && cancelToken.isCanceled()) {
            throw new InterruptedIOException("请求已停止");
        }
    }

    private static String tlsFailureMessage(String endpoint, SSLException e) {
        return "HTTPS/TLS 连接失败。当前请求地址: " + endpoint
                + "\n\n请检查第三方 API 是否支持 Android/HarmonyOS 的 TLS 1.2/1.3、证书链是否有效、域名是否正确且不要用 IP 地址直连。"
                + "\n如果第三方只兼容 OpenAI Chat Completions，请在设置里把接口模式切到 Chat Completions。"
                + "\n\n原始错误: " + e.getMessage();
    }

    private static String extractErrorMessage(String responseText) {
        try {
            JSONObject root = new JSONObject(responseText);
            JSONObject error = root.optJSONObject("error");
            if (error != null) {
                String message = error.optString("message", responseText);
                if (message.contains("Country, region, or territory not supported")) {
                    return "当前网络位置不在 OpenAI API 支持范围内。直连 OpenAI 不可用，可在设置里填写你自己有权使用的合规后端或兼容接口地址。";
                }
                return message;
            }
        } catch (Exception ignored) {
        }
        return responseText;
    }

    private static ImageResult extractResponsesImage(JSONObject response) {
        JSONArray output = response.optJSONArray("output");
        if (output == null) {
            return new ImageResult("", "");
        }
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item == null) {
                continue;
            }
            ImageResult direct = extractImageFromObject(item);
            if (!direct.imageSource.isEmpty()) {
                return direct;
            }
            JSONArray content = item.optJSONArray("content");
            if (content == null) {
                continue;
            }
            for (int j = 0; j < content.length(); j++) {
                JSONObject part = content.optJSONObject(j);
                if (part == null) {
                    continue;
                }
                ImageResult nested = extractImageFromObject(part);
                if (!nested.imageSource.isEmpty()) {
                    return nested;
                }
            }
        }
        return new ImageResult("", "");
    }

    private static ImageResult extractImageFromObject(JSONObject item) {
        String type = item.optString("type", "");
        boolean likelyImage = type.contains("image_generation")
                || type.contains("image")
                || item.has("result")
                || item.has("b64_json")
                || item.has("image_url");
        if (!likelyImage) {
            return new ImageResult("", "");
        }
        String revisedPrompt = item.optString("revised_prompt", "");
        Object result = item.opt("result");
        String source = result instanceof String ? normalizeImageSource((String) result) : "";
        if (source.isEmpty()) {
            source = normalizeImageSource(item.optString("b64_json", ""));
        }
        if (source.isEmpty()) {
            source = normalizeImageSource(item.optString("url", ""));
        }
        if (source.isEmpty()) {
            source = normalizeImageSource(item.optString("image_url", ""));
        }
        if (source.isEmpty() && result instanceof JSONObject) {
            ImageResult nested = extractImageFromObject((JSONObject) result);
            if (!nested.imageSource.isEmpty()) {
                return nested.revisedPrompt.isEmpty() && !revisedPrompt.isEmpty()
                        ? new ImageResult(nested.imageSource, revisedPrompt)
                        : nested;
            }
        } else if (source.isEmpty() && result instanceof JSONArray) {
            JSONArray array = (JSONArray) result;
            for (int i = 0; i < array.length(); i++) {
                JSONObject nestedObject = array.optJSONObject(i);
                if (nestedObject == null) {
                    continue;
                }
                ImageResult nested = extractImageFromObject(nestedObject);
                if (!nested.imageSource.isEmpty()) {
                    return nested.revisedPrompt.isEmpty() && !revisedPrompt.isEmpty()
                            ? new ImageResult(nested.imageSource, revisedPrompt)
                            : nested;
                }
            }
        }
        return new ImageResult(source, revisedPrompt);
    }

    private static String normalizeImageSource(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("data:image/")) {
            return trimmed;
        }
        return "data:image/png;base64," + trimmed;
    }

    private static String extractResponsesText(JSONObject response) {
        String direct = response.optString("output_text", "");
        if (!direct.isEmpty()) {
            return direct;
        }

        JSONArray output = response.optJSONArray("output");
        StringBuilder builder = new StringBuilder();
        if (output != null) {
            for (int i = 0; i < output.length(); i++) {
                JSONObject item = output.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                JSONArray content = item.optJSONArray("content");
                if (content == null) {
                    continue;
                }
                for (int j = 0; j < content.length(); j++) {
                    JSONObject part = content.optJSONObject(j);
                    if (part == null) {
                        continue;
                    }
                    appendText(builder, part.optString("text", ""));
                }
            }
        }
        return builder.length() > 0 ? builder.toString() : response.toString();
    }

    private static String extractResponsesReasoning(JSONObject response) {
        JSONArray output = response.optJSONArray("output");
        StringBuilder builder = new StringBuilder();
        if (output == null) {
            return "";
        }
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String type = item.optString("type", "");
            if (!type.contains("reasoning")) {
                continue;
            }
            appendText(builder, item.optString("text", ""));
            Object summary = item.opt("summary");
            if (summary instanceof String) {
                appendText(builder, (String) summary);
            } else if (summary instanceof JSONArray) {
                JSONArray array = (JSONArray) summary;
                for (int j = 0; j < array.length(); j++) {
                    JSONObject part = array.optJSONObject(j);
                    if (part != null) {
                        appendText(builder, part.optString("text", ""));
                    }
                }
            }
            JSONArray content = item.optJSONArray("content");
            if (content != null) {
                for (int j = 0; j < content.length(); j++) {
                    JSONObject part = content.optJSONObject(j);
                    if (part != null && part.optString("type", "").contains("reasoning")) {
                        appendText(builder, part.optString("text", ""));
                    }
                }
            }
        }
        return builder.toString();
    }

    private static ChatParts extractChatCompletionParts(JSONObject response) {
        JSONArray choices = response.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            return splitThink(response.toString(), "");
        }
        JSONObject choice = choices.optJSONObject(0);
        if (choice == null) {
            return splitThink(response.toString(), "");
        }
        JSONObject message = choice.optJSONObject("message");
        if (message == null) {
            return splitThink(response.toString(), "");
        }
        String reasoning = message.optString("reasoning_content", "");
        if (reasoning.isEmpty()) {
            Object reasoningValue = message.opt("reasoning");
            if (reasoningValue instanceof String) {
                reasoning = (String) reasoningValue;
            } else if (reasoningValue instanceof JSONObject) {
                reasoning = ((JSONObject) reasoningValue).optString("text", reasoningValue.toString());
            }
        }
        Object content = message.opt("content");
        String text;
        if (content instanceof String) {
            text = (String) content;
        } else if (content instanceof JSONArray) {
            JSONArray parts = (JSONArray) content;
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.optJSONObject(i);
                if (part != null) {
                    appendText(builder, part.optString("text", ""));
                }
            }
            text = builder.length() > 0 ? builder.toString() : response.toString();
        } else {
            text = response.toString();
        }
        return splitThink(text, reasoning);
    }

    private static ChatParts splitThink(String text, String existingReasoning) {
        if (text == null) {
            return new ChatParts("", existingReasoning == null ? "" : existingReasoning);
        }
        String lower = text.toLowerCase();
        int start = lower.indexOf("<think>");
        int end = lower.indexOf("</think>");
        if (start >= 0 && end > start) {
            String reasoning = text.substring(start + 7, end).trim();
            String answer = (text.substring(0, start) + text.substring(end + 8)).trim();
            if (existingReasoning != null && !existingReasoning.trim().isEmpty()) {
                reasoning = existingReasoning.trim() + "\n\n" + reasoning;
            }
            return new ChatParts(answer, reasoning);
        }
        return new ChatParts(text, existingReasoning == null ? "" : existingReasoning);
    }

    private static void appendText(StringBuilder builder, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("\n");
        }
        builder.append(text);
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

    static class ChatResult {
        final String responseId;
        final String text;
        final String reasoning;

        ChatResult(String responseId, String text, String reasoning) {
            this.responseId = responseId;
            this.text = text;
            this.reasoning = reasoning == null ? "" : reasoning;
        }
    }

    static class ImageResult {
        final String imageSource;
        final String revisedPrompt;

        ImageResult(String imageSource, String revisedPrompt) {
            this.imageSource = imageSource == null ? "" : imageSource;
            this.revisedPrompt = revisedPrompt == null ? "" : revisedPrompt;
        }
    }

    private static class ChatParts {
        final String text;
        final String reasoning;

        ChatParts(String text, String reasoning) {
            this.text = text == null ? "" : text;
            this.reasoning = reasoning == null ? "" : reasoning;
        }
    }

    static class CancelToken {
        private volatile boolean canceled;
        private volatile HttpURLConnection connection;

        void cancel() {
            canceled = true;
            HttpURLConnection current = connection;
            if (current != null) {
                current.disconnect();
            }
        }

        boolean isCanceled() {
            return canceled;
        }

        void attach(HttpURLConnection connection) {
            this.connection = connection;
            if (canceled && connection != null) {
                connection.disconnect();
            }
        }

        void clear(HttpURLConnection connection) {
            if (this.connection == connection) {
                this.connection = null;
            }
        }
    }
}
