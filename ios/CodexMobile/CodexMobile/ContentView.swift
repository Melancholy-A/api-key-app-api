import SwiftUI
import PhotosUI
import UniformTypeIdentifiers
import AVFoundation
import UIKit
import WebKit

struct ContentView: View {
    @EnvironmentObject private var settings: SettingsStore
    @EnvironmentObject private var store: ChatStore
    @StateObject private var viewModel = ChatViewModel()
    @StateObject private var speechPlayer = SpeechPlayer()

    @State private var showingSettings = false
    @State private var showingHistory = false
    @State private var showingImporter = false
    @State private var selectedPhoto: PhotosPickerItem?
    @State private var browserURL: URL?

    var body: some View {
        GeometryReader { proxy in
            let wide = proxy.size.width >= 760
            HStack(spacing: 0) {
                if wide {
                    HistorySidebar()
                        .frame(width: min(320, proxy.size.width * 0.32))
                        .environmentObject(store)
                    Divider()
                }
                mainChat(wide: wide)
            }
            .background(Color(.systemBackground))
        }
        .sheet(isPresented: $showingSettings) {
            SettingsView()
                .environmentObject(settings)
        }
        .sheet(isPresented: $showingHistory) {
            NavigationStack {
                HistorySidebar()
                    .environmentObject(store)
                    .navigationTitle("历史")
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("关闭") { showingHistory = false }
                        }
                    }
            }
        }
        .sheet(item: $browserURL) { url in
            WebBrowserView(url: url)
        }
        .fileImporter(isPresented: $showingImporter, allowedContentTypes: [.item], allowsMultipleSelection: true) { result in
            viewModel.handleImportedFiles(result)
        }
        .onChange(of: selectedPhoto) { item in
            guard let item else { return }
            Task {
                await viewModel.addPhoto(item)
                selectedPhoto = nil
            }
        }
    }

    private func mainChat(wide: Bool) -> some View {
        VStack(spacing: 0) {
            ChatTopBar(
                wide: wide,
                onHistory: { showingHistory = true },
                onSettings: { showingSettings = true },
                onNewChat: { store.newSession() },
                onOpenURL: { url in browserURL = url }
            )
            .environmentObject(settings)
            .environmentObject(store)
            .environmentObject(viewModel)

            Divider()

            MessageList(onOpenURL: { browserURL = $0 }, onRevise: { message in
                viewModel.startRevision(from: message)
            })
            .environmentObject(store)
            .environmentObject(speechPlayer)

            Divider()

            Composer(
                selectedPhoto: $selectedPhoto,
                showingImporter: $showingImporter,
                onOpenURL: { url in browserURL = url },
                onSend: { viewModel.send(store: store, settings: settings) },
                onGenerateImage: { viewModel.generateImage(store: store, settings: settings) },
                onStop: { viewModel.stop() }
            )
            .environmentObject(viewModel)
        }
    }
}

@MainActor
final class ChatViewModel: ObservableObject {
    @Published var draft = ""
    @Published var pendingAttachments: [ChatAttachment] = []
    @Published var isBusy = false
    @Published var status = ""
    @Published var toolsExpanded = false

    private var runningTask: Task<Void, Never>?
    private var revisionTarget: ChatMessage?

