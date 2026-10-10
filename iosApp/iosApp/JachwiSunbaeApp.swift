import ComposeApp
import SwiftUI

@main
struct JachwiSunbaeApp: App {
    private let services = IOSServices()

    init() {
        IOSPlatformKt.configureIOSPlatform(implementation: services)
    }

    var body: some Scene {
        WindowGroup { ComposeContent().ignoresSafeArea(.keyboard) }
    }
}

private struct ComposeContent: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        #if DEBUG
        MainViewControllerKt.MainViewController(baseUrl: "https://dev-api.jachwi-sunbae.kr")
        #else
        MainViewControllerKt.MainViewController(baseUrl: "https://api.jachwi-sunbae.kr")
        #endif
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
