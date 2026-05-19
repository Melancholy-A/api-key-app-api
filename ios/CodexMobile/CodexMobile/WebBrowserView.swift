import SwiftUI
import WebKit

struct WebBrowserView: View {
    let initialURL: URL

    @Environment(\.dismiss) private var dismiss
    @State private var urlText: String
    @State private var currentURL: URL
    @State private var fitToWidth = true

    init(url: URL) {
        initialURL = url
        _currentURL = State(initialValue: url)
        _urlText = State(initialValue: url.absoluteString)
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                HStack(spacing: 8) {
                    TextField("网址", text: $urlText)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.URL)
                        .textFieldStyle(.roundedBorder)
                        .onSubmit(openTypedURL)

                    Button(action: openTypedURL) {
                        Image(systemName: "arrow.forward.circle.fill")
                    }
                    .buttonStyle(.borderedProminent)

                    Button {
                        fitToWidth.toggle()
                    } label: {
                        Image(systemName: fitToWidth ? "arrow.down.right.and.arrow.up.left" : "arrow.up.left.and.arrow.down.right")
                    }
                    .buttonStyle(.bordered)
                    .accessibilityLabel(fitToWidth ? "原始布局" : "适配布局")
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 8)

                BrowserRepresentable(url: currentURL, fitToWidth: fitToWidth)
                    .ignoresSafeArea(edges: .bottom)
            }
            .navigationTitle("网页")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
            }
        }
    }

    private func openTypedURL() {
        var value = urlText.trimmingCharacters(in: .whitespacesAndNewlines)
        if !value.contains("://") {
            value = "https://\(value)"
        }
        if let url = URL(string: value) {
            currentURL = url
            urlText = value
        }
    }
}

struct BrowserRepresentable: UIViewRepresentable {
    var url: URL
    var fitToWidth: Bool

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.allowsBackForwardNavigationGestures = true
        webView.scrollView.minimumZoomScale = 0.5
        webView.scrollView.maximumZoomScale = 3.0
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        if webView.url != url {
            webView.load(URLRequest(url: url))
        }
        let script: String
        if fitToWidth {
            script = """
            var meta = document.querySelector('meta[name=viewport]');
            if (!meta) {
              meta = document.createElement('meta');
              meta.name = 'viewport';
              document.head.appendChild(meta);
            }
            meta.content = 'width=device-width, initial-scale=1, maximum-scale=5';
            """
        } else {
            script = """
            var meta = document.querySelector('meta[name=viewport]');
            if (meta) { meta.content = 'width=1200, initial-scale=1'; }
            """
        }
        webView.evaluateJavaScript(script)
    }
}