    func send(store: ChatStore, settings: SettingsStore) {
        let visibleText = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !visibleText.isEmpty || !pendingAttachments.isEmpty else { return }
        let snapshot = settings.snapshot()
        guard !snapshot.apiKey.isEmpty else {
            status = "请先在设置里保存 API key"
            return
        }

        let prompt: String
        if let revisionTarget {
            prompt = "请基于下面这条消息继续处理。\n\n原消息：\n\(revisionTarget.text)\n\n修改要求：\n\(visibleText)"
        } else {
            prompt = visibleText
        }

        var userMessage = ChatMessage(role: .user, text: visibleText, attachments: pendingAttachments)
        let attachments = pendingAttachments
        draft = ""
        pendingAttachments = []
        self.revisionTarget = nil
        store.append(userMessage)
        isBusy = true
        status = snapshot.agentToolsEnabled && snapshot.apiMode == .responses ? "智能体正在判断工具..." : (snapshot.searchEnabled ? "正在搜索..." : "正在请求...")

        runningTask = Task {
            do {
                var requestMessages = store.current.messages
                if snapshot.searchEnabled && !(snapshot.agentToolsEnabled && snapshot.apiMode == .responses) {
                    let sources = try await SearchClient().search(query: prompt, settings: snapshot)
                    userMessage.sources = sources
                    store.update(userMessage)
                    requestMessages = store.current.messages
                    if let index = requestMessages.lastIndex(where: { $0.id == userMessage.id }) {
                        requestMessages[index].text = applyCustomInstructions(
                            searchPrompt(userPrompt: prompt, sources: sources),
                            instructions: snapshot.customInstructions
                        )
                        requestMessages[index].attachments = attachments
                    }
                } else if let index = requestMessages.lastIndex(where: { $0.id == userMessage.id }) {
                    requestMessages[index].text = applyCustomInstructions(prompt, instructions: snapshot.customInstructions)
                }

                status = snapshot.agentToolsEnabled && snapshot.apiMode == .responses ? "智能体正在执行工具..." : "正在生成..."
                let result = try await OpenAIClient().sendMessage(
                    settings: snapshot,
                    messages: requestMessages,
                    previousResponseId: store.current.responseId
                )
                let text = result.text.isEmpty ? "接口没有返回文本内容。" : result.text
                store.append(ChatMessage(role: .assistant, text: text, reasoning: result.reasoning, sources: result.sources))
                store.setResponseId(result.responseId ?? store.current.responseId)
                status = ""
            } catch is CancellationError {
                status = "已停止"
            } catch {
                store.append(ChatMessage(role: .assistant, text: "发送失败：\(error.localizedDescription)"))
                status = ""
            }
            isBusy = false
            runningTask = nil
        }
    }

    func generateImage(store: ChatStore, settings: SettingsStore) {
        let prompt = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !prompt.isEmpty else { return }
        let snapshot = settings.snapshot()
        guard !snapshot.apiKey.isEmpty else {
            status = "请先在设置里保存 API key"
            return
        }

        draft = ""
        store.append(ChatMessage(role: .user, text: "生成图片：\(prompt)"))
        isBusy = true
        status = "正在生图..."

        runningTask = Task {
            do {
                let result = try await OpenAIClient().generateImage(settings: snapshot, prompt: prompt)
                let note = result.revisedPrompt?.isEmpty == false ? "已生成图片。\n\n修订提示词：\(result.revisedPrompt!)" : "已生成图片。"
                store.append(ChatMessage(role: .assistant, text: note, imageUrl: result.imageUrl))
                status = ""
            } catch is CancellationError {
                status = "已停止"
            } catch {
                store.append(ChatMessage(role: .assistant, text: "生图失败：\(error.localizedDescription)"))
                status = ""
            }
            isBusy = false
            runningTask = nil
        }
    }

    func stop() {
        runningTask?.cancel()
        isBusy = false
        status = "正在停止..."
    }

    func startRevision(from message: ChatMessage) {
        revisionTarget = message
        draft = ""
        toolsExpanded = false
        status = "已选择该消息，输入修改要求后直接发送"
    }

    func addPhoto(_ item: PhotosPickerItem) async {
        guard let data = try? await item.loadTransferable(type: Data.self) else {
            status = "图片读取失败"
            return
        }
        let mime = item.supportedContentTypes.first?.preferredMIMEType ?? "image/jpeg"
        let name = "photo-\(Int(Date().timeIntervalSince1970)).jpg"
        pendingAttachments.append(ChatAttachment(name: name, mimeType: mime, dataUrl: dataURL(data: data, mimeType: mime), isImage: true))
    }

    func handleImportedFiles(_ result: Result<[URL], Error>) {
        switch result {
        case .success(let urls):
            for url in urls {
                let access = url.startAccessingSecurityScopedResource()
                defer {
                    if access { url.stopAccessingSecurityScopedResource() }
                }
                guard let data = try? Data(contentsOf: url) else {
                    status = "文件读取失败：\(url.lastPathComponent)"
                    continue
                }
                let type = UTType(filenameExtension: url.pathExtension)
                let mime = type?.preferredMIMEType ?? "application/octet-stream"
                pendingAttachments.append(ChatAttachment(
                    name: url.lastPathComponent,
                    mimeType: mime,
                    dataUrl: dataURL(data: data, mimeType: mime),
                    isImage: type?.conforms(to: .image) ?? false
                ))
            }
        case .failure(let error):
            status = "文件选择失败：\(error.localizedDescription)"
        }
    }

