# 실제 빌드·테스트 보고서

## 저렴한 한 끼 찾기 — 2026-10-29

- 개인 `codex/cheap-meals`, 기존 독립 React clone `codex/mobile-cheap-meals`에서 최신 develop `021267b` 기반으로 구현했다. 이전 변경은 `1855200`과 기존 패치에 보존했다.
- 홈 카드, 전체/식당/편의점/배달, 실제 API·필터·점진 표시·상세 시트·탭 상태 보존을 구현했다. 최신 인증/Emotion/라우터를 사용하며 구형 하단 메뉴를 가져오지 않았다.
- React 전체 100개 파일/680개 테스트 통과. 이후 지도 SDK 처리와 상세 초점·로딩·위치 요청 취소 보완 후 관련 **21개 파일/184개 테스트**가 통과했다. lint/format/typecheck/production build 성공. 초기 추가 회귀 테스트의 로딩 지연 대기와 초점 복원 실패는 수정 후 통과했다.
- 실제 API 네 조회 200, 미인증 401. 시청 1km 22곳, 이마트24 2,337개, 세븐일레븐 미수집 0개, 배달 108개. Simulator 실제 WKWebView API·화면 테스트 2/2, Android 단위 테스트/APK/lint/KMP Simulator framework 성공.
- 실제 iPhone 12 mini에서 홈/전체/상품 이미지·상세·스크롤/배달·상세/탭 필터 유지와 수정 반영 확인. 새 Mac IP의 네이버 허용 URL 등록이 남아 지도 타일·마커·드래그는 이번 실기기에서 미검증이다. 업소 API와 목록은 동작한다.
- 상위 팀 저장소, Xcode Team/Bundle/Info.plist, 기본 실행 URL/Release를 보존했다. 새 작업은 로컬 커밋으로만 보존하고 원격 push/merge/배포하지 않는다.
- 실행법·증거·남은 확인: [CHEAP_MEALS](CHEAP_MEALS.md), [API 결과](verification/cheap-meals-api-2026-10-29.json).

## 작업 저장 재검증 — 2026-10-29

개인 `codex/thrifty-preview`의 변경을 WebView 전환과 알뜰 지도 Preview 두 커밋으로 기록한다. 문서 포맷, WebView URL 정책, Android/KMP 필수 검사, 개인 Team을 제외한 두 커밋 대상 iOS Simulator 앱·UI 테스트 코드 빌드가 통과했다. 실서버·실기기 기능 검증일은 아래 10월 27일이며 이번에 반복하지 않았다. 세부 상태와 후속 작업은 [작업 기록](WORK_LOG.md)을 따른다.

## 알뜰 지도 Preview — 2026-10-27 저녁

작업 브랜치는 `codex/thrifty-preview`다. `feat/webview-shell`의 미커밋 구현과 서명을 보존하고, Git 제외 `.tools/thrifty-preview`에 최신 develop을 복제해 알뜰 UI만 이식했다. 소스는 `preview/thrifty.patch`와 manifest로 재현한다. 실행·복귀 명령은 [THRIFTY_PREVIEW](THRIFTY_PREVIEW.md)를 따른다.

