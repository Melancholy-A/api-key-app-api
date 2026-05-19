import Foundation

@MainActor
final class SettingsStore: ObservableObject {
    @Published var baseUrl: String {
        didSet { defaults.set(normalizeBaseUrl(baseUrl), forKey: Keys.baseUrl) }
    }

    @Published var apiMode: ApiMode {
        didSet { defaults.set(apiMode.rawValue, forKey: Keys.apiMode) }
    }

    @Published var model: String {
        didSet { defaults.set(model.trimmedOrDefault("gpt-4.1"), forKey: Keys.model) }
    }

    @Published var imageModel: String {
        didSet { defaults.set(imageModel.trimmedOrDefault("image-2"), forKey: Keys.imageModel) }
    }

    @Published var imageRoute: ImageRoute {
        didSet { defaults.set(imageRoute.rawValue, forKey: Keys.imageRoute) }
    }

    @Published var imageSize: String {
        didSet { defaults.set(imageSize.trimmedOrDefault("1024x1024"), forKey: Keys.imageSize) }
    }

    @Published var searchEnabled: Bool {
        didSet { defaults.set(searchEnabled, forKey: Keys.searchEnabled) }
    }

    @Published var searchEndpoint: String {
        didSet { defaults.set(searchEndpoint.trimmingCharacters(in: .whitespacesAndNewlines), forKey: Keys.searchEndpoint) }
    }

    @Published var searchAuthMode: SearchAuthMode {
        didSet { defaults.set(searchAuthMode.rawValue, forKey: Keys.searchAuthMode) }
    }

    @Published var searchResultCount: Int {
        didSet { defaults.set(max(1, min(10, searchResultCount)), forKey: Keys.searchResultCount) }
    }

    @Published private(set) var hasApiKey: Bool
    @Published private(set) var hasSearchApiKey: Bool
    @Published var availableModels: [String]

    private let defaults: UserDefaults
    private let keychain: KeychainStore

    init(defaults: UserDefaults = .standard, keychain: KeychainStore = .shared) {
        self.defaults = defaults
        self.keychain = keychain
        baseUrl = normalizeBaseUrl(defaults.string(forKey: Keys.baseUrl) ?? "https://api.openai.com/v1")
        apiMode = ApiMode(rawValue: defaults.string(forKey: Keys.apiMode) ?? "") ?? .responses
        model = (defaults.string(forKey: Keys.model) ?? "gpt-4.1").trimmedOrDefault("gpt-4.1")
        imageModel = (defaults.string(forKey: Keys.imageModel) ?? "image-2").trimmedOrDefault("image-2")
        imageRoute = ImageRoute(rawValue: defaults.string(forKey: Keys.imageRoute) ?? "") ?? .responsesTool
        imageSize = (defaults.string(forKey: Keys.imageSize) ?? "1024x1024").trimmedOrDefault("1024x1024")
        searchEnabled = defaults.object(forKey: Keys.searchEnabled) as? Bool ?? false
        searchEndpoint = defaults.string(forKey: Keys.searchEndpoint) ?? ""
        searchAuthMode = SearchAuthMode(rawValue: defaults.string(forKey: Keys.searchAuthMode) ?? "") ?? .none
        let savedCount = defaults.object(forKey: Keys.searchResultCount) as? Int ?? 5
        searchResultCount = max(1, min(10, savedCount))
        hasApiKey = !keychain.read(account: Keys.apiKey).isEmpty
        hasSearchApiKey = !keychain.read(account: Keys.searchApiKey).isEmpty
        availableModels = ["gpt-4.1", "gpt-4.1-mini", "gpt-4o", "gpt-4o-mini", "o4-mini"]
    }

    func saveApiKey(_ value: String) {
        keychain.save(value, account: Keys.apiKey)
        hasApiKey = !keychain.read(account: Keys.apiKey).isEmpty
    }

    func clearApiKey() {
        keychain.delete(account: Keys.apiKey)
        hasApiKey = false
    }

    func saveSearchApiKey(_ value: String) {
        keychain.save(value, account: Keys.searchApiKey)
        hasSearchApiKey = !keychain.read(account: Keys.searchApiKey).isEmpty
    }

    func clearSearchApiKey() {
        keychain.delete(account: Keys.searchApiKey)
        hasSearchApiKey = false
    }

    func snapshot() -> AppSettingsSnapshot {
        AppSettingsSnapshot(
            apiKey: keychain.read(account: Keys.apiKey),
            baseUrl: normalizeBaseUrl(baseUrl),
            apiMode: apiMode,
            model: model.trimmedOrDefault("gpt-4.1"),
            imageModel: imageModel.trimmedOrDefault("image-2"),
            imageRoute: imageRoute,
            imageSize: imageSize.trimmedOrDefault("1024x1024"),
            searchEnabled: searchEnabled,
            searchEndpoint: searchEndpoint.trimmingCharacters(in: .whitespacesAndNewlines),
            searchAuthMode: searchAuthMode,
            searchApiKey: keychain.read(account: Keys.searchApiKey),
            searchResultCount: max(1, min(10, searchResultCount))
        )
    }

    func refreshModels() async throws {
        let snapshot = snapshot()
        let models = try await OpenAIClient().fetchModels(settings: snapshot)
        availableModels = models.isEmpty ? availableModels : models
        if let first = models.first, model.isEmpty {
            model = first
        }
    }

    private enum Keys {
        static let apiKey = "openai_api_key"
        static let searchApiKey = "search_api_key"
        static let baseUrl = "base_url"
        static let apiMode = "api_mode"
        static let model = "model"
        static let imageModel = "image_model"
        static let imageRoute = "image_route"
        static let imageSize = "image_size"
        static let searchEnabled = "search_enabled"
        static let searchEndpoint = "search_endpoint"
        static let searchAuthMode = "search_auth_mode"
        static let searchResultCount = "search_result_count"
    }
}

func normalizeBaseUrl(_ value: String) -> String {
    var trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
    if trimmed.isEmpty {
        trimmed = "https://api.openai.com/v1"
    }
    while trimmed.hasSuffix("/") {
        trimmed.removeLast()
    }
    let suffixes = ["/chat/completions", "/images/generations", "/responses", "/models"]
    let lower = trimmed.lowercased()
    for suffix in suffixes where lower.hasSuffix(suffix) {
        return String(trimmed.dropLast(suffix.count))
    }
    return trimmed
}

extension String {
    func trimmedOrDefault(_ fallback: String) -> String {
        let value = trimmingCharacters(in: .whitespacesAndNewlines)
        return value.isEmpty ? fallback : value
    }
}
