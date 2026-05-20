import Foundation

enum ClientError: LocalizedError {
    case missingApiKey
    case invalidURL(String)
    case http(Int, String)
    case badResponse(String)

    var errorDescription: String? {
        switch self {
        case .missingApiKey:
            return "还没有保存 API key"
        case .invalidURL(let value):
            return "接口地址无效：\(value)"
        case .http(let code, let body):
            return "HTTP \(code): \(body)"
        case .badResponse(let value):
            return "返回格式无法识别：\(value)"
        }
    }
}

final class OpenAIClient {
    func fetchModels(settings: AppSettingsSnapshot) async throws -> [String] {
        let json = try await requestJSON(path: "models", method: "GET", body: nil, settings: settings)
        let data = json["data"] as? [[String: Any]] ?? []
        return data.compactMap { $0["id"] as? String }
            .filter { id in
                id.hasPrefix("gpt-") || id.hasPrefix("o") || id.hasPrefix("claude-") || id.hasPrefix("gemini-")
            }
            .sorted(by: >)
    }

    func sendMessage(settings: AppSettingsSnapshot, messages: [ChatMessage], previousResponseId: String?) async throws -> ChatResult {
        guard !settings.apiKey.isEmpty else { throw ClientError.missingApiKey }
        switch settings.apiMode {
        case .responses:
            if settings.agentToolsEnabled {
                return try await sendAgentResponses(settings: settings, messages: messages, previousResponseId: previousResponseId)
            }
            return try await sendResponses(settings: settings, messages: messages, previousResponseId: previousResponseId)
        case .chatCompletions:
            return try await sendChatCompletions(settings: settings, messages: messages)
        }
    }

    func generateImage(settings: AppSettingsSnapshot, prompt: String) async throws -> ImageResult {
        guard !settings.apiKey.isEmpty else { throw ClientError.missingApiKey }
        switch settings.imageRoute {
        case .imagesEndpoint:
            return try await generateViaImagesEndpoint(settings: settings, prompt: prompt)
        case .responsesTool:
            return try await generateViaResponsesTool(settings: settings, prompt: prompt)
        }
    }

    private func sendResponses(settings: AppSettingsSnapshot, messages: [ChatMessage], previousResponseId: String?) async throws -> ChatResult {
        guard let last = messages.last(where: { $0.role == .user }) else {
            throw ClientError.badResponse("没有用户消息")
        }
        var body: [String: Any] = [
            "model": settings.model,
            "input": [[
                "role": "user",
                "content": responsesContent(for: last)
            ]]
        ]
        if let previousResponseId, !previousResponseId.isEmpty {
            body["previous_response_id"] = previousResponseId
        }

        let json = try await requestJSON(path: "responses", method: "POST", body: body, settings: settings)
        return ChatResult(
            responseId: json["id"] as? String,
            text: extractResponsesText(json),
            reasoning: extractResponsesReasoning(json)
        )
    }