    private func dataURL(data: Data, mimeType: String) -> String {
        "data:\(mimeType);base64,\(data.base64EncodedString())"
    }

    private func searchPrompt(userPrompt: String, sources: [SearchSource]) -> String {
        let sourceText = sources.enumerated().map { index, source in
            """
            [\(index + 1)] \(source.title)
            URL: \(source.url)
            摘要: \(source.snippet)
            """
        }.joined(separator: "\n\n")
        return """
        用户问题：
        \(userPrompt)

        联网搜索结果：
        \(sourceText)

        请直接基于搜索结果回答，必要时标注来源编号；如果搜索结果不足，也要明确说明缺口。
        """
    }

    private func applyCustomInstructions(_ prompt: String, instructions: String) -> String {
        let value = instructions.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return prompt }
        return """
        以下是用户在 App 中保存的个人指令/长期偏好，请在不冲突的情况下遵守，不要主动复述。

        \(value)

        用户请求：
        \(prompt)
        """
    }
}

@MainActor
final class SpeechPlayer: ObservableObject {
    private let synthesizer = AVSpeechSynthesizer()

    func speak(_ text: String) {
        let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return }
        synthesizer.stopSpeaking(at: .immediate)
        let utterance = AVSpeechUtterance(string: value)
        utterance.voice = AVSpeechSynthesisVoice(language: "zh-CN") ?? AVSpeechSynthesisVoice(language: Locale.current.identifier)
        synthesizer.speak(utterance)
    }

    func stop() {
        synthesizer.stopSpeaking(at: .immediate)
    }
}

struct ChatTopBar: View {
    @EnvironmentObject private var settings: SettingsStore
    @EnvironmentObject private var store: ChatStore
    @EnvironmentObject private var viewModel: ChatViewModel

    var wide: Bool
    var onHistory: () -> Void
    var onSettings: () -> Void
    var onNewChat: () -> Void
    var onOpenURL: (URL) -> Void

    var body: some View {
        HStack(spacing: 10) {
            if !wide {
                Button(action: onHistory) {
                    Image(systemName: "line.3.horizontal")
                }
                .buttonStyle(.plain)
                .font(.title3)
            }

            Spacer(minLength: 4)

            VStack(alignment: .center, spacing: 2) {
                Text("Codex")
                    .font(.headline)
                    .lineLimit(1)
                Text(settings.model)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }

            Spacer(minLength: 4)

            Menu {
                ForEach(settings.availableModels, id: \.self) { model in
                    Button(model) { settings.model = model }
                }
            } label: {
                Image(systemName: "cpu")
            }
            .buttonStyle(.plain)

            Button(action: onNewChat) {
                Image(systemName: "square.and.pencil")
            }
            .buttonStyle(.plain)

            Button(action: onSettings) {
                Image(systemName: "gearshape")
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(Color(.systemBackground))
    }
}

struct MessageList: View {
    @EnvironmentObject private var store: ChatStore
    var onOpenURL: (URL) -> Void
    var onRevise: (ChatMessage) -> Void

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 12) {
                    ForEach(store.current.messages) { message in
                        MessageRow(message: message, onOpenURL: onOpenURL, onRevise: onRevise)
                            .id(message.id)
                    }
                }
                .padding(14)
            }
            .onChange(of: store.current.messages.count) { _ in
                if let last = store.current.messages.last?.id {
                    withAnimation(.easeOut(duration: 0.25)) {
                        proxy.scrollTo(last, anchor: .bottom)
                    }
                }
            }
        }
    }
}

struct MessageRow: View {
    @EnvironmentObject private var speechPlayer: SpeechPlayer
    var message: ChatMessage
    var onOpenURL: (URL) -> Void
    var onRevise: (ChatMessage) -> Void

