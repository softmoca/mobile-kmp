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

## 2026-10-29 — 저렴한 한 끼 찾기 구현·검증

- 개인 `codex/cheap-meals`, 기존 독립 React clone `codex/mobile-cheap-meals`에서 최신 develop `021267b` 기반으로 구현했다. 이전 변경은 `1855200`과 기존 패치에 보존했다.
- 홈 카드, 전체/식당/편의점/배달, 실제 API·필터·점진 표시·상세 시트·탭 상태 보존을 구현했다. 최신 인증/Emotion/라우터를 사용하며 구형 하단 메뉴를 가져오지 않았다.
- React 전체 100개 파일/680개 테스트 통과. 이후 지도 SDK 처리와 상세 초점·로딩·위치 요청 취소 보완 후 관련 **21개 파일/184개 테스트**가 통과했다. lint/format/typecheck/production build 성공. 초기 추가 회귀 테스트의 로딩 지연 대기와 초점 복원 실패는 수정 후 통과했다.
- 실제 API 네 조회 200, 미인증 401. 시청 1km 22곳, 이마트24 2,337개, 세븐일레븐 미수집 0개, 배달 108개. Simulator 실제 WKWebView API·화면 테스트 2/2, Android 단위 테스트/APK/lint/KMP Simulator framework 성공.
- 실제 iPhone 12 mini에서 홈/전체/상품 이미지·상세·스크롤/배달·상세/탭 필터 유지와 수정 반영 확인. 새 Mac IP의 네이버 허용 URL 등록이 남아 지도 타일·마커·드래그는 이번 실기기에서 미검증이다. 업소 API와 목록은 동작한다.
- 상위 팀 저장소, Xcode Team/Bundle/Info.plist, 기본 실행 URL/Release를 보존했다. 새 작업은 로컬 커밋으로만 보존하고 원격 push/merge/배포하지 않는다.
- 실행법·증거·남은 확인: [CHEAP_MEALS](CHEAP_MEALS.md), [API 결과](verification/cheap-meals-api-2026-10-29.json).

### 이번 작업의 로컬 커밋과 마감 상태

- React `eee8af8`: 네이버 지도 SDK 인증 실패·객체 정리·오류 표시·재시도 연결을 보존했다.
- React `c4fb83c`: 홈 진입, 네 카테고리, 실제 API·UI·테스트·설명 문서를 보존했다.
- 개인 저장소 커밋 제목: `feat: 저렴한 한 끼 Preview와 iPhone 검증 기록을 보존한다`. 패치·실행 스크립트·API 검사·XCTest·증거를 함께 저장한다.
- 마지막 지도 재시도 수정 뒤 페이지 13개 테스트와 typecheck/production build가 재통과했다. 패치는 고정 develop에 적용되며 변경된 35개 파일이 현재 소스와 일치했다.
- Mac이 잠겨 최종 추가 UI 확인은 중단됐다. 이미 확인한 실제 iPhone 결과와 지도 URL 등록 대기 항목을 [검증 요약](verification/cheap-meals-results-2026-10-29.json)에 구분했다.
- 브라우저 375px/1024px 가로 넘침 없음, 상품 24→48개 표시, 탭 왕복 시 스크롤 위치 유지, 실제 서울시청 주소 검색 22곳을 확인했다.

## 2026-10-29 — Preview 주소 고정 가능성 확인

