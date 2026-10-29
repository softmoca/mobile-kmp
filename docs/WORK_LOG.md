# 작업 기록

작업 단위별 변경 내용, 검증 시점, 남은 확인 항목을 기록한다. 상세 실행법과 증거는 각 문서에 연결하며, 커밋 이력으로 해당 시점의 소스를 복원할 수 있게 한다.

## 2026-10-27 — iOS WebView 전환

- 커밋: [`7a93372`](https://github.com/softmoca/mobile-kmp/commit/7a933729f1a7748a3342e302be29ec9e8a8bba6c) — `feat: iOS 앱을 React WebView 셸로 전환한다`
- 변경: iOS 실행 화면을 기존 React 웹을 여는 WKWebView로 전환했다. Kotlin·Android 소스와 기존 Keychain은 보존했다. Debug/Release URL 정책, 웹 쿠키 유지, 새로고침·오류 복구, 외부 URL 및 PDF 처리를 추가했다.
- 로컬 개발: 개인 저장소에서 HTTPS React Preview, dev API 프록시, Secure/HttpOnly 쿠키와 WSS HMR 연결을 준비했다. 상위 팀 저장소는 읽기 전용으로 사용했다.
- 10월 27일 검증: URL 정책, 실제 dev 인증·쿠키 복원·PNG API 업로드·PDF 다운로드, React 라우팅, Simulator/서명 iPhone 빌드, iPhone 업데이트 설치·실행·재실행, Android/KMP 필수 검사 통과.
- 남은 확인: 실제 GPS와 권한 재허용, 시스템 사진 선택/HEIC, PDF 공유·Files 저장, 전화 발신. API 수준 검증과 실기기 UI 검증을 구분한다.
- 기록: [WebView QA](WEBVIEW_QA.md), [구조](WEBVIEW_ARCHITECTURE.md), [로컬 실행법](WEBVIEW_LOCAL_DEVELOPMENT.md), [검증 결과](verification/webview-results-2026-10-27.json).

## 2026-10-27 — 알뜰 지도 Preview와 실제 dev API

- 커밋 제목: `feat: 실제 개발 API를 사용하는 알뜰 지도 Preview를 통합한다`
- 변경: 최신 develop React에 알뜰 지도, 주소 검색, 반경 선택, 지도 재검색, 업소 목록·상세·길찾기를 통합했다. 모바일 하단 네 개 탭을 복원하고 지도 SDK 인증 실패가 전체 화면을 중단하는 문제를 수정했다.
- 소스 보존: `preview/manifest.json`에 기준 커밋을 고정하고 `preview/thrifty.patch`에 격리 clone의 소스·테스트 변경을 저장했다. 준비·실행·저장 스크립트로 같은 Preview를 재현한다.
- 10월 27일 검증: React 100개 파일/627개 테스트, lint, format, production build/typecheck 통과. 실제 dev 업소 API, WKWebView 쿠키·세션 복원, iOS UI 자동화, Simulator 및 서명 iPhone 빌드 통과. iPhone과 Mac에서 실제 네이버 지도·업소 목록·상세를 확인했다.
- 실기기 상태: 사용자가 iPhone/Mac의 CA 신뢰와 지도 Application URL 등록을 완료했다. 실제 iPhone에서 반경 전환과 카카오맵 길찾기를 확인했고 React 수정이 앱 재빌드 없이 반영됐다.
- 남은 확인: 실제 GPS/권한 거부·재허용, 시스템 사진 선택/HEIC, PDF 공유·Files 저장, 전화 발신. 기능은 로컬 Preview에 있으며 팀 dev 웹 배포는 하지 않았다.
- 기록: [Preview 실행과 QA](THRIFTY_PREVIEW.md), [API 계약](API_CONTRACT.md), [검증 결과](verification/thrifty-results-2026-10-27.json).

## 2026-10-29 — 작업 이력 저장 요청

사용자 요청에 따라 개인 저장소 `softmoca/mobile-kmp`의 `codex/thrifty-preview`에서 미커밋 작업을 기능 단위로 커밋하고 같은 원격 브랜치에 push한다. 개인 Xcode Team 값은 로컬에 유지하고 커밋에서는 제외한다. 인증서·개인키·실제 환경설정·의존성·빌드 결과는 Git 제외 경로에 유지한다. 상위 팀 저장소의 브랜치와 파일은 변경하지 않는다.

### 저장 전 재검증

- 문서 포맷 검사와 WebView URL 정책 Debug/Release 테스트 통과.
- Android 단위 테스트·Debug APK·lint 및 KMP iOS Simulator framework 빌드 통과(Gradle 증분 빌드).
- 개인 Team을 제외한 각 커밋 대상 소스를 별도 폴더로 추출해 iOS Simulator 앱·UI 테스트 코드 `build-for-testing` 통과. 이번에는 테스트 실행과 실기기 QA를 반복하지 않았으며, 위 10월 27일 결과와 구분한다.
- Preview 패치가 현재 React 소스·테스트 변경과 동일하고 고정한 기준 커밋에 적용됨을 확인했다. 스크립트 문법 및 결과 JSON 검사도 통과했다.
- 커밋 대상에서 실제 키·개인 Team·인증서·환경설정 제외를 확인했다. 기존 로컬 Xcode 프로젝트와 Info.plist는 작업 시작 시점과 바이트 단위로 동일하다.
- 격리 빌드에서 사용하지 않는 기존 KMP framework 검색 경로 경고가 남지만 WebView 앱과 테스트 코드 컴파일·링크는 성공했다.

앞으로도 작업 단위마다 변경 내용, 실제 검증 결과, 미검증 항목과 관련 문서를 이 파일에 추가한다. 검증일과 커밋일이 다르면 구분해서 적는다.