    var body: some View {
        HStack(alignment: .top) {
            if message.role == .user {
                Spacer(minLength: 40)
            }

            VStack(alignment: message.role == .user ? .trailing : .leading, spacing: 8) {
                VStack(alignment: .leading, spacing: 8) {
                    if let reasoning = message.reasoning, !reasoning.isEmpty {
                        DisclosureGroup("思考过程") {
                            MarkdownText(reasoning)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .font(.caption)
                    }

                    if !message.text.isEmpty {
                        MarkdownText(message.text)
                            .textSelection(.enabled)
                    }

                    if let imageUrl = message.imageUrl {
                        GeneratedImageView(source: imageUrl)
                    }

                    if !message.attachments.isEmpty {
                        AttachmentStrip(attachments: message.attachments)
                    }

                    if !message.sources.isEmpty {
                        DisclosureGroup("联网搜索来源") {
                            VStack(alignment: .leading, spacing: 8) {
                                ForEach(message.sources) { source in
                                    Button {
                                        if let url = URL(string: source.url) {
                                            onOpenURL(url)
                                        }
                                    } label: {
                                        VStack(alignment: .leading, spacing: 3) {
                                            Text(source.title)
                                                .font(.caption)
                                                .bold()
                                                .lineLimit(2)
                                            Text(source.snippet)
                                                .font(.caption2)
                                                .foregroundStyle(.secondary)
                                                .lineLimit(3)
                                        }
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                        .font(.caption)
                    }
                }
                .padding(message.role == .user ? 12 : 0)
                .background(message.role == .user ? Color(.systemGray6) : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: message.role == .user ? 22 : 0, style: .continuous))
                .frame(maxWidth: 680, alignment: message.role == .user ? .trailing : .leading)

                HStack(spacing: 8) {
                    Button {
                        UIPasteboard.general.string = message.text
                    } label: {
                        Image(systemName: "doc.on.doc")
                    }
                    .font(.caption)
                    .buttonStyle(.plain)
                    .foregroundStyle(.secondary)

                    if message.role == .assistant {
                        Button {
                            speechPlayer.speak(message.text)
                        } label: {
                            Image(systemName: "speaker.wave.2")
                        }
                        .font(.caption)
                        .buttonStyle(.plain)
                        .foregroundStyle(.secondary)

                        ShareLink(item: message.text) {
                            Image(systemName: "square.and.arrow.up")
                        }
                        .font(.caption)
                        .buttonStyle(.plain)
                        .foregroundStyle(.secondary)
                    }

                    if message.role == .user {
                        Button {
                            onRevise(message)
                        } label: {
                            Image(systemName: "slider.horizontal.3")
                        }
                        .font(.caption)
                        .buttonStyle(.plain)
                        .foregroundStyle(.secondary)
                        .accessibilityLabel("修改要求")
                    }
                }
            }

            if message.role == .assistant {
                Spacer(minLength: 40)
            }
        }
    }
}

struct Composer: View {
    @EnvironmentObject private var viewModel: ChatViewModel
    @Binding var selectedPhoto: PhotosPickerItem?
    @Binding var showingImporter: Bool
    var onOpenURL: (URL) -> Void
    var onSend: () -> Void
    var onGenerateImage: () -> Void
    var onStop: () -> Void

    var body: some View {
        VStack(spacing: 8) {
            if !viewModel.status.isEmpty {
                Text(viewModel.status)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            if !viewModel.pendingAttachments.isEmpty {
                AttachmentStrip(attachments: viewModel.pendingAttachments)
            }

            HStack(alignment: .bottom, spacing: 8) {
                TextEditor(text: $viewModel.draft)
                    .frame(minHeight: 42, maxHeight: 120)
                    .padding(6)
                    .scrollContentBackground(.hidden)
                    .background(Color(.systemGray6))
                    .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))

                Button {
                    viewModel.toolsExpanded.toggle()
                } label: {
                    Image(systemName: viewModel.toolsExpanded ? "minus" : "plus")
                        .font(.system(size: 14, weight: .semibold))
                        .frame(width: 30, height: 30)
                }
                .buttonStyle(.plain)
                .background(Color(.systemGray6))
                .clipShape(Circle())

                Button {
                    viewModel.isBusy ? onStop() : onSend()
                } label: {
                    Image(systemName: viewModel.isBusy ? "stop.fill" : "arrow.up")
                        .font(.system(size: 17, weight: .bold))
                        .frame(width: 38, height: 38)
                }
                .buttonStyle(.plain)
                .foregroundStyle(.white)
                .background(Color(.label))
                .clipShape(Circle())
            }

            if viewModel.toolsExpanded {
                HStack(spacing: 10) {
                    PhotosPicker(selection: $selectedPhoto, matching: .images) {
                        Label("图片", systemImage: "photo")
                    }
                    .buttonStyle(.bordered)

                    Button {
                        showingImporter = true
                    } label: {
                        Label("文件", systemImage: "paperclip")
                    }
                    .buttonStyle(.bordered)

                    Button(action: openDraftURL) {
                        Label("网页", systemImage: "safari")
                    }
                    .buttonStyle(.bordered)

                    Button(action: onGenerateImage) {
                        Label("生图", systemImage: "sparkles")
                    }
                    .buttonStyle(.bordered)

                    Spacer()
                }
                .font(.caption)
            }
        }
        .padding(.horizontal, 12)
        .padding(.top, 8)
        .padding(.bottom, 10)
        .background(Color(.systemBackground))
    }

    private func openDraftURL() {
        var value = viewModel.draft.trimmingCharacters(in: .whitespacesAndNewlines)
        if !value.contains("://") {
            value = "https://\(value)"
        }
        if let url = URL(string: value), !value.isEmpty {
            onOpenURL(url)
        }
    }
}

struct HistorySidebar: View {
    @EnvironmentObject private var store: ChatStore
    @State private var query = ""

    private var filteredSessions: [ChatSession] {
        let value = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return store.sessions }
        return store.sessions.filter {
            $0.title.localizedCaseInsensitiveContains(value)
                || $0.messages.contains { $0.text.localizedCaseInsensitiveContains(value) }
        }
    }

    var body: some View {
        List {
            Section {
                Button {
                    store.newSession()
                } label: {
                    Label("新聊天", systemImage: "square.and.pencil")
                }
                TextField("搜索历史", text: $query)
                    .textInputAutocapitalization(.never)
            }

            Section("历史聊天") {
                ForEach(filteredSessions) { session in
                    Button {
                        store.select(session)
                    } label: {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(session.title)
                                .lineLimit(1)
                            Text(session.subtitle)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .swipeActions {
                        Button(role: .destructive) {
                            store.delete(session)
                        } label: {
                            Label("删除", systemImage: "trash")
                        }
                    }
                }
            }
        }
        .listStyle(.sidebar)
    }
}

struct SettingsView: View {
    @EnvironmentObject private var settings: SettingsStore
    @Environment(\.dismiss) private var dismiss
    @State private var apiKeyInput = ""
    @State private var searchKeyInput = ""
    @State private var refreshStatus = ""

    var body: some View {
        NavigationStack {
            Form {
                Section("API") {
                    HStack {
                        Text(settings.hasApiKey ? "API key 已保存" : "未保存 API key")
                        Spacer()
                        if settings.hasApiKey {
                            Button("清除", role: .destructive) {
                                settings.clearApiKey()
                            }
                        }
                    }
                    SecureField(settings.hasApiKey ? "输入新 key 可替换" : "API key", text: $apiKeyInput)
                        .textInputAutocapitalization(.never)
                    Button("保存 API key") {
                        settings.saveApiKey(apiKeyInput)
                        apiKeyInput = ""
                    }
                    TextField("接口地址", text: $settings.baseUrl)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.URL)
                    Picker("接口模式", selection: $settings.apiMode) {
                        ForEach(ApiMode.allCases) { mode in
                            Text(mode.label).tag(mode)
                        }
                    }
                    TextField("模型", text: $settings.model)
                        .textInputAutocapitalization(.never)
                    Picker("常用模型", selection: $settings.model) {
                        ForEach(settings.availableModels, id: \.self) { model in
                            Text(model).tag(model)
                        }
                    }
                    Button("刷新模型") {
                        Task {
                            do {
                                refreshStatus = "正在刷新..."
                                try await settings.refreshModels()
                                refreshStatus = "已刷新"
                            } catch {
                                refreshStatus = error.localizedDescription
                            }
                        }
                    }
                    if !refreshStatus.isEmpty {
                        Text(refreshStatus)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                Section("智能体工具") {
                    Toggle("自动工具模式", isOn: $settings.agentToolsEnabled)
                    Toggle("允许自动生图", isOn: $settings.agentImageToolEnabled)
                    Toggle("备用本地搜索", isOn: $settings.searchEnabled)
                    TextEditor(text: $settings.customInstructions)
                        .frame(minHeight: 80)
                        .overlay(alignment: .topLeading) {
                            if settings.customInstructions.isEmpty {
                                Text("个人指令/记忆，可留空")
                                    .foregroundStyle(.secondary)
                                    .padding(.top, 8)
                                    .padding(.leading, 5)
                            }
                        }
                    TextField("本地 custom_search 接口，可留空使用内置搜索", text: $settings.searchEndpoint)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.URL)
                    Picker("鉴权方式", selection: $settings.searchAuthMode) {
                        ForEach(SearchAuthMode.allCases) { mode in
                            Text(mode.label).tag(mode)
                        }
                    }
                    Stepper("结果数 \(settings.searchResultCount)", value: $settings.searchResultCount, in: 1...10)
                    HStack {
                        Text(settings.hasSearchApiKey ? "搜索 key 已保存" : "未保存搜索 key")
                        Spacer()
                        if settings.hasSearchApiKey {
                            Button("清除", role: .destructive) {
                                settings.clearSearchApiKey()
                            }
                        }
                    }
                    SecureField("搜索 API key", text: $searchKeyInput)
                        .textInputAutocapitalization(.never)
                    Button("保存搜索 key") {
                        settings.saveSearchApiKey(searchKeyInput)
                        searchKeyInput = ""
                    }
                }

                Section("生图") {
                    Picker("路由", selection: $settings.imageRoute) {
                        ForEach(ImageRoute.allCases) { route in
                            Text(route.label).tag(route)
                        }
                    }
                    TextField("生图模型", text: $settings.imageModel)
                        .textInputAutocapitalization(.never)
                    TextField("尺寸", text: $settings.imageSize)
                        .textInputAutocapitalization(.never)
                }
            }
            .navigationTitle("设置")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") { dismiss() }
                }
            }
        }
    }
}

struct MarkdownText: View {
    var text: String
    @Environment(\.colorScheme) private var colorScheme
    @State private var height: CGFloat = 32