- 사용자 요청: IP가 바뀔 때마다 네이버 Maps 허용 URL을 수정하지 않는 방법을 확인했다.
- 기존 Mac Bonjour 호스트와 별도 `jachwi-preview.local` 광고를 각각 시험했다. 기존 CA로 호스트 SAN을 포함한 leaf 인증서를 만들고 Mac HTTPS 200을 확인했지만, 실제 iPhone WKWebView에서는 연결되지 않았다. Safari에서도 Mac 기본 호스트 연결이 실패했다. IP 직접 연결은 성공했다. 이름 조회/연결에 대한 네트워크 제약이 의심되지만 정확한 차단 원인은 확정하지 않았다.
- 시험용 서버·Bonjour 광고를 종료하고 스크립트와 leaf 인증서를 기존 상태로 복원했다. 현재 실행 주소는 다시 `https://192.168.3.253:3443`이다. Mac 이름·공유기·DNS·방화벽·인증서 신뢰·Xcode 설정은 변경하지 않았다.
- 같은 Wi-Fi에서는 공유기의 DHCP 예약으로 Mac에 같은 IP를 배정하는 방안을 우선 권장한다. 현재 Wi-Fi의 공유기를 사용자가 관리할 수 있는지 확인을 요청했다. 예약 설정과 새 IP의 네이버 등록은 아직 수행하지 않았다.
- 개발 장소가 달라져도 주소를 유지하려면 별도 고정 도메인과 개발 환경 또는 접근 제어된 터널이 필요하다. 이번에는 외부 공개나 새 서비스 가입을 진행하지 않았다.

## 2026-10-29 — 새 IP 등록 후 지도 인증 재검증

- 사용자 응답: 공용 Wi-Fi 환경이며 `192.168.3.253`의 네이버 Web 서비스 URL 등록을 완료했다. DHCP 예약이나 외부 터널 설정은 진행하지 않았다.
- 종료되어 있던 HTTPS Preview를 같은 IP로 재시작했다. 실제 iPhone 12 mini를 기존 Bundle ID와 Preview 환경변수로 재실행하고 Mac 브라우저도 새로고침했다. 앱 홈·전체·식당 및 판교 1km 실제 업소 2곳을 확인했다.
- 두 환경 모두 지도 인증 실패가 재현됐다. 지도 재시도 버튼의 전체 새로고침 이후에도 같다. 지도 SDK 다운로드 HTTP 200, 네이버 인증 HTTP 401 / 오류 코드 `200` / `Authentication Failed`를 확인했다. 요청 주소는 실제 Preview와 일치하며 Referer 위조나 인증 우회는 하지 않았다.
- 브라우저에서 사용 중인 `ncpKeyId`와 Preview 로컬 키·팀 로컬 키의 일치 여부만 비교했다. 키·Secret·토큰은 출력하거나 검증 자료에 저장하지 않았다. 콘솔에 저장된 URL 한 줄을 사용자에게 요청했다. 설정 오등록·반영 지연 중 하나로 단정하지 않는다.
- [검증 JSON](verification/cheap-meals-map-registration-2026-10-29.json)과 [실기기 오류 화면](verification/cheap-meals-iphone-map-auth-after-registration-2026-10-29.png)을 남겼다. 지도 타일·마커 선택·드래그 검증은 미완료다. 소스·Xcode·Release·팀 저장소 변경, push/merge/배포는 없다.

## 2026-10-29 — 지도 허용 IP 정정 후 인증 성공

- 사용자가 네이버 등록 IP를 `192.168.3.255`에서 실제 Mac IP인 `192.168.3.253`으로 정정했다. 같은 Preview 코드·키로 인증 응답이 HTTP 200 / `result: true`로 바뀌었다. 이전 `6d233b7`의 인증 실패 기록은 정정 전 상태로 보존한다.
- 브라우저와 실제 iPhone 12 mini에서 네이버 타일·실제 업소 마커·반경 원을 확인했다. 브라우저에서 마커 선택 → 업소 상세 → 닫기 → 지도 드래그 → 이 위치에서 찾기를 수행해 다른 업소 목록으로 갱신됐다. warning/error 로그 0건.
- 사용자가 iPhone 위치 권한을 직접 선택했고 이후 현재 위치 주변으로 이동해 반경 2km 조회와 빈 결과 표시가 완료됐다. 실제 좌표와 현재 위치 화면은 증거 파일에 저장하지 않았다. 권한 거부·재허용은 시험하지 않았다.
- Device Hub의 클릭·드래그가 실기기에 반영되지 않아 실기기 마커 상세·지도 이동 재검색은 사용자 수동 확인을 요청했다. 브라우저 성공을 실기기 조작 성공으로 대신 기록하지 않는다.
- [지도 인증 성공 요약](verification/cheap-meals-map-success-2026-10-29.json), [실기기 지도](verification/cheap-meals-iphone-map-2026-10-29.png), [브라우저 재검색](verification/cheap-meals-browser-map-requery-2026-10-29.png)을 보존한다. 이번 변경은 검증 문서·증거만이며 프론트 소스, Xcode·서명·Release 및 상위 팀 저장소를 변경하지 않았다. 원격 push/merge/배포하지 않았다.

