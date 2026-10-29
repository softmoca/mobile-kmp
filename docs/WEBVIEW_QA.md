# WebView QA

알뜰 Preview 후속 검증은 [THRIFTY_PREVIEW](THRIFTY_PREVIEW.md)와 [최신 빌드 보고서](BUILD_REPORT.md)를 따른다. 아래는 앞선 기본 dev WebView 전환 검증이다.

검증일: **2026-10-27, Asia/Seoul**. `feat/webview-shell`, Xcode 27.1, 격리된 iPhone 12 mini / iOS 27.0 Simulator에서 검사했다. 처음에는 물리 iPhone 12 mini가 연결되지 않았으나 사용자가 연결한 뒤 iOS 27.0.1 실기기에 같은 Bundle ID의 서명 앱을 업데이트 설치하고 실행했다. 설치·실행 명령은 모두 성공했다. 아래 결과는 실기기 전체 QA 완료를 뜻하지 않는다.

## 이번에 확인한 결과

| 항목           | 결과와 근거                                                                                                                                                                            |
| -------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| dev React 화면 | 성공. Simulator와 실제 iPhone에서 배포 웹 홈·매물 기록/지도·등록 폼 표시 확인. Compose 헤더·탭바 없음                                                                                  |
| Safe Area      | 12 mini Simulator 및 실기기 세로 화면의 노치/홈 인디케이터 경계 확인. 가로·큰 글자·소프트웨어 키보드 전체 동작 추가 확인 필요                                                          |
| React Router   | XCTest에서 홈 → 본 방 기록 이동, 앱 종료·재실행 후 홈 표시 성공. 실제 iPhone에서도 홈 → 매물 기록/지도 → 등록 폼 이동과 종료·재실행 확인                                               |
| 인증           | WKWebView에서 dev login/refresh, 같은 회원 복원, Secure/HttpOnly·Path·만료일 확인. JS에서 HttpOnly 쿠키를 읽을 수 없음 확인. 별도 runner 프로세스 재실행 후 같은 회원 복원도 통과      |
| 인증 오류      | 쿠키 없는 refresh의 401 확인. 실제 Access Token 만료 후 React의 동시 갱신/안내 흐름은 추가 수동 QA                                                                                     |
| 새 웹 리소스   | HTML 캐시 재검증 정책과 `reloadFromOrigin()` 실행 확인. 이번 작업 중 새 팀 웹 배포를 만들지는 않음                                                                                     |
| 사진           | 실제 WebKit fetch/FormData로 PNG 업로드 201. 시스템 picker/카메라/HEIC 실기기 선택은 미검증                                                                                            |
| 지도·위치      | 네이버 지도 타일·기본 위치 표시, iOS 앱 위치 권한창 및 dev 웹 위치 권한창 확인. 고정 QA 위치로 검사하며 실제 GPS 이동은 미검증                                                         |
| PDF            | 실제 두 QA 매물 비교 API → Blob → 웹과 같은 anchor download/즉시 revoke → WKDownload → 유효한 PDF 임시 파일 생성 성공. Quick Look/공유/Files 최종 조작은 실기기 QA 필요                |
| 외부 링크      | 정확한 origin·scheme 허용/차단 로컬 테스트 통과. Safari/tel/mailto 실제 앱 전환은 미검증                                                                                               |
| 실패·재시도    | 연결 불가 HTTPS 로컬 서버에서 오류 화면·재시도 버튼 XCTest 통과. 초기 Simulator의 웹 회원 연결 오류도 보안 설정 변경 없이 재실행 후 정상화. 네트워크 단절 중 작성한 입력 보존은 미검증 |
| Simulator 빌드 | 성공. 기존 KMP 없이 Swift/WebKit 앱 빌드                                                                                                                                               |
| iPhone arm64   | 미서명 빌드와 **기존 Personal Team을 사용한 서명 빌드 모두 성공**. 계정/프로파일 자동 갱신 옵션 없이 기존 로컬 설정 사용. 실제 iPhone 12 mini에 업데이트 설치·실행·종료 후 재실행 성공 |
| Android/KMP    | 기존 단위 테스트·Debug APK·lint·iOS Simulator KMP framework 빌드 성공                                                                                                                  |
| 로컬 HTTPS     | 개인 저장소 어댑터로 React 빌드·Simulator 표시·dev API login/refresh 동일 회원·Secure/HttpOnly 쿠키 확인                                                                               |
| 로컬 HMR       | 신뢰된 TLS로 WSS 연결 후 `hot`, `hash`, `ok` 수신. 팀 소스를 수정하지 않아 실제 편집 후 HMR 화면 교체는 미검증                                                                         |
| 보존           | 기존 Team/Bundle ID/서명 필드와 기존 Info.plist 값 모두 동일함을 구조 비교로 확인. 기존 Keychain 삭제/이전 없음                                                                        |

