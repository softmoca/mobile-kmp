# 앱 구조와 기술 선택

## 범위와 경계

`mobile-kmp`는 별도의 개인 Git 저장소다. 상위 팀 저장소의 파일과 Git 메타데이터는 변경하지 않는다. 기존 REST API만 사용하며 별도 서버·DB·WebView는 없다. 실제 데이터 대신 화면을 채우는 데모 모드는 만들지 않았다.

## 공유 코드와 플랫폼 코드

- `composeApp/src/commonMain/.../data`: 직렬화 DTO, Ktor API 클라이언트, 인증 StateFlow. 서버 HTTP 메서드·JSON·바이너리 계약을 명시한다.
- `.../domain`: 입력 검증, 비교 선택 제약. UI 없이 공통 테스트한다.
- `.../ui`: Compose Material3 화면, Navigation, 요청 로딩·오류·빈 상태, 기능별 화면 파일.
- `.../platform`: 사진 선택, 현재 위치, 지도, 보안 저장소, PDF에 필요한 작은 인터페이스.
- `androidMain`: Android Keystore, 사진 선택기, LocationManager, MapLibre + OSM 네이티브 지도, PdfRenderer와 파일 공유.
- `iosMain`: ComposeUIViewController와 Swift 플랫폼 구현을 연결하는 어댑터.
- `iosApp`: SwiftUI 호스트, Keychain·PhotosUI·CoreLocation·MapKit·PDFKit. 화면과 비즈니스 로직은 공유 Kotlin에 남긴다.

의존 방향은 UI → API/도메인, 플랫폼 구현 → 공통 인터페이스다. 한 개 KMP 모듈로 시작하며 기능마다 모듈을 나누지 않았다.

## 버전 선정 근거

| 구성                       | 고정 버전                   | 근거                                                                        |
| -------------------------- | --------------------------- | --------------------------------------------------------------------------- |
| Kotlin / Compose compiler  | 2.2.21                      | Kotlin 공식 호환표의 Gradle 8.14, AGP 8.11.1 범위                           |
| Gradle                     | 8.14.3                      | 위 계열의 패치 버전, AGP 요구 8.13 이상                                     |
| Android Gradle Plugin      | 8.11.1                      | Android API 36 지원                                                         |
| Compose Multiplatform      | 1.9.3                       | 안정 패치, iOS 접근성 수정 포함                                             |
| Material3                  | 1.9.0                       | CMP 1.9.3 지정 의존성                                                       |
| Navigation / Lifecycle     | 2.9.1 / 2.9.6               | CMP 1.9.3 릴리스 지정 조합                                                  |
| Ktor                       | 3.3.1                       | 해당 소스의 Kotlin 2.2.20, coroutines 1.10.2, serialization 1.9.0 조합 확인 |
| coroutines / serialization | 1.10.2 / 1.9.0              | Ktor 3.3.1 조합 유지                                                        |
| JDK                        | 실제 빌드 21, 바이트코드 17 | AGP 최소 JDK 17                                                             |
| Android                    | min 26, compile/target 36   | 네이티브 기능과 SDK 호환 범위                                               |
| iOS                        | 최소 16                     | Swift 네이티브 호스트와 사진·지도·PDF API 범위                              |

공식 템플릿의 `androidTarget`, `iosArm64`, `iosSimulatorArm64`, `commonMain` 공유 Compose 및 static framework 구조를 기반으로 구성했다. 현재 개발 기기는 Apple Silicon이다. Intel Mac simulator 타깃은 포함하지 않는다.

