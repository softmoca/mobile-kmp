# 작업 기록

작업 단위별 변경 내용, 검증 시점, 남은 확인 항목을 기록한다. 상세 실행법과 증거는 각 문서에 연결하며, 커밋 이력으로 해당 시점의 소스를 복원할 수 있게 한다.

## 2026-10-27 — iOS WebView 전환

- 커밋 제목: `feat: iOS 앱을 React WebView 셸로 전환한다`
- 변경: iOS 실행 화면을 기존 React 웹을 여는 WKWebView로 전환했다. Kotlin·Android 소스와 기존 Keychain은 보존했다. Debug/Release URL 정책, 웹 쿠키 유지, 새로고침·오류 복구, 외부 URL 및 PDF 처리를 추가했다.
- 로컬 개발: 개인 저장소에서 HTTPS React Preview, dev API 프록시, Secure/HttpOnly 쿠키와 WSS HMR 연결을 준비했다. 상위 팀 저장소는 읽기 전용으로 사용했다.
- 10월 27일 검증: URL 정책, 실제 dev 인증·쿠키 복원·PNG API 업로드·PDF 다운로드, React 라우팅, Simulator/서명 iPhone 빌드, iPhone 업데이트 설치·실행·재실행, Android/KMP 필수 검사 통과.
- 남은 확인: 실제 GPS와 권한 재허용, 시스템 사진 선택/HEIC, PDF 공유·Files 저장, 전화 발신. API 수준 검증과 실기기 UI 검증을 구분한다.
- 기록: [WebView QA](WEBVIEW_QA.md), [구조](WEBVIEW_ARCHITECTURE.md), [로컬 실행법](WEBVIEW_LOCAL_DEVELOPMENT.md), [검증 결과](verification/webview-results-2026-10-27.json).

## 2026-10-29 — 작업 이력 저장 요청

사용자 요청에 따라 개인 저장소 `softmoca/mobile-kmp`의 `codex/thrifty-preview`에서 미커밋 작업을 기능 단위로 커밋하고 같은 원격 브랜치에 push한다. 개인 Xcode Team 값은 로컬에 유지하고 커밋에서는 제외한다. 인증서·개인키·실제 환경설정·의존성·빌드 결과는 Git 제외 경로에 유지한다. 상위 팀 저장소의 브랜치와 파일은 변경하지 않는다.