[실행 요약 JSON](verification/webview-results-2026-10-27.json).

실기기 증거: [iPhone 홈](verification/webview-iphone-home-2026-10-27.png), [iPhone 등록 폼](verification/webview-iphone-form-2026-10-27.png). 실기기 화면 확인 중 새 매물을 저장하거나 기존 기록을 변경하지 않았다. 화면 미러링 연결 상태에서는 하드웨어 키보드용 입력 도구막대가 표시되어 소프트웨어 키보드 전체 QA와 구분한다.

Simulator 증거 이미지: [dev 홈](verification/webview-home-2026-10-27.png), [네이버 지도](verification/webview-map-2026-10-27.png), [로컬 HTTPS 홈](verification/webview-local-https-2026-10-27.png).

## 재현과 로그

```sh
npm run test:webview
./gradlew :composeApp:testDebugUnitTest :composeApp:assembleDebug :composeApp:lintDebug :composeApp:linkDebugFrameworkIosSimulatorArm64

xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppDevSmoke \
  -configuration Debug -destination 'platform=iOS Simulator,id=<QA-SIMULATOR-UUID>' \
  -derivedDataPath .tools/WebViewShellData -parallel-testing-enabled NO \
  -collect-test-diagnostics never CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- test
```

최종 핵심 dev XCTest **3개 통과**, 기존 Compose 테스트 1개 skip. 별도 runner 재실행 검사 **1개 통과**, 연결 불가 로컬 서버의 오류·재시도 UI 검사 **1개 통과**. 실제 dev 검사는 `iosAppDevSmoke`에서만 켠다. 기본 scheme은 앱 실행 전에 테스트를 skip한다. 테스트 runner의 WKWebView와 설치 앱의 쿠키는 각각 별도 앱 저장소다. API 검사는 앱과 동일한 WebViewModel 소스를 테스트 runner에 컴파일해서 검사하고, 설치 앱의 화면은 별도 XCUITest로 확인한다.

`testDevCookiesAPIPhotoAndPDFDownload`가 QA 회원 번호만 테스트 runner UserDefaults에 남긴다. 토큰/쿠키 값을 로그나 테스트 설정에 저장하지 않는다. 새 프로세스에서 디스크의 WebKit 쿠키가 같은 회원을 복원하는지 확인할 때 다음 검사를 별도 실행한다.

```sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppDevSmoke \
  -configuration Debug -destination 'platform=iOS Simulator,id=<동일-QA-SIMULATOR-UUID>' \
  -derivedDataPath .tools/WebViewShellData \
  -only-testing:iosAppUITests/WebViewTests/testPersistedMemberAfterRunnerRestart \
  -parallel-testing-enabled NO -collect-test-diagnostics never \
  CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- test-without-building
```

Git 제외 경로의 실제 로그:

