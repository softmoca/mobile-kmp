# WebView 구조와 책임

## 선택과 변경 범위

웹과 앱의 화면 차이를 없애기 위해 iOS 화면의 단일 소스는 배포된 React 웹이다. SwiftUI는 WKWebView, 최초 로딩/연결 오류, PDF 미리보기를 감싼다. React의 DOM/CSS/라우터를 다시 구현하거나 주입 CSS로 바꾸지 않는다.

| 구성                         | 책임                                                                              |
| ---------------------------- | --------------------------------------------------------------------------------- |
| `JachwiSunbaeApp.swift`      | SwiftUI 앱 진입, WebViewScreen 표시                                               |
| `WebViewConfiguration.swift` | Debug/dev, Debug 로컬 HTTPS, Release/운영 웹의 시작 주소와 정확한 origin 비교     |
| `WebViewModel.swift`         | 영구 WKWebView, 이동 정책, 재시도/당김 새로고침, 웹 confirm/alert, PDF 다운로드   |
| `WebViewScreen.swift`        | 안전 영역, 로딩/오류 화면, Quick Look PDF 미리보기·시스템 공유                    |
| React 웹                     | 모든 업무 UI, React Router, 로그인/refresh/401, 매물·사진·지도·체크리스트·PDF API |
| `scripts/local-web.cjs`      | 팀 Webpack 설정을 읽어 개인 저장소 안에서만 HTTPS 개발 빌드                       |

`iosApp` scheme과 기존 서명값은 유지한다. iOS target에서 Compose framework 빌드 단계·링크와 IOSServices 컴파일을 분리했다. 소스와 Gradle 구성은 삭제하지 않았다. Android는 기존 Compose 앱이다.

## 화면·수명주기

WebView를 네이티브 safe area 안에 한 번만 배치하고 자동 scroll inset을 끈다. 웹 viewport/CSS가 화면과 내부 스크롤을 담당한다. SwiftUI 키보드 safe area는 무시하고 WKWebView의 visualViewport와 웹의 기존 키보드 처리를 사용한다. 헤더·탭바를 추가하지 않는다. 뒤/앞으로 스와이프는 WebKit history를 사용한다. 웹의 저장하지 않은 입력 확인창은 WKUIDelegate의 confirm으로 처리한다.

최초 요청은 HTML 캐시를 재검증하며, 당김 새로고침은 `reloadFromOrigin()`이다. 배포 HTML의 `Cache-Control: no-cache,max-age=0,must-revalidate`와 해시가 붙은 JS/CSS를 확인했다. 쿠키나 website data를 지우는 새로고침은 없다. 앱이 백그라운드에서 돌아올 때 자동 reload하지 않는다. 웹 프로세스 종료/연결 실패/HTTP 오류에는 재시도 화면을 표시한다. SPA 내부 API 오류는 기존 웹의 오류 UI가 처리한다.

## 인증과 데이터 전환

`WKWebsiteDataStore.default()`는 쿠키·웹 데이터를 디스크에 보존한다. [Apple 데이터 저장소 문서](https://developer.apple.com/documentation/webkit/wkwebsitedatastore)와 [WebKit 프로필 설명](https://webkit.org/blog/14423/building-profiles-with-new-webkit-api/)을 기준으로 기본 영구 저장소를 사용한다.

네이티브는 JWT를 읽어 저장하거나 헤더/쿠키에 주입하지 않는다. 상위 React `apiClient.ts`와 backend `AuthController.java`, dev `/v3/api-docs`에서 다음 계약을 확인했다.

- `POST /api/auth/login`: 유효한 Refresh Cookie면 같은 회원 복원, 없거나 무효면 새 익명 회원 생성.
- `POST /api/auth/refresh`: 유효한 쿠키로 토큰 갱신. 무효면 401이며 새 회원을 만들지 않는다.
- Refresh Cookie: `Secure`, `HttpOnly`, `SameSite=Lax`, `Path=/api/auth`, 만료시간 포함. 웹 인증 요청은 `credentials: include`를 사용한다.
- Access Token과 인증 실패 처리는 기존 React 메모리 상태를 따른다.

Compose의 iOS Keychain은 별도 저장소다. 새 WebView에 자동 이전하지 않고 삭제하지도 않는다. 앱 삭제, Bundle ID/Team 변경, 브라우저 데이터 삭제는 전환 절차가 아니다. 웹 쿠키가 만료되면 웹 로그인 계약에 따라 새 회원이 생길 수 있다. 자동 데이터 병합은 구현하지 않는다.

## URL·기기 기능 경계

앱 내부 최상위 이동은 시작 URL과 **scheme/host/port가 같은 HTTPS origin**만 허용한다. 문자열 접두사나 하위 도메인 일괄 허용을 하지 않는다. 같은 origin의 새 창 링크는 현재 WebView에서 연다. 신뢰한 메인 프레임에서 사용자가 누른 외부 HTTP(S)·tel·mailto 링크는 시스템에 전달한다. 알 수 없는 scheme, 외부 페이지 자동 이동, 다른 origin의 blob은 차단한다. 지도·API·이미지 subresource의 일반 HTTPS 요청은 웹이 담당한다.

사진 input은 WebKit의 시스템 선택기를 사용한다. 웹은 현재 JPEG/PNG/WebP, 5MiB, 30장 제한을 적용한다. backend는 HEIC/HEIF도 처리하지만 **현재 React는 원본 HEIC MIME을 허용하지 않는다**. 사진 보관함에서 호환 JPEG로 변환된 경우와 Files에서 원본 HEIC를 고르는 경우는 다르며 실기기 QA가 필요하다. 임의 변환 브리지는 추가하지 않았다. 카메라·위치 목적 문자열을 유지/추가했다. 전체 사진 보관함을 읽는 별도 네이티브 권한은 요구하지 않는다.

PDF는 웹이 인증 API로 받은 Blob의 `a.download` 동작을 [WKDownload](https://developer.apple.com/documentation/webkit/wkdownload)로 이어받는다. 허용 origin의 PDF MIME, 파일 헤더 `%PDF-`, 완료 크기 50MiB 이하를 확인해 임시 파일로 저장하고 Quick Look에서 열기·공유·파일 저장을 제공한다. 알려진 Content-Length는 저장 전에 검사하고, 알 수 없는 길이는 완료 후 검사하므로 다운로드 중 디스크 사용량의 엄격한 상한은 아니다. 공유창이 닫히면 임시 파일을 정리한다. 외부 origin 다운로드 리다이렉트는 거부한다.

인증서 검증 우회, ATS 일괄 예외, JS 네이티브 메시지 브리지, 새 Analytics는 없다. WKWebView inspectable은 Debug/iOS 16.4 이상에서만 켠다.

## Android 확장

후속 Android 셸은 동일한 웹 origin·내부/외부 이동 정책·HTTPS 로컬 정책을 사용하면 된다. CookieManager, 파일 선택, 위치 요청, PDF 파일 공유는 Android 플랫폼 API로 연결해야 한다. 웹 UI와 API 계약·QA 시나리오는 공유하지만 WKWebView/Android WebView 인스턴스와 권한 코드를 KMP로 억지로 통합하지 않는다. 기존 Kotlin 도메인/UI와 네이티브 인증 저장소는 자동으로 웹에 연결되지 않는다.
