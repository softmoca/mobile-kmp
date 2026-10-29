# 자취선배 모바일

이 브랜치의 iOS 앱은 **기존 React 웹을 표시하는 WKWebView 셸**이다. 기본 Debug 주소는 `https://dev.jachwi-sunbae.kr`이다. 웹의 헤더·하단 메뉴·React Router·API 인증을 그대로 사용한다. 별도의 Compose 화면이나 네이티브 탭바를 겹치지 않는다.

개인 `mobile-kmp` 저장소에서만 작업한다. 상위 팀 저장소는 읽기 전용이다. 기존 Compose 앱은 `codex/kmp-mobile`의 `ff869a3`와 Draft PR #2에 보존되며, Kotlin·Android 소스도 이 브랜치에 남아 있다.

## iPhone에서 실행

1. `iosApp/iosApp.xcodeproj`를 Xcode에서 연다.
2. 기존 `iosApp` scheme, **Debug**, 연결한 iPhone을 선택한다.
3. 현재 Personal Team과 Bundle Identifier를 유지하고 Run한다. iOS WebView 실행에는 Gradle/JDK가 필요 없다.
4. 첫 화면이 dev 웹의 홈인지 확인한다. 화면 맨 위에서 아래로 당기면 쿠키를 유지한 채 새로고침한다. 앱을 완전히 종료한 뒤 실행해도 최신 HTML을 재검증한다.

**기존 앱을 삭제하지 말고 같은 식별자로 업데이트 설치한다.** 기존 Compose Keychain 기록은 삭제하지 않지만 WebKit 쿠키로 자동 이관하지 않는다. 첫 WebView 실행은 별도 익명 회원으로 시작할 수 있으며 기존 Compose 기록이 표시되지 않는 것이 데이터 삭제를 뜻하지 않는다. Safari, WebView, 로컬 HTTPS 호스트의 쿠키도 각각 다르다. 전환 전에 기존 앱의 중요한 기록·회원 번호를 확인한다. 자동 계정 연결/병합 기능은 없다.

개인 Team·Bundle Identifier가 포함된 `project.pbxproj` 전체를 무심코 커밋하지 않는다. 시작 시점의 개인 설정은 Git 제외 경로 `.tools/webview-baseline/`에 보존했다. Xcode 계정 로그인·기기 신뢰·개발자 모드는 사용자가 처리한다. App Store/TestFlight 배포는 필요 없다.

## Simulator 빌드와 테스트

```sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath .tools/WebViewShellData \
  CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- build

npm run test:webview
```

`test:webview`는 네트워크 없이 Debug/Release URL 정책을 검사한다. 기본 `iosApp` scheme의 네트워크 테스트는 skip한다. 아래 검사는 **새 QA Simulator**에서만 실행한다. 실제 dev 익명 회원과 테스트 매물을 만들며, 테스트 매물은 정리하지만 회원 삭제 API가 없어 익명 회원은 남는다.

```sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppDevSmoke \
  -configuration Debug -destination 'platform=iOS Simulator,id=<QA-SIMULATOR-UUID>' \
  -derivedDataPath .tools/WebViewShellData -parallel-testing-enabled NO \
  CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- test
```

최신 실행 결과와 미검증 범위는 [WebView QA](docs/WEBVIEW_QA.md), [빌드 보고서](docs/BUILD_REPORT.md)를 따른다. 기존 Compose 검증 결과를 WebView 검증 결과로 보지 않는다.

## 웹 수정 반영

- **배포 dev 모드:** 웹이 dev에 배포된 후 당김 새로고침 또는 종료·재실행. 앱 재빌드는 필요 없다. 백그라운드에서 돌아올 때는 작성 중인 입력을 지키기 위해 자동 새로고침하지 않는다.
- **선택적 로컬 모드:** [HTTPS 로컬 개발 안내](docs/WEBVIEW_LOCAL_DEVELOPMENT.md)에 따라 개인 저장소에서 `npm run dev:web`를 실행하고 Xcode Debug 실행 환경에 `JACHWI_WEB_URL`을 지정한다. HTTP 주소는 거부한다. Release는 이 환경변수를 무시하고 팀의 운영 웹 주소만 사용한다. 이번 검증에서 운영 웹/API는 호출하지 않는다.

