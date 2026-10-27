# 실제 빌드·테스트 보고서

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
