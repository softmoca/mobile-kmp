# 실제 빌드·테스트 보고서

검증일: **2026-10-10, Asia/Seoul**. 앱 디렉터리에서 실행했다. 아래 초기 구현 검증 단계에서는 서버 배포/운영 DB 변경/원격 push를 하지 않았다.

후속 게시 단계에서 사용자 요청에 따라 [Issue #1](https://github.com/softmoca/mobile-kmp/issues/1)과 [Draft PR #2](https://github.com/softmoca/mobile-kmp/pull/2)를 생성하고 개인 저장소에 Push했다. `main`은 초기 기반 커밋 `25dabad`, PR 작업 브랜치는 `codex/kmp-mobile`이다. 실제 iPhone 설치·QA 준비는 [IPHONE_QA](IPHONE_QA.md)에 정리했으며, 기기 설치·Apple 계정 인증·서명 설정·Merge는 아직 수행하지 않았다. 이 후속 변경은 문서와 서명 파일 ignore 규칙이며 앱 코드는 변경하지 않았다.

## 환경

- macOS Apple Silicon, JDK Temurin 21.0.12.1, Gradle 8.14.3.
- Android SDK 36 / Build Tools 35.0.0. SDK는 이 저장소의 `.tools/android-sdk`에 설치했다.
- Xcode 27.0 (27A266a), iPhone 18 Pro / iOS Simulator 27.0.
- Debug 개발 API 배포: v1.1.0, `fb5e371d7b56c2dee0df82d87c01205be8cf5a3f`. 상위 저장소 HEAD와 일치.
- 처음의 DNS/Gradle 캐시/Simulator 접근 제한은 필요한 빌드 도구 접근을 승인받아 다시 실행했다. 실패 로그를 성공으로 간주하지 않았다.

## 최종 완료된 검사

### Android 빌드·로컬 테스트·Lint

```sh
./gradlew :composeApp:testDebugUnitTest :composeApp:assembleDebug \
  :composeApp:lintDebug :composeApp:assembleRelease --console=plain
```

**BUILD SUCCESSFUL**. Debug APK와 미서명 Release APK 생성. 로컬 테스트 **33개 성공**(API 19, 도메인 5, UI 상태 9). 실제 서버 테스트 2개는 기본 실행에서 opt-in 조건 때문에 skip한다. 이 두 검사는 아래 별도 실행에서 통과했다. Lint 오류는 0이다. 고정 안정 버전보다 최신 버전이 있다는 알림, 의도적인 동기 SharedPreferences commit, KTX/버전 카탈로그 권고 등의 경고는 남는다.

- `composeApp/build/outputs/apk/debug/composeApp-debug.apk`
- `composeApp/build/outputs/apk/release/composeApp-release-unsigned.apk`
- `composeApp/build/reports/tests/testDebugUnitTest/index.html`
- `composeApp/build/reports/lint-results-debug.html`
- 실제 로그: `.tools/android-final.log`

Android 실물 기기·에뮬레이터 설치/권한 UI 조작은 수행하지 않았다. APK 빌드와 JVM에서 실제 API 클라이언트 실행을 검증한 것이다.

### 실제 개발 API 클라이언트 테스트

```sh
RUN_DEV_API_SMOKE=1 ./gradlew :composeApp:testDebugUnitTest \
  :composeApp:assembleDebug :composeApp:lintDebug --console=plain
```

`DevApiSmokeTest` **2/2 성공**. 이 실행 전체에는 당시 UI 경합 테스트 실패 1건이 있었으며 이후 원인을 수정하고 최종 로컬 테스트 전체를 재실행해 성공했다. 개발 API 테스트의 성공과 전체 빌드 실패를 구분한다.

검증한 기능: 새 비밀번호 보호 테스트 회원 로그인, 세션 복구, 빈 목록, 매물 CRUD, 자유 메모, 사용자 체크리스트 CRUD, 두 단계 체크리스트 적용, 체크 상태·메모·진행률, PNG 사진 추가/바이트 조회/대표 지정/삭제, 두 매물 PDF 생성, 주소 검색/역지오코딩/주변 시설. PDF **63,280 bytes** 및 `%PDF-` 헤더를 확인했다. 주변 시설 **173개**를 실제 응답으로 받았다.

테스트 매물과 체크리스트는 삭제하고 빈 목록을 확인했다. 테스트 계정 2개는 회원 삭제 API가 없어 남는다. 비밀번호와 JWT는 출력·파일 저장하지 않았다. 증거 요약은 [verification/dev-api-results.json](verification/dev-api-results.json)에 보존했다.

### iOS Simulator 앱 빌드

```sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath .tools/DerivedData CODE_SIGNING_ALLOWED=NO build
```

**BUILD SUCCEEDED**. 공통 Kotlin → arm64 Simulator framework → Swift 호스트 → 최종 `.app` 링크까지 확인했다. `.tools/ios-app-final.log`.

산출물: `.tools/DerivedData/Build/Products/Debug-iphonesimulator/JachwiSunbae.app`.

### iOS Simulator 실제 UI 테스트

```sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppDevSmoke \
  -configuration Debug \
  -destination 'platform=iOS Simulator,id=2FC71432-C035-4DD2-A4F9-3E02C83BAFD0' \
  -derivedDataPath .tools/DerivedData \
  -resultBundlePath .tools/ios-ui-signed.xcresult \
  -test-timeouts-enabled YES -default-test-execution-time-allowance 90 \
  -maximum-test-execution-time-allowance 120 \
  CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- test
```

**TEST SUCCEEDED**, XCTest **1개 성공 / 0 실패**, 테스트 실행 36.628초. 비밀번호 표시 버튼, 닉네임 입력, 실제 개발 API 로그인, 빈 매물 목록, 앱 프로세스 종료·재실행 후 **Keychain 세션 복원**, **네이티브 MapKit 표시**, 로그아웃 후 로그인 화면 복귀를 검증했다. `.tools/ios-ui-signed.log`와 `.tools/ios-ui-signed.xcresult`에 원본 결과가 있다.

초기 `CODE_SIGNING_ALLOWED=NO` 실행은 Keychain entitlement 누락으로 실패했다. Simulator `securityd`가 `-34018`을 기록했으며, 로컬 ad-hoc 서명을 켜자 Xcode가 Simulator용 application-identifier를 생성하고 전체 흐름이 통과했다. Apple 계정이나 사용자의 인증서를 사용하지 않았다. 미서명 빌드 성공은 Keychain을 포함한 실행 성공을 의미하지 않는다.

UI 테스트는 매번 무작위 `qa_ios_` 개발 회원을 사용한다. 실패 재시도 중 생성된 회원을 포함해 테스트 회원은 삭제 API가 없어 남는다. 이 테스트에서는 매물·사진·체크리스트를 만들지 않았다. 성공 화면은 [빈 매물 목록](verification/ios-empty-properties.png), [MapKit 지도](verification/ios-mapkit.png)에 보존했다.

### iPhone 기기용 미서명 빌드

```sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -sdk iphoneos \
  -destination 'generic/platform=iOS' \
  -derivedDataPath .tools/DerivedDataDevice CODE_SIGNING_ALLOWED=NO build
```

**BUILD SUCCEEDED**. iosArm64 프레임워크와 기기용 Swift 앱 링크 성공. `.tools/ios-device-build.log`. 실제 기기에 설치한 것은 아니다.

## 빌드 과정에서 발견하고 수정한 문제

- 공통 Navigation의 Android Bundle 전용 접근을 SavedState `read`로 수정.
- 회원 `id/name`, 사진 목록 `items` 등 실제 DTO 연결 수정.
- Android 사진/파일 선택의 nullable coroutine 타입 추론 수정.
- 로그인 복원 시 오프라인 예외가 Compose 효과 밖으로 전파되는 문제 수정.
- 화면 종료가 보안 저장소 삭제를 취소하지 않도록 로그아웃/401 정리 순서·취소 경계를 수정.
- 중복 새로고침 취소·이전 응답 덮어쓰기와 주변 검색 조건 경합 수정.
- JUnit smoke 테스트의 반환 타입을 Unit으로 고정.
- Simulator generic 빌드가 없는 x86_64 리소스를 찾지 않도록 Xcode ARCHS를 arm64로 명시.
- Kotlin에서 내보낸 Swift 콜백 반환 타입을 실제 헤더의 Void에 맞추고 MapKit camera 속성 충돌을 수정.
- 사진 디코딩을 백그라운드에서 최대 2048px로 제한하고 iOS HEIC는 ImageIO 경로를 추가.
- Android 12+ 기기 간 이전에서도 암호화 세션 파일을 제외하도록 dataExtractionRules 추가.
- iOS 실제 실행 중 발견한 비동기 PlistSanityCheck 종료를 수정. Compose가 요구하는 `CADisableMinimumFrameDurationOnPhone=true`를 Info.plist에 추가하고 기기용 앱도 재빌드.
- XCTest가 Compose 입력란을 찾도록 testTag 추가. 새 Simulator의 키보드 안내창 처리와 스크롤 위치 안정 대기를 테스트에 추가.

## 남은 경고와 실행 범위

선택 Kotlin 버전의 공식 표는 Xcode 26.0까지이며 실제 설치된 Xcode 27에서 위 빌드가 통과했다. 이 결과를 모든 Xcode/iOS 버전 지원으로 확대하지 않는다. Simulator 링크의 ICU 데이터 오브젝트에는 minos17.2 메타데이터 경고가 있다. 확인한 오브젝트는 코드(__text)가 없고 정적 데이터만 포함하며 OS 미해결 심볼도 없다. iOS 16 최소 버전은 설정되어 있지만 iOS 16 런타임 실행은 하지 않았다.

위치 권한 거부·재허용과 실제 GPS, 사진 선택기의 실제 사진(HEIC 포함), PDF 파일 저장과 다른 앱으로의 공유는 기기별 수동 검증이 남는다. Android Release 서명과 Apple Team/Provisioning은 사용자의 배포 자격증명을 필요로 하므로 설정하지 않았다. 운영 API 변경 요청은 한 번도 보내지 않았다.
