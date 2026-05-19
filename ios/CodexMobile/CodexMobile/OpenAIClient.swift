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