    var body: some View {
        MarkdownWebView(markdown: text, isDark: colorScheme == .dark, height: $height)
            .frame(height: max(32, height))
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct MarkdownWebView: UIViewRepresentable {
    var markdown: String
    var isDark: Bool
    @Binding var height: CGFloat

    func makeCoordinator() -> Coordinator {
        Coordinator(height: $height)
    }

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.userContentController.add(context.coordinator, name: "height")
        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.isOpaque = false
        webView.backgroundColor = .clear
        webView.scrollView.backgroundColor = .clear
        webView.scrollView.isScrollEnabled = false
        webView.navigationDelegate = context.coordinator
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        context.coordinator.height = $height
        let signature = "\(markdown)\n--dark=\(isDark)"
        guard context.coordinator.signature != signature else { return }
        context.coordinator.signature = signature
        webView.loadHTMLString(Self.html(markdown: markdown, isDark: isDark), baseURL: nil)
    }

    static func dismantleUIView(_ uiView: WKWebView, coordinator: Coordinator) {
        uiView.configuration.userContentController.removeScriptMessageHandler(forName: "height")
    }

    private static func html(markdown: String, isDark: Bool) -> String {
        let encoded = ((try? JSONEncoder().encode(markdown))
            .flatMap { String(data: $0, encoding: .utf8) } ?? "\"\"")
            .replacingOccurrences(of: "</", with: "<\\/")
        let foreground = isDark ? "#f3f4f6" : "#111827"
        let secondary = isDark ? "#a1a1aa" : "#6b7280"
        let codeBackground = isDark ? "#18181b" : "#f3f4f6"
        let border = isDark ? "#3f3f46" : "#e5e7eb"
        return """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.10/dist/katex.min.css">
          <style>
            html, body { margin:0; padding:0; background:transparent; color:\(foreground); font:-apple-system-body; }
            body { overflow:hidden; word-wrap:break-word; }
            #content { font-size:16px; line-height:1.48; }
            p { margin:0 0 0.7em; }
            p:last-child { margin-bottom:0; }
            pre { overflow-x:auto; padding:10px; border-radius:8px; background:\(codeBackground); border:1px solid \(border); }
            code { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size:0.92em; background:\(codeBackground); border-radius:5px; padding:1px 4px; }
            pre code { padding:0; background:transparent; }
            blockquote { margin:0 0 0.7em; padding-left:10px; border-left:3px solid \(border); color:\(secondary); }
            table { border-collapse:collapse; width:100%; margin:0 0 0.7em; }
            th, td { border:1px solid \(border); padding:6px; text-align:left; }
            a { color:#2563eb; text-decoration:none; }
            ul, ol { padding-left:1.4em; margin-top:0; }
            .katex-display { overflow-x:auto; overflow-y:hidden; }
          </style>
        </head>
        <body>
          <div id="content"></div>
          <script src="https://cdn.jsdelivr.net/npm/marked@12.0.2/marked.min.js"></script>
          <script src="https://cdn.jsdelivr.net/npm/dompurify@3.1.6/dist/purify.min.js"></script>
          <script src="https://cdn.jsdelivr.net/npm/katex@0.16.10/dist/katex.min.js"></script>
          <script src="https://cdn.jsdelivr.net/npm/katex@0.16.10/dist/contrib/auto-render.min.js"></script>
          <script>
            const raw = \(encoded);
            const target = document.getElementById('content');
            function escapeHTML(value) {
              return value.replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
            }
            function postHeight() {
              const h = Math.max(document.body.scrollHeight, document.documentElement.scrollHeight, 32);
              window.webkit.messageHandlers.height.postMessage(h);
            }
            function render() {
              let html = window.marked ? marked.parse(raw, { breaks: true }) : escapeHTML(raw).replace(/\\n/g, '<br>');
              if (window.DOMPurify) html = DOMPurify.sanitize(html);
              target.innerHTML = html;
              if (window.renderMathInElement) {
                renderMathInElement(target, {
                  delimiters: [
                    {left: '$$', right: '$$', display: true},
                    {left: '\\\\[', right: '\\\\]', display: true},
                    {left: '\\\\(', right: '\\\\)', display: false},
                    {left: '$', right: '$', display: false}
                  ],
                  throwOnError: false
                });
              }
              postHeight();
              setTimeout(postHeight, 80);
              setTimeout(postHeight, 350);
            }
            window.addEventListener('load', render);
            setTimeout(render, 700);
          </script>
        </body>
        </html>
        """
    }

    final class Coordinator: NSObject, WKScriptMessageHandler, WKNavigationDelegate {
        var height: Binding<CGFloat>
        var signature = ""

        init(height: Binding<CGFloat>) {
            self.height = height
        }

        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            let next: CGFloat?
            if let value = message.body as? Double {
                next = CGFloat(value)
            } else if let value = message.body as? Int {
                next = CGFloat(value)
            } else {
                next = nil
            }
            guard let next else { return }
            DispatchQueue.main.async {
                self.height.wrappedValue = min(max(next, 32), 4000)
            }
        }
    }
}

struct AttachmentStrip: View {
    var attachments: [ChatAttachment]

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(attachments) { attachment in
                    Label(attachment.name, systemImage: attachment.isImage ? "photo" : "doc")
                        .font(.caption)
                        .lineLimit(1)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 5)
                        .background(Color(.tertiarySystemGroupedBackground))
                        .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                }
            }
        }
    }
}

struct GeneratedImageView: View {
    var source: String

    var body: some View {
        Group {
            if source.hasPrefix("data:image"),
               let comma = source.firstIndex(of: ","),
               let data = Data(base64Encoded: String(source[source.index(after: comma)...])),
               let image = UIImage(data: data) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
            } else if let url = URL(string: source) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().scaledToFit()
                    case .failure:
                        Text(source).font(.caption)
                    case .empty:
                        ProgressView()
                    @unknown default:
                        EmptyView()
                    }
                }
            }
        }
        .frame(maxHeight: 360)
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
    }
}

extension URL: Identifiable {
    public var id: String { absoluteString }
}
