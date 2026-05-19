import Foundation

@MainActor
final class ChatStore: ObservableObject {
    @Published private(set) var sessions: [ChatSession]
    @Published private(set) var current: ChatSession

    private let defaults: UserDefaults
    private let sessionsKey = "ios_chat_sessions"
    private let currentIdKey = "ios_current_session_id"
    private let maxSessions = 50

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        let decoded = Self.decodeSessions(from: defaults.data(forKey: sessionsKey))
        if let currentId = defaults.string(forKey: currentIdKey),
           let selected = decoded.first(where: { $0.id.uuidString == currentId }) {
            sessions = decoded
            current = selected
        } else if let first = decoded.first {
            sessions = decoded
            current = first
        } else {
            let fresh = ChatSession()
            sessions = [fresh]
            current = fresh
        }
        persist()
    }

    func newSession() {
        let fresh = ChatSession()
        current = fresh
        sessions.insert(fresh, at: 0)
        trimAndPersist()
    }

    func select(_ session: ChatSession) {
        current = session
        defaults.set(session.id.uuidString, forKey: currentIdKey)
    }

    func delete(_ session: ChatSession) {
        sessions.removeAll { $0.id == session.id }
        if sessions.isEmpty {
            sessions = [ChatSession()]
        }
        if current.id == session.id {
            current = sessions[0]
        }
        persist()
    }

    func append(_ message: ChatMessage) {
        current.messages.append(message)
        touchCurrent()
    }

    func update(_ message: ChatMessage) {
        guard let index = current.messages.firstIndex(where: { $0.id == message.id }) else { return }
        current.messages[index] = message
        touchCurrent()
    }

    func setResponseId(_ value: String?) {
        current.responseId = value
        touchCurrent()
    }

    func clearCurrentMessages() {
        current.messages = []
        current.responseId = nil
        touchCurrent()
    }

    private func touchCurrent() {
        current.updatedAt = Date()
        if let firstUser = current.messages.first(where: { $0.role == .user }) {
            let title = firstUser.text.replacingOccurrences(of: "\n", with: " ")
            current.title = String(title.prefix(24)).trimmedOrDefault("新聊天")
        }
        if let index = sessions.firstIndex(where: { $0.id == current.id }) {
            sessions[index] = current
        } else {
            sessions.insert(current, at: 0)
        }
        sessions.sort { $0.updatedAt > $1.updatedAt }
        trimAndPersist()
    }

    private func trimAndPersist() {
        if sessions.count > maxSessions {
            sessions = Array(sessions.prefix(maxSessions))
        }
        persist()
    }

    private func persist() {
        if let data = try? JSONEncoder().encode(sessions) {
            defaults.set(data, forKey: sessionsKey)
        }
        defaults.set(current.id.uuidString, forKey: currentIdKey)
    }

    private static func decodeSessions(from data: Data?) -> [ChatSession] {
        guard let data else { return [] }
        return (try? JSONDecoder().decode([ChatSession].self, from: data)) ?? []
    }
}