## 2026-10-29 — dev 배포 중 조회 오류 진단과 복구 확인

- 사용자가 실기기 검증 중 갑자기 데이터가 사라졌다고 보고했다. 13:10 KST에 로컬 Preview HTTPS 200과 지도 타일 표시를 확인했고, dev API의 health/info·편의점·배달 조회는 ALB HTTP 503이었다.
- 기존 팀 배포 기록을 읽어 개발 파이프라인의 새 커밋 `e15644e2` 배포와 13:06~13:11 `BlockTraffic` 5분 8초를 확인했다. 단일 인스턴스 in-place 배포의 트래픽 차단·앱 교체 구간과 API 중단이 일치했다. IP·인증서·네이버 등록을 다시 바꾸지 않았다.
- 13:12:40 KST health 200 / UP, info 200 / 배포 커밋 일치를 확인했다. 배포 이벤트는 13:13 트래픽 허용까지 성공했다. 브라우저 편의점 `다시 시도`로 2,337개 상품 복구, 식당 2km 새 조회로 판교 인근 8곳과 마커 표시를 확인했다.
- 실제 iPhone 오류 문구도 현재 위치 2km의 정상 빈 결과로 바뀌었다. 정밀 좌표·현재 위치 화면은 증거에 저장하지 않았다. Device Hub 입력 미반영으로 서울시청 검색·마커 상세·지도 이동 재검색은 사용자 수동 확인을 요청했다.
- [복구 요약](verification/cheap-meals-api-recovery-2026-10-29.json)과 브라우저 상품·식당 화면을 보존한다. 문서 포맷과 JSON·Git diff를 검사한다. 이번 변경은 진단 기록뿐이며 소스·네이티브 설정·상위 팀 저장소를 수정하지 않는다. 배포·재시작·롤백·AWS 설정 변경, 원격 push/merge는 수행하지 않았다.

## 2026-10-29 — iPhone 지도 수동 검증 완료

- dev API 복구 후 사용자가 실제 iPhone에서 서울시청 검색에 이어 목록 표시·마커 상세 열기·지도 이동 후 재검색이 모두 동작한다고 응답했다.
- Device Hub 자동 입력은 반영되지 않았으므로 이번 결과는 사용자 직접 조작·보고에 근거한 수동 검증으로 구분했다. 새 자동 테스트 실행이나 화면 촬영 결과로 기록하지 않는다.
- [실기기 확인 기록](verification/cheap-meals-iphone-manual-2026-10-29.json)을 추가하고 CHEAP_MEALS·빌드 보고서·구현 상태의 해당 대기 항목을 완료로 갱신했다. 이전 인증 실패·배포 중단·복구 JSON은 당시 상태 그대로 보존한다.
- 이번 변경은 문서와 검증 JSON뿐이다. 문서 포맷 검사·JSON 구문 검사·Git diff를 확인하고 개인 `codex/cheap-meals`에 로컬 커밋한다. 기존 Xcode 설정 변경은 포함하지 않는다. push/merge/배포는 수행하지 않는다.
- 권한 거부·재허용, 전화 발신, 큰 글꼴·가로 모드, iOS 16, Android 실기기는 기존 미검증 상태를 유지한다.

## 2026-10-29 — 저렴한 한 끼 작업 원격 저장 승인

