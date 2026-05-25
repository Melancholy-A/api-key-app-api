package com.codex.apikeychat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
    private static final String NO_DISPLAY_TEXT_FALLBACK = "接口没有返回可展示文本，原始响应过大，已避免直接渲染。";

    static ChatResult sendMessage(
            String apiMode,
            String baseUrl,
            String apiKey,
            String model,
            String reasoningEffort,
            String prompt,
            List<AttachmentPayload> attachments,
            String previousResponseId,
            CancelToken cancelToken
    ) throws Exception {
        if (ApiKeyStore.MODE_CHAT_COMPLETIONS.equals(apiMode)) {
            return sendChatCompletions(baseUrl, apiKey, model, reasoningEffort, prompt, attachments, cancelToken);
        }
        return sendResponses(baseUrl, apiKey, model, reasoningEffort, prompt, attachments, previousResponseId, cancelToken);
    }

    static ChatResult sendMessageStreaming(
            String apiMode,
            String baseUrl,
            String apiKey,
            String model,
            String reasoningEffort,
            String prompt,
            List<AttachmentPayload> attachments,
            String previousResponseId,
            StreamCallback callback,
            CancelToken cancelToken
    ) throws Exception {
        if (ApiKeyStore.MODE_CHAT_COMPLETIONS.equals(apiMode)) {
            return sendChatCompletionsStreaming(baseUrl, apiKey, model, reasoningEffort, prompt, attachments, callback, cancelToken);
        }
        return sendResponsesStreaming(baseUrl, apiKey, model, reasoningEffort, prompt, attachments, previousResponseId, callback, cancelToken);
    }

    static ChatResult sendAgentMessage(
            String baseUrl,
            String apiKey,
            String model,
            String reasoningEffort,
            String prompt,
            List<AttachmentPayload> attachments,
            String previousResponseId,
            ToolConfig toolConfig,
            ToolHandler toolHandler,
            CancelToken cancelToken
    ) throws Exception {
        return sendAgentMessageInternal(
                baseUrl,
                apiKey,
                model,
                reasoningEffort,
                prompt,
                attachments,
                previousResponseId,
                toolConfig,
                toolHandler,
                cancelToken,
                1
        );
    }

    private static ChatResult sendAgentMessageInternal(
            String baseUrl,
            String apiKey,
            String model,
            String reasoningEffort,
            String prompt,
            List<AttachmentPayload> attachments,
            String previousResponseId,
            ToolConfig toolConfig,
            ToolHandler toolHandler,
            CancelToken cancelToken,
            int recoveryPasses
    ) throws Exception {
        JSONObject body = buildResponsesBody(model, reasoningEffort, prompt, attachments, previousResponseId);
        body.put("instructions", agentInstructions(toolConfig));
        body.put("tools", buildAgentTools(toolConfig));
        body.put("tool_choice", "auto");

        JSONObject response = postJsonWithReasoningFallback(endpoint(baseUrl, "responses"), apiKey, body, cancelToken);
        JSONArray allSources = extractResponsesSources(response);
        StringBuilder toolSummary = new StringBuilder();
        StringBuilder toolImages = new StringBuilder();
        String responseId = response.optString("id", previousResponseId == null ? "" : previousResponseId);

        for (int round = 0; round < maxToolRounds(toolConfig); round++) {
            checkCanceled(cancelToken);
            ArrayList<ToolCall> calls = extractFunctionCalls(response);
            if (calls.isEmpty() || toolHandler == null) {
                ChatResult result = responsesResult(response, responseId);
                return result.withSourcesToolsAndImages(allSources, toolSummary.toString(), toolImages.toString());
            }

            JSONArray toolOutputs = new JSONArray();
            for (ToolCall call : calls) {
                ToolResult toolResult = toolHandler.handleTool(call.name, call.arguments, cancelToken);
                if (!toolResult.summary.isEmpty()) {
                    appendText(toolSummary, "工具 " + call.name + ": " + toolResult.summary);
                }
                if (!toolResult.imageMarkdown.isEmpty()) {
                    appendText(toolImages, toolResult.imageMarkdown);
                }
                appendSources(allSources, toolResult.sources);

                JSONObject output = new JSONObject();
                output.put("type", "function_call_output");
                output.put("call_id", call.callId);
                output.put("output", toolResult.output);
                toolOutputs.put(output);
            }

            JSONObject next = new JSONObject();
            next.put("model", model);
            next.put("previous_response_id", responseId);
            next.put("input", toolOutputs);
            next.put("tools", buildAgentTools(toolConfig));
            next.put("tool_choice", "auto");
            addResponsesReasoningOptions(next, reasoningEffort);
            try {
                response = postJsonWithReasoningFallback(endpoint(baseUrl, "responses"), apiKey, next, cancelToken);
            } catch (IOException e) {
                if (!looksLikeToolOutputStateError(e.getMessage())) {
                    throw e;
                }
                return synthesizeToolFallback(
                        baseUrl,
                        apiKey,
                        model,
                        reasoningEffort,
                        prompt,
                        attachments,
                        responseId,
                        toolSummary.toString(),
                        toolImages.toString(),
                        allSources,
                        toolOutputs,
                        toolConfig,
                        toolHandler,
                        recoveryPasses,
                        cancelToken
                );
            }
            responseId = response.optString("id", responseId);
            appendSources(allSources, extractResponsesSources(response));
        }

        ChatResult result = responsesResult(response, responseId);
        appendText(toolSummary, "工具循环达到上限，已返回当前结果。");
        return result.withSourcesToolsAndImages(allSources, toolSummary.toString(), toolImages.toString());
    }

    static ChatResult sendAgentMessageStreaming(
            String baseUrl,
            String apiKey,
            String model,
            String reasoningEffort,
            String prompt,
            List<AttachmentPayload> attachments,
            String previousResponseId,
            ToolConfig toolConfig,
            ToolHandler toolHandler,
            StreamCallback callback,
            CancelToken cancelToken
    ) throws Exception {
        return sendAgentMessageStreamingInternal(
                baseUrl,
                apiKey,
                model,
                reasoningEffort,
                prompt,
                attachments,
                previousResponseId,
                toolConfig,
                toolHandler,
                callback,
                cancelToken,
                1
        );
    }

    private static ChatResult sendAgentMessageStreamingInternal(
            String baseUrl,
            String apiKey,
            String model,
            String reasoningEffort,
            String prompt,
            List<AttachmentPayload> attachments,
            String previousResponseId,
            ToolConfig toolConfig,
            ToolHandler toolHandler,
            StreamCallback callback,
            CancelToken cancelToken,
            int recoveryPasses
    ) throws Exception {
        JSONObject body = buildResponsesBody(model, reasoningEffort, prompt, attachments, previousResponseId);
        body.put("instructions", agentInstructions(toolConfig));
        body.put("tools", buildAgentTools(toolConfig));
        body.put("tool_choice", "auto");

        StreamResponse stream = postResponsesStreamWithReasoningFallback(endpoint(baseUrl, "responses"), apiKey, body, callback, cancelToken);
        JSONObject response = mergeStreamedFunctionCalls(stream.completedResponse == null ? new JSONObject() : stream.completedResponse, stream.functionCalls);
        JSONArray allSources = extractResponsesSources(response);
        StringBuilder toolSummary = new StringBuilder();
        StringBuilder toolImages = new StringBuilder();
        String responseId = response.optString("id", stream.responseId.isEmpty() ? previousResponseId == null ? "" : previousResponseId : stream.responseId);

        for (int round = 0; round < maxToolRounds(toolConfig); round++) {
            checkCanceled(cancelToken);
            ArrayList<ToolCall> calls = extractFunctionCalls(response);
            if (calls.isEmpty() || toolHandler == null) {
                ChatResult result = response.length() > 0
                        ? responsesResult(response, responseId)
                        : new ChatResult(responseId, stream.text.toString(), stream.reasoning.toString(), allSources, toolSummary.toString());
                if (isNoDisplayTextFallback(result.text) && stream.text.length() > 0) {
                    result = new ChatResult(responseId, stream.text.toString(), result.reasoning, allSources, toolSummary.toString());
                }
                return result.withSourcesToolsAndImages(allSources, toolSummary.toString(), toolImages.toString());
            }

            JSONArray toolOutputs = new JSONArray();
            for (ToolCall call : calls) {
                notifyStatus(callback, toolStatusText(call.name, true));
                ToolResult toolResult = toolHandler.handleTool(call.name, call.arguments, cancelToken);
                notifyStatus(callback, toolResult.summary.isEmpty() ? toolStatusText(call.name, false) : toolResult.summary);
                if (!toolResult.summary.isEmpty()) {
                    appendText(toolSummary, "工具 " + call.name + ": " + toolResult.summary);
                }
                if (!toolResult.imageMarkdown.isEmpty()) {
                    appendText(toolImages, toolResult.imageMarkdown);
                }
                appendSources(allSources, toolResult.sources);
                notifySources(callback, allSources);

                JSONObject output = new JSONObject();
                output.put("type", "function_call_output");
                output.put("call_id", call.callId);
                output.put("output", toolResult.output);
                toolOutputs.put(output);
            }

            JSONObject next = new JSONObject();
            next.put("model", model);
            next.put("previous_response_id", responseId);
            next.put("input", toolOutputs);
            next.put("tools", buildAgentTools(toolConfig));
            next.put("tool_choice", "auto");
            addResponsesReasoningOptions(next, reasoningEffort);
            notifyStatus(callback, "正在整理工具结果...");
            try {
                stream = postResponsesStreamWithReasoningFallback(endpoint(baseUrl, "responses"), apiKey, next, callback, cancelToken);
            } catch (IOException e) {
                if (!looksLikeToolOutputStateError(e.getMessage())) {
                    throw e;
                }
                return synthesizeToolFallbackStreaming(
                        baseUrl,
                        apiKey,
                        model,
                        reasoningEffort,
                        prompt,
                        attachments,
                        responseId,
                        toolSummary.toString(),
                        toolImages.toString(),
                        allSources,
                        toolOutputs,
                        toolConfig,
                        toolHandler,
                        recoveryPasses,
                        callback,
                        cancelToken
                );
            }
            response = mergeStreamedFunctionCalls(stream.completedResponse == null ? new JSONObject() : stream.completedResponse, stream.functionCalls);
            responseId = response.optString("id", stream.responseId.isEmpty() ? responseId : stream.responseId);
            appendSources(allSources, extractResponsesSources(response));
            notifySources(callback, allSources);
        }

        ChatResult result = response.length() > 0
                ? responsesResult(response, responseId)
                : new ChatResult(responseId, stream.text.toString(), stream.reasoning.toString(), allSources, toolSummary.toString());
        if (isNoDisplayTextFallback(result.text) && stream.text.length() > 0) {
            result = new ChatResult(responseId, stream.text.toString(), result.reasoning, allSources, toolSummary.toString());
        }
        appendText(toolSummary, "工具循环达到上限，已返回当前结果。");
        return result.withSourcesToolsAndImages(allSources, toolSummary.toString(), toolImages.toString());
    }

    private static ChatResult synthesizeToolFallback(
            String baseUrl,
            String apiKey,
            String model,
            String reasoningEffort,
            String prompt,
            List<AttachmentPayload> attachments,
            String responseId,
            String toolSummary,
            String imageMarkdown,
            JSONArray sources,
            JSONArray toolOutputs,
            ToolConfig toolConfig,
            ToolHandler toolHandler,
            int recoveryPasses,
            CancelToken cancelToken
    ) throws Exception {
        String fallbackPrompt = buildToolFallbackPrompt(prompt, toolSummary, toolOutputs, sources, recoveryPasses > 0);
        if (recoveryPasses > 0 && toolHandler != null && hasUsableTools(toolConfig)) {
            try {
                ChatResult recovered = sendAgentMessageInternal(
                        baseUrl,
                        apiKey,
                        model,
                        reasoningEffort,
                        fallbackPrompt,
                        attachments,
                        null,
                        recoveryToolConfig(toolConfig),
                        toolHandler,
                        cancelToken,
                        recoveryPasses - 1
                );
                return mergeRecoveredToolResult(recovered, responseId, imageMarkdown, sources);
            } catch (Exception ignored) {
            }
        }
        try {
            JSONObject body = buildResponsesBody(model, reasoningEffort, fallbackPrompt, attachments, null);
            JSONObject response = postJsonWithReasoningFallback(endpoint(baseUrl, "responses"), apiKey, body, cancelToken);
            String id = response.optString("id", responseId == null ? "" : responseId);
            appendSources(sources, extractResponsesSources(response));
            ChatResult result = responsesResult(response, id);
            return result.withSourcesToolsAndImages(sources, "", imageMarkdown);
        } catch (Exception fallbackError) {
            String summary = appendCompatibilityNote(toolSummary);
            return toolOnlyResult(responseId, summary, imageMarkdown, sources);
        }
    }

    private static ChatResult synthesizeToolFallbackStreaming(
            String baseUrl,
            String apiKey,
            String model,
            String reasoningEffort,
            String prompt,
            List<AttachmentPayload> attachments,
            String responseId,
            String toolSummary,
            String imageMarkdown,
            JSONArray sources,
            JSONArray toolOutputs,
            ToolConfig toolConfig,
            ToolHandler toolHandler,
            int recoveryPasses,
            StreamCallback callback,
            CancelToken cancelToken
    ) throws Exception {
        String fallbackPrompt = buildToolFallbackPrompt(prompt, toolSummary, toolOutputs, sources, recoveryPasses > 0);
        if (recoveryPasses > 0 && toolHandler != null && hasUsableTools(toolConfig)) {
            try {
                notifyStatus(callback, "正在继续完成工具任务...");
                ChatResult recovered = sendAgentMessageStreamingInternal(
                        baseUrl,
                        apiKey,
                        model,
                        reasoningEffort,
                        fallbackPrompt,
                        attachments,
                        null,
                        recoveryToolConfig(toolConfig),
                        toolHandler,
                        callback,
                        cancelToken,
                        recoveryPasses - 1
                );
                return mergeRecoveredToolResult(recovered, responseId, imageMarkdown, sources);
            } catch (Exception ignored) {
            }
        }
        try {
            notifyStatus(callback, "正在生成最终回答...");
            JSONObject body = buildResponsesBody(model, reasoningEffort, fallbackPrompt, attachments, null);
            StreamResponse stream = postResponsesStreamWithReasoningFallback(endpoint(baseUrl, "responses"), apiKey, body, callback, cancelToken);
            appendSources(sources, stream.sources);
            if (stream.completedResponse != null) {
                String id = stream.completedResponse.optString("id", stream.responseId.isEmpty() ? responseId == null ? "" : responseId : stream.responseId);
                appendSources(sources, extractResponsesSources(stream.completedResponse));
                ChatResult result = responsesResult(stream.completedResponse, id);
                if (isNoDisplayTextFallback(result.text) && stream.text.length() > 0) {
                    result = new ChatResult(id, stream.text.toString(), result.reasoning, result.sources, result.toolSummary);
                }
                return result.withSourcesToolsAndImages(sources, "", imageMarkdown);
            }
            String id = stream.responseId.isEmpty() ? responseId == null ? "" : responseId : stream.responseId;
            ChatResult result = new ChatResult(id, stream.text.toString(), stream.reasoning.toString(), sources, "");
            return result.withSourcesToolsAndImages(sources, "", imageMarkdown);
        } catch (Exception fallbackError) {
            String summary = appendCompatibilityNote(toolSummary);
            notifyStatus(callback, "工具已执行完成");
            return toolOnlyResult(responseId, summary, imageMarkdown, sources);
        }
    }

    private static String buildToolFallbackPrompt(String prompt, String toolSummary, JSONArray toolOutputs, JSONArray sources, boolean allowMoreTools) {
        StringBuilder builder = new StringBuilder();
        builder.append("你正在整理一个移动端 App 已经执行完成的工具结果。第三方接口不支持标准 function_call_output 续轮，所以工具结果会作为普通上下文提供。\n\n");
        builder.append("请基于原始用户请求、附件和工具结果，给出最终回答。\n");
        builder.append("要求：\n");
        builder.append("- 不要提到接口兼容、续轮失败、function_call_output 或内部工具名。\n");
        builder.append("- 如果工具保存了 Word、Excel、PPT、CSV、HTML 或图片文件，明确告诉用户文件已保存及路径，并简要说明内容。\n");
        builder.append("- 如果工具结果包含搜索资料，结合图片和用户问题直接回答，并保留关键来源 URL。\n");
        if (allowMoreTools) {
            builder.append("- 如果已有工具结果还不足以完成用户要求，可以继续使用可用工具；需要深度搜索、读取网页、生成文件时继续做，不要只给半截答案。\n");
            builder.append("- 不要无意义重复已经完成的保存动作；只有为了修正或补全结果时才重新生成文件。\n");
        }
        builder.append("- 回答要自然、完整，不要只复述工具日志。\n\n");
        builder.append("原始用户请求：\n");
        builder.append(prompt == null || prompt.trim().isEmpty() ? "（空）" : prompt.trim());
        builder.append("\n\n工具执行摘要：\n");
        builder.append(toolSummary == null || toolSummary.trim().isEmpty() ? "（无）" : userFacingToolSummary(toolSummary));
        builder.append("\n\n工具输出 JSON：\n");
        builder.append(limitForPrompt(toolOutputs == null ? "[]" : toolOutputs.toString(), 28000));
        JSONArray promptSources = sourcesForPrompt(sources);
        if (promptSources.length() > 0) {
            builder.append("\n\n来源 JSON：\n");
            builder.append(limitForPrompt(promptSources.toString(), 12000));
        }
        return builder.toString();
    }

    private static ChatResult mergeRecoveredToolResult(ChatResult recovered, String fallbackResponseId, String imageMarkdown, JSONArray previousSources) {
        JSONArray mergedSources = new JSONArray();
        appendSources(mergedSources, previousSources);
        if (recovered != null) {
            appendSources(mergedSources, recovered.sources);
            return recovered.withSourcesToolsAndImages(mergedSources, "", imageMarkdown);
        }
        return toolOnlyResult(fallbackResponseId, "", imageMarkdown, mergedSources);
    }

    private static boolean hasUsableTools(ToolConfig config) {
        ToolConfig value = config == null ? new ToolConfig() : config;
        return value.hostedWebSearch
                || (value.localTools && (value.openUrlTool || value.customSearchTool || value.contextSearchTool || value.imageGenerationTool || value.documentTools));
    }

    private static ToolConfig recoveryToolConfig(ToolConfig config) {
        ToolConfig source = config == null ? new ToolConfig() : config;
        ToolConfig copy = new ToolConfig();
        copy.hostedWebSearch = source.hostedWebSearch;
        copy.localTools = source.localTools;
        copy.openUrlTool = source.openUrlTool;
        copy.customSearchTool = source.customSearchTool;
        copy.contextSearchTool = source.contextSearchTool;
        copy.imageGenerationTool = source.imageGenerationTool;
        copy.documentTools = source.documentTools;
        copy.deepSearch = source.deepSearch;
        copy.quickSearchContext = source.quickSearchContext;
        copy.maxToolRounds = source.maxToolRounds;
        return copy;
    }

    private static String appendCompatibilityNote(String toolSummary) {
        StringBuilder builder = new StringBuilder(toolSummary == null ? "" : toolSummary.trim());
        appendText(builder, "当前第三方接口不支持继续提交工具结果，且最终整理回答失败，已返回 App 已执行完成的工具结果。");
        return builder.toString();
    }

    private static String limitForPrompt(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(0, maxChars)) + "\n...（内容过长，已截断）";
    }

    private static ChatResult toolOnlyResult(String responseId, String toolSummary, String imageMarkdown, JSONArray sources) {
        String summary = toolSummary == null ? "" : toolSummary.trim();
        String imageText = imageMarkdown == null ? "" : imageMarkdown.trim();
        String displayText = userFacingToolSummary(summary);
        if (displayText.isEmpty()) {
            displayText = "工具已执行完成。";
        }
        if (!imageText.isEmpty() && !displayText.contains(imageText)) {
            displayText = displayText + "\n\n" + imageText;
        }
        return new ChatResult(responseId, displayText, "", sources, summary);
    }

    private static String userFacingToolSummary(String summary) {
        if (summary == null || summary.trim().isEmpty()) {
            return "";
        }
        return summary.trim()
                .replace("工具 create_spreadsheet: ", "")
                .replace("工具 create_document: ", "")
                .replace("工具 create_presentation: ", "")
                .replace("工具 edit_document: ", "")
                .replace("工具 edit_spreadsheet: ", "")
                .replace("工具 edit_presentation: ", "")
                .replace("工具 generate_image: ", "")
                .replace("工具 custom_search: ", "")
                .replace("工具 open_url: ", "");
    }

    private static boolean isNoDisplayTextFallback(String text) {
        return text != null && text.trim().equals(NO_DISPLAY_TEXT_FALLBACK);
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
            String reasoningEffort,
            String prompt,
            List<AttachmentPayload> attachments,
            String previousResponseId,
            CancelToken cancelToken
    ) throws Exception {
        JSONObject body = buildResponsesBody(model, reasoningEffort, prompt, attachments, previousResponseId);
        JSONObject response = postJsonWithReasoningFallback(endpoint(baseUrl, "responses"), apiKey, body, cancelToken);
        String id = response.optString("id", previousResponseId == null ? "" : previousResponseId);
        return responsesResult(response, id);
    }

    private static ChatResult sendResponsesStreaming(
            String baseUrl,
            String apiKey,
            String model,
            String reasoningEffort,
            String prompt,
            List<AttachmentPayload> attachments,
            String previousResponseId,
            StreamCallback callback,
            CancelToken cancelToken
    ) throws Exception {
        JSONObject body = buildResponsesBody(model, reasoningEffort, prompt, attachments, previousResponseId);
        StreamResponse stream = postResponsesStreamWithReasoningFallback(endpoint(baseUrl, "responses"), apiKey, body, callback, cancelToken);
        if (stream.completedResponse != null) {
            String id = stream.completedResponse.optString("id", stream.responseId.isEmpty() ? previousResponseId == null ? "" : previousResponseId : stream.responseId);
            ChatResult result = responsesResult(stream.completedResponse, id);
            if (isNoDisplayTextFallback(result.text) && stream.text.length() > 0) {
                result = new ChatResult(id, stream.text.toString(), result.reasoning, result.sources, result.toolSummary);
            }
            return result;
        }
        return new ChatResult(stream.responseId, stream.text.toString(), stream.reasoning.toString(), stream.sources, "");
    }

    private static JSONObject buildResponsesBody(
            String model,
            String reasoningEffort,
            String prompt,
            List<AttachmentPayload> attachments,
            String previousResponseId
    ) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("instructions", baseInstructions());

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
                if (attachment.extractedText != null && !attachment.extractedText.trim().isEmpty()) {
                    JSONObject text = new JSONObject();
                    text.put("type", "input_text");
                    text.put("text", "Office 文件已在 App 本地提取文本: " + attachment.filename + "\n\n" + attachment.extractedText.trim());
                    content.put(text);
                } else {
                    item.put("type", "input_file");
                    item.put("filename", attachment.filename);
                    item.put("file_data", attachment.dataUrl);
                }
            }
            if (item.length() > 0) {
                content.put(item);
            }
        }

        user.put("content", content);
        input.put(user);
        body.put("input", input);

        if (previousResponseId != null && !previousResponseId.isEmpty()) {
            body.put("previous_response_id", previousResponseId);
        }
        addResponsesReasoningOptions(body, reasoningEffort);
        return body;
    }

    private static JSONArray buildAgentTools(ToolConfig config) throws Exception {
        ToolConfig value = config == null ? new ToolConfig() : config;
        JSONArray tools = new JSONArray();
        if (value.hostedWebSearch) {
            JSONObject webSearch = new JSONObject();
            webSearch.put("type", "web_search");
            tools.put(webSearch);
        }
        if (value.localTools && value.openUrlTool) {
            tools.put(functionTool(
                    "open_url",
                    "Fetch a web page URL and return a readable title, summary, and content snippet. Use this when the user asks to open, inspect, summarize, or verify a specific URL.",
                    new String[]{"url"},
                    new String[]{"URL to fetch. Must start with http:// or https://."}
            ));
        }
        if (value.localTools && value.customSearchTool) {
            tools.put(functionTool(
                    "custom_search",
                    "Search the web through the mobile app's configured provider. For normal fresh-information questions, use this once to get summary results. In deep search mode, use it first, then open only the few most relevant sources.",
                    new String[]{"query"},
                    new String[]{"Search query."}
            ));
        }
        if (value.localTools && value.contextSearchTool) {
            tools.put(functionTool(
                    "search_context",
                    "Search only the current chat's active branch, compressed context summary, and generated Office file records. Do not search other chat sessions. Use this when the user refers to earlier messages in this same chat, says '刚才/之前/那个文件/继续', asks to continue a generated file, or when important context may have been compressed.",
                    new String[]{"query"},
                    new String[]{"Natural-language query describing the context, file, requirement, or earlier discussion to retrieve."}
            ));
        }
        if (value.localTools && value.imageGenerationTool) {
            tools.put(functionTool(
                    "generate_image",
                    "Generate an image when the user explicitly asks to draw, create, or generate a picture. Do not use this for ordinary image analysis.",
                    new String[]{"prompt"},
                    new String[]{"Image generation prompt."}
            ));
        }
        if (value.localTools && value.documentTools) {
            tools.put(functionTool(
                    "create_spreadsheet",
                    "Create a native XLSX spreadsheet file in the app exports folder when the user asks for Excel, XLSX, table, spreadsheet, or sheet file. Use CSV content as the structured table payload. If the user explicitly asks for CSV, use a .csv filename.",
                    new String[]{"filename", "csv"},
                    new String[]{"File name ending in .xlsx by default, or .csv only if the user explicitly asks for CSV.", "Complete CSV content, including a header row."}
            ));
            tools.put(functionTool(
                    "create_document",
                    "Create a native DOCX Word document in the app exports folder when the user asks for Word, DOCX, report, article, plan, contract draft, or document file. Use markdown-like text for headings, paragraphs, lists, and simple tables.",
                    new String[]{"filename", "title", "markdown"},
                    new String[]{"File name ending in .docx.", "Document title.", "Document content in markdown-like plain text."}
            ));
            tools.put(functionTool(
                    "create_presentation",
                    "Create a native PPTX presentation file in the app exports folder when the user asks for PPT, PPTX, slide deck, or presentation. Use markdown with --- between slides. If the user explicitly asks for HTML slides, use a .html filename.",
                    new String[]{"filename", "title", "markdown"},
                    new String[]{"File name ending in .pptx by default, or .html only if the user explicitly asks for HTML.", "Presentation title.", "Slide markdown. Separate slides with a line containing only ---."}
            ));
            tools.put(functionTool(
                    "edit_document",
                    "Create a new DOCX copy from the uploaded Word document or supplied markdown. Use this for Word polishing, paragraph replacement, rewriting, or safe document edits. Never overwrite the original file. This safe editor may simplify complex original formatting.",
                    new String[]{"filename", "title", "markdown", "replacements_json"},
                    new String[]{"Output .docx filename.", "Document title.", "Complete edited markdown content. Leave empty only for simple find/replace on the uploaded Word document.", "JSON array like [{\"find\":\"old\",\"replace\":\"new\"}] for simple replacements; use [] when markdown is complete."}
            ));
            tools.put(functionTool(
                    "edit_spreadsheet",
                    "Create a new XLSX copy from the uploaded Excel workbook. Use operations_json to modify cells, and append_sheet_csv to append a new worksheet. Never overwrite the original file.",
                    new String[]{"filename", "operations_json", "append_sheet_name", "append_sheet_csv"},
                    new String[]{"Output .xlsx filename.", "JSON array of cell operations like [{\"sheet\":1,\"cell\":\"B2\",\"value\":\"new\"}], or [] if only appending a sheet.", "Name for the appended worksheet, or empty.", "CSV content for a new worksheet, or empty."}
            ));
            tools.put(functionTool(
                    "edit_presentation",
                    "Create a new PPTX copy from the uploaded PowerPoint or supplied slide markdown. Use this for replacing ordinary text boxes, changing titles/body text, or rebuilding simple slides. Never overwrite the original file.",
                    new String[]{"filename", "title", "markdown", "replacements_json"},
                    new String[]{"Output .pptx filename.", "Presentation title.", "Complete edited slide markdown separated by ---. Leave empty only for simple find/replace on uploaded PPT text.", "JSON array like [{\"find\":\"old\",\"replace\":\"new\"}] for simple replacements; use [] when markdown is complete."}
            ));
        }
        return tools;
    }

    private static int maxToolRounds(ToolConfig config) {
        ToolConfig value = config == null ? new ToolConfig() : config;
        return Math.max(1, Math.min(4, value.maxToolRounds));
    }

    private static String agentInstructions(ToolConfig config) {
        ToolConfig value = config == null ? new ToolConfig() : config;
        String searchMode = value.deepSearch
                ? value.quickSearchContext
                ? "当前为深度搜索模式：App 已经在用户消息里放入搜索候选来源。请先从这些候选中选择最相关的 1-3 个 URL 调用 open_url 核对，再综合回答；不要只根据摘要声称已经打开网页，也不要批量打开网页。"
                : "当前为深度搜索模式：先调用 custom_search 获取更多摘要结果，再只打开少数最相关网页核对，不要批量打开网页。"
                : value.quickSearchContext
                ? "当前为快速搜索模式：App 已经在用户消息里放入搜索结果候选。优先依据这些候选来源回答，不要再为了普通最新信息调用网页工具；只有候选明显不足或用户要求深度搜索时才继续搜索。"
                : "当前为默认自动模式：需要实时信息时优先调用 custom_search 获取摘要结果；如果应用侧搜索被关闭或工具不可用，再使用托管 web_search。普通搜索不要连续打开多个网页。";
        String realtimeRule = value.quickSearchContext
                ? "包含“最新、今天、现在、新闻、价格、官网、搜索、查一下”等实时信息意图时，优先使用用户消息里的搜索候选来源。"
                : "包含“最新、今天、现在、新闻、价格、官网、搜索、查一下”等实时信息意图时，先使用 custom_search。";
        return baseInstructions()
                + "\n你运行在一个移动端智能体外壳中。你可以按需使用工具。" + realtimeRule
                + searchMode
                + " custom_search 代表 App 配置的专用搜索服务，会返回搜索源、耗时、缓存状态和来源数量。"
                + " search_context 代表 App 本地上下文查询，但只检索当前对话当前分支、自动压缩摘要和本对话已生成 Office 文件记录；不会跨不同聊天记录查询。当用户说“刚才、之前、这个文件、继续修改、按前面要求”等当前对话上下文指代时优先调用。"
                + " open_url 只在用户给出具体 URL、要求打开来源、或深度搜索需要核对关键网页时使用。"
                + " 用户明确要 Word/DOCX/文档文件/报告文件时调用 create_document；明确要 Excel/XLSX/表格文件/工作簿时调用 create_spreadsheet；明确要 PPT/PPTX/演示稿时调用 create_presentation。默认生成原生 Office 文件，除非用户明确要求 CSV 或 HTML。Excel 单元格如果以 = 开头会保存为可计算公式；Word 中独立 LaTeX/公式行会尽量保存为 Office 公式对象；PPT 中独立公式行会用更适合演示稿的公式样式呈现。"
                + " 用户上传 Office 文件并要求修改、润色、替换文本、修改单元格、追加工作表、替换 PPT 标题或正文时，调用 edit_document、edit_spreadsheet 或 edit_presentation；所有修改都必须生成新文件，不要覆盖原文件。"
                + " 如果用户说“刚才生成的 Word/这个 Excel/上一个 PPT/继续修改这个文件”，优先对本对话最近生成的同类型 Office 文件调用对应 edit 工具，不要要求用户重复上传。"
                + " 需要生成图片时调用 generate_image。不要声称自己不能联网、不能打开网页或不能生成图片，除非工具返回失败。最终回答要直接、清楚，并在使用来源时尽量保留来源 URL。";
    }

    private static String baseInstructions() {
        return "默认使用中文和用户交流。若接口会返回 reasoning、thinking、thoughts 或思考摘要，请也使用中文表达；不要把思考摘要写成英文标题或英文段落，除非用户明确要求英文。"
                + " 输出 Markdown 时保持结构清晰：代码块必须使用独立三反引号围栏，例如 ```java 后立刻换行写代码，结束围栏也独占一行；不要把语言名、代码和反引号黏在同一行。"
                + " 输出 LaTeX 时用标准分隔符，块公式优先使用 $$...$$ 或 \\[...\\]；函数或关系符后接变量时要留空格或使用花括号，例如 \\sin x、\\cos x、P(A \\mid B)，不要写成 \\sinx、\\cosx、\\midB。";
    }

    private static String toolStatusText(String toolName, boolean running) {
        String name = toolName == null ? "" : toolName;
        if ("custom_search".equals(name)) {
            return running ? "正在搜索网页..." : "网页搜索完成";
        }
        if ("open_url".equals(name)) {
            return running ? "正在读取网页..." : "网页读取完成";
        }
        if ("search_context".equals(name)) {
            return running ? "正在查询本地上下文..." : "本地上下文查询完成";
        }
        if ("generate_image".equals(name)) {
            return running ? "正在生成图片..." : "图片生成完成";
        }
        if ("create_spreadsheet".equals(name)) {
            return running ? "正在生成表格..." : "表格生成完成";
        }
        if ("create_document".equals(name)) {
            return running ? "正在生成 Word 文档..." : "Word 文档生成完成";
        }
        if ("edit_document".equals(name)) {
            return running ? "正在生成 Word 修改版..." : "Word 修改版生成完成";
        }
        if ("edit_spreadsheet".equals(name)) {
            return running ? "正在生成 Excel 修改版..." : "Excel 修改版生成完成";
        }
        if ("edit_presentation".equals(name)) {
            return running ? "正在生成 PPT 修改版..." : "PPT 修改版生成完成";
        }
        if ("create_presentation".equals(name)) {
            return running ? "正在生成演示稿..." : "演示稿生成完成";
        }
        return running ? "正在执行工具..." : "工具执行完成";
    }

    private static JSONObject functionTool(String name, String description, String[] required, String[] descriptions) throws Exception {
        JSONObject tool = new JSONObject();
        tool.put("type", "function");
        tool.put("name", name);
        tool.put("description", description);
        JSONObject parameters = new JSONObject();
        parameters.put("type", "object");
        JSONObject properties = new JSONObject();
        JSONArray requiredArray = new JSONArray();
        for (int i = 0; i < required.length; i++) {
            JSONObject property = new JSONObject();
            property.put("type", "string");
            property.put("description", descriptions.length > i ? descriptions[i] : required[i]);
            properties.put(required[i], property);
            requiredArray.put(required[i]);
        }
        parameters.put("properties", properties);
        parameters.put("required", requiredArray);
        parameters.put("additionalProperties", false);
        tool.put("parameters", parameters);
        return tool;
    }

    private static ChatResult sendChatCompletions(
            String baseUrl,
            String apiKey,
            String model,
            String reasoningEffort,
            String prompt,
            List<AttachmentPayload> attachments,
            CancelToken cancelToken
    ) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);

        JSONArray messages = new JSONArray();
        messages.put(systemMessage());
        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", buildChatContent(prompt, attachments));
        messages.put(user);
        body.put("messages", messages);
        addChatReasoningOptions(body, reasoningEffort);

        JSONObject response = postJsonWithReasoningFallback(endpoint(baseUrl, "chat/completions"), apiKey, body, cancelToken);
        ChatParts parts = extractChatCompletionParts(response);
        return new ChatResult(response.optString("id", ""), parts.text, parts.reasoning);
    }

    private static ChatResult sendChatCompletionsStreaming(
            String baseUrl,
            String apiKey,
            String model,
            String reasoningEffort,
            String prompt,
            List<AttachmentPayload> attachments,
            StreamCallback callback,
            CancelToken cancelToken
    ) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);

        JSONArray messages = new JSONArray();
        messages.put(systemMessage());
        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", buildChatContent(prompt, attachments));
        messages.put(user);
        body.put("messages", messages);
        addChatReasoningOptions(body, reasoningEffort);

        ChatStreamState stream = postChatStreamWithReasoningFallback(endpoint(baseUrl, "chat/completions"), apiKey, body, callback, cancelToken);
        return new ChatResult(stream.responseId, stream.text.toString(), stream.reasoning.toString());
    }

    private static JSONObject systemMessage() throws Exception {
        JSONObject system = new JSONObject();
        system.put("role", "system");
        system.put("content", baseInstructions());
        return system;
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
                if (attachment.extractedText != null && !attachment.extractedText.trim().isEmpty()) {
                    text.append(attachment.extractedText.trim());
                } else {
                    text.append(attachment.dataUrl);
                }
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

    private static JSONObject postJsonWithReasoningFallback(String endpoint, String apiKey, JSONObject body, CancelToken cancelToken) throws Exception {
        try {
            return postJson(endpoint, apiKey, body, cancelToken);
        } catch (IOException e) {
            boolean removePreviousResponseId = hasPreviousResponseId(body) && looksLikePreviousResponseIdError(e.getMessage());
            boolean removeReasoning = hasReasoningOptions(body) && looksLikeReasoningOptionError(e.getMessage());
            if (!removePreviousResponseId && !removeReasoning) {
                throw e;
            }
            JSONObject retry = new JSONObject(body.toString());
            if (removePreviousResponseId) {
                removePreviousResponseId(retry);
            }
            if (removeReasoning) {
                removeReasoningOptions(retry);
            }
            return postJson(endpoint, apiKey, retry, cancelToken);
        }
    }

    private static StreamResponse postResponsesStreamWithReasoningFallback(
            String endpoint,
            String apiKey,
            JSONObject body,
            StreamCallback callback,
            CancelToken cancelToken
    ) throws Exception {
        try {
            return postResponsesStream(endpoint, apiKey, body, callback, cancelToken);
        } catch (IOException e) {
            boolean removePreviousResponseId = hasPreviousResponseId(body) && looksLikePreviousResponseIdError(e.getMessage());
            boolean removeReasoning = hasReasoningOptions(body) && looksLikeReasoningOptionError(e.getMessage());
            if (!removePreviousResponseId && !removeReasoning) {
                throw e;
            }
            JSONObject retry = new JSONObject(body.toString());
            if (removePreviousResponseId) {
                removePreviousResponseId(retry);
            }
            if (removeReasoning) {
                removeReasoningOptions(retry);
            }
            return postResponsesStream(endpoint, apiKey, retry, callback, cancelToken);
        }
    }

    private static ChatStreamState postChatStreamWithReasoningFallback(
            String endpoint,
            String apiKey,
            JSONObject body,
            StreamCallback callback,
            CancelToken cancelToken
    ) throws Exception {
        try {
            return postChatStream(endpoint, apiKey, body, callback, cancelToken);
        } catch (IOException e) {
            if (!hasReasoningOptions(body) || !looksLikeReasoningOptionError(e.getMessage())) {
                throw e;
            }
            JSONObject retry = new JSONObject(body.toString());
            removeReasoningOptions(retry);
            return postChatStream(endpoint, apiKey, retry, callback, cancelToken);
        }
    }

    private static StreamResponse postResponsesStream(
            String endpoint,
            String apiKey,
            JSONObject body,
            StreamCallback callback,
            CancelToken cancelToken
    ) throws Exception {
        JSONObject streamBody = new JSONObject(body.toString());
        streamBody.put("stream", true);
        byte[] bytes = streamBody.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        if (cancelToken != null) {
            cancelToken.attach(connection);
        }
        try {
            checkCanceled(cancelToken);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "text/event-stream, application/json");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(180000);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = connection.getOutputStream()) {
                checkCanceled(cancelToken);
                out.write(bytes);
            }

            int code = connection.getResponseCode();
            InputStream input = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code + ": " + extractErrorMessage(readAll(input)));
            }
            StreamResponse state = new StreamResponse();
            readSse(input, cancelToken, (event, data) -> handleResponsesStreamEvent(event, data, state, callback));
            return state;
        } catch (SSLException e) {
            throw new IOException(tlsFailureMessage(endpoint, e), e);
        } finally {
            if (cancelToken != null) {
                cancelToken.clear(connection);
            }
        }
    }

    private static ChatStreamState postChatStream(
            String endpoint,
            String apiKey,
            JSONObject body,
            StreamCallback callback,
            CancelToken cancelToken
    ) throws Exception {
        JSONObject streamBody = new JSONObject(body.toString());
        streamBody.put("stream", true);
        byte[] bytes = streamBody.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        if (cancelToken != null) {
            cancelToken.attach(connection);
        }
        try {
            checkCanceled(cancelToken);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "text/event-stream, application/json");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(180000);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = connection.getOutputStream()) {
                checkCanceled(cancelToken);
                out.write(bytes);
            }

            int code = connection.getResponseCode();
            InputStream input = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code + ": " + extractErrorMessage(readAll(input)));
            }
            ChatStreamState state = new ChatStreamState();
            readSse(input, cancelToken, (event, data) -> handleChatStreamEvent(data, state, callback));
            return state;
        } catch (SSLException e) {
            throw new IOException(tlsFailureMessage(endpoint, e), e);
        } finally {
            if (cancelToken != null) {
                cancelToken.clear(connection);
            }
        }
    }

    private static void readSse(InputStream input, CancelToken cancelToken, SseHandler handler) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String eventName = "";
            StringBuilder data = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                checkCanceled(cancelToken);
                if (line.isEmpty()) {
                    dispatchSse(eventName, data, handler);
                    eventName = "";
                    data.setLength(0);
                    continue;
                }
                if (line.startsWith(":")) {
                    continue;
                }
                if (line.startsWith("event:")) {
                    eventName = line.substring(6).trim();
                    continue;
                }
                if (line.startsWith("data:")) {
                    if (data.length() > 0) {
                        data.append('\n');
                    }
                    data.append(line.substring(5).trim());
                } else if (data.length() == 0 && line.trim().startsWith("{")) {
                    data.append(line.trim());
                }
            }
            dispatchSse(eventName, data, handler);
        }
    }

    private static void dispatchSse(String eventName, StringBuilder data, SseHandler handler) throws Exception {
        if (data == null || data.length() == 0) {
            return;
        }
        String value = data.toString().trim();
        if (value.isEmpty() || "[DONE]".equals(value)) {
            return;
        }
        handler.handle(eventName == null ? "" : eventName, value);
    }

    private static void handleResponsesStreamEvent(
            String eventName,
            String data,
            StreamResponse state,
            StreamCallback callback
    ) throws Exception {
        JSONObject event = new JSONObject(data);
        String type = event.optString("type", eventName == null ? "" : eventName);
        if (type.isEmpty()) {
            type = eventName == null ? "" : eventName;
        }
        if (event.has("error")) {
            JSONObject error = event.optJSONObject("error");
            throw new IOException(error == null ? event.toString() : error.optString("message", error.toString()));
        }
        if (event.has("response")) {
            JSONObject response = event.optJSONObject("response");
            if (response != null) {
                state.completedResponse = response;
                state.responseId = response.optString("id", state.responseId);
                appendSources(state.sources, extractResponsesSources(response));
                notifySources(callback, state.sources);
            }
        } else if (event.has("output") || event.has("output_text")) {
            state.completedResponse = event;
            state.responseId = event.optString("id", state.responseId);
            appendSources(state.sources, extractResponsesSources(event));
            notifySources(callback, state.sources);
        }

        String delta = firstNonEmpty(event, "delta", "text");
        if (!delta.isEmpty()) {
            if (isReasoningStreamType(type)) {
                state.reasoning.append(delta);
                notifyReasoningDelta(callback, delta);
            } else if (isTextStreamType(type)) {
                state.text.append(delta);
                notifyTextDelta(callback, delta);
            }
        }

        if (type.contains("web_search_call")) {
            if (type.contains("completed")) {
                notifyStatus(callback, "联网搜索完成");
            } else if (type.contains("searching")) {
                notifyStatus(callback, "正在联网搜索...");
            } else if (type.contains("in_progress")) {
                notifyStatus(callback, "正在准备联网搜索...");
            }
        } else if (type.contains("image_generation_call")) {
            notifyStatus(callback, type.contains("completed") ? "图片生成完成" : "正在生成图片...");
        } else if (type.contains("function_call_arguments.done")) {
            notifyStatus(callback, "正在执行工具...");
        } else if (type.contains("output_item.done")) {
            JSONObject item = event.optJSONObject("item");
            if (item != null) {
                String itemType = item.optString("type", "");
                if (itemType.contains("web_search_call")) {
                    notifyStatus(callback, "联网搜索完成");
                } else if (itemType.contains("image_generation_call")) {
                    notifyStatus(callback, "图片生成完成");
                } else if (itemType.contains("function_call")) {
                    state.functionCalls.put(item);
                    notifyStatus(callback, "正在执行工具...");
                }
            }
        }
    }

    private static void handleChatStreamEvent(
            String data,
            ChatStreamState state,
            StreamCallback callback
    ) throws Exception {
        JSONObject event = new JSONObject(data);
        if (event.has("error")) {
            JSONObject error = event.optJSONObject("error");
            throw new IOException(error == null ? event.toString() : error.optString("message", error.toString()));
        }
        state.responseId = event.optString("id", state.responseId);
        JSONArray choices = event.optJSONArray("choices");
        if (choices == null) {
            return;
        }
        for (int i = 0; i < choices.length(); i++) {
            JSONObject choice = choices.optJSONObject(i);
            if (choice == null) {
                continue;
            }
            JSONObject delta = choice.optJSONObject("delta");
            if (delta == null) {
                JSONObject message = choice.optJSONObject("message");
                if (message == null) {
                    continue;
                }
                ChatParts parts = extractChatCompletionParts(event);
                if (!parts.text.isEmpty()) {
                    state.text.append(parts.text);
                    notifyTextDelta(callback, parts.text);
                }
                if (!parts.reasoning.isEmpty()) {
                    state.reasoning.append(parts.reasoning);
                    notifyReasoningDelta(callback, parts.reasoning);
                }
                continue;
            }
            String text = delta.optString("content", "");
            if (!text.isEmpty()) {
                state.text.append(text);
                notifyTextDelta(callback, text);
            }
            String reasoning = firstNonEmpty(delta, "reasoning_content", "reasoning", "thinking", "thoughts");
            if (!reasoning.isEmpty()) {
                state.reasoning.append(reasoning);
                notifyReasoningDelta(callback, reasoning);
            }
        }
    }

    private static boolean isTextStreamType(String type) {
        String value = type == null ? "" : type;
        return value.contains("output_text.delta")
                || value.contains("response.text.delta")
                || value.contains("message.delta")
                || value.endsWith(".text.delta");
    }

    private static boolean isReasoningStreamType(String type) {
        String value = type == null ? "" : type.toLowerCase();
        return value.contains("reasoning") || value.contains("thinking");
    }

    private static void notifyTextDelta(StreamCallback callback, String delta) {
        if (callback != null && delta != null && !delta.isEmpty()) {
            callback.onTextDelta(delta);
        }
    }

    private static void notifyReasoningDelta(StreamCallback callback, String delta) {
        if (callback != null && delta != null && !delta.isEmpty()) {
            callback.onReasoningDelta(delta);
        }
    }

    private static void notifyStatus(StreamCallback callback, String status) {
        if (callback != null && status != null && !status.trim().isEmpty()) {
            callback.onStatus(status.trim());
        }
    }

    private static void notifySources(StreamCallback callback, JSONArray sources) {
        if (callback != null && sources != null && sources.length() > 0) {
            callback.onSources(sources);
        }
    }

    private static void addResponsesReasoningOptions(JSONObject body, String effort) throws Exception {
        JSONObject reasoning = new JSONObject();
        reasoning.put("effort", normalizeReasoningEffort(effort));
        reasoning.put("summary", "auto");
        body.put("reasoning", reasoning);
    }

    private static void addChatReasoningOptions(JSONObject body, String effort) throws Exception {
        body.put("reasoning_effort", normalizeReasoningEffort(effort));
    }

    private static String normalizeReasoningEffort(String effort) {
        if (ApiKeyStore.REASONING_MEDIUM.equals(effort)) {
            return ApiKeyStore.REASONING_MEDIUM;
        }
        if (ApiKeyStore.REASONING_HIGH.equals(effort)) {
            return ApiKeyStore.REASONING_HIGH;
        }
        if (ApiKeyStore.REASONING_XHIGH.equals(effort)) {
            return ApiKeyStore.REASONING_XHIGH;
        }
        return ApiKeyStore.REASONING_LOW;
    }

    private static boolean hasReasoningOptions(JSONObject body) {
        return body != null && (body.has("reasoning") || body.has("reasoning_effort"));
    }

    private static boolean hasPreviousResponseId(JSONObject body) {
        return body != null && body.has("previous_response_id");
    }

    private static void removePreviousResponseId(JSONObject body) {
        if (body != null) {
            body.remove("previous_response_id");
        }
    }

    private static void removeReasoningOptions(JSONObject body) {
        if (body == null) {
            return;
        }
        body.remove("reasoning");
        body.remove("reasoning_effort");
    }

    private static boolean looksLikeReasoningOptionError(String message) {
        String lower = message == null ? "" : message.toLowerCase();
        return lower.contains("reasoning")
                || lower.contains("reasoning_effort")
                || lower.contains("unknown parameter")
                || lower.contains("unsupported parameter")
                || lower.contains("unrecognized")
                || lower.contains("not supported");
    }

    private static boolean looksLikePreviousResponseIdError(String message) {
        String lower = message == null ? "" : message.toLowerCase();
        return lower.contains("previous_response_id")
                || lower.contains("previous response")
                || lower.contains("responses websocket");
    }

    private static boolean looksLikeToolOutputStateError(String message) {
        String lower = message == null ? "" : message.toLowerCase();
        return lower.contains("no tool call found")
                || lower.contains("function call output")
                || lower.contains("function_call_output")
                || (lower.contains("call_id") && lower.contains("tool"));
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
        if (trimmed.length() < 64 || !trimmed.matches("^[A-Za-z0-9+/=\\r\\n]+$")) {
            return "";
        }
        return "data:image/png;base64," + trimmed;
    }

    private static ChatResult responsesResult(JSONObject response, String responseId) {
        return new ChatResult(
                responseId,
                extractResponsesText(response),
                extractResponsesReasoning(response),
                extractResponsesSources(response),
                ""
        );
    }

    private static ArrayList<ToolCall> extractFunctionCalls(JSONObject response) {
        ArrayList<ToolCall> calls = new ArrayList<>();
        JSONArray output = response.optJSONArray("output");
        if (output == null) {
            return calls;
        }
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String type = item.optString("type", "");
            if (!type.contains("function_call")) {
                continue;
            }
            String callId = item.optString("call_id", item.optString("id", ""));
            String name = item.optString("name", "");
            if (callId.isEmpty() || name.isEmpty()) {
                continue;
            }
            JSONObject arguments = new JSONObject();
            Object rawArguments = item.opt("arguments");
            if (rawArguments instanceof JSONObject) {
                arguments = (JSONObject) rawArguments;
            } else if (rawArguments instanceof String && !((String) rawArguments).trim().isEmpty()) {
                try {
                    arguments = new JSONObject((String) rawArguments);
                } catch (Exception ignored) {
                }
            }
            calls.add(new ToolCall(callId, name, arguments));
        }
        return calls;
    }

    private static JSONObject mergeStreamedFunctionCalls(JSONObject response, JSONArray functionCalls) {
        if (functionCalls == null || functionCalls.length() == 0) {
            return response == null ? new JSONObject() : response;
        }
        JSONObject merged;
        try {
            merged = response == null ? new JSONObject() : new JSONObject(response.toString());
        } catch (Exception ignored) {
            merged = new JSONObject();
        }
        JSONArray output = merged.optJSONArray("output");
        if (output == null) {
            output = new JSONArray();
            try {
                merged.put("output", output);
            } catch (Exception ignored) {
                return merged;
            }
        }
        for (int i = 0; i < functionCalls.length(); i++) {
            JSONObject call = functionCalls.optJSONObject(i);
            if (call == null || hasSameFunctionCall(output, call)) {
                continue;
            }
            output.put(call);
        }
        return merged;
    }

    private static boolean hasSameFunctionCall(JSONArray output, JSONObject call) {
        String callId = call.optString("call_id", call.optString("id", ""));
        String name = call.optString("name", "");
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String oldCallId = item.optString("call_id", item.optString("id", ""));
            String oldName = item.optString("name", "");
            if (!callId.isEmpty() && callId.equals(oldCallId)) {
                return true;
            }
            if (!name.isEmpty() && name.equals(oldName) && call.optString("arguments", "").equals(item.optString("arguments", ""))) {
                return true;
            }
        }
        return false;
    }

    private static JSONArray extractResponsesSources(JSONObject response) {
        JSONArray sources = new JSONArray();
        JSONArray output = response.optJSONArray("output");
        if (output == null) {
            return sources;
        }
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item == null) {
                continue;
            }
            addSourceIfPresent(sources, item);
            Object action = item.opt("action");
            if (action instanceof JSONObject) {
                addSourceIfPresent(sources, (JSONObject) action);
                JSONArray actionSources = ((JSONObject) action).optJSONArray("sources");
                appendSources(sources, actionSources);
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
                addSourceIfPresent(sources, part);
                JSONArray annotations = part.optJSONArray("annotations");
                if (annotations == null) {
                    continue;
                }
                for (int k = 0; k < annotations.length(); k++) {
                    JSONObject annotation = annotations.optJSONObject(k);
                    if (annotation != null) {
                        addSourceIfPresent(sources, annotation);
                    }
                }
            }
        }
        return sources;
    }

    private static void addSourceIfPresent(JSONArray sources, JSONObject item) {
        String url = firstNonEmpty(item, "url", "source_url", "uri");
        if (url.isEmpty()) {
            return;
        }
        if (containsSource(sources, url)) {
            return;
        }
        JSONObject source = new JSONObject();
        try {
            source.put("url", url);
            source.put("title", firstNonEmpty(item, "title", "name", "source_title"));
            source.put("snippet", firstNonEmpty(item, "snippet", "text", "description"));
            source.put("publishedAt", firstNonEmpty(item, "published_at", "publishedAt", "date"));
            source.put("kind", "source");
            source.put("channel", "hosted");
            source.put("provider", "hosted web_search");
            sources.put(source);
        } catch (Exception ignored) {
        }
    }

    private static boolean containsSource(JSONArray sources, String url) {
        for (int i = 0; i < sources.length(); i++) {
            JSONObject item = sources.optJSONObject(i);
            if (item != null && url.equals(item.optString("url", ""))) {
                return true;
            }
        }
        return false;
    }

    private static void appendSources(JSONArray target, JSONArray values) {
        if (target == null || values == null) {
            return;
        }
        for (int i = 0; i < values.length(); i++) {
            JSONObject item = values.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String url = item.optString("url", "");
            String kind = item.optString("kind", "");
            String diagnosticKey = item.optString("diagnosticKey", "");
            if (!url.isEmpty() && mergeDuplicateSource(target, url, item)) {
                continue;
            }
            if ("diagnostic".equals(kind) && !diagnosticKey.isEmpty() && containsDiagnostic(target, diagnosticKey)) {
                continue;
            }
            target.put(sourceWithDefaultChannel(item));
        }
    }

    private static boolean mergeDuplicateSource(JSONArray target, String url, JSONObject incoming) {
        for (int i = 0; i < target.length(); i++) {
            JSONObject item = target.optJSONObject(i);
            if (item == null || !url.equals(item.optString("url", ""))) {
                continue;
            }
            mergeSourceMetadata(item, incoming);
            return true;
        }
        return false;
    }

    private static void mergeSourceMetadata(JSONObject target, JSONObject incoming) {
        if (target == null || incoming == null) {
            return;
        }
        mergeSourceField(target, incoming, "channel");
        mergeSourceField(target, incoming, "provider");
        if (target.optString("kind", "").isEmpty() && !incoming.optString("kind", "").isEmpty()) {
            try {
                target.put("kind", incoming.optString("kind", ""));
            } catch (Exception ignored) {
            }
        }
    }

    private static void mergeSourceField(JSONObject target, JSONObject incoming, String key) {
        String oldValue = target.optString(key, "");
        String newValue = incoming.optString(key, "");
        if (newValue.isEmpty() || oldValue.contains(newValue)) {
            return;
        }
        try {
            target.put(key, oldValue.isEmpty() ? newValue : oldValue + " / " + newValue);
        } catch (Exception ignored) {
        }
    }

    private static boolean containsDiagnostic(JSONArray sources, String diagnosticKey) {
        if (diagnosticKey == null || diagnosticKey.isEmpty()) {
            return false;
        }
        for (int i = 0; i < sources.length(); i++) {
            JSONObject item = sources.optJSONObject(i);
            if (item != null && diagnosticKey.equals(item.optString("diagnosticKey", ""))) {
                return true;
            }
        }
        return false;
    }

    private static JSONObject sourceWithDefaultChannel(JSONObject item) {
        try {
            JSONObject copy = new JSONObject(item.toString());
            if ("diagnostic".equals(copy.optString("kind", ""))) {
                return copy;
            }
            if (copy.optString("kind", "").isEmpty()) {
                copy.put("kind", "source");
            }
            if (copy.optString("channel", "").isEmpty()) {
                copy.put("channel", "hosted");
            }
            if (copy.optString("provider", "").isEmpty()) {
                copy.put("provider", "hosted web_search");
            }
            return copy;
        } catch (Exception ignored) {
            return item;
        }
    }

    private static JSONArray sourcesForPrompt(JSONArray sources) {
        JSONArray filtered = new JSONArray();
        if (sources == null) {
            return filtered;
        }
        for (int i = 0; i < sources.length(); i++) {
            JSONObject item = sources.optJSONObject(i);
            if (item == null || "diagnostic".equals(item.optString("kind", ""))) {
                continue;
            }
            filtered.put(item);
        }
        return filtered;
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
        if (builder.length() > 0) {
            return builder.toString();
        }
        ImageResult image = extractResponsesImage(response);
        if (!image.imageSource.isEmpty()) {
            StringBuilder markdown = new StringBuilder("![生成图片](").append(image.imageSource).append(")");
            if (!image.revisedPrompt.isEmpty()) {
                markdown.append("\n\n优化后的提示词：").append(image.revisedPrompt);
            }
            return markdown.toString();
        }
        String raw = response.toString();
        return raw.length() > 4000 ? NO_DISPLAY_TEXT_FALLBACK : raw;
    }

    private static String extractResponsesReasoning(JSONObject response) {
        StringBuilder builder = new StringBuilder();
        appendReasoningValue(builder, response.opt("reasoning"));
        appendReasoningValue(builder, response.opt("reasoning_content"));
        appendReasoningValue(builder, response.opt("thinking"));
        JSONArray output = response.optJSONArray("output");
        if (output == null) {
            return builder.toString();
        }
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String type = item.optString("type", "");
            boolean reasoningItem = type.contains("reasoning") || item.has("reasoning") || item.has("reasoning_content") || item.has("thinking");
            if (!reasoningItem) {
                continue;
            }
            appendText(builder, item.optString("text", ""));
            appendReasoningValue(builder, item.opt("summary"));
            appendReasoningValue(builder, item.opt("reasoning"));
            appendReasoningValue(builder, item.opt("reasoning_content"));
            appendReasoningValue(builder, item.opt("thinking"));
            JSONArray content = item.optJSONArray("content");
            if (content != null) {
                for (int j = 0; j < content.length(); j++) {
                    JSONObject part = content.optJSONObject(j);
                    if (part == null) {
                        continue;
                    }
                    String partType = part.optString("type", "");
                    if (partType.contains("reasoning") || part.has("reasoning") || part.has("reasoning_content") || part.has("thinking")) {
                        appendReasoningValue(builder, part);
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
        StringBuilder reasoningBuilder = new StringBuilder();
        appendReasoningValue(reasoningBuilder, message.opt("reasoning_content"));
        appendReasoningValue(reasoningBuilder, message.opt("reasoning"));
        appendReasoningValue(reasoningBuilder, message.opt("thinking"));
        appendReasoningValue(reasoningBuilder, message.opt("thoughts"));
        String reasoning = reasoningBuilder.toString();
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

    private static void appendReasoningValue(StringBuilder builder, Object value) {
        if (value == null || value == JSONObject.NULL) {
            return;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (isUsefulReasoningText(text)) {
                appendText(builder, text);
            }
            return;
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                appendReasoningValue(builder, array.opt(i));
            }
            return;
        }
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            String direct = firstNonEmpty(object,
                    "reasoning_content",
                    "reasoning_text",
                    "thinking",
                    "thoughts",
                    "summary_text",
                    "text",
                    "content"
            );
            if (isUsefulReasoningText(direct)) {
                appendText(builder, direct);
            }
            appendReasoningValue(builder, object.opt("summary"));
            appendReasoningValue(builder, object.opt("reasoning"));
            appendReasoningValue(builder, object.opt("reasoning_content"));
            return;
        }
        String text = value.toString().trim();
        if (isUsefulReasoningText(text)) {
            appendText(builder, text);
        }
    }

    private static boolean isUsefulReasoningText(String text) {
        if (text == null) {
            return false;
        }
        String value = text.trim();
        if (value.isEmpty()) {
            return false;
        }
        String lower = value.toLowerCase();
        return !("auto".equals(lower)
                || "none".equals(lower)
                || "low".equals(lower)
                || "medium".equals(lower)
                || "high".equals(lower)
                || "minimal".equals(lower)
                || "detailed".equals(lower)
                || "concise".equals(lower)
                || "summary".equals(lower)
                || "true".equals(lower)
                || "false".equals(lower));
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
        final JSONArray sources;
        final String toolSummary;

        ChatResult(String responseId, String text, String reasoning) {
            this(responseId, text, reasoning, new JSONArray(), "");
        }

        ChatResult(String responseId, String text, String reasoning, JSONArray sources, String toolSummary) {
            this.responseId = responseId;
            this.text = text;
            this.reasoning = reasoning == null ? "" : reasoning;
            this.sources = sources == null ? new JSONArray() : sources;
            this.toolSummary = toolSummary == null ? "" : toolSummary;
        }

        ChatResult withSourcesToolsAndImages(JSONArray sources, String toolSummary, String imageMarkdown) {
            String displayText = text == null ? "" : text;
            String friendlyToolSummary = userFacingToolSummary(toolSummary);
            if (!friendlyToolSummary.isEmpty() && (displayText.trim().isEmpty() || isNoDisplayTextFallback(displayText))) {
                displayText = friendlyToolSummary;
            }
            String imageText = imageMarkdown == null ? "" : imageMarkdown.trim();
            if (!imageText.isEmpty() && !displayText.contains(imageText)) {
                displayText = displayText.trim().isEmpty() ? imageText : displayText.trim() + "\n\n" + imageText;
            }
            String combinedReasoning = reasoning;
            if (toolSummary != null && !toolSummary.trim().isEmpty()) {
                combinedReasoning = toolSummary.trim()
                        + (combinedReasoning == null || combinedReasoning.trim().isEmpty() ? "" : "\n\n" + combinedReasoning.trim());
            }
            return new ChatResult(responseId, displayText, combinedReasoning, sources, toolSummary);
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

    static class ToolConfig {
        boolean hostedWebSearch = true;
        boolean localTools = true;
        boolean openUrlTool = true;
        boolean customSearchTool = true;
        boolean contextSearchTool = true;
        boolean imageGenerationTool = false;
        boolean documentTools = false;
        boolean deepSearch = false;
        boolean quickSearchContext = false;
        int maxToolRounds = 2;
    }

    interface ToolHandler {
        ToolResult handleTool(String name, JSONObject arguments, CancelToken cancelToken) throws Exception;
    }

    interface StreamCallback {
        void onTextDelta(String delta);

        void onReasoningDelta(String delta);

        void onStatus(String status);

        void onSources(JSONArray sources);
    }

    private interface SseHandler {
        void handle(String eventName, String data) throws Exception;
    }

    private static class StreamResponse {
        final StringBuilder text = new StringBuilder();
        final StringBuilder reasoning = new StringBuilder();
        final JSONArray sources = new JSONArray();
        final JSONArray functionCalls = new JSONArray();
        String responseId = "";
        JSONObject completedResponse;
    }

    private static class ChatStreamState {
        final StringBuilder text = new StringBuilder();
        final StringBuilder reasoning = new StringBuilder();
        String responseId = "";
    }

    static class ToolResult {
        final String output;
        final String summary;
        final JSONArray sources;
        final String imageMarkdown;

        ToolResult(String output, String summary, JSONArray sources) {
            this(output, summary, sources, "");
        }

        ToolResult(String output, String summary, JSONArray sources, String imageMarkdown) {
            this.output = output == null ? "" : output;
            this.summary = summary == null ? "" : summary;
            this.sources = sources == null ? new JSONArray() : sources;
            this.imageMarkdown = imageMarkdown == null ? "" : imageMarkdown;
        }
    }

    private static class ToolCall {
        final String callId;
        final String name;
        final JSONObject arguments;

        ToolCall(String callId, String name, JSONObject arguments) {
            this.callId = callId;
            this.name = name;
            this.arguments = arguments == null ? new JSONObject() : arguments;
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