- 실제 dev health/info/OpenAPI 확인. 배포 SHA `43b8d66`이 기준 develop과 일치. 인증 조회에서 시청 500m/1km/2km 2/22/75건, 빈 지역 0건, 미인증 401, 잘못된 반경 400. Mock fallback 없음.
- HTTPS Preview의 WKWebView에서 Secure/HttpOnly 쿠키, 같은 회원 refresh 및 페이지 재로드 후 복원, 실제 업소 API의 3개 반경 응답을 확인했다. iOS 테스트 로그 `.tools/thrifty-ios-tests-2.log`의 API 검사가 통과했다.
- iOS UI 자동화도 통과했다(`.tools/thrifty-ios-ui-final.log`, 1개/실패 0). 홈 → 지도 → 알뜰 → 실제 주소 검색 → 업소 상세/길찾기 링크 → 목록 → 500m 전환을 검사했다. 초기 자동화의 한글 입력·키보드 가림·펼친 시트·접근성 선택자 문제는 수정 후 재검증했다.
- iOS Simulator test build와 기존 Personal Team 서명 iPhone 빌드 성공. 기존 Android 단위 테스트·APK·lint·KMP Simulator framework 검사도 성공했다.
- 이번 작업에서 `project.pbxproj`와 `Info.plist`는 시작 시점과 바이트 단위로 같다. 앱 삭제·Keychain 삭제·원격 push·PR merge·운영 배포 없음.
- 사용자가 iPhone/Mac의 CA 전체 신뢰와 같은 Maps Application의 호스트 등록을 완료한 뒤, 두 환경 모두 HTTPS 및 네이버 지도 타일/마커가 정상 표시됐다. 인증서 경고·TLS 검증을 우회하지 않았다.
- 실제 iPhone 12 mini에서 네 개 탭, 지도 → 알뜰, 업소 마커 선택, 목록/상세 펼치기, 원본 메뉴/가격, 반경 전환을 확인했다. 판교 기준 500m/1km/2km가 1/2/6곳으로 갱신됐다. 길찾기는 실제 카카오맵 앱으로 전환됐다. Mac에서는 서울시청 주소 검색, 2/22/75곳, 지도 드래그 후 재검색 69곳, 상세/전화·외부 지도 링크를 확인했다.
- SDK 인증 실패가 늦게 도착해 내부 객체가 폐기될 때 화면 전체가 중단되는 문제를 재현·수정했다. 지도 오류만 표시하고 실제 업소 목록/반경/상세는 계속 동작한다. 회귀 테스트를 포함해 웹 100개 파일/627개 테스트, lint, format:check, production build/typecheck 통과.
- React 수정이 iPhone WKWebView에 앱 재빌드 없이 반영됐다. 모듈 수정에 따라 자동 새로고침이 일어날 수 있어 입력 상태 유지까지 보장하지 않는다. 공개 인증서 전달용 HTTP 서버는 종료했고 HTTPS Preview 서버는 실행 중이다.
- [실기기 지도](verification/thrifty-iphone-map-2026-10-27.png), [목록](verification/thrifty-iphone-list-2026-10-27.png), [상세](verification/thrifty-iphone-detail-2026-10-27.png), [Mac 상세](verification/thrifty-mac-detail-2026-10-27.png)를 저장했다. 실제 GPS/거부·재허용, 사진 선택/HEIC, PDF 공유/Files, 전화 발신은 이번에 검증하지 않았다.
- 상위 팀 저장소는 읽기 전용이었다. 조회 중 HEAD가 다른 작업으로 바뀌었으나 본 작업에서 상위 Git/추적 파일을 쓰거나 되돌리지 않았다.

> 앞선 `feat/webview-shell`의 iOS 실행 화면은 WKWebView입니다. 최신 실행법·검증은 [WebView QA](WEBVIEW_QA.md)와 [WebView 구조](WEBVIEW_ARCHITECTURE.md)를 따릅니다. 아래 내용은 `codex/kmp-mobile`의 기존 Compose 구현/검증 기록이며, Android는 기존 구현을 유지합니다.

## 현재 WebView 전환 결과 — 2026-10-27

- `feat/webview-shell`에서 dev React를 WKWebView로 표시한다. KMP 소스와 Android는 보존하고 iOS 실행 대상만 분리했다.
- URL 정책 Debug/Release 테스트 통과. dev XCTest에서 인증·쿠키 속성·동일 회원 복원·PNG 업로드·PDF 다운로드·React Router·재실행 확인. 별도 runner 프로세스의 동일 회원 복원도 통과했다.
- iOS Simulator 빌드, iPhone arm64 미서명 및 기존 개인 설정의 **서명 빌드 성공**. Android 단위 테스트·APK·lint·KMP Simulator framework 검사도 성공했다.
- 개인 저장소의 로컬 HTTPS 어댑터로 React 화면·dev 프록시 login/refresh·Secure/HttpOnly 쿠키·WSS HMR의 hot/hash/ok 메시지를 검증했다. 팀 소스 편집에 따른 HMR 화면 반영은 미검증이다.
- 초기 새 Simulator 부팅/설치 지연과 일시적인 웹 API 연결 실패를 관찰했으며 해당 QA Simulator 재시작·재시도로 복구했다. ATS/TLS 검증을 완화하지 않았다.
- 사용자가 iPhone을 연결한 뒤 같은 Bundle ID로 업데이트 설치·실행·재실행에 성공했고 실제 홈/매물 기록 지도/등록 폼을 확인했다. 사진 선택/HEIC·실제 GPS·PDF 공유/Files·전화/메일 전환은 미검증이다. 기존 서명 필드와 기존 Info.plist 값은 모두 보존했다. 네이티브 Keychain 세션은 웹 쿠키로 자동 이전하지 않는다.
- 상위 저장소는 읽기만 했다. 작업 중 상위 Git 상태는 별도의 작업에 의해 변경되어 시작 상태와 달라졌으며 되돌리거나 수정하지 않았다. Push·PR 변경·Merge·운영 배포는 하지 않았다.

