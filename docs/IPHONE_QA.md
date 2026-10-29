# 실제 iPhone 설치와 기능 점검

> 현재 `feat/webview-shell`의 iOS 실행 화면은 WKWebView입니다. 최신 실행법·검증은 [WebView QA](WEBVIEW_QA.md)와 [WebView 구조](WEBVIEW_ARCHITECTURE.md)를 따릅니다. 아래 내용은 `codex/kmp-mobile`의 기존 Compose 구현/검증 기록이며, Android는 기존 구현을 유지합니다.

확인일: 2026-10-27. 이 안내는 `iosApp/iosApp.xcodeproj`, 실제 빌드 결과, Apple 공식 문서를 기준으로 작성했다. **실제 iPhone 설치와 아래 수동 점검은 아직 수행하지 않았다.** 기존 로컬 Xcode의 Team·Bundle Identifier·Info.plist 변경은 보존했다. Apple 계정 인증, 기기 신뢰와 Developer Mode는 사용자가 자신의 Mac·iPhone에서 진행한다.

관련 작업: [Issue #1](https://github.com/softmoca/mobile-kmp/issues/1), [Draft PR #2](https://github.com/softmoca/mobile-kmp/pull/2). 오류 수정과 검증 결과는 `codex/kmp-mobile` 브랜치에 후속 커밋으로 반영한다. 실기기 검토 전에는 PR을 Merge하지 않는다.

## 현재 준비 상태

| 항목                   | 확인한 내용                                                                                                                      |
| ---------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| 프로젝트 / 실행 scheme | `iosApp/iosApp.xcodeproj` / `iosApp`                                                                                             |
| 기본 Run 환경          | Debug, `https://dev-api.jachwi-sunbae.kr`                                                                                        |
| 앱 식별자              | Debug `kr.jachwisunbae.app.dev`, Release `kr.jachwisunbae.app`                                                                   |
| 기기 조건              | iOS 16.0 이상, arm64 iPhone. 사용 중인 Xcode가 기기 iOS를 지원해야 함                                                            |
| 서명                   | Automatic. 이 Mac에서 사용자가 설정한 Debug Team·Bundle Identifier는 변경하지 않았다. 공유 저장소에 개인 서명 값을 추가하지 않음 |
| 빌드                   | iPhone용 `iosArm64` 프레임워크와 미서명 앱 링크 성공. 설치 가능한 개발 서명 앱은 별도 생성 필요                                  |
| Simulator 검증         | 최신 익명 회원·Keychain Refresh Cookie 복원·MapKit 및 매물/메모 흐름. 실제 실행 결과는 BUILD_REPORT 참조                         |
| 실기기 미검증          | GPS·권한 분기, 실제 HEIC 사진 선택, PDF 미리보기·저장·공유, 전체 편집 화면                                                       |

빌드 기준 환경은 Apple Silicon Mac, JDK 21, Xcode 27.1 (27A9275)이다. Kotlin 2.2.21 공식 호환표는 Xcode 26.0까지이며, Xcode 27에서는 이 프로젝트의 빌드와 Simulator 테스트가 통과했다. iOS 16 런타임까지 검증한 것은 아니다. 자세한 결과는 [BUILD_REPORT](BUILD_REPORT.md), 기술 조건은 [ARCHITECTURE](ARCHITECTURE.md)를 참고한다.

공통 Kotlin과 `iosMain`을 Gradle로 빌드해 정적 `ComposeApp.framework`를 만들고, Xcode의 Swift 호스트가 이를 링크한다. 실기기는 `iosArm64`, Apple Silicon Simulator는 `iosSimulatorArm64` 타깃을 사용한다. Xcode의 **Build Compose framework** 단계가 Gradle을 실행하므로 프레임워크를 수동 복사할 필요는 없다.

## 프로젝트 열기

터미널에서 개인 저장소로 이동한 뒤 Xcode 프로젝트를 연다.

```sh
cd ~/Desktop/woowacourse/2026-jachwi-sunbae/mobile-kmp
git status --short --branch
open iosApp/iosApp.xcodeproj
```

브랜치가 `codex/kmp-mobile`인지 확인한다. 현재 로컬 `project.pbxproj`, `Info.plist` 변경은 사용자가 만든 설정이므로 git restore로 지우지 않는다. Finder에서 개인 저장소의 `iosApp/iosApp.xcodeproj`를 더블 클릭해도 된다. 상위 팀 저장소의 기존 모바일 프로젝트가 아닌 이 프로젝트를 연다.

## 1. Mac과 iPhone 준비

1. Mac에 JDK 21과 기기 iOS를 지원하는 Xcode를 준비한다. Xcode에서 필요한 iOS 플랫폼 지원이 없다고 나오면 안내에 따라 설치한다. 프로젝트의 Xcode 빌드 단계가 Gradle의 `embedAndSignAppleFrameworkForXcode`를 자동 실행한다.
2. iPhone 잠금을 해제하고 데이터 전송이 가능한 케이블로 Mac에 연결한다. 기기에 ‘이 컴퓨터를 신뢰하겠습니까?’가 나타나면 자신의 Mac인지 확인하고 직접 신뢰·기기 암호 입력을 진행한다. [Apple 컴퓨터 신뢰 안내](https://support.apple.com/en-us/109054)
3. Xcode 27의 실행 대상 메뉴에서 **Manage Devices…**를 열거나 **Xcode → Open Developer Tool → Device Hub**로 이동한다. 해당 iPhone을 선택하고 Pair 등 화면의 연결 절차를 따른다. 처음에는 케이블 연결을 권장한다. 최신 Device Hub의 케이블 없는 최초 페어링은 iOS 27 이상이 필요하다. [Apple Device Hub 안내](https://developer.apple.com/documentation/xcode/managing-your-simulated-and-physical-devices-in-device-hub)
4. iPhone **설정 → 개인정보 보호 및 보안 → 개발자 모드**를 켜고 안내에 따라 재시작한다. 재시작 후 활성화 확인과 기기 암호 입력까지 완료한다. 메뉴가 없다면 Device Hub에서 먼저 페어링을 시작한다. 이전 Xcode에서는 장치 관리 화면 이름이 Devices and Simulators일 수 있다. [Apple Developer Mode 안내](https://developer.apple.com/documentation/xcode/enabling-developer-mode-on-a-device)

## 2. Apple Team과 개발 서명 설정

Xcode의 **Settings → Apple Accounts**에서 사용자가 자신의 Apple Account로 로그인한다. 계정이 Apple Developer Program에 가입되어 있지 않아도 **Personal Team**으로 개인 기기 테스트가 가능하다. 현재 Apple 안내의 제한은 App ID 최대 10개, 등록 기기 최대 3대, 기기당 설치 앱 최대 3개다. App ID·기기 등록과 provisioning profile에는 7일 만료 제한이 있으며 앱을 다시 빌드·설치해야 할 수 있다. TestFlight·App Store 배포는 별도의 Developer Program 범위다. [Apple 개발자 계정 및 Personal Team 안내](https://developer.apple.com/help/account/basics/about-your-developer-account)

1. `iosApp/iosApp.xcodeproj`를 열고 **TARGETS → iosApp → Signing & Capabilities**를 선택한다.
2. **Debug** 구성에서 기존 **Team**과 **Automatically manage signing**을 확인한다. 아직 설정하지 않은 Mac이라면 자신의 Team을 선택한다. **Set Up Signing** 버튼이 보이면 그 절차를 진행한다.
3. Bundle Identifier가 해당 Team에서 등록 가능한 고유 값인지 확인한다. 충돌한다면 개인 개발용으로 고유한 Debug 식별자를 설정하고 `.dev` 구분을 유지한다. Bundle Identifier를 바꾸면 별도 앱·Keychain 서비스로 취급되므로 기존 세션이 이어지지 않는다. 식별자를 바꿔도 API 환경은 Debug/Release 조건으로 결정된다.
4. 상단 실행 대상을 실제 iPhone으로 선택한다. 자동 서명은 연결된 기기를 등록하고 개발 provisioning profile을 준비한다. **Register**가 표시되면 사용자가 등록한다. 수동 웹 등록은 보통 필요하지 않으며, 조직 계정의 수동 등록에는 Account Holder 또는 Admin 권한이 필요하다. [Apple 실기기 서명·실행](https://developer.apple.com/documentation/xcode/running-your-app-on-simulated-or-physical-devices), [기기 등록](https://developer.apple.com/help/account/devices/register-a-single-device)
5. Signing 영역의 오류가 없는지 확인한다. 이 프로젝트의 기본 MapKit·사진 선택·위치·PDF 기능을 위해 추가 지도 키나 Apple 결제 설정을 만들 필요는 없다.

Apple 로그인 비밀번호, 2단계 인증 코드, 인증서 개인키, provisioning profile 내용을 채팅·이슈·PR에 붙여 넣지 않는다. 이 안내에는 실제 Team ID나 서명 자격증명을 넣지 않는다. Xcode가 변경한 개인 Team/서명 설정은 공유 변경에 섞이지 않도록 검토한다.

## 3. 개발 앱 설치와 첫 실행

1. scheme **iosApp**의 **Edit Scheme → Run → Build Configuration**이 **Debug**인지 확인한다. Release는 운영 API를 사용하므로 이 점검에 사용하지 않는다.
2. 실제 iPhone을 실행 대상으로 선택하고 **Product → Run** 또는 `⌘R`로 빌드한다. 자동 서명이 완료되면 Xcode가 설치하고 실행한다. 기기의 잠금을 해제한 상태로 유지하고, 개발자 신뢰 또는 실행 허용 안내가 나타나면 자신의 서명 계정과 앱을 확인하여 기기에서 진행한다. [Apple 기기 실행 안내](https://help.apple.com/xcode/mac/current/en.lproj/dev5a825a1ca.html)
3. 첫 실행은 자동으로 익명 회원의 내 매물 화면으로 진입한다. **마이 → 회원 번호**를 확인한다. 닉네임과 비밀번호 입력 화면은 제거되었다.
4. 이전 앱의 Access Token 전용 로그인 정보가 남아 있으면 복원 불가 안내가 나온다. **새 기록으로 시작하기**는 기존 회원과 다른 빈 기록을 만들므로 안내를 읽고 선택한다. 기존 기록 삭제는 아니지만 앱에서 그 기록을 이어 볼 수 없다. 단순 네트워크 오류라면 새 기록 생성 없이 **다시 시도하기**를 사용한다.
5. 앱 전환기에서 앱을 종료한 뒤 홈 화면에서 다시 실행해 **같은 회원 번호와 매물**로 복원되는지 확인한다. 디버거 연결 중의 실행과 케이블을 분리한 일반 실행을 각각 확인한다.

Simulator에 사용한 `CODE_SIGN_IDENTITY=-`는 실제 iPhone 설치용 인증서를 대신하지 않는다. `CODE_SIGNING_ALLOWED=NO` 산출물은 컴파일 확인용이다. 이전 Simulator 시험에서는 서명을 꺼 Keychain entitlement가 빠지면서 `-34018` 오류가 났고, 기본 로컬 서명을 켜 해결했다. 실제 기기에서는 Xcode가 선택한 Team의 개발 서명과 profile을 사용한다. [PLATFORM의 서명·Keychain 설명](PLATFORM.md#xcode-실행과-네이티브-ui-검사)

## 4. 기능별 수동 QA

아래 결과는 모두 **미실행**으로 시작한다. 성공·실패·미실행과 증거를 실제 확인 후 기록한다. 테스트 전용 매물 2개와 사용자 체크리스트 1개를 만들고, 비교가 끝난 뒤 만든 항목만 삭제한다. API에 회원 삭제가 없어 테스트 회원은 남는다.

| ID    | 동작                                                                                 | 통과 기준                                                                                                                   | 결과   |
| ----- | ------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------- | ------ |
| IP-01 | 최초 자동 익명 진입, 회원 번호 확인, 종료·재실행, 네트워크 단절·복구                 | 같은 회원·기록 복원. 연결 실패 시 재시도하며 자동 새 기록 생성 없음. 구버전 정보는 안내 후 명시 확인                        | 미실행 |
| IP-02 | 내 매물 → + 등록에서 이름·금액·옵션·날짜를 입력해 2개 생성                           | UI 표시와 같은 원 단위, 0원 허용, 누락/잘못된 날짜 차단, 저장 후 실제 목록·상세에 동일 값 표시                              | 미실행 |
| IP-03 | 긴 이름·주소 매물 수정, 자유 메모 저장, 검색·완료 필터, 삭제 취소                    | 수정·메모 재조회 일치. 사진 없음 표시, 검색/완료 상태·빈 결과 정상, 삭제 취소 시 유지                                       | 미실행 |
| IP-04 | 사진 추가를 열었다 취소, 작은 JPEG/PNG 및 HEIC를 각각 선택, 대표 지정·전체 보기·삭제 | 시스템 선택기 사용, 취소를 성공으로 표시하지 않음, HEIC 정상 방향 표시, 대표 변경·삭제 반영                                 | 미실행 |
| IP-05 | 변환 후 5MiB를 넘는 사진, 업로드 중 네트워크 단절                                    | 용량/네트워크 오류 안내, 앱 생존, 실패 후 재시도 가능. 사진 업로드는 한 장씩 진행                                           | 미실행 |
| IP-06 | 앱 진입·지도 진입 후 ‘현재 위치’ 버튼, 위치 허용과 정밀 위치 끔                      | 버튼 전에는 위치 요청 없음. 허용 시 실제 기기 위치로 이동, 대략 위치도 처리. 최초 서울시청 중심을 현재 위치로 표시하지 않음 | 미실행 |
| IP-07 | 위치 요청 거부, 해당 앱 위치 권한 재허용, 위치 서비스 사용 불가 상태                 | 실패/권한 안내 후 주소 검색과 지도 직접 선택 가능. 서비스 복구 후 재요청 가능                                               | 미실행 |
| IP-08 | 주소 검색·지도 탭으로 좌표 선택, 매물 핀, 반경/카테고리 변경 후 주변 시설 조회       | 저장한 주소·좌표·핀 일치, MapKit 출처 유지, 새 조건에 맞는 시설 개수·거리, 실패 시 오류 표시                                | 미실행 |
| IP-09 | 사용자 체크리스트 만들기·검색·항목 추가/제외·순서 변경·수정                          | 선택 개수 1~30 검증, 저장 후 단계·항목·순서 유지. 직접 질문 생성 기능은 제공하지 않음                                       | 미실행 |
| IP-10 | 매물의 단계별 기본/개인 체크리스트 적용, 상태·항목 메모, 양식 교체                   | 저장·집계 갱신과 스크롤 유지, 교체 시 공통 항목의 상태/메모 승계. 진행률 100%와 미완료 필터 일치                            | 미실행 |
| IP-11 | 매물 1개와 2개 선택, 비교 화면·가로 스크롤                                           | 1개에서 비교 불가, 2개에서 선택 순서·금액·옵션·메모·체크 결과 비교. 최대 5개 제약 확인                                      | 미실행 |
| IP-12 | 비교 PDF 생성 → 미리보기 → 파일 저장 → 저장 취소 → 공유 시트 열기/닫기               | PDFKit 미리보기, 한글/사진/메모·체크 내용과 선택 순서, 파일 앱의 저장 파일 재열기. 취소를 성공으로 표시하지 않음            | 미실행 |
| IP-13 | 필요하면 테스트용 수신 대상을 사용하여 PDF 실제 공유                                 | 사용자가 고른 대상 앱으로 PDF 전달 성공. 실제 상대에게 보내는 동작은 사용자가 직접 수행                                     | 미실행 |
| IP-14 | 한글 키보드, 긴 주소/질문, 큰 글자, 세로/가로 회전, 홈 이동·복귀                     | 입력/저장 버튼 접근 가능, 노치·홈 인디케이터 겹침 없음, 비교 스크롤 가능, 복귀 후 화면 사용 가능                            | 미실행 |
| IP-15 | 폼 입력 후 네트워크 단절·복구, 저장 버튼 연속 누름                                   | 입력 유지와 재시도, 진행 중 중복 저장 방지, 앱 종료/민감 정보 노출 없음                                                     | 미실행 |
| IP-16 | 생성한 QA 매물·개인 체크리스트 정리, 종료·재실행                                     | QA 항목만 삭제, 빈 목록 확인, 재실행해도 같은 익명 회원. 로그아웃 UI는 제공하지 않음                                        | 미실행 |

실기기 점검 시 다음 구현 조건을 함께 고려한다.

- 사진 선택은 PhotosUI 시스템 선택기로 선택한 사진만 받는다. 전체 사진 라이브러리 권한 팝업이 뜨지 않는 것이 정상이다. 카메라 직접 촬영 버튼은 구현하지 않았다. HEIC는 JPEG로 변환하고 변환 결과에 용량 제한을 적용한다.
- 실제 GPS를 확인할 때 Xcode의 위치 시뮬레이션을 적용하지 않는다. 권한 분기는 기기의 해당 앱 설정에서 바꿔 확인하고 시험 후 사용자가 원하는 설정으로 되돌린다.
- 정밀 위치를 끈 경우 실제 주소 한 점과 동일한 정확도를 요구하지 않는다. 위치를 얻지 못하면 임의 좌표로 성공 처리하지 않아야 한다.
- 파일 저장에는 전체 저장소 권한이 필요하지 않다. 실제 공유를 검증하지 않고 시트만 열었다면 IP-12까지만 성공으로 기록한다.
- 미저장 폼 이탈 확인은 아직 없다. 화면을 나가기 전 저장한다. 앱 삭제를 로그아웃이나 Keychain 초기화 방법으로 간주하지 않는다.
- 세션 만료(401)와 양식 교체의 전체 회귀 범위는 [UI_QA](UI_QA.md)를 따른다. 테스트를 위해 기기 시각이나 실제 사용자 계정 토큰을 조작하지 않는다.

## 5. 선택: 실제 iPhone에서 XCTest

기본 수동 설치 후 자동 검사를 실행하려면 **iosAppUITests** target에도 같은 Team과 등록 가능한 고유 테스트 Bundle Identifier를 지정한다. **iosApp** scheme에서 `⌘U`는 앱을 실행하기 전에 skip한다. **iosAppDevSmoke** scheme에서 `⌘U`는 자동 익명 진입·같은 회원 복원·지도·매물/메모 생성과 삭제를 검사한다. 테스트 대상은 기존 기록이 없는 별도 테스트 기기/앱이어야 한다. 평소 사용하는 익명 기록에 자동 검사를 실행하지 않는다.

`iosAppDevSmoke`는 빈 저장소에서 새 회원을 만들고 재실행에서는 해당 회원을 복원한다. 회원 삭제 API가 없어 남으며 정상 종료는 테스트 매물 삭제까지 확인한다. 실패 시 로그의 `KMP iOS QA` 이름으로 남은 항목을 찾아 정리한다. Simulator 성공을 실기기 성공으로 기록하지 않는다. 물리 기기 XCTest는 아직 실행하지 않았다.

## 6. 설치 문제를 만났을 때

| 증상                                    | 확인할 것                                                                                                       |
| --------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| 기기가 목록에 없거나 Unavailable        | 잠금 해제·케이블·신뢰·Device Hub 페어링 상태, Xcode 플랫폼 지원, iOS 최소 버전                                  |
| Developer Mode 메뉴가 없음              | 먼저 Device Hub에서 페어링 시작. 재시작 후 활성화 확인까지 완료했는지 확인                                      |
| Team / profile / Bundle Identifier 오류 | 본인 Team 선택, 자동 서명, Debug 식별자 등록 가능 여부. 조직 권한 문제면 관리자에게 상태 확인                   |
| 며칠 뒤 실행되지 않음                   | Personal Team의 7일 profile 만료 여부. 같은 개발 설정으로 Xcode 재빌드·재설치                                   |
| 로그인 후 보안 저장소 오류              | 실기기 개발 서명과 최종 entitlement 확인. `CODE_SIGNING_ALLOWED=NO`나 Simulator용 서명을 사용하지 않았는지 확인 |
| 첫 화면 뒤 앱 종료                      | Xcode 오류/크래시 기록 확보. 프로젝트의 `CADisableMinimumFrameDurationOnPhone=true`가 유지되는지 확인           |
| Gradle/JDK 오류                         | JDK 21 설치와 Xcode 빌드 단계의 `JAVA_HOME`, Gradle 의존성 다운로드 환경 확인                                   |
| 위치·사진·PDF만 실패                    | 네이티브 UI가 열린 단계, 권한 상태, 입력 파일 형식·크기, 네트워크 상태를 해당 IP 항목과 함께 기록               |

서명 진단은 [Apple Keychain entitlement 안내](https://developer.apple.com/forums/thread/114456)를 참고한다. 공유하는 로그·화면에는 비밀번호, JWT, Apple 계정, UDID, 실제 개인 주소·사진이 포함되지 않게 한다. 기기 전체 설정 초기화나 인증서 삭제를 기본 해결책으로 사용하지 않는다.

## 결과 기록 양식

```text
검증일:
코드 commit:
기기 모델 / iOS 버전:
Xcode 버전:
빌드 구성: Debug / 개발 API
서명 유형: Personal Team 또는 Developer Program (실제 Team ID는 적지 않음)
설치·단독 실행: 성공 / 실패 / 미실행
QA ID:
실행 단계:
예상 결과:
실제 결과:
증거: 민감 정보를 제거한 화면/로그
생성한 테스트 항목 정리: 완료 / 남은 항목
```

실행 결과는 [BUILD_REPORT](BUILD_REPORT.md)와 [UI_QA](UI_QA.md)에 함께 반영한다. 안내를 작성한 것과 해당 기기에서 기능이 성공한 것을 구분한다.