    private func sendAgentResponses(settings: AppSettingsSnapshot, messages: [ChatMessage], previousResponseId: String?) async throws -> ChatResult {
        guard let last = messages.last(where: { $0.role == .user }) else {
            throw ClientError.badResponse("没有用户消息")
        }
        var body: [String: Any] = [
            "model": settings.model,
            "instructions": "你运行在一个移动端智能体外壳中。你可以按需使用工具；需要实时信息时直接使用 web_search；需要读取网页时调用 open_url；需要应用侧搜索时调用 custom_search。不要声称自己不能联网或不能打开网页，除非工具返回失败。最终回答要直接、清楚，并在使用来源时尽量保留来源 URL。",
            "input": [[
                "role": "user",
                "content": responsesContent(for: last)
            ]],
            "tools": agentTools(settings: settings),
            "tool_choice": "auto"
        ]
        if let previousResponseId, !previousResponseId.isEmpty {
            body["previous_response_id"] = previousResponseId
        }

        var json = try await requestJSON(path: "responses", method: "POST", body: body, settings: settings)
        var responseId = json["id"] as? String ?? previousResponseId
        var sources = extractResponsesSources(json)
        var toolSummary: [String] = []

        for _ in 0..<4 {
            let calls = extractFunctionCalls(json)
            if calls.isEmpty { break }
            var outputs: [[String: Any]] = []
            for call in calls {
                let result = try await runTool(call: call, settings: settings)
                toolSummary.append(result.summary)
                sources.append(contentsOf: result.sources.filter { source in
                    !sources.contains(where: { $0.url == source.url })
                })
                outputs.append([
                    "type": "function_call_output",
                    "call_id": call.callId,
                    "output": result.output
                ])
            }
            var next: [String: Any] = [
                "model": settings.model,
                "input": outputs,
                "tools": agentTools(settings: settings),
                "tool_choice": "auto"
            ]
            if let responseId, !responseId.isEmpty {
                next["previous_response_id"] = responseId
            }
            json = try await requestJSON(path: "responses", method: "POST", body: next, settings: settings)
            responseId = json["id"] as? String ?? responseId
            let newSources = extractResponsesSources(json)
            sources.append(contentsOf: newSources.filter { source in
                !sources.contains(where: { $0.url == source.url })
            })
        }

        let reasoning = ([toolSummary.joined(separator: "\n")] + [extractResponsesReasoning(json) ?? ""])
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .joined(separator: "\n\n")
        return ChatResult(
            responseId: responseId,
            text: extractResponsesText(json),
            reasoning: reasoning.isEmpty ? nil : reasoning,
            sources: sources
        )
    }

    private func sendChatCompletions(settings: AppSettingsSnapshot, messages: [ChatMessage]) async throws -> ChatResult {
        let recent = messages.suffix(18)
        let payloadMessages = recent.map { message -> [String: Any] in
            if message.role == .assistant {
                return ["role": "assistant", "content": message.text]
            }
            return ["role": "user", "content": chatContent(for: message)]
        }
        let body: [String: Any] = [
            "model": settings.model,
            "messages": payloadMessages
        ]
        let json = try await requestJSON(path: "chat/completions", method: "POST", body: body, settings: settings)
        return ChatResult(
            responseId: json["id"] as? String,
            text: extractChatText(json),
            reasoning: extractChatReasoning(json)
        )
    }

    private func agentTools(settings: AppSettingsSnapshot) -> [[String: Any]] {
        var tools: [[String: Any]] = [
            ["type": "web_search"],
            functionTool(
                name: "open_url",
                description: "Fetch a web page URL and return a readable title, summary, and content snippet. Use this when the user asks to open, inspect, summarize, or verify a specific URL.",
                properties: ["url": "URL to fetch. Must start with http:// or https://."]
            ),
            functionTool(
                name: "custom_search",
                description: "Search the web through the mobile app's configured search provider. Use this if hosted web_search is insufficient, blocked, or the user asks to search a specific local/custom source.",
                properties: ["query": "Search query."]
            )
        ]
        if settings.agentImageToolEnabled {
            tools.append(functionTool(
                name: "generate_image",
                description: "Generate an image when the user explicitly asks to draw, create, or generate a picture.",
                properties: ["prompt": "Image generation prompt."]
            ))
        }
        return tools
    }

    private func functionTool(name: String, description: String, properties: [String: String]) -> [String: Any] {
        var propertySchema: [String: Any] = [:]
        for (key, value) in properties {
            propertySchema[key] = ["type": "string", "description": value]
        }
        return [
            "type": "function",
            "name": name,
            "description": description,
            "parameters": [
                "type": "object",
                "properties": propertySchema,
                "required": Array(properties.keys),
                "additionalProperties": false
            ]
        ]
    }