- [공식 Compose 템플릿](https://github.com/JetBrains/compose-multiplatform-template/blob/main/shared/build.gradle.kts)
- [Kotlin Multiplatform 호환표](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html)
- [CMP 1.9.3 릴리스와 의존성](https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.9.3)
- [AGP 8.11 요구사항](https://developer.android.com/build/releases/agp-8-11-0-release-notes)
- [Ktor 3.3.1 고정 의존성](https://github.com/ktorio/ktor/blob/3.3.1/gradle/libs.versions.toml)

설치된 Xcode 27.1 / iOS Simulator 27은 Kotlin 2.2.21 공식 호환표의 Xcode 26.0보다 새롭다. 따라서 호환표만으로 지원을 확정하지 않고 실제 컴파일·링크·앱 실행 결과를 BUILD_REPORT에 기록한다.

## 인증과 환경

Android BuildConfig와 iOS Debug/Release 조건부 컴파일이 API 주소를 선택한다. Debug와 Release의 앱 식별자를 분리해 토큰 저장 공간도 분리한다. HTTP 로그에 토큰·본문을 출력하지 않는다. 인증 사진과 PDF도 동일한 Bearer 경계를 사용하며, 서버가 돌려준 임의 URL로 토큰을 보내지 않는다.

`SessionManager`가 익명 회원과 세션의 수명 주기를 관리한다. 보안 저장소가 비어 있는 최초 진입 또는 사용자가 새 기록 시작을 확인한 경우에 본문 없는 `POST /api/auth/login`을 호출한다. 저장된 세션은 `POST /api/auth/refresh`로 복원한다. Access Token은 메모리에만 두며, 버전 2 세션 JSON의 Refresh Cookie·회원 ID·API origin을 플랫폼 보안 저장소에 보관한다.

보호 API의 401은 같은 세션에서 한 번만 refresh하고 원 요청을 한 번 재시도한다. 동시 401 요청은 mutex와 refresh cycle로 갱신 결과·실패를 공유하며, 늦게 도착한 이전 세션 응답이 새 세션을 지우지 않도록 identity를 비교한다. 403이나 네트워크 오류를 인증 만료로 처리하지 않는다. 네트워크 오류에는 기존 쿠키를 보존한다. refresh 실패로 연결이 만료되면 확인용 marker를 저장하며 `/login`으로 자동 전환하지 않는다. 구버전 Access Token 전용 저장값도 자동 삭제하지 않고 새 기록 시작 확인을 제공한다.

Refresh Cookie는 `/api/auth/refresh`에만 직접 전달하고 리다이렉트를 따르지 않는다. 서버의 Secure·HttpOnly·Path·domain을 확인하며 응답 쿠키의 보안 저장이 끝난 뒤 새 Access Token을 공개한다. Ktor Darwin 3.3.1은 NSURLSession의 공유 Cookie Storage를 사용하지 않고, Android도 별도 CookieJar를 설치하지 않는다. 보안 저장소 읽기·복호화 오류는 빈 저장소와 구분하여 신규 회원의 묵시적 생성을 막는다.

UI는 `bootstrapSession`으로 최초 연결을 준비하고 회원 ID가 바뀌면 Navigation과 선택 상태를 새로 만든다. 최신 웹처럼 마이에는 익명 회원 안내와 내 기록 이동을 제공하며 닉네임·비밀번호·로그아웃 버튼은 없다. 수집 이벤트·PostHog·별도 테스터 세션 관리도 넣지 않는다.

## 검증 전략

- 공통 단위/MockEngine 테스트: 익명 인증 계약·쿠키 범위, 세션 복원·갱신 동시성·실패 보존, 빈 목록, 취소, 필드 검증, 비교 제약.
- Android 단위 테스트는 공통 테스트를 JVM에서 실행한다.
- `RUN_DEV_API_SMOKE=1`은 실제 개발 API의 별도 임시 계정에서 앱 ApiClient를 검증하는 명시적 선택이다. 매물/체크리스트는 finally에서 정리한다. 회원 삭제 API가 없어 임시 회원은 남으며, 테스트 자격증명을 저장하거나 출력하지 않는다.
- 빌드와 네이티브 UI 테스트는 실제 실행 결과만 보고한다. 설치·서명·권한·외부 지도 타일은 단위 테스트 통과와 구분한다.
