import SwiftUI

@main
struct CodexMobileApp: App {
    @StateObject private var settings = SettingsStore()
    @StateObject private var chatStore = ChatStore()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(settings)
                .environmentObject(chatStore)
        }
    }
}
