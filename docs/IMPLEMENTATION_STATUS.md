# 구현 상태

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

2026-10-27 기준. 이번 목표는 실제 개발 API를 사용하는 iPhone 앱을 반복 개선하는 것이다. 구현·API 실행·Simulator UI·물리 기기 검증을 구분한다. 최신 실행 결과는 [BUILD_REPORT](BUILD_REPORT.md)에 기록한다.

| 기능                 | 현재 구현                                                                            | 검증 범위                                                             |
| -------------------- | ------------------------------------------------------------------------------------ | --------------------------------------------------------------------- |
| 익명 인증            | 최초 자동 로그인, 닉네임/비밀번호 화면 제거                                          | 단위 테스트와 실제 dev API                                            |
| 세션 복원·갱신       | Refresh Cookie 보안 저장, Access Token 메모리, 같은 회원 복원, 401 한 번 갱신·재요청 | 동시성/실패 보존 단위 테스트, 실제 401·refresh, Simulator Keychain    |
| 구버전·만료·오프라인 | 기존 기록 연결을 자동으로 새 회원으로 바꾸지 않음, 재시도·새 기록 명시 확인          | API/화면 상태 테스트. iPhone 수동 오류 QA 필요                        |
| 매물 CRUD·메모       | 실제 목록/상세/등록/수정/삭제, 메모 저장·조회                                        | 실제 API, Simulator 등록/메모/재실행/삭제                             |
| 금액·날짜·옵션       | 원 단위 Long, 서버 DTO·입력 검증                                                     | 도메인 테스트·실제 매물 API                                           |
| 목록 UI              | 검색·완료 필터, 서버 진행률, 사진 없음, 긴 이름·주소                                 | 필터 단위 테스트, Simulator QA 데이터. 다수 매물/큰 글자 수동 QA 필요 |
| 사진                 | 업로드·인증 바이트·대표 지정·삭제, 오류·재시도                                       | 실제 PNG API. iPhone PhotosUI/HEIC·전체보기 수동 QA 필요              |
| 사용자 체크리스트    | 시스템 항목 선택·순서·CRUD                                                           | 실제 API·입력 테스트                                                  |
| 단계별 체크          | 기본/사용자 양식 적용, GOOD/CAUTION/UNCONFIRMED·메모·집계, 갱신 중 화면 유지         | 실제 API·상태 회귀 테스트. 기기 스크롤 QA 필요                        |
| 주소·주변 시설       | 실제 검색·역지오코딩, 반경·카테고리 조건, 이전 응답 폐기                             | 실제 dev API·조건 경합 테스트                                         |
| iOS 지도·위치        | MapKit, 사용자 요청 시 CoreLocation 권한                                             | Simulator 지도. 실제 GPS·거부/재허용 미검증                           |
| Android 네이티브     | Keystore·MapLibre·사진·PDF 유지                                                      | Debug 빌드·Lint. Android 실기기는 이번 범위 제외                      |
| 비교·PDF             | 2~5개 순서 유지 비교, 서버 PDF·PDFKit/파일/공유                                      | 선택 제약 테스트·실제 PDF API. iPhone 저장/공유·내용 시각 QA 필요     |
| 개발 환경            | Debug dev API, Release 설정 유지, 개인 Xcode 설정 보존                               | Simulator와 기기용 빌드 결과는 BUILD_REPORT 기준                      |

## 기존 구조와 최신화

공통 Kotlin UI·도메인·API와 Android/iOS 네이티브 브리지를 유지했다. Spring Boot의 Controller/DTO와 배포 OpenAPI를 비교했고 기존 React는 화면·흐름 참고 자료로만 사용했다. 웹을 감싸는 WebView나 별도 Mock 서버를 추가하지 않았다. 단위 테스트의 MockEngine/Fixture 외에는 화면 데이터를 만들어 채우는 모드가 없다.

최신 웹의 익명 기록 안내, 기록 목록과 상세 사용 흐름을 반영했다. 새 홈 카드·날씨·알림·시세 기능까지 범위를 넓히지 않았다. PostHog·클릭·화면 이벤트·테스터 세션 관리·`comparison-views` 호출은 없다. 백엔드와 운영 환경도 변경하지 않았다.

## 남은 범위

1. 사용자의 iPhone에서 기존 Team·Bundle Identifier로 Debug 설치 후 같은 회원·기록 복원, 실제 사진·GPS·PDF 저장/공유를 확인한다.
2. 작은 화면·큰 글자·많은 매물·긴 주소/질문에서 접근성과 스크롤을 점검한다. 메모 초안 유지와 체크 집계·완료 필터를 실제 사용으로 확인한다.
3. 회원 삭제·닉네임 계정 복원 API가 없어 새 QA 익명 회원은 서버에 남는다. 테스트 매물·체크리스트는 생성 이름/ID를 추적하고 삭제 결과를 확인한다.
4. 임의 질문 생성, 다중 사진 업로드, 미저장 폼 이탈 확인은 아직 제공하지 않는다. 사진은 한 번에 한 장, API 한도는 5MiB다.

App Store/TestFlight, Android 실기기, 운영 API 변경, 신규 백엔드 개발은 이번 작업 목표가 아니다. [Draft PR #2](https://github.com/softmoca/mobile-kmp/pull/2)는 사용자의 실기기 검토가 끝날 때까지 Merge하지 않는다.
