import QuickLook
import SwiftUI
import WebKit

struct WebViewScreen: View {
    @StateObject private var model = WebViewModel()

    var body: some View {
        ZStack {
            WebViewHost(webView: model.webView)
                // The native container owns device safe areas; WebKit owns keyboard/visualViewport changes.
                .ignoresSafeArea(.keyboard)
                .accessibilityAction(named: "웹 새로고침") { model.reload() }
            if let message = model.errorMessage {
                VStack(spacing: 16) {
                    Text("페이지를 열지 못했어요").font(.headline)
                    Text(message).multilineTextAlignment(.center)
                    Button("다시 시도") { model.reload() }.buttonStyle(.borderedProminent)
                }
                .padding(24)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color(uiColor: .systemBackground))
            } else if model.isLoading && !model.hasLoadedPage {
                ProgressView("자취선배를 불러오는 중")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color(uiColor: .systemBackground))
            }
        }
        .background(Color.white)
        .sheet(item: $model.document, onDismiss: model.removePreview) { document in
            PDFPreview(url: document.url)
        }
        .alert("파일을 열지 못했어요", isPresented: Binding(
            get: { model.downloadError != nil },
            set: { if !$0 { model.downloadError = nil } }
        )) {
            Button("확인", role: .cancel) { model.downloadError = nil }
        } message: { Text(model.downloadError ?? "") }
    }
}

private struct WebViewHost: UIViewRepresentable {
    let webView: WKWebView
    func makeUIView(context: Context) -> WKWebView { webView }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
}

private struct PDFPreview: UIViewControllerRepresentable {
    let url: URL
    func makeCoordinator() -> Coordinator { Coordinator(url: url) }
    func makeUIViewController(context: Context) -> QLPreviewController {
        let controller = QLPreviewController()
        controller.dataSource = context.coordinator
        return controller
    }
    func updateUIViewController(_ controller: QLPreviewController, context: Context) {}

    final class Coordinator: NSObject, QLPreviewControllerDataSource {
        let url: URL
        init(url: URL) { self.url = url }
        func numberOfPreviewItems(in controller: QLPreviewController) -> Int { 1 }
        func previewController(_ controller: QLPreviewController, previewItemAt index: Int) -> QLPreviewItem {
            url as NSURL
        }
    }
}
