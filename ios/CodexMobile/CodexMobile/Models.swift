import Foundation

enum MessageRole: String, Codable {
    case user
    case assistant
}

enum ApiMode: String, CaseIterable, Identifiable, Codable {
    case responses
    case chatCompletions

    var id: String { rawValue }

    var label: String {
        switch self {
        case .responses:
            return "Responses API"
        case .chatCompletions:
            return "Chat Completions"
        }
    }
}

enum ImageRoute: String, CaseIterable, Identifiable, Codable {
    case responsesTool
    case imagesEndpoint

    var id: String { rawValue }

    var label: String {
        switch self {
        case .responsesTool:
            return "Responses 生图工具"
        case .imagesEndpoint:
            return "/images/generations"
        }
    }
}

enum SearchAuthMode: String, CaseIterable, Identifiable, Codable {
    case none
    case bearer
    case xApiKey
    case queryApiKey

    var id: String { rawValue }

    var label: String {
        switch self {
        case .none:
            return "不鉴权"
        case .bearer:
            return "Authorization: Bearer"
        case .xApiKey:
            return "X-API-Key"
        case .queryApiKey:
            return "api_key 参数"
        }
    }
}

struct ChatAttachment: Identifiable, Codable, Hashable {
    var id = UUID()
    var name: String
    var mimeType: String
    var dataUrl: String
    var isImage: Bool
}

struct SearchSource: Identifiable, Codable, Hashable {
    var id = UUID()
    var title: String
    var snippet: String
    var url: String
    var publishedAt: String?
}

struct ChatMessage: Identifiable, Codable, Hashable {
    var id = UUID()
    var role: MessageRole
    var text: String
    var reasoning: String?
    var imageUrl: String?
    var sources: [SearchSource] = []
    var attachments: [ChatAttachment] = []
    var createdAt = Date()
}

struct ChatSession: Identifiable, Codable, Hashable {
    var id = UUID()
    var title = "新聊天"
    var messages: [ChatMessage] = []
    var responseId: String?
    var updatedAt = Date()

    var subtitle: String {
        "\(messages.count) 条"
    }
}

struct ChatResult {
    var responseId: String?
    var text: String
    var reasoning: String?
    var sources: [SearchSource] = []
}

struct ImageResult {
    var imageUrl: String
    var revisedPrompt: String?
}

struct AppSettingsSnapshot {
    var apiKey: String
    var baseUrl: String
    var apiMode: ApiMode
    var model: String
    var imageModel: String
    var imageRoute: ImageRoute
    var imageSize: String
    var searchEnabled: Bool
    var agentToolsEnabled: Bool
    var agentImageToolEnabled: Bool
    var customInstructions: String
    var searchEndpoint: String
    var searchAuthMode: SearchAuthMode
    var searchApiKey: String
    var searchResultCount: Int
}
