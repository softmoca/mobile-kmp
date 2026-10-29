import Combine
import UIKit
import WebKit

struct WebDocument: Identifiable {
    let id = UUID()
    let url: URL
}

final class WebViewModel: NSObject, ObservableObject, WKNavigationDelegate, WKUIDelegate, WKDownloadDelegate {
    @Published var isLoading = true
    @Published var hasLoadedPage = false
    @Published var errorMessage: String?
    @Published var downloadError: String?
    @Published var document: WebDocument?
    let webView: WKWebView
    private let configuration: WebViewConfiguration
    private var downloadURLs: [ObjectIdentifier: URL] = [:]
    private var previewDirectory: URL?
    private let maximumPDFBytes: Int64 = 50 * 1024 * 1024

    override init() {
        configuration = WebViewConfiguration()
        let webConfiguration = WKWebViewConfiguration()
        webConfiguration.websiteDataStore = .default()
        webConfiguration.allowsInlineMediaPlayback = true
        webView = WKWebView(frame: .zero, configuration: webConfiguration)
        super.init()
        webView.navigationDelegate = self
        webView.uiDelegate = self
        webView.allowsBackForwardNavigationGestures = true
        webView.scrollView.contentInsetAdjustmentBehavior = .never
        webView.scrollView.keyboardDismissMode = .interactive
        webView.scrollView.alwaysBounceVertical = true
        webView.isOpaque = false
        webView.backgroundColor = .white
        #if DEBUG
        if #available(iOS 16.4, *) { webView.isInspectable = true }
        #endif
        let refresh = UIRefreshControl()
        refresh.accessibilityLabel = "웹 새로고침"
        refresh.addTarget(self, action: #selector(reload), for: .valueChanged)
        webView.scrollView.refreshControl = refresh
        loadStartPage()
    }

    private func loadStartPage() {
        guard configuration.configurationError == nil else {
            errorMessage = configuration.configurationError
            isLoading = false
            return
        }
        webView.load(URLRequest(url: configuration.startURL, cachePolicy: .reloadRevalidatingCacheData,
                                timeoutInterval: 30))
    }

    @objc func reload() {
        errorMessage = nil
        if let url = webView.url, configuration.isInternal(url) {
            webView.reloadFromOrigin()
        } else {
            loadStartPage()
        }
    }

    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        errorMessage = nil
        isLoading = true
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        hasLoadedPage = true
        endLoading()
    }

    private func endLoading() {
        isLoading = false
        webView.scrollView.refreshControl?.endRefreshing()
    }

