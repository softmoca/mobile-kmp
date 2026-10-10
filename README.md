# 자취선배 모바일

Kotlin Multiplatform + Compose Multiplatform으로 만든 Android·iOS 앱이다. 기존 Spring Boot API를 사용한다. 이 디렉터리는 상위 팀 프로젝트와 별도 Git 저장소다.

## 환경 준비

- JDK 21 (최소 17), Android SDK 36와 Build Tools 35.0.0
- macOS Apple Silicon + Xcode, iOS Simulator 런타임
- Android Studio 또는 Kotlin Multiplatform을 지원하는 IntelliJ IDEA
- 버전 조합과 근거: [ARCHITECTURE](docs/ARCHITECTURE.md)

저장소 루트 `local.properties`에 개인 SDK 경로를 설정한다. 이 파일은 Git에서 제외한다.

```properties
sdk.dir=/Users/your-name/Library/Android/sdk
```

이번 작업 환경에는 `.tools/android-sdk`에 SDK를 설치했다. `.tools`는 로컬 도구·로그 전용이며 커밋하지 않는다. Gradle wrapper는 저장소에 포함되어 있다.

## Android

```sh
./gradlew :composeApp:testDebugUnitTest :composeApp:assembleDebug :composeApp:lintDebug
```

APK: `composeApp/build/outputs/apk/debug/composeApp-debug.apk`.

에뮬레이터 또는 USB 디버깅 기기를 연결하고 다음을 실행한다.

```sh
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

Android Studio에서 루트 폴더를 열고 composeApp을 실행해도 된다.

## iOS Simulator

`iosApp/iosApp.xcodeproj`를 Xcode에서 열고 `iosApp` scheme과 iPhone Simulator를 선택해 실행한다. Gradle framework 빌드가 Xcode 빌드 단계에 포함된다.

```sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath .tools/DerivedData CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- build
```

Simulator UI 검사는 `iosApp` scheme의 `test` 액션으로 실행한다. 새 격리 개발 회원의 실제 로그인·로그아웃까지 검사하려면 명시적으로 `iosAppDevSmoke` scheme을 선택한다.

```sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppDevSmoke \
  -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 18 Pro' \
  -derivedDataPath .tools/DerivedData CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- test
```

기기 이름은 설치된 Simulator로 바꾼다. `iosAppDevSmoke`만 테스트 프로세스에 `RUN_DEV_UI_SMOKE=1`을 전달한다.

Simulator도 Keychain에 접근하려면 로컬 ad-hoc 서명이 필요하다. Apple 계정 없이 위 명령으로 서명할 수 있다. `CODE_SIGNING_ALLOWED=NO`는 컴파일 확인에만 사용한다. 이를 실행용으로 사용하면 Keychain entitlement 누락으로 로그인이 저장되지 않는다.

실제 iPhone에서는 Xcode의 Signing & Capabilities에서 자신의 Apple Team을 선택하고, 고유 Bundle Identifier를 설정한 뒤 연결한 기기를 선택한다. 기기에서 개발자 모드와 개발자 인증서 신뢰가 필요할 수 있다. 개인 Apple ID 서명 또는 배포 계정 인증은 사용자가 처리한다.

## API 환경과 키

| 빌드 | API | 앱 식별자 |
|---|---|---|
| Debug | `https://dev-api.jachwi-sunbae.kr` | `kr.jachwisunbae.app.dev` |
| Release | `https://api.jachwi-sunbae.kr` | `kr.jachwisunbae.app` |

운영 API를 대상으로 자동 데이터 변경 테스트를 실행하지 않는다. Release 빌드는 사용자 배포 서명 설정이 필요하다. 토큰과 비밀번호를 환경 파일·소스코드에 넣지 않는다.

기본 지도는 Android OSM, iOS MapKit을 사용하여 별도 지도 키가 없다. 주소·주변시설 조회는 기존 백엔드 제공자를 사용한다. 라이선스·타일 정책·배포시 검토 사항은 [PLATFORM](docs/PLATFORM.md)을 참조한다.

## 개발 서버 실제 연동 검사

기본 단위 테스트는 서버를 변경하지 않는다. 아래 명령은 새 테스트 회원을 생성하고 해당 회원의 테스트 매물·체크리스트만 작성·삭제한다. 회원 삭제 API가 없어 테스트 회원은 남는다.

```sh
RUN_DEV_API_SMOKE=1 ./gradlew :composeApp:testDebugUnitTest \
  --tests 'kr.jachwisunbae.app.DevApiSmokeTest' --rerun-tasks
```

자격증명·JWT는 출력하거나 파일에 저장하지 않는다. 실제 사용자 닉네임으로 테스트하지 않는다.

## 사용 흐름

1. 닉네임과 선택 비밀번호로 로그인한다. 비밀번호 없는 같은 닉네임은 기록을 공유하므로 개인 기록에는 비밀번호를 사용한다.
2. 내 매물에서 기본 금액·주소·방 옵션을 입력한다. 지도에서 주소 검색 또는 현재 위치를 선택할 수 있다.
3. 매물 상세에서 사진·대표 사진·자유 메모를 관리한다. 방문/계약 전 체크리스트를 적용하고 상태·항목 메모를 기록한다.
4. 체크리스트 탭에서 기본 항목을 조합해 사용자 체크리스트를 만든다.
5. 지도에서 매물과 주변 시설을 확인한다. 내 매물에서 2~5개를 선택해 비교하고 PDF를 미리보기·저장·공유한다.

## 문서

- [API_CONTRACT](docs/API_CONTRACT.md): 소스/배포 계약 검증
- [ARCHITECTURE](docs/ARCHITECTURE.md): 코드 구조와 버전 근거
- [PLATFORM](docs/PLATFORM.md): 권한·지도·보안 저장소·PDF
- [IMPLEMENTATION_STATUS](docs/IMPLEMENTATION_STATUS.md): 기능별 구현과 미검증 범위
- [BUILD_REPORT](docs/BUILD_REPORT.md): 실제 빌드·테스트 결과
