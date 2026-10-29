import XCTest

final class SmokeTests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }

    func testAnonymousSessionAndPropertyLifecycle() throws {
        guard ProcessInfo.processInfo.environment["RUN_LEGACY_COMPOSE_UI_SMOKE"] == "1" else {
            throw XCTSkip("익명 로그인은 앱 실행만으로 개발 회원을 만들 수 있어 명시적 개발 UI 검사에서만 실행합니다.")
        }
        // Use the isolated QA simulator. Never reset or replace an existing user's stored session.
        let app = XCUIApplication()
        app.launch()
        let create = app.buttons["+ 등록"]
        XCTAssertTrue(create.waitForExistence(timeout: 30), "개발 API 익명 세션 또는 매물 목록 표시 실패")
        XCTAssertTrue(app.staticTexts["첫 번째 집을 기록해 보세요"].waitForExistence(timeout: 10))
        attachScreenshot(app, named: "empty-property-list")

        let profile = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "마이")).firstMatch
        XCTAssertTrue(profile.waitForExistence(timeout: 5))
        profile.tap()
        let memberId = app.descendants(matching: .any).matching(identifier: "anonymous-member-id").firstMatch
        XCTAssertTrue(memberId.waitForExistence(timeout: 5))
        let originalMember = memberId.label
        XCTAssertTrue(originalMember.hasPrefix("회원 번호 "))
        XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "anonymous-member").firstMatch.exists)
        attachScreenshot(app, named: "anonymous-member")

        app.terminate()
        app.launch()
        XCTAssertTrue(create.waitForExistence(timeout: 30), "Keychain의 Refresh 세션을 복원하지 못했습니다.")
        XCTAssertTrue(profile.waitForExistence(timeout: 5))
        profile.tap()
        XCTAssertTrue(memberId.waitForExistence(timeout: 5))
        XCTAssertEqual(memberId.label, originalMember, "앱 재실행 후 다른 익명 회원으로 바뀌었습니다.")
        attachScreenshot(app, named: "restored-anonymous-member")

        let mapTab = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "지도")).firstMatch
        XCTAssertTrue(mapTab.waitForExistence(timeout: 5))
        mapTab.tap()
        XCTAssertTrue(app.buttons["현재 위치로 이동"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.maps.firstMatch.waitForExistence(timeout: 10), "네이티브 MapKit 지도가 표시되지 않았습니다.")
        attachScreenshot(app, named: "native-mapkit")

        let propertyName = "KMP iOS QA " + UUID().uuidString.prefix(8) + " LONG-NAME"
        let address = "QA-only long address for layout verification, Building 101 Room 1001, no real location selected."
        let memo = "KMP iOS QA memo " + UUID().uuidString + ": saved through the UI and verified after app restart."
        let cleanup = PropertyCleanupStatus()
        // A property name is safe diagnostic data; no credential or cookie is logged.
        print("UI QA temporary property: \(propertyName)")
        addTeardownBlock {
            switch cleanup.phase {
            case .notSubmitted:
                print("UI QA registration was not submitted: \(propertyName)")
            case .submissionAttempted:
                print("UI QA cleanup NOT confirmed. Find temporary property: \(propertyName)")
            case .deleted:
                break
            }
        }
        let propertiesTab = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "내 매물")).firstMatch
        XCTAssertTrue(propertiesTab.waitForExistence(timeout: 5))
        propertiesTab.tap()
        XCTAssertTrue(create.waitForExistence(timeout: 5))
        create.tap()

        let nameInput = tagged("property-name-input", in: app)
        XCTAssertTrue(nameInput.waitForExistence(timeout: 5))
        nameInput.tap()
        app.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: "새 매물".count))
        app.typeText(propertyName)
        // XCTest typing can trigger iOS's introduction after the field first receives focus.
        let keyboardIntroduction = app.buttons["Continue"]
        if keyboardIntroduction.waitForExistence(timeout: 3) {
            keyboardIntroduction.tap()
        }
        let addressInput = tagged("property-address-input", in: app)
        tapVisible(addressInput, in: app)
        app.typeText(address)
        tapVisible(tagged("property-submit", in: app), in: app, beforeTap: cleanup.markSubmissionAttempted)

        let detailName = tagged("property-detail-name", in: app)
        XCTAssertTrue(detailName.waitForExistence(timeout: 30), "매물 등록 후 상세 화면 표시 실패")
        XCTAssertEqual(detailName.label, propertyName)
        XCTAssertEqual(tagged("property-detail-address", in: app).label, address)
        XCTAssertTrue(app.staticTexts["등록한 사진이 없어요"].exists)
        attachScreenshot(app, named: "created-property-long-text-no-photo")

        let memoTab = app.buttons["메모"]
        memoTab.tap()
        let memoInput = tagged("property-memo-input", in: app)
        XCTAssertTrue(memoInput.waitForExistence(timeout: 10))
        tapVisible(memoInput, in: app)
        app.typeText(memo)
        tapVisible(tagged("property-memo-save", in: app), in: app)
        XCTAssertTrue(app.staticTexts["메모를 저장했어요."].waitForExistence(timeout: 20), "메모 저장 성공을 확인하지 못했습니다.")

        // Restart drops the in-memory screen state, so reopening exercises a server read.
        app.terminate()
        app.launch()
        XCTAssertTrue(create.waitForExistence(timeout: 30))
        let propertyRow = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH %@", "property-row-")).firstMatch
        XCTAssertTrue(propertyRow.waitForExistence(timeout: 10))
        attachScreenshot(app, named: "persisted-property-list")
        tapVisible(propertyRow, in: app)
        XCTAssertTrue(detailName.waitForExistence(timeout: 15))
        XCTAssertEqual(detailName.label, propertyName)
        memoTab.tap()
        XCTAssertTrue(memoInput.waitForExistence(timeout: 10))
        let restoredMemo = (memoInput.value as? String ?? "") + " " + memoInput.label
        XCTAssertTrue(restoredMemo.contains(memo), "재조회한 메모가 저장한 메모와 다릅니다.")
        attachScreenshot(app, named: "persisted-property-memo")

        app.buttons["기본 정보"].tap()
        tapVisible(tagged("property-delete", in: app), in: app)
        let confirmDelete = tagged("confirm-delete", in: app)
        XCTAssertTrue(confirmDelete.waitForExistence(timeout: 5))
        confirmDelete.tap()
        XCTAssertTrue(app.staticTexts["첫 번째 집을 기록해 보세요"].waitForExistence(timeout: 20), "매물 삭제 후 빈 목록을 확인하지 못했습니다.")
        XCTAssertFalse(propertyRow.exists, "삭제한 테스트 매물이 목록에 남아 있습니다.")
        cleanup.markDeleted()
        print("UI QA property cleanup confirmed: \(propertyName)")
        attachScreenshot(app, named: "empty-list-after-property-delete")
        // The test property is deleted; the API has no member deletion endpoint.
        app.terminate()
    }

    private func tagged(_ identifier: String, in app: XCUIApplication) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: identifier).firstMatch
    }

    private func tapVisible(_ element: XCUIElement, in app: XCUIApplication, beforeTap: () -> Void = {},
                            file: StaticString = #filePath, line: UInt = #line) {
        func viewport() -> CGRect {
            let scroll = app.scrollViews.firstMatch
            var bounds = (scroll.exists ? scroll.frame : app.frame).intersection(app.frame)
            let keyboard = app.keyboards.firstMatch
            if keyboard.exists, bounds.intersects(keyboard.frame) {
                bounds.size.height = max(0, keyboard.frame.minY - bounds.minY)
            }
            return bounds.insetBy(dx: 12, dy: 12)
        }
        func unobscured() -> Bool {
            guard element.exists, element.isHittable else { return false }
            let frame = element.frame
            return !frame.isEmpty && viewport().contains(CGPoint(x: frame.midX, y: frame.midY))
        }
        for _ in 0..<12 {
            if unobscured() { break }
            let bounds = viewport()
            guard !bounds.isEmpty, bounds.height > 80 else {
                XCTFail("스크롤할 표시 영역이 없습니다: \(element.identifier)", file: file, line: line)
                return
            }
            let targetFrame = element.exists ? element.frame : .zero
            let scrollDown = !targetFrame.isEmpty && targetFrame.midY < bounds.midY
            let upperY = bounds.minY + bounds.height * 0.25
            let lowerY = bounds.minY + bounds.height * 0.75
            let origin = app.coordinate(withNormalizedOffset: .zero)
            let start = origin.withOffset(CGVector(dx: bounds.midX - app.frame.minX,
                                                   dy: (scrollDown ? upperY : lowerY) - app.frame.minY))
            let end = origin.withOffset(CGVector(dx: bounds.midX - app.frame.minX,
                                                 dy: (scrollDown ? lowerY : upperY) - app.frame.minY))
            print("UI QA scroll \(element.identifier): target=\(targetFrame), viewport=\(bounds), down=\(scrollDown)")
            start.press(forDuration: 0.05, thenDragTo: end, withVelocity: .slow, thenHoldForDuration: 0.1)
        }
        XCTAssertTrue(unobscured(), "스크롤 후에도 요소를 누를 수 없습니다: \(element.identifier)", file: file, line: line)
        var previousFrame: CGRect?
        let settled = NSPredicate { _, _ in
            let frame = element.frame
            defer { previousFrame = frame }
            return unobscured() && previousFrame == frame
        }
        let wait = XCTNSPredicateExpectation(predicate: settled, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [wait], timeout: 8), .completed,
                       "스크롤이 안정되지 않았습니다: \(element.identifier)", file: file, line: line)
        beforeTap()
        element.tap()
    }

    private func attachScreenshot(_ app: XCUIApplication, named name: String) {
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = name
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }
}

// XCTest teardown is @Sendable; protect the small shared state instead of capturing a mutable local.
private final class PropertyCleanupStatus: @unchecked Sendable {
    enum Phase { case notSubmitted, submissionAttempted, deleted }

    private let lock = NSLock()
    private var storedPhase: Phase = .notSubmitted

    var phase: Phase {
        lock.lock()
        defer { lock.unlock() }
        return storedPhase
    }

    func markSubmissionAttempted() { update(.submissionAttempted) }
    func markDeleted() { update(.deleted) }

    private func update(_ phase: Phase) {
        lock.lock()
        defer { lock.unlock() }
        storedPhase = phase
    }
}
