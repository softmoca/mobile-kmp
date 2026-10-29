import Foundation

@main
struct WebViewConfigurationTests {
    static func main() {
        let config = WebViewConfiguration(environment: [:])
        #if DEBUG
        precondition(config.startURL == WebViewConfiguration.developmentURL)
        precondition(WebViewConfiguration(environment: ["JACHWI_WEB_URL": "https://192.168.0.2:3000"]).startURL.host == "192.168.0.2")
        precondition(WebViewConfiguration(environment: ["JACHWI_WEB_URL": "http://192.168.0.2:3000"]).configurationError != nil)
        #else
        precondition(config.startURL == WebViewConfiguration.productionURL)
        precondition(WebViewConfiguration(environment: ["JACHWI_WEB_URL": "https://192.168.0.2:3000"]).startURL == config.startURL)
        #endif
        for value in ["https://192.168.0.2:3000", "https://10.0.0.2:3000", "https://172.16.0.1:3000", "https://mac.local:3000", "https://localhost:3000"] {
            precondition(WebViewConfiguration.isLocalDevelopmentURL(URL(string: value)!))
        }
        for value in ["http://192.168.0.2:3000", "https://evil.example", "https://10.0.0.1.evil", "https://172.32.0.1", "https://192.168.0.1@evil.example", "https://user@mac.local", "https://mac.local/path", "https://mac.local?q=secret", "file:///tmp/test", "https://mac.local.evil.example"] {
            precondition(!WebViewConfiguration.isLocalDevelopmentURL(URL(string: value)!))
        }
        precondition(config.isInternal(config.startURL.appendingPathComponent("properties/1")))
        for value in ["https://evil.example", "\(config.startURL):444", "\(config.startURL).evil.example", "http://\(config.startURL.host!)", "https://user@\(config.startURL.host!)"] {
            precondition(!config.isInternal(URL(string: value)!))
        }
        precondition(config.isInternalBlob(URL(string: "blob:\(config.startURL)/test-id")!))
        precondition(!config.isInternalBlob(URL(string: "blob:https://evil.example/test-id")!))
        for value in ["tel:01000000000", "mailto:test@example.com", "https://example.com"] {
            precondition(WebViewConfiguration.canOpenExternally(URL(string: value)!))
        }
        for value in ["javascript:alert(1)", "file:///etc/passwd", "data:text/html,test", "custom:launch"] {
            precondition(!WebViewConfiguration.canOpenExternally(URL(string: value)!))
        }
        print("WebView configuration and navigation boundary tests passed")
    }
}