- 사용자가 실기기 검증 완료 후 현재 작업의 push를 요청했다. 대상은 개인 저장소 `softmoca/mobile-kmp`의 `codex/cheap-meals`이다.
- 이전 원격 기준 `6e7129c` 이후 기능 구현, 주소·지도 인증, dev 배포 중 오류·복구, 실기기 수동 검증까지의 6개 커밋과 이번 승인 기록을 함께 보존한다.
- 독립 React clone의 구현은 `preview/cheap-meals.patch`에 포함돼 있다. `.tools`의 로컬 환경설정·인증서·키·의존성 및 미커밋 Xcode 설정은 push에 포함하지 않는다.
- 기존 기능 테스트·플랫폼 빌드 결과를 유지하고 이번 문서 변경의 포맷·diff를 검사한다. 개인 작업 브랜치를 push한 뒤 원격 HEAD와 로컬 HEAD의 일치를 확인한다. 팀 저장소·main/develop·PR·배포는 변경하지 않는다.

## 2026-10-29 — iPhone 푸시 작업 전 체크포인트

- 사용자가 알림 수신 기능 연결 전에 미커밋 상태를 먼저 커밋하도록 요청했다. 기능 소스·검증 기록은 `c77300f`까지 이미 원격에 보존돼 있었다.
- 유일한 미커밋 변경인 Xcode Debug의 기존 Personal Team 지정 한 줄을 값 변경 없이 로컬 커밋한다. 인증서·개인키·환경설정 파일은 포함하지 않는다. 이번 체크포인트는 원격에 push하지 않는다.
- Xcode 프로젝트 plist 문법과 문서 포맷을 확인한다. 기존에 실기기 빌드에 사용하던 설정을 보존하는 작업이며 기능 코드는 바꾸지 않는다.

## 2026-10-29 — 알림 작업 중단과 저렴한 한 끼 재실행 복구

- 사용자가 알림 작업을 다시 중단하고 앱에서 저렴한 한 끼가 보이지 않는 문제 확인을 요청했다. 알림 네이티브 변경·문서는 `codex/ios-push`의 stash `ac7f05df83c56f031b2f284af44bf90ad04d3446`, 웹 변경은 독립 clone의 `codex/mobile-ios-push` 커밋 `f83a076`에 보존했다. 실제 iPhone에 알림 앱을 설치하거나 테스트 알림을 발송하지 않았다.
- 개인 저장소는 `codex/cheap-meals`, 독립 React는 `codex/mobile-cheap-meals`로 복귀했다. 저렴한 한 끼 소스는 삭제되지 않았다. 기본 실행으로 카드가 없는 dev 홈을 재현했고, 같은 앱을 로컬 HTTPS Preview로 실행하자 카드가 다시 표시됐다.
- 원인은 실행 환경변수 `JACHWI_WEB_URL`이 홈 화면에서 다시 켤 때 전달되지 않는 구조다. Debug에서 검증된 로컬 HTTPS origin을 UserDefaults에 기억하고 다음 실행에 복원한다. 빈 환경변수 값으로 명시적으로 초기화한다. 저장된 주소도 매번 기존 URL 정책으로 검사하며 Release는 저장값과 환경변수를 모두 무시한다.
- 브랜치 복귀 중 파일 교체와 Webpack 재빌드가 겹쳐 잠시 모듈 오류·페이지 연결 오류가 있었지만 재빌드 성공과 HTTPS 200, 실제 iPhone 홈 카드 복구를 확인했다. TLS/ATS 검증·기존 앱 Bundle ID·Personal Team은 유지했다.
- Debug 주소 복원·초기화·잘못된 주소 거부·Release 무시 테스트가 통과했다. 수정 앱의 Simulator·Personal Team 서명 iPhone 빌드와 같은 Bundle ID 업데이트 설치가 성공했다. 로컬 주소로 한 번 실행한 뒤 환경변수를 제거하고 종료·재실행했을 때 실제 iPhone에서 저렴한 한 끼 카드가 유지됐다. [재실행 증거](verification/cheap-meals-iphone-relaunch-2026-10-29.png)를 보존한다. 원격 push·팀 저장소 수정은 하지 않는다.