    private func extractFunctionCalls(_ json: [String: Any]) -> [AgentToolCall] {
        guard let output = json["output"] as? [[String: Any]] else { return [] }
        return output.compactMap { item in
            let type = item["type"] as? String ?? ""
            guard type.contains("function_call") else { return nil }
            guard let name = item["name"] as? String, !name.isEmpty else { return nil }
            let callId = item["call_id"] as? String ?? item["id"] as? String ?? ""
            guard !callId.isEmpty else { return nil }
            var arguments: [String: Any] = [:]
            if let dict = item["arguments"] as? [String: Any] {
                arguments = dict
            } else if let text = item["arguments"] as? String,
                      let data = text.data(using: .utf8),
                      let parsed = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                arguments = parsed
            }
            return AgentToolCall(callId: callId, name: name, arguments: arguments)
        }
    }

    private func runTool(call: AgentToolCall, settings: AppSettingsSnapshot) async throws -> AgentToolResult {
        switch call.name {
        case "custom_search":
            let query = (call.arguments["query"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            guard !query.isEmpty else {
                return AgentToolResult(output: "custom_search failed: missing query", summary: "custom_search 缺少 query", sources: [])
            }
            let results = try await SearchClient().search(query: query, settings: settings)
            let payload = results.map { ["title": $0.title, "snippet": $0.snippet, "url": $0.url, "publishedAt": $0.publishedAt ?? ""] }
            let output = jsonString(["query": query, "results": payload])
            return AgentToolResult(output: output, summary: "custom_search: \(query)，\(results.count) 条结果", sources: results)
        case "open_url":
            let value = (call.arguments["url"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            guard let url = URL(string: value), ["http", "https"].contains(url.scheme?.lowercased() ?? "") else {
                return AgentToolResult(output: "open_url failed: URL must start with http:// or https://", summary: "open_url 地址无效", sources: [])
            }
            let summary = try await fetchPageSummary(url: url)
            let source = SearchSource(title: value, snippet: summary, url: value, publishedAt: nil)
            return AgentToolResult(output: jsonString(["url": value, "summary": summary]), summary: "open_url: \(value)", sources: [source])
        case "generate_image":
            guard settings.agentImageToolEnabled else {
                return AgentToolResult(output: "generate_image is disabled in app settings.", summary: "自动生图未开启", sources: [])
            }
            let prompt = (call.arguments["prompt"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            guard !prompt.isEmpty else {
                return AgentToolResult(output: "generate_image failed: missing prompt", summary: "generate_image 缺少 prompt", sources: [])
            }
            let image = try await generateImage(settings: settings, prompt: prompt)
            let markdown = "![生成图片](\(image.imageUrl))"
            return AgentToolResult(output: jsonString(["markdown": markdown, "image_url": image.imageUrl]), summary: "generate_image: 已生成图片", sources: [])
        default:
            return AgentToolResult(output: "Unknown tool: \(call.name)", summary: "未知工具: \(call.name)", sources: [])
        }
    }

    private func fetchPageSummary(url: URL) async throws -> String {
        var request = URLRequest(url: url)
        request.timeoutInterval = 30
        request.setValue("Mozilla/5.0 CodexMobile-iOS/1.0", forHTTPHeaderField: "User-Agent")
        let (data, _) = try await URLSession.shared.data(for: request)
        let html = String(data: data, encoding: .utf8) ?? ""
        let title = firstRegex(#"<title[^>]*>(.*?)</title>"#, html)
        let meta = firstRegex(#"<meta[^>]+(?:name|property)=["'](?:description|og:description)["'][^>]+content=["']([^"']+)["'][^>]*>"#, html)
        let body = html
            .replacingOccurrences(of: #"<script[\s\S]*?</script>"#, with: " ", options: .regularExpression)
            .replacingOccurrences(of: #"<style[\s\S]*?</style>"#, with: " ", options: .regularExpression)
            .replacingOccurrences(of: #"<[^>]+>"#, with: " ", options: .regularExpression)
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return ["标题: \(title)", "摘要: \(meta)", "正文片段: \(String(body.prefix(900)))"]
            .filter { !$0.hasSuffix(": ") }
            .joined(separator: "。")
    }

    private func firstRegex(_ pattern: String, _ text: String) -> String {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive, .dotMatchesLineSeparators]) else { return "" }
        let ns = text as NSString
        let match = regex.firstMatch(in: text, options: [], range: NSRange(location: 0, length: ns.length))
        guard let match, match.numberOfRanges > 1, match.range(at: 1).location != NSNotFound else { return "" }
        return ns.substring(with: match.range(at: 1))
            .replacingOccurrences(of: #"<[^>]+>"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func jsonString(_ object: Any) -> String {
        guard JSONSerialization.isValidJSONObject(object),
              let data = try? JSONSerialization.data(withJSONObject: object),
              let text = String(data: data, encoding: .utf8) else {
            return "\(object)"
        }
        return text
    }

    private func generateViaImagesEndpoint(settings: AppSettingsSnapshot, prompt: String) async throws -> ImageResult {
        let body: [String: Any] = [
            "model": settings.imageModel,
            "prompt": prompt,
            "size": settings.imageSize,
            "n": 1
        ]
        let json = try await requestJSON(path: "images/generations", method: "POST", body: body, settings: settings)
        guard let data = json["data"] as? [[String: Any]], let first = data.first else {
            throw ClientError.badResponse("\(json)")
        }
        if let b64 = first["b64_json"] as? String, !b64.isEmpty {
            return ImageResult(imageUrl: "data:image/png;base64,\(b64)", revisedPrompt: first["revised_prompt"] as? String)
        }
        if let url = first["url"] as? String, !url.isEmpty {
            return ImageResult(imageUrl: url, revisedPrompt: first["revised_prompt"] as? String)
        }
        throw ClientError.badResponse("\(json)")
    }

    private func generateViaResponsesTool(settings: AppSettingsSnapshot, prompt: String) async throws -> ImageResult {
        let body: [String: Any] = [
            "model": settings.model,
            "input": "Generate an image from this prompt:\n\(prompt)",
            "tools": [[
                "type": "image_generation",
                "size": settings.imageSize
            ]]
        ]
        let json = try await requestJSON(path: "responses", method: "POST", body: body, settings: settings)
        if let image = extractResponsesImage(json) {
            return image
        }
        throw ClientError.badResponse("Responses 生图工具没有返回 image_generation_call.result")
    }

    private func requestJSON(path: String, method: String, body: [String: Any]?, settings: AppSettingsSnapshot) async throws -> [String: Any] {
        guard !settings.apiKey.isEmpty else { throw ClientError.missingApiKey }
        let urlString = "\(normalizeBaseUrl(settings.baseUrl))/\(path)"
        guard let url = URL(string: urlString) else { throw ClientError.invalidURL(urlString) }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.timeoutInterval = 90
        request.setValue("Bearer \(settings.apiKey)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("CodexMobile-iOS/1.0", forHTTPHeaderField: "User-Agent")
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: body, options: [])
        }

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw ClientError.badResponse("没有 HTTP 响应")
        }
        guard 200..<300 ~= http.statusCode else {
            let message = String(data: data, encoding: .utf8) ?? ""
            throw ClientError.http(http.statusCode, message)
        }
        let object = try JSONSerialization.jsonObject(with: data, options: [])
        guard let json = object as? [String: Any] else {
            throw ClientError.badResponse(String(data: data, encoding: .utf8) ?? "")
        }
        return json
    }

    private func responsesContent(for message: ChatMessage) -> [[String: Any]] {
        var content: [[String: Any]] = []
        if !message.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            content.append(["type": "input_text", "text": message.text])
        }
        for attachment in message.attachments {
            if attachment.isImage {
                content.append(["type": "input_image", "image_url": attachment.dataUrl])
            } else {
                content.append(["type": "input_file", "filename": attachment.name, "file_data": attachment.dataUrl])
            }
        }
        return content.isEmpty ? [["type": "input_text", "text": ""]] : content
    }

    private func chatContent(for message: ChatMessage) -> [[String: Any]] {
        var content: [[String: Any]] = [["type": "text", "text": message.text]]
        for attachment in message.attachments {
            if attachment.isImage {
                content.append(["type": "image_url", "image_url": ["url": attachment.dataUrl]])
            } else {
                content[0]["text"] = "\(message.text)\n\n文件附件：\(attachment.name)\n\(attachment.dataUrl)"
            }
        }
        return content
    }

    private func extractChatText(_ json: [String: Any]) -> String {
        guard let choice = (json["choices"] as? [[String: Any]])?.first,
              let message = choice["message"] as? [String: Any] else {
            return ""
        }
        if let value = message["content"] as? String {
            return stripThinkTags(value).text
        }
        if let array = message["content"] as? [[String: Any]] {
            return array.compactMap { $0["text"] as? String }.joined(separator: "\n")
        }
        return ""
    }

    private func extractChatReasoning(_ json: [String: Any]) -> String? {
        guard let choice = (json["choices"] as? [[String: Any]])?.first,
              let message = choice["message"] as? [String: Any] else {
            return nil
        }
        if let value = message["reasoning_content"] as? String, !value.isEmpty {
            return value
        }
        if let content = message["content"] as? String {
            return stripThinkTags(content).reasoning
        }
        return nil
    }

    private func extractResponsesText(_ json: [String: Any]) -> String {
        if let value = json["output_text"] as? String, !value.isEmpty {
            return stripThinkTags(value).text
        }
        guard let output = json["output"] as? [[String: Any]] else { return "" }
        var parts: [String] = []
        for item in output {
            if let content = item["content"] as? [[String: Any]] {
                for part in content {
                    if let text = part["text"] as? String {
                        parts.append(text)
                    } else if let text = part["content"] as? String {
                        parts.append(text)
                    }
                }
            }
        }
        return stripThinkTags(parts.joined(separator: "\n")).text
    }

    private func extractResponsesReasoning(_ json: [String: Any]) -> String? {
        guard let output = json["output"] as? [[String: Any]] else { return nil }
        var parts: [String] = []
        for item in output {
            if item["type"] as? String == "reasoning" {
                if let summary = item["summary"] as? [[String: Any]] {
                    parts.append(contentsOf: summary.compactMap { $0["text"] as? String })
                }
                if let text = item["text"] as? String {
                    parts.append(text)
                }
            }
            if let content = item["content"] as? [[String: Any]] {
                for part in content {
                    if let reasoning = part["reasoning"] as? String {
                        parts.append(reasoning)
                    }
                }
            }
        }
        let joined = parts.joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
        if !joined.isEmpty {
            return joined
        }
        return stripThinkTags(extractResponsesText(json)).reasoning
    }

    private func extractResponsesImage(_ json: [String: Any]) -> ImageResult? {
        guard let output = json["output"] as? [[String: Any]] else { return nil }
        for item in output where item["type"] as? String == "image_generation_call" {
            if let result = item["result"] as? String, !result.isEmpty {
                return ImageResult(imageUrl: "data:image/png;base64,\(result)", revisedPrompt: nil)
            }
        }
        return nil
    }

    private func extractResponsesSources(_ json: [String: Any]) -> [SearchSource] {
        guard let output = json["output"] as? [[String: Any]] else { return [] }
        var sources: [SearchSource] = []
        func add(_ item: [String: Any]) {
            let url = item["url"] as? String ?? item["source_url"] as? String ?? item["uri"] as? String ?? ""
            guard !url.isEmpty, !sources.contains(where: { $0.url == url }) else { return }
            sources.append(SearchSource(
                title: item["title"] as? String ?? item["name"] as? String ?? item["source_title"] as? String ?? url,
                snippet: item["snippet"] as? String ?? item["text"] as? String ?? item["description"] as? String ?? "",
                url: url,
                publishedAt: item["published_at"] as? String ?? item["publishedAt"] as? String
            ))
        }
        for item in output {
            add(item)
            if let action = item["action"] as? [String: Any] {
                add(action)
                if let actionSources = action["sources"] as? [[String: Any]] {
                    actionSources.forEach(add)
                }
            }
            if let content = item["content"] as? [[String: Any]] {
                for part in content {
                    add(part)
                    if let annotations = part["annotations"] as? [[String: Any]] {
                        annotations.forEach(add)
                    }
                }
            }
        }
        return sources
    }

    private func stripThinkTags(_ text: String) -> (text: String, reasoning: String?) {
        guard let start = text.range(of: "<think>", options: .caseInsensitive),
              let end = text.range(of: "</think>", options: .caseInsensitive),
              start.upperBound <= end.lowerBound else {
            return (text, nil)
        }
        let reasoning = String(text[start.upperBound..<end.lowerBound]).trimmingCharacters(in: .whitespacesAndNewlines)
        let visible = (String(text[..<start.lowerBound]) + String(text[end.upperBound...]))
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return (String(visible), reasoning.isEmpty ? nil : reasoning)
    }
}

private struct AgentToolCall {
    var callId: String
    var name: String
    var arguments: [String: Any]
}

private struct AgentToolResult {
    var output: String
    var summary: String
    var sources: [SearchSource]
}