- `.tools/webview-final-tests.log`, `.tools/webview-final-tests.xcresult`: 최종 dev XCTest.
- `.tools/webview-persisted-cookie-test.log`: 별도 runner 프로세스에서 쿠키 복원.
- `.tools/webview-retry-test.log`: 연결 실패·재시도 UI 검사.
- `.tools/webview-device-build.log`, `.tools/webview-device-signed-build.log`: iPhone arm64 빌드.
- `.tools/physical-install.json`, `.tools/physical-launch.json`, `.tools/physical-relaunch.json`: 실제 iPhone 업데이트 설치·실행 성공. 개인 기기 정보가 있어 Git에서 제외한다.
- `.tools/webview-gradle.log`: 기존 Android/KMP 회귀 검사.
- `.tools/local-web-server.log`: 로컬 HTTPS 프록시 응답. 테스트용 CA는 `.tools/certs/`에만 있고 격리 QA Simulator에만 신뢰를 추가했다. Mac 시스템 신뢰와 실제 iPhone은 변경하지 않았다.

테스트는 별도 익명 회원의 `WebView QA <임의값>` 매물 두 개와 사진을 생성한다. 매물 삭제 204로 정리를 확인했다. 회원 삭제 API가 없어 QA 익명 회원은 남는다. 초기 시뮬레이터 부팅/설치 지연은 해당 QA Simulator만 재시작해 해결했다. 초기 실패 로그도 `.tools/webview-dev-tests.log`에 보존했다. 운영 데이터에는 요청하지 않았다.

## 실제 iPhone에서 이어서 할 일

1. 이번 실기기 업데이트 설치는 완료했다. 다음 변경은 기존 Team/Bundle Identifier/`iosApp`/Debug로 Run한다. 기존 앱을 삭제하지 않는다.
2. 첫 WebView는 Compose Keychain 세션과 별도임을 확인한다. 중요한 기존 기록이 있다면 삭제·재설치 대신 먼저 보존/복구 방법을 결정한다.
3. 아래 항목을 확인하고 기기/iOS 버전, 웹 배포 시점, 재현 경로를 적는다. 실패 시 토큰·쿠키·개인 사진·실제 주소를 로그에 복사하지 않는다.

- [ ] 홈/매물/체크리스트/마이의 웹 헤더·하단 메뉴가 한 번만 보임
- [ ] 노치·가로모드·홈 인디케이터·큰 글자에서 잘림/이중 스크롤 없음
- [ ] QA 매물 생성 → 강제 종료·재실행 → 같은 기록 복원 → QA 매물 정리
- [ ] 뒤로 스와이프, 폼 입력 후 이탈 확인창, 키보드 열기/닫기·입력칸 스크롤
- [ ] 당김 새로고침 후 로그인 유지, dev에 새 웹 배포 후 앱 재빌드 없이 반영
- [ ] 사진 보관함 선택/취소, 카메라 촬영, JPEG/PNG/WebP 업로드, 5MiB 초과·30장 한도
- [ ] HEIC 보관함 사진이 호환 JPEG로 전달되는지, Files 원본 HEIC의 웹 제한 안내 확인
- [ ] 위치 허용/거부/재허용, 현재 위치 이동, 지도 핀·주소·주변시설, 네트워크 오류
- [ ] PDF 생성 후 한글/사진/비교 순서 확인, 공유 → 파일에 저장 → 재열기, 공유 취소
- [ ] 실제 웹에 있는 외부 링크의 Safari 전환, tel/mailto 처리, 복귀 후 웹 상태 유지
- [ ] 입력 중 백그라운드/복귀, 인터넷 끊김/복구, 재시도, 세션 만료 안내
- [ ] 로컬 HTTPS 인증서 신뢰·네트워크 권한, 실제 iPhone 접속, React 수정 후 HMR

HEIC는 현재 웹의 accept/MIME 제한을 따르므로 모든 원본 HEIC 업로드를 지원한다고 볼 수 없다. 로컬 네이버 지도는 개발 호스트의 허용 도메인 등록이 필요할 수 있다. 두 제한을 네이티브 인증/보안 우회로 해결하지 않는다.