구체적인 로그·스크린샷·재현 명령·실기기 체크리스트는 [WEBVIEW_QA](WEBVIEW_QA.md)를 따른다. 아래는 보존한 Compose 검증 기록이다.

검증일: **2026-10-27, Asia/Seoul**. 개인 저장소 `mobile-kmp`에서 실행했다. [Issue #1](https://github.com/softmoca/mobile-kmp/issues/1)과 [Draft PR #2](https://github.com/softmoca/mobile-kmp/pull/2)의 후속 검증이며 작업 브랜치는 `codex/kmp-mobile`이다. 상위 팀 저장소와 운영 서버를 변경하지 않았고 PR을 Merge하지 않았다.

## 확인한 환경

- macOS Apple Silicon, Temurin JDK 21.0.12.1, Gradle 8.14.3.
- 실제 설치 Xcode **27.1 (27A9275)**. iPhone 18 Pro / iOS Simulator **27.0 (24A434)**.
- 신규 테스트 전용 Simulator: `KMP Dev QA 20261027`, UUID `16437E57-81CA-4079-A5B4-1B7E92AC4E44`. 기존 기기·Simulator의 앱 저장소를 초기화하지 않았다.
- dev API **2.1.0**, 배포 커밋 `7e3fa9aa18f67bc1db75da92fd10df660cf3ca9f`. `/v3/api-docs`와 `/actuator/info`를 조회했다.
- 계약 비교 시점의 읽기 전용 상위 소스 HEAD는 `e7494a02b6a8c3081807b8b8755e7fec8b1e5c69`로 배포 SHA와 다르다. 연결 기준은 배포 계약과 실제 응답이다.
- 사용자가 변경한 `project.pbxproj`와 `Info.plist`는 그대로 보존하고 개인 Team·서명 설정을 커밋에 포함하지 않는다.

## 로컬 테스트와 Android 빌드

```sh
./gradlew :composeApp:testDebugUnitTest :composeApp:assembleDebug :composeApp:lintDebug --console=plain
```

**BUILD SUCCESSFUL**. 로컬 테스트 **54개 통과**: API 37, 입력 검증 4, UI 상태 13. 외부 API 테스트 2개는 기본 실행에서 opt-in 조건으로 skip했다. Debug APK 생성 성공, Lint **오류 0 / 경고 31**. Android 실기기 검증과 이번 변경의 Release 빌드는 실행하지 않았다.

- 인증: 최초 익명 진입, Refresh Cookie scope·형식, 기존 회원 복원, 동시 401 단일 갱신/실패 공유, 두 번째 401 종료, 이전 세션 응답 격리, legacy/origin 불일치, 저장소 오류·재시도·쿠키 보존.
- UI: 기존 화면을 유지한 새로고침, 메모 저장 뒤 늦은 조회 차단, 체크 확정 응답 뒤 조회 실패 보존, 실제 완료 필터, 요청 경합·취소·중복 동작.
- 로그: `.tools/refresh-20261027/android-final-insets-validation.log`.
- XML 보존: `.tools/refresh-20261027/unit-test-results/`.
- APK: `composeApp/build/outputs/apk/debug/composeApp-debug.apk`.
- Lint 보고서: `composeApp/build/reports/lint-results-debug.html`.

## 실제 dev API 검사

```sh
RUN_DEV_API_SMOKE=1 ./gradlew :composeApp:testDebugUnitTest \
  --tests 'kr.jachwisunbae.app.DevApiSmokeTest' --rerun-tasks --console=plain
```

**2/2 통과, 0 skip, 0 실패**, 테스트 실행 7.36초. `ApiClient`와 실제 dev 서버를 사용했으며 별도 Mock 서버가 없다.

| 검증       | 실제 결과                                                                                            |
| ---------- | ---------------------------------------------------------------------------------------------------- |
| 익명 인증  | 신규 회원 생성, `/members/me` 동일 ID                                                                |
| 세션 복원  | 새 ApiClient가 보관한 Refresh Cookie로 같은 회원 복원, Access Token은 저장하지 않음                  |
| 토큰 갱신  | 명시 refresh와 테스트 interceptor가 보낸 무효 Bearer의 실제 401 이후 refresh 1회·원 요청 재시도 성공 |
| 매물·메모  | 매물 2개 생성·조회·수정·삭제, 줄바꿈 포함 메모 저장·재조회                                           |
| 체크리스트 | 사용자 양식 생성·수정·조회·삭제, 방문/계약 전 적용, GOOD/CAUTION/UNCONFIRMED 변경, 항목 메모·집계    |
| 사진       | 최소 PNG fixture 업로드·인증 바이트 조회·대표 지정·삭제, 사진 없는 목록                              |
| PDF        | 두 매물 비교 PDF **66,002 bytes**, Content-Type 및 `%PDF-` 확인                                      |
| 지도       | 주소 검색·역지오코딩, 주변 **155개**(버스정류장 **42개**), 편의시설 필터 **37개**                    |
| 정리       | QA 매물 2개·사진·사용자 양식 1개 삭제, 매물·양식 빈 목록 확인                                        |

매물/양식 이름은 `KMP QA 4813d476 ...`로 구분했다. 이 실행은 별도 익명 회원 2개를 사용했으며 회원 삭제 API가 없어 회원은 남는다. 테스트 종료 시 매물·체크리스트 삭제 실패는 테스트 실패로 처리한다. Refresh Cookie·JWT·개인 인증 정보는 출력하거나 파일에 저장하지 않았다.

로그와 XML: `.tools/refresh-20261027/dev-api-smoke.log`, `.tools/refresh-20261027/dev-api-test-results/`. 요약: [verification/dev-api-results-2026-10-27.json](verification/dev-api-results-2026-10-27.json).

## iOS Simulator 검증

```sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppDevSmoke \
  -configuration Debug \
  -destination 'platform=iOS Simulator,id=16437E57-81CA-4079-A5B4-1B7E92AC4E44' \
  -derivedDataPath .tools/refresh-20261027/DerivedData \
  -resultBundlePath .tools/refresh-20261027/ios-ui-insets-fixed.xcresult \
  -collect-test-diagnostics never \
  -test-timeouts-enabled YES -default-test-execution-time-allowance 300 \
  -maximum-test-execution-time-allowance 360 \
  CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- DEVELOPMENT_TEAM= test
```

**TEST SUCCEEDED: 1/1 통과, 0 실패, 121.110초.** 앱 실행·같은 익명 회원의 Keychain 복원·네이티브 MapKit을 확인했다. 29자 QA 매물명과 긴 주소로 등록하고 사진 없음 표시·메모 저장을 검사했다. 앱을 종료·재실행한 뒤 서버에서 매물과 메모를 다시 읽었으며, 마지막에 테스트 매물을 삭제하고 빈 목록을 확인했다.

QA 이름: `KMP iOS QA 7F56EEC6 LONG-NAME`. 로그의 `UI QA property cleanup confirmed`까지 확인했다. 로그: `.tools/refresh-20261027/ios-ui-insets-fixed.log`, 결과 번들: 위 명령의 `.xcresult`.

확인한 화면: [긴 이름·주소와 사진 없음](verification/ios-property-long-text-2026-10-27.png), [MapKit](verification/ios-mapkit-2026-10-27.png), [재실행 후 메모](verification/ios-memo-restored-2026-10-27.png), [삭제 후 빈 목록](verification/ios-cleanup-empty-2026-10-27.png). [실행 요약 JSON](verification/ios-results-2026-10-27.json)에 검증한 코드 커밋과 결과를 기록했다.

Simulator는 로컬 ad-hoc 서명을 사용했다. Apple 계정 인증서나 실기기 provisioning profile을 사용하지 않았다. 기본 `iosApp` scheme의 UI 테스트는 앱 실행 전 skip하며 실제 네트워크 검사는 `iosAppDevSmoke`에서만 opt-in한다. 테스트 전용 Simulator의 익명 회원은 남고 정상 종료 시 QA 매물은 삭제한다.

## iPhone arm64 빌드

```sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -sdk iphoneos -destination 'generic/platform=iOS' \
  -derivedDataPath .tools/refresh-20261027/DerivedDataDevice \
  CODE_SIGNING_ALLOWED=NO DEVELOPMENT_TEAM= build
```

**BUILD SUCCEEDED**. 등록 경로와 키보드 여백 수정까지 포함한 `iosArm64` 프레임워크와 Swift 앱 링크를 확인했다. 로그: `.tools/refresh-20261027/ios-device-insets-final.log`. 산출물은 `.tools/refresh-20261027/DerivedDataDevice/Build/Products/Debug-iphoneos/JachwiSunbae.app`이다.

실제 iPhone 설치 성공을 뜻하지 않는다. Xcode에서 기존 Team·Bundle Identifier를 유지하고 실제 기기를 선택해 개발 서명으로 설치해야 한다. 개인 설정 파일은 시작 시 저장한 SHA-256과 비교해 보존 여부를 확인한다. [IPHONE_QA](IPHONE_QA.md)를 따른다.

## 이번 검증 중 수정한 문제

1. 실제 API 계약 변경: 닉네임/비밀번호 DTO와 endpoint를 제거하고 익명 로그인·Refresh Cookie로 전환했다. `/nearby`에서 제거된 TRANSPORT는 별도 버스정류장 endpoint로 조회한다.
2. 보안 저장소의 없음과 읽기 오류를 구분한다. 저장 실패 후 수신한 쿠키는 메모리에서 보존해 저장 재시도를 먼저 하며 중복 익명 회원 생성을 막는다. 저장 성공 전 프로세스가 종료되면 복원을 보장할 수 없으므로 오류를 숨기지 않는다.
3. 일반 요청 갱신·늦은 응답·계정 전환 경합을 보호하고, 만료를 조용한 새 계정 생성으로 처리하지 않는다.
4. UI 갱신 중 기존 화면과 메모 초안을 유지하고, 확정된 체크 저장 결과가 후속 조회 실패 때문에 이전 상태로 보이지 않도록 했다.
5. 초기 통합 컴파일의 URLBuilder 접근과 TRANSPORT 표시 분기 누락을 수정했다.
6. MockEngine 기본 dispatcher와 runTest 가상 시계가 분리되어 인증 30초 제한이 먼저 만료되던 테스트 환경을 공통 scheduler로 맞췄다. 앱의 timeout을 제거하지 않았다. 이후 54개 테스트가 통과했다.
7. 실제 Simulator에서 매물 등록이 빈 화면으로 열리는 문제를 재현했다. Navigation 2.9.1의 nonAndroid 경로 우선순위에서 `property/new`가 `property/{id}`와 겹쳤다. 매물/양식 생성 경로를 `property-create`/`template-create`로 분리했다. 고정 버전의 `NavDeepLink.kt`와 `NavDestination.nonAndroid.kt` 소스도 확인했다.
8. 키보드가 나타나면 등록 폼이 이름 입력칸 높이로 줄어드는 문제를 영상·접근성 트리로 확인했다. 바깥 Scaffold의 `safeDrawing` 여백을 NavHost에서 소비하지 않아 하위 `imePadding()`이 키보드 높이를 다시 적용했다. `consumeWindowInsets(insets)`로 중복 적용을 막았다. [Compose Scaffold 여백 처리 기준](https://developer.android.com/develop/ui/compose/system/material-insets)을 함께 확인했다.

중간 Simulator 실행은 등록 경로·키보드 소개 패널·중복 여백 문제로 등록 제출 전에 실패했다. 테스트는 안내 패널을 조건부로 닫고 키보드 위 표시 영역에서 스크롤하도록 보완했다. 실패 실행을 성공으로 기록하지 않으며, 최종 실행 결과와 증거는 위 Simulator 항목에 기록한다.

## 남은 범위와 경고

- 실제 iPhone의 설치·GPS·위치 권한 거부/재허용, 실제 HEIC 선택, PDFKit/Files/외부 공유, 작은 화면·큰 글꼴·다수 매물 사용은 사용자의 기기에서 이어서 확인한다.
- iOS 16 최소 설정은 유지하지만 해당 런타임에서 실행하지 않았다. Simulator의 ICU minOS 17.2와 XCTest minOS 17 경고, MapLibre deprecated API·Gradle/AGP 권고 경고가 남는다. Xcode 27.1의 실제 빌드 결과를 모든 버전 지원으로 확대하지 않는다.
- App Store/TestFlight, Android 실기기, PostHog/행동 분석, 운영 환경 변경은 이번 작업에 포함하지 않는다.
- 테스트 데이터만 생성·정리했다. 새 테스트 회원은 삭제 endpoint가 없어 남는다. 상위 팀 저장소는 읽기 전용이며 개인 원격의 Draft PR만 갱신한다.

## 이전 초기 검증

2026-10-10의 v1.1.0 닉네임 로그인 기준 결과는 과거 기록이다: 로컬 33개, 실제 API 2개, Simulator XCTest 1개, Android Debug/미서명 Release·Lint와 iPhone 미서명 빌드 성공. 당시 PDF는 63,280바이트, 주변 시설은 173개였다. [당시 보고서](https://github.com/softmoca/mobile-kmp/blob/6f4788347a68ee85ccaf68c31b8f5429ab5a46c1/docs/BUILD_REPORT.md)와 기존 `verification` 파일은 현재 익명 계약의 검증 결과로 사용하지 않는다.
