import Foundation

/// The web owns authentication, routing and UI. Only this origin may navigate inside the shell.
struct WebViewConfiguration {
    static let developmentURL = URL(string: "https://dev.jachwi-sunbae.kr")!
    static let productionURL = URL(string: "https://www.jachwi-sunbae.kr")!
    let startURL: URL
    let configurationError: String?

    init(environment: [String: String] = ProcessInfo.processInfo.environment,
         localPreviewDefaults: UserDefaults? = nil) {
        #if DEBUG
        let defaultsKey = "jachwi.debug.localPreviewURL"
        // Launch environment variables disappear when iOS starts the app from its home-screen icon.
        // An explicitly empty value clears the remembered Preview and returns to the deployed dev web.
        if environment["JACHWI_WEB_URL"] == "" {
            localPreviewDefaults?.removeObject(forKey: defaultsKey)
        }
        let value = environment["JACHWI_WEB_URL"] ?? localPreviewDefaults?.string(forKey: defaultsKey)
        if let value, !value.isEmpty {
            if let url = URL(string: value), Self.isLocalDevelopmentURL(url) {
                startURL = url
                configurationError = nil
                localPreviewDefaults?.set(url.absoluteString, forKey: defaultsKey)
            } else {
                startURL = Self.developmentURL
                configurationError = "로컬 주소는 신뢰할 수 있는 인증서를 사용하는 https://Mac-LAN-IP:포트 또는 https://호스트.local:포트여야 합니다."
            }
        } else {
            startURL = Self.developmentURL
            configurationError = nil
        }
        #else
        startURL = Self.productionURL
        configurationError = nil
        #endif
    }

    func isInternal(_ url: URL) -> Bool {
        url.scheme?.lowercased() == "https"
            && url.user == nil && url.password == nil
            && url.host?.lowercased() == startURL.host?.lowercased()
            && (url.port ?? 443) == (startURL.port ?? 443)
    }

    func isInternalBlob(_ url: URL) -> Bool {
        guard url.scheme == "blob", let origin = URL(string: String(url.absoluteString.dropFirst(5))) else {
            return false
        }
        return isInternal(origin)
    }

    static func canOpenExternally(_ url: URL) -> Bool {
        guard url.user == nil, url.password == nil else { return false }
        return ["https", "http", "tel", "mailto"].contains(url.scheme?.lowercased() ?? "")
    }

    static func isLocalDevelopmentURL(_ url: URL) -> Bool {
        guard url.scheme == "https", url.user == nil, url.password == nil,
              url.query == nil, url.fragment == nil,
              url.path.isEmpty || url.path == "/", let host = url.host?.lowercased() else { return false }
        if host == "localhost" || (host.hasSuffix(".local") && host.count > 6) { return true }
        let components = host.split(separator: ".", omittingEmptySubsequences: false)
        let parts = components.compactMap { UInt8($0) }
        guard components.count == 4, parts.count == 4 else { return false }
        return parts[0] == 10 || (parts[0] == 172 && (16...31).contains(parts[1]))
            || (parts[0] == 192 && parts[1] == 168) || parts == [127, 0, 0, 1]
    }
}
