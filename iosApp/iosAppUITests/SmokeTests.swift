import XCTest

final class SmokeTests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }

    func testLoginScreenAndOptionalDevSession() throws {
        let app = XCUIApplication()
        app.launch()
        let start = app.buttons["시작하기"]
        // The test account may persist in Keychain from a prior explicit network run.
        if !start.waitForExistence(timeout: 12) {
            let profile = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "마이")).firstMatch
            XCTAssertTrue(profile.waitForExistence(timeout: 10))
            profile.tap()
            let logout = app.buttons["로그아웃"].firstMatch
            XCTAssertTrue(logout.waitForExistence(timeout: 5))
            logout.tap()
            app.buttons.matching(identifier: "로그아웃").allElementsBoundByIndex.last?.tap()
            XCTAssertTrue(start.waitForExistence(timeout: 10))
        }
        XCTAssertFalse(start.isEnabled)
        XCTAssertTrue(app.staticTexts["자취선배"].exists)
        let initialReveal = app.buttons["보기"]
        XCTAssertTrue(initialReveal.isHittable)
        initialReveal.press(forDuration: 0.15)
        XCTAssertTrue(app.buttons["숨김"].waitForExistence(timeout: 5),
                      "키보드를 열기 전 로컬 버튼 터치가 전달되지 않았습니다.\n" + app.debugDescription)
        app.buttons["숨김"].press(forDuration: 0.15)
        XCTAssertTrue(initialReveal.waitForExistence(timeout: 5))
        let nickname = app.descendants(matching: .any).matching(identifier: "login-nickname").firstMatch
        XCTAssertTrue(nickname.waitForExistence(timeout: 5))
        nickname.tap()
        app.typeText("qa_ios_" + UUID().uuidString.prefix(12))
        // Fresh simulators may display Apple's keyboard onboarding above the app.
        let keyboardContinue = app.buttons["Continue"]
        if keyboardContinue.waitForExistence(timeout: 2) { keyboardContinue.tap() }
        XCTAssertTrue(start.isEnabled)
        attachScreenshot(app, named: "login-screen")

        guard ProcessInfo.processInfo.environment["RUN_DEV_UI_SMOKE"] == "1" else { return }
        // Explicit opt-in creates only a new, isolated development account; no existing account is touched.
        app.swipeUp(velocity: .slow)
        // Compose scroll settling is not included in XCTest's UIKit idle tracking.
        // Wait for two equal, unobscured frames so the first touch does not merely stop inertia.
        var previousFrame: CGRect?
        let settled = NSPredicate { _, _ in
            let frame = start.frame
            defer { previousFrame = frame }
            let keyboard = app.keyboards.firstMatch
            let unobscured = !keyboard.exists || frame.maxY < keyboard.frame.minY
            return start.isHittable && unobscured && previousFrame == frame
        }
        expectation(for: settled, evaluatedWith: nil)
        waitForExpectations(timeout: 8)
        // Verify a local Compose state change before involving the network.
        let revealPassword = app.buttons["보기"]
        XCTAssertTrue(revealPassword.isHittable)
        revealPassword.press(forDuration: 0.15)
        XCTAssertTrue(app.buttons["숨김"].waitForExistence(timeout: 5),
                      "로그인 요청 전 로컬 버튼 터치도 전달되지 않았습니다.\n" + app.debugDescription)
        attachScreenshot(app, named: "password-toggle-touch")
        start.press(forDuration: 0.15)
        let create = app.buttons["+ 등록"]
        XCTAssertTrue(create.waitForExistence(timeout: 30), "개발 API 로그인 또는 매물 목록 표시 실패")
        XCTAssertTrue(app.staticTexts["첫 번째 집을 기록해 보세요"].waitForExistence(timeout: 10))
        attachScreenshot(app, named: "empty-property-list")
        app.terminate()
        app.launch()
        XCTAssertTrue(create.waitForExistence(timeout: 30), "Keychain에 저장한 세션을 복원하지 못했습니다.")
        let mapTab = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "지도")).firstMatch
        XCTAssertTrue(mapTab.waitForExistence(timeout: 5))
        mapTab.tap()
        XCTAssertTrue(app.buttons["현재 위치로 이동"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.maps.firstMatch.waitForExistence(timeout: 10), "네이티브 MapKit 지도가 표시되지 않았습니다.")
        attachScreenshot(app, named: "native-mapkit")
        let profile = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "마이")).firstMatch
        profile.tap()
        let logout = app.buttons["로그아웃"].firstMatch
        XCTAssertTrue(logout.waitForExistence(timeout: 5))
        logout.tap()
        app.buttons.matching(identifier: "로그아웃").allElementsBoundByIndex.last?.tap()
        XCTAssertTrue(start.waitForExistence(timeout: 10))
    }

    private func attachScreenshot(_ app: XCUIApplication, named name: String) {
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = name
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }
}