    private func fail(_ error: Error) {
        let code = (error as NSError).code
        guard code != NSURLErrorCancelled, code != 102 else { return }
        endLoading()
        errorMessage = "인터넷 연결과 서버 상태를 확인해 주세요. 로컬 모드라면 Mac의 HTTPS 인증서와 네트워크 권한도 확인해 주세요."
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        fail(error)
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        fail(error)
    }

    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        endLoading()
        errorMessage = "웹 화면이 종료되었습니다. 다시 시도하면 저장된 쿠키로 페이지를 엽니다."
    }

    private func dialogPresenter(for frame: WKFrameInfo) -> UIViewController? {
        guard frame.isMainFrame, frame.request.url.map(configuration.isInternal) == true else { return nil }
        var controller = webView.window?.rootViewController
        while let presented = controller?.presentedViewController { controller = presented }
        return controller is UIAlertController ? nil : controller
    }

    func webView(_ webView: WKWebView, runJavaScriptConfirmPanelWithMessage message: String,
                 initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping (Bool) -> Void) {
        guard let presenter = dialogPresenter(for: frame) else { completionHandler(false); return }
        let alert = UIAlertController(title: "자취선배", message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "취소", style: .cancel) { _ in completionHandler(false) })
        alert.addAction(UIAlertAction(title: "확인", style: .default) { _ in completionHandler(true) })
        presenter.present(alert, animated: true)
    }

    func webView(_ webView: WKWebView, runJavaScriptAlertPanelWithMessage message: String,
                 initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping () -> Void) {
        guard let presenter = dialogPresenter(for: frame) else { completionHandler(); return }
        let alert = UIAlertController(title: "자취선배", message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "확인", style: .default) { _ in completionHandler() })
        presenter.present(alert, animated: true)
    }

    func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        guard let url = action.request.url else { decisionHandler(.cancel); return }
        let trustedSource = action.sourceFrame.isMainFrame
            && action.sourceFrame.request.url.map(configuration.isInternal) == true
        if configuration.isInternal(url) {
            if action.shouldPerformDownload {
                decisionHandler(trustedSource ? .download : .cancel)
            } else if action.targetFrame == nil {
                decisionHandler(.cancel)
                webView.load(action.request)
            } else {
                decisionHandler(.allow)
            }
        } else if configuration.isInternalBlob(url) && trustedSource {
            // The React PDF export uses an <a download href="blob:...">; no token or JS bridge is needed.
            decisionHandler(.download)
        } else {
            decisionHandler(.cancel)
            if !hasLoadedPage && action.targetFrame?.isMainFrame != false {
                endLoading()
                errorMessage = "허용되지 않은 주소로 이동하려고 했습니다. 시작 페이지에서 다시 시도해 주세요."
            }
            if trustedSource && action.navigationType == .linkActivated
                && WebViewConfiguration.canOpenExternally(url) {
                UIApplication.shared.open(url)
            }
        }
    }

    func webView(_ webView: WKWebView, decidePolicyFor response: WKNavigationResponse,
                 decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void) {
        guard let url = response.response.url,
              configuration.isInternal(url) || configuration.isInternalBlob(url) else {
            decisionHandler(.cancel)
            return
        }
        if response.isForMainFrame, let http = response.response as? HTTPURLResponse, http.statusCode >= 400 {
            decisionHandler(.cancel)
            endLoading()
            errorMessage = "서버가 페이지를 제공하지 못했습니다. 잠시 후 다시 시도해 주세요."
        } else if response.response.mimeType == "application/pdf" {
            decisionHandler(.download)
        } else {
            decisionHandler(response.canShowMIMEType ? .allow : .cancel)
        }
    }

    func webView(_ webView: WKWebView, navigationAction: WKNavigationAction, didBecome download: WKDownload) {
        download.delegate = self
        endLoading()
    }

    func webView(_ webView: WKWebView, navigationResponse: WKNavigationResponse, didBecome download: WKDownload) {
        download.delegate = self
        endLoading()
    }

    func download(_ download: WKDownload, decideDestinationUsing response: URLResponse,
                  suggestedFilename: String, completionHandler: @escaping (URL?) -> Void) {
        guard response.mimeType == "application/pdf", response.expectedContentLength <= maximumPDFBytes,
              let origin = response.url,
              configuration.isInternal(origin) || configuration.isInternalBlob(origin) else {
            completionHandler(nil)
            downloadError = "허용된 웹에서 생성한 50MB 이하 PDF만 열 수 있습니다."
            return
        }
        do {
            let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString,
                                                                                         isDirectory: true)
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            let filename = URL(fileURLWithPath: suggestedFilename).lastPathComponent
            let url = directory.appendingPathComponent(filename.hasSuffix(".pdf") ? filename : "comparison.pdf")
            downloadURLs[ObjectIdentifier(download)] = url
            completionHandler(url)
        } catch {
            completionHandler(nil)
            downloadError = "PDF를 저장할 공간을 확보한 뒤 다시 시도해 주세요."
        }
    }

    func download(_ download: WKDownload, willPerformHTTPRedirection response: HTTPURLResponse,
                  newRequest request: URLRequest, decisionHandler: @escaping (WKDownload.RedirectPolicy) -> Void) {
        decisionHandler(request.url.map(configuration.isInternal) == true ? .allow : .cancel)
    }

    func downloadDidFinish(_ download: WKDownload) {
        guard let url = downloadURLs.removeValue(forKey: ObjectIdentifier(download)) else { return }
        do {
            let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
            let file = try FileHandle(forReadingFrom: url)
            defer { try? file.close() }
            guard size <= maximumPDFBytes, try file.read(upToCount: 5) == Data("%PDF-".utf8) else {
                throw CocoaError(.fileReadCorruptFile)
            }
            guard document == nil else {
                try? FileManager.default.removeItem(at: url.deletingLastPathComponent())
                downloadError = "열린 PDF를 닫은 뒤 다시 내려받아 주세요."
                return
            }
            previewDirectory = url.deletingLastPathComponent()
            document = WebDocument(url: url)
        } catch {
            try? FileManager.default.removeItem(at: url.deletingLastPathComponent())
            downloadError = "정상적인 PDF 파일을 받지 못했습니다. 다시 내려받아 주세요."
        }
    }

    func download(_ download: WKDownload, didFailWithError error: Error, resumeData: Data?) {
        if let url = downloadURLs.removeValue(forKey: ObjectIdentifier(download)) {
            try? FileManager.default.removeItem(at: url.deletingLastPathComponent())
        }
        downloadError = "PDF 다운로드에 실패했습니다. 인터넷 연결을 확인하고 다시 시도해 주세요."
    }

    func removePreview() {
        if let directory = previewDirectory { try? FileManager.default.removeItem(at: directory) }
        previewDirectory = nil
    }
}
