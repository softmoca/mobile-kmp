import WebKit
import XCTest

final class WebViewTests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
        guard ProcessInfo.processInfo.environment["RUN_DEV_UI_SMOKE"] == "1" else {
            throw XCTSkip("새 QA Simulator에서 iosAppDevSmoke scheme으로만 실제 dev 웹을 검사합니다.")
        }
    }

    @MainActor
    func testDevCookiesAPIPhotoAndPDFDownload() async throws {
        continueAfterFailure = true // Always reach cleanup of this test's disposable properties.
        let model = WebViewModel()
        try await waitForPage(model)
        XCTAssertTrue(model.webView.configuration.websiteDataStore.isPersistent)
        XCTAssertEqual(model.webView.url?.host, "dev.jachwi-sunbae.kr")
        let probe = try await js(model, """
        const result={origin:location.origin};
        for (const mode of ['cors','no-cors']) {
          try { result[mode]=(await fetch('https://dev-api.jachwi-sunbae.kr/api/auth/refresh',
            {method:'POST',credentials:'omit',mode})).status; } catch(e) { result[mode]=e.message; }
        }
        return result;
        """)
        print("WebView refresh connectivity probe: \(probe ?? "nil")")
        let auth = """
        const response = await fetch('https://dev-api.jachwi-sunbae.kr/api/auth/login', {method:'POST', credentials:'include'});
        if (!response.ok) throw new Error('Login status ' + response.status);
        const {data} = await response.json();
        return data.member.memberId;
        """
        let member = try await js(model, auth) as? Int
        XCTAssertNotNil(member)
        if let member { UserDefaults.standard.set(member, forKey: "WebViewQA.memberId") }
        let cookies = await model.webView.configuration.websiteDataStore.httpCookieStore.allCookies()
        let refresh = try XCTUnwrap(cookies.first { $0.name == "refresh_token" })
        XCTAssertTrue(refresh.isSecure)
        XCTAssertTrue(refresh.isHTTPOnly)
        XCTAssertEqual(refresh.path, "/api/auth")
        XCTAssertNotNil(refresh.expiresDate)
        let exposesRefreshCookie = try await js(model, "return document.cookie.includes('refresh_token');") as? Bool
        XCTAssertEqual(exposesRefreshCookie, false)

        // Reload through the shell's real control; no cookie deletion or native token storage.
        model.reload()
        try await waitForPage(model)
        let restored = try await js(model, auth) as? Int
        XCTAssertEqual(member, restored)
        let missingCookieStatus = try await js(model, """
        return (await fetch('https://dev-api.jachwi-sunbae.kr/api/auth/refresh', {method:'POST', credentials:'omit'})).status;
        """) as? Int
        XCTAssertEqual(missingCookieStatus, 401)

        let qaName = "WebView QA " + String(UUID().uuidString.prefix(8))
        let ids = try await js(model, """
        const base = 'https://dev-api.jachwi-sunbae.kr';
        const {data:auth} = await (await fetch(base+'/api/auth/refresh', {method:'POST',credentials:'include'})).json();
        const headers = {'Authorization':'Bearer '+auth.accessToken,'Content-Type':'application/json'};
        const ids = [];
        try {
          for (let i=0;i<2;i++) {
            const r = await fetch(base+'/api/properties', {method:'POST',headers,
              body:JSON.stringify({name:name+' '+i,depositAmount:10000000,monthlyRentAmount:500000,
                address:'서울특별시 강남구 테헤란로 1',latitude:37.4979,longitude:127.0276,roomOptions:[],utilityOptions:[]})});
            if (!r.ok) throw new Error('Create status '+r.status);
            ids.push((await r.json()).data.id);
          }
          return ids;
        } catch(error) {
          for (const id of ids) await fetch(base+'/api/properties/'+id, {method:'DELETE',headers});
          throw error;
        }
        """, arguments: ["name": qaName]) as? [Int] ?? []
        XCTAssertEqual(ids.count, 2)
        do {
            let photoStatus = try await js(model, """
            const base='https://dev-api.jachwi-sunbae.kr';
            const {data:auth}=await (await fetch(base+'/api/auth/refresh',{method:'POST',credentials:'include'})).json();
            const bytes=Uint8Array.from(atob('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aY9sAAAAASUVORK5CYII='),c=>c.charCodeAt(0));
            const form=new FormData(); form.append('file',new Blob([bytes],{type:'image/png'}),'webview-qa.png');
            return (await fetch(base+'/api/properties/'+id+'/photos',{method:'POST',headers:{Authorization:'Bearer '+auth.accessToken},body:form})).status;
            """, arguments: ["id": ids[0]]) as? Int
            XCTAssertEqual(photoStatus, 201)
            _ = try await js(model, """
            const base='https://dev-api.jachwi-sunbae.kr';
            const {data:auth}=await (await fetch(base+'/api/auth/refresh',{method:'POST',credentials:'include'})).json();
            const r=await fetch(base+'/api/properties/export.pdf',{method:'POST',
              headers:{Authorization:'Bearer '+auth.accessToken,'Content-Type':'application/json'},body:JSON.stringify({propertyIds:ids})});
            if (!r.ok) throw new Error('PDF status '+r.status);
            const url=URL.createObjectURL(await r.blob()); const a=document.createElement('a');
            a.href=url; a.download='webview-qa.pdf'; document.body.append(a); a.click(); a.remove();
            setTimeout(()=>URL.revokeObjectURL(url),0); return true;
            """, arguments: ["ids": ids])
            for _ in 0..<100 where model.document == nil && model.downloadError == nil {
                try await Task.sleep(nanoseconds: 200_000_000)
            }
            XCTAssertNil(model.downloadError)
            let document = try XCTUnwrap(model.document)
            XCTAssertTrue(FileManager.default.fileExists(atPath: document.url.path))
            model.removePreview()
        } catch {
            try await cleanup(model, ids: ids)
            throw error
        }
        try await cleanup(model, ids: ids)
        print("WebView dev QA: persistent Secure/HttpOnly cookie, member restore, missing-cookie 401, PNG API upload, PDF download; QA properties removed.")
    }

    /// Run this test again in a separate xcodebuild invocation to verify cookie restoration from disk.
    @MainActor
    func testPersistedMemberAfterRunnerRestart() async throws {
        let previous = UserDefaults.standard.integer(forKey: "WebViewQA.memberId")
        guard previous > 0 else { throw XCTSkip("Run the dev cookie test first on the same QA Simulator.") }
        let model = WebViewModel()
        try await waitForPage(model)
        let member = try await js(model, """
        const r=await fetch('https://dev-api.jachwi-sunbae.kr/api/auth/refresh',{method:'POST',credentials:'include'});
        if (!r.ok) throw new Error('Restore status '+r.status);
        return (await r.json()).data.member.memberId;
        """) as? Int
        XCTAssertEqual(member, previous)
        print("WebView persisted cookie restored the same QA member.")
    }

    @MainActor
    private func cleanup(_ model: WebViewModel, ids: [Int]) async throws {
        let statuses = try await js(model, """
        const base='https://dev-api.jachwi-sunbae.kr';
        const {data:auth}=await (await fetch(base+'/api/auth/refresh',{method:'POST',credentials:'include'})).json();
        return await Promise.all(ids.map(async id => (await fetch(base+'/api/properties/'+id,
          {method:'DELETE',headers:{Authorization:'Bearer '+auth.accessToken}})).status));
        """, arguments: ["ids": ids]) as? [Int]
        XCTAssertEqual(statuses, ids.map { _ in 204 }, "QA 매물 정리가 실패했습니다.")
    }

    @MainActor
    private func waitForPage(_ model: WebViewModel) async throws {
        for _ in 0..<150 {
            if model.errorMessage != nil { break }
            if !model.webView.isLoading && model.hasLoadedPage { return }
            try await Task.sleep(nanoseconds: 200_000_000)
        }
        XCTFail("dev 웹 로딩 실패: \(model.errorMessage ?? "timeout")")
        throw URLError(.timedOut)
    }

    @MainActor
    private func js(_ model: WebViewModel, _ script: String, arguments: [String: Any] = [:]) async throws -> Any? {
        try await model.webView.callAsyncJavaScript(script, arguments: arguments, in: nil, contentWorld: .page)
    }

    func testReactScreenAndRouterAfterRelaunch() throws {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.webViews.firstMatch.waitForExistence(timeout: 30))
        XCTAssertTrue(app.webViews.links["매물 기록하기 직접 본 매물을 기록해요"].firstMatch.waitForExistence(timeout: 30))
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "webview-dev-home"
        screenshot.lifetime = .keepAlways
        add(screenshot)
        app.webViews.links["매물 기록하기 직접 본 매물을 기록해요"].firstMatch.tap()
        XCTAssertTrue(app.webViews.staticTexts["본 방 기록"].firstMatch.waitForExistence(timeout: 10))
        app.terminate()
        app.launch()
        XCTAssertTrue(app.webViews.links["매물 기록하기 직접 본 매물을 기록해요"].firstMatch.waitForExistence(timeout: 30))
    }

    func testUnavailableLocalServerShowsRetry() {
        let app = XCUIApplication()
        app.launchEnvironment["JACHWI_WEB_URL"] = "https://127.0.0.1:1"
        app.launch()
        XCTAssertTrue(app.staticTexts["페이지를 열지 못했어요"].waitForExistence(timeout: 35))
        app.buttons["다시 시도"].tap()
        XCTAssertTrue(app.staticTexts["페이지를 열지 못했어요"].waitForExistence(timeout: 35))
        app.terminate()
    }

    func testThriftyPreviewUI() throws {
        guard let origin = ProcessInfo.processInfo.environment["JACHWI_PREVIEW_URL"],
              let url = URL(string: origin), WebViewConfiguration.isLocalDevelopmentURL(url) else {
            throw XCTSkip("신뢰된 HTTPS Preview가 실행 중일 때만 검사합니다.")
        }
        let app = XCUIApplication()
        app.launchEnvironment["JACHWI_WEB_URL"] = origin
        app.launch()
        let mapTab = app.webViews.links["지도"].firstMatch
        XCTAssertTrue(mapTab.waitForExistence(timeout: 40))
        mapTab.tap()
        let thrifty = app.webViews.switches["알뜰"].firstMatch
        if !thrifty.waitForExistence(timeout: 20) {
            let failure = XCTAttachment(screenshot: app.screenshot())
            failure.name = "thrifty-entry-failure"
            failure.lifetime = .keepAlways
            add(failure)
            print(app.debugDescription)
            XCTFail("지도 탭에서 알뜰 버튼을 찾지 못했습니다.")
            return
        }
        thrifty.tap()
        let search = app.webViews.buttons["주소 또는 위치 검색"].firstMatch
        XCTAssertTrue(search.waitForExistence(timeout: 15))
        search.tap()
        let field = app.webViews.textFields["동네 주소 검색"].firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 10))
        field.tap()
        // iOS의 한글 조합/React controlled input 사이에 각 글자가 반영된 뒤 다음 글자를 보낸다.
        var typed = ""
        for character in "세종대로 110" {
            field.typeText(String(character))
            typed.append(character)
            XCTAssertEqual(field.value as? String, typed)
        }
        field.typeText("\n")
        let address = app.webViews.buttons.matching(NSPredicate(format: "label CONTAINS %@", "세종대로 110")).firstMatch
        if !address.waitForExistence(timeout: 20) {
            let failure = XCTAttachment(screenshot: app.screenshot())
            failure.name = "thrifty-address-failure"
            failure.lifetime = .keepAlways
            add(failure)
            print(app.debugDescription)
            XCTFail("실제 주소 검색 결과를 찾지 못했습니다.")
            return
        }
        // WebKit 입력 액세서리가 첫 결과의 중앙을 가리므로 사용자가 누르는 완료 버튼으로 닫는다.
        let keyboardDone = app.toolbars.buttons["Done"].firstMatch
        if keyboardDone.exists { keyboardDone.tap() }
        address.tap()
        let list = app.webViews.buttons.matching(NSPredicate(format: "label CONTAINS %@", "상세 보기")).firstMatch
        XCTAssertTrue(list.waitForExistence(timeout: 20))
        list.tap()
        let expand = app.webViews.buttons["전체 펼치기"].firstMatch
        if expand.waitForExistence(timeout: 5) { expand.tap() }
        XCTAssertTrue(app.webViews.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "메뉴·서비스")).firstMatch.waitForExistence(timeout: 10))
        XCTAssertTrue(app.webViews.links.matching(NSPredicate(format: "label CONTAINS %@", "길찾기")).firstMatch.exists)
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "thrifty-preview-live-detail"
        screenshot.lifetime = .keepAlways
        add(screenshot)
        app.webViews.buttons["가게 목록으로"].firstMatch.tap()
        app.webViews.buttons["가게 목록 닫기"].firstMatch.tap()
        app.webViews.switches["500m"].firstMatch.tap()
        XCTAssertEqual(app.webViews.switches["500m"].firstMatch.value as? String, "1")
        XCTAssertTrue(app.webViews.buttons["가게 목록 닫기"].firstMatch.waitForExistence(timeout: 20))
        XCTAssertFalse(app.webViews.buttons["가게 목록으로"].firstMatch.exists)
    }

    @MainActor
    func testThriftyPreviewCookiesAndContract() async throws {
        guard let origin = ProcessInfo.processInfo.environment["JACHWI_PREVIEW_URL"],
              let url = URL(string: origin), WebViewConfiguration.isLocalDevelopmentURL(url) else {
            throw XCTSkip("신뢰된 HTTPS Preview가 실행 중일 때만 검사합니다.")
        }
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .default()
        let web = WKWebView(frame: .zero, configuration: configuration)
        web.load(URLRequest(url: url))
        for _ in 0..<150 {
            if web.url?.host == url.host && !web.isLoading { break }
            try await Task.sleep(nanoseconds: 200_000_000)
        }
        let result = try await web.callAsyncJavaScript("""
        const login = await fetch('/api/auth/login', {method:'POST', credentials:'include'});
        if (!login.ok) throw new Error('Login status '+login.status);
        const first = (await login.json()).data.member.memberId;
        const refresh = await fetch('/api/auth/refresh', {method:'POST', credentials:'include'});
        if (!refresh.ok) throw new Error('Refresh status '+refresh.status);
        const {data} = await refresh.json();
        const counts = [];
        for (const radius of [500,1000,2000]) {
          const response = await fetch('/api/maps/good-price-stores?latitude=37.5665&longitude=126.978&radius='+radius,
            {headers:{Authorization:'Bearer '+data.accessToken}});
          if (!response.ok) throw new Error('Stores status '+response.status);
          const stores=(await response.json()).data;
          if (stores.count!==stores.stores.length || stores.radius!==radius) throw new Error('Contract mismatch');
          counts.push(stores.count);
        }
        return {memberId:first, sameMember:first===data.member.memberId, counts, exposesCookie:document.cookie.includes('refresh_token'),
          origin:location.origin, serviceWorkers:navigator.serviceWorker ? (await navigator.serviceWorker.getRegistrations()).length : 0};
        """, arguments: [:], in: nil, contentWorld: .page) as? [String: Any]
        XCTAssertEqual(result?["sameMember"] as? Bool, true)
        XCTAssertEqual(result?["exposesCookie"] as? Bool, false)
        XCTAssertEqual(result?["origin"] as? String, origin)
        XCTAssertEqual(result?["serviceWorkers"] as? Int, 0, "MSW must not run in the live Preview.")
        let counts = try XCTUnwrap(result?["counts"] as? [Int])
        XCTAssertEqual(counts.count, 3)
        XCTAssertTrue(counts.allSatisfy { $0 > 0 })
        let cookies = await web.configuration.websiteDataStore.httpCookieStore.allCookies()
        let refreshCookie = try XCTUnwrap(cookies.first { $0.name == "refresh_token" && $0.domain == url.host })
        XCTAssertTrue(refreshCookie.isSecure && refreshCookie.isHTTPOnly)
        web.reloadFromOrigin()
        for _ in 0..<150 {
            if !web.isLoading { break }
            try await Task.sleep(nanoseconds: 200_000_000)
        }
        let restoredMember = try await web.callAsyncJavaScript("""
        const response=await fetch('/api/auth/refresh',{method:'POST',credentials:'include'});
        if (!response.ok) throw new Error('Reload refresh status '+response.status);
        return (await response.json()).data.member.memberId;
        """, arguments: [:], in: nil, contentWorld: .page) as? Int
        XCTAssertEqual(restoredMember, result?["memberId"] as? Int)
        print("Thrifty Preview authenticated store counts (500/1000/2000m): \(counts)")
    }
}