## 저렴한 한 끼 찾기 Preview

현재 `codex/cheap-meals`는 최신 develop을 기반으로 홈 카드 → 전체/식당/편의점/배달을 제공한다. 실제 dev API와 최신 인증·디자인 시스템을 사용하며 기존 홈 구조를 유지한다. 독립 React clone은 `.tools/thrifty-preview`, 브랜치는 `codex/mobile-cheap-meals`다. 이전 알뜰 지도 소스·패치는 보존했다.

```sh
# Node 22 환경, 개인 mobile-kmp 루트
npm run preview:prepare
npm --prefix .tools/thrifty-preview/frontend ci
JACHWI_LOCAL_ORIGIN=https://192.168.3.253:3443 npm run dev:preview
```

Xcode Debug 실행 환경 `JACHWI_WEB_URL`을 같은 HTTPS origin으로 지정한다. 앱이 이 주소를 기억하므로 홈 화면 아이콘으로 다시 실행해도 Preview를 유지한다. 배포 dev 웹으로 복귀하려면 값을 비운 `JACHWI_WEB_URL=`로 한 번 실행한다. 기존 인증서·ignored 환경설정이 필요하며 IP가 바뀌면 인증서 SAN과 네이버 허용 URL도 갱신한다. 10월 29일 사용자 등록 주소를 `192.168.3.255`에서 `192.168.3.253`으로 정정한 뒤 실제 iPhone과 브라우저의 지도 표시를 확인했다. `npm run verify:cheap-meals-api`로 네 API를 검증하고 `npm run preview:save`로 소스를 `preview/cheap-meals.patch`에 저장한다.

실행·복구·수동 QA·실제 API와 제약은 **[저렴한 한 끼 찾기 안내](docs/CHEAP_MEALS.md)**를 따른다. 세븐일레븐 0건은 현재 dev 데이터의 미수집 상태다. 과거 알뜰 지도 기록은 [THRIFTY_PREVIEW](docs/THRIFTY_PREVIEW.md)에 남겼다.

## Android와 기존 KMP

Android는 기존 Compose 구현을 유지한다. JDK 21과 개인 `local.properties`의 Android SDK 경로가 필요하다.

```sh
./gradlew :composeApp:testDebugUnitTest :composeApp:assembleDebug :composeApp:lintDebug
```

Android WebView 전환은 후속 작업이다. `IOSServices.swift`와 Kotlin iOS 진입점도 보존했지만 현재 iOS 앱의 실행·링크 대상에서는 제외했다. 기존 구현을 보고 싶으면 별도 checkout에서 `codex/kmp-mobile`을 사용한다. 현재 서명 변경을 reset하거나 앱을 삭제해서 복구하지 않는다.

## 문서

- [WORK_LOG](docs/WORK_LOG.md): 작업별 변경 내용·검증 상태·후속 확인 기록

- [WEBVIEW_ARCHITECTURE](docs/WEBVIEW_ARCHITECTURE.md): UI 단일 소스, URL·쿠키·PDF 정책, Android 확장
- [WEBVIEW_LOCAL_DEVELOPMENT](docs/WEBVIEW_LOCAL_DEVELOPMENT.md): HTTPS·API 프록시·HMR·네이버 지도 제약
- [WEBVIEW_QA](docs/WEBVIEW_QA.md): 실제 검사 결과와 iPhone 체크리스트
- [BUILD_REPORT](docs/BUILD_REPORT.md), [IMPLEMENTATION_STATUS](docs/IMPLEMENTATION_STATUS.md): 현재/기존 구현 구분
- [API_CONTRACT](docs/API_CONTRACT.md): 기존 API 계약 근거

상위 저장소를 수정하지 않고 개인 저장소 루트에서 문서를 정렬한다.

```sh
npm ci
npm run format:docs
npm run format:docs:check
```
