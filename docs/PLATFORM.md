# 네이티브 기능과 설정

확인일: 2026-10-10. 공통 Compose UI는 `PlatformServices`와 `NativeMap`만 사용한다. Android는 Kotlin/Android API, iOS는 `IOSPlatformBridge`를 구현한 Swift `IOSServices`가 Apple SDK를 호출한다. WebView는 사용하지 않는다.

## 로그인 정보

- Android: Android Keystore에서 생성한 비추출 AES 키, `AES/GCM/NoPadding`, 매번 생성되는 IV로 토큰을 암호화한다. SharedPreferences에는 암호문과 IV만 저장한다. 키 접근/암호화/디스크 작업은 IO dispatcher에서 수행한다. `allowBackup=false`로 다른 기기로 암호문만 복원되는 상황을 방지한다. 손상·키 무효화 시 세션을 비운다.
- iOS: Keychain generic-password item, `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`, 앱 번들 ID별 서비스 이름을 사용한다. iCloud 동기화를 활성화하지 않는다. 앱 삭제 후에도 Keychain이 남을 수 있으므로 로그아웃 때 명시적으로 삭제한다.
- Debug와 Release의 번들/애플리케이션 ID가 다르므로 개발·운영 토큰을 분리한다. 비밀번호는 저장하지 않는다.

근거: [Android Keystore](https://developer.android.com/privacy-and-security/keystore), [Apple Keychain Services](https://developer.apple.com/documentation/security/keychain-services).

## 사진

Android는 `PickVisualMedia`로 시스템 사진 선택기를 연다. 해당 선택기가 없는 버전에서는 AndroidX가 `ACTION_OPEN_DOCUMENT`로 대체한다. 전체 사진 라이브러리 읽기 권한은 필요하지 않다. JPEG/PNG는 원본 바이트를 읽고 다른 디코딩 가능한 형식은 JPEG로 변환한다.

iOS는 PhotosUI `PHPickerViewController`로 사용자가 고른 사진 한 장만 전달받는다. 사진 라이브러리 전체 권한을 요청하지 않는다. HEIC 등도 UIKit으로 읽어 JPEG로 변환하므로 API와 PDF 처리에서 사용할 수 있다. 양쪽 모두 백엔드 `PhotoFile`과 일치하는 5MiB 한도를 확인한다. 변환 후에도 5MiB를 넘으면 더 작은 사진을 고르도록 안내한다. 카메라 촬영은 별도 구현하지 않았다.

서버에서 받은 사진을 화면에 표시할 때는 긴 변 2048px 이하로 디코딩한다. Android는 BitmapFactory 크기 조회와 sampling, iOS는 ImageIO의 원본 직접 thumbnail 생성과 방향 보정 후 PNG 정규화를 사용한다. 큰 압축 이미지의 전체 해상도 메모리 할당을 줄이고 iOS 기존 HEIC 사진도 표시한다. UI는 디코딩을 Default dispatcher에서 수행한다.

근거: [Android Photo Picker](https://developer.android.com/training/data-storage/shared/photo-picker), [PHPickerViewController](https://developer.apple.com/documentation/photosui/phpickerviewcontroller).

## 위치와 지도

위치 권한은 사용자가 ‘현재 위치’를 누를 때만 요청한다. Android는 대략적 위치도 허용하고 OS LocationManager로 한 번 요청한다. iOS는 CoreLocation의 사용 중 권한을 요청한다. 20~25초 안에 위치를 얻지 못하면 오류를 반환하며 임의 좌표를 현재 위치로 대체하지 않는다. 권한을 거부해도 주소 검색과 지도 직접 선택을 사용할 수 있다. 배경 위치 추적은 하지 않는다.

빈 지도 최초 카메라 중심은 서울시청 주변이다. 이는 사용자 위치나 등록 매물 데이터가 아니다. 실제 매물·주변 시설 핀은 서버 좌표만 사용한다. 선택한 주소, 현재 위치, 지도 탭은 공통 `Coordinates`로 전달하며 카메라를 이동한다.

| 플랫폼 | 구현 | 설정과 조건 |
| --- | --- | --- |
| iOS | Apple MapKit `MKMapView` | 네이티브 SDK는 별도 지도 API 키를 요구하지 않는다. Apple 기본 출처 표기를 유지한다. MapKit JS/서버 API는 사용하지 않는다. Apple SDK/서비스 약관 적용. |
| Android | MapLibre Native 13.5.2 네이티브 MapView + OSM 표준 래스터 타일 | 키·결제 계정 없이 개발 가능. MapLibre는 BSD-2-Clause, 지도 데이터는 ODbL. 화면에 `© OpenStreetMap contributors`와 라이선스 링크를 표시한다. |

MapLibre 13.5.2는 공식 2026-09-23 안정 릴리스다. Maven Central 게시 POM의 Kotlin 2.2.10, coroutines 1.10.2, OkHttp 4.12.0 의존성을 확인했고 프로젝트 Kotlin 2.2.21 조합으로 빌드한다. MapLibre 자체는 Android와 iOS를 지원하며, 이 앱의 iOS 구현은 별도 외부 의존성이 없는 Apple MapKit을 사용한다. MapLibre 네이티브 MapView를 Compose AndroidView에 넣으므로 JavaScript/WebView 지도가 아니다.

OSM 공용 타일은 무료 무제한 상용 CDN이 아니다. 서비스 보장/SLA가 없으며 접근이 제한될 수 있다. 앱을 식별하는 User-Agent, 공식 HTTPS URL, MapLibre의 리소스 캐시와 OkHttp의 HTTP 만료 기반 64MiB 캐시를 사용한다. 타일 prefetch zoom delta는 0으로 설정한다. 일괄 다운로드·사전 수집·오프라인 다운로드 기능은 없다. 트래픽 증가나 공개 배포 시 이용 정책에 맞는 타일 제공자와 용량/요금을 별도로 검토해야 한다. 이 작업에서는 유료 리소스/계정/지도 키를 만들지 않았다.

외부 SDK로 교체하며 공개용 키가 필요해지면 Android는 추적하지 않는 Gradle properties/CI 환경, iOS는 추적하지 않는 xcconfig/CI에서 전달한다. 앱에 내장하는 공개 지도 키에는 패키지·서명/번들 ID 제한을 적용한다. 서버용 비밀키는 앱에 넣지 않는다.

근거: [Apple Maps SDK 안내](https://developer.apple.com/maps/), [MKMapView](https://developer.apple.com/documentation/mapkit/mkmapview), [MapLibre Android 시작하기](https://maplibre.org/maplibre-native/android/examples/getting-started/), [MapLibre 안정 릴리스](https://github.com/maplibre/maplibre-native/releases/tag/android-v13.5.2), [게시된 의존성 POM](https://repo.maven.apache.org/maven2/org/maplibre/gl/android-sdk/13.5.2/android-sdk-13.5.2.pom), [MapLibre 라이선스](https://maplibre.org/maplibre-native/android/examples/), [OSM 데이터 라이선스](https://www.openstreetmap.org/copyright), [OSM 타일 정책](https://operations.osmfoundation.org/policies/tiles/).

## PDF

서버가 생성한 PDF 바이트를 사용한다. Android는 OS `PdfRenderer`로 앱 내부 미리보기를 제공한다. 저장은 Storage Access Framework의 `CreateDocument`, 공유는 앱 캐시 디렉터리로 제한된 FileProvider의 임시 읽기 권한을 사용한다. 외부 저장소 전체 권한이나 외부 PDF 뷰어 앱 설치가 필요하지 않다.

iOS는 PDFKit `PDFView`, 파일 앱 `UIDocumentPickerViewController`, 공유 시트 `UIActivityViewController`를 사용한다. iPad 팝오버 기준점을 설정했다. 임시 PDF는 앱 내부 임시 저장소에 쓰며 24시간 이상 지난 파일은 다음 생성 시 정리한다. iOS 임시 파일에는 완전한 파일 보호 옵션을 사용한다. 파일 저장/공유를 취소하면 성공으로 표시하지 않도록 결과를 반환한다.

근거: [Android PdfRenderer](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer), [Apple PDFView](https://developer.apple.com/documentation/pdfkit/pdfview).

## Xcode 실행과 네이티브 UI 검사

`iosApp/iosApp.xcodeproj`를 열고 `iosApp` scheme, iPhone Simulator를 선택한다. 빌드 단계에서 `:composeApp:embedAndSignAppleFrameworkForXcode`가 실행된다. JDK 21이 필요하다. Debug는 개발 API, Release는 운영 API로 컴파일한다.

Apple Silicon Mac의 arm64 Simulator와 arm64 iPhone을 대상으로 한다. Xcode 프로젝트의 ARCHS도 arm64로 맞췄다. Intel Mac용 x86_64 Simulator 타깃은 이 프로젝트에 포함하지 않았다.

Compose 1.9.3의 iPhone 고주사율 확인을 통과하도록 Info.plist에 `CADisableMinimumFrameDurationOnPhone=true`를 넣었다. 누락하면 초기 화면이 잠시 보인 뒤 Compose의 plist 검사에서 앱이 종료될 수 있으므로 삭제하지 않는다.

```sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 18 Pro' \
  -derivedDataPath build/ios CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- build

xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 18 Pro' \
  -derivedDataPath build/ios CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- test
```

Simulator 실행에도 Xcode의 기본 ad-hoc 서명을 유지한다. `CODE_SIGNING_ALLOWED=NO`로 서명을 끄면 앱이 실행되더라도 Keychain에 필요한 `application-identifier` entitlement가 없어 로그인 저장이 `errSecMissingEntitlement(-34018)`로 실패할 수 있다. 서명 없는 실기기 빌드는 컴파일 확인에만 사용한다. 문제가 생기면 Simulator의 securityd 로그와 Xcode가 생성한 `*-Simulated.xcent` 및 바이너리의 `__TEXT,__entitlements` 링크 여부를 확인한다. 실기기 산출물은 최종 `.app`의 `codesign -d --entitlements -` 출력으로 확인한다. [Apple의 Keychain entitlement 진단](https://developer.apple.com/forums/thread/114456).

`iosAppUITests/SmokeTests.swift`는 로그인 화면, 빈 닉네임에서 버튼 비활성, 닉네임 입력과 스크린샷을 검사한다. 네트워크 로그인은 별도 `iosAppDevSmoke` scheme을 명시적으로 선택할 때만 실행한다. 이 scheme이 테스트 프로세스에 `RUN_DEV_UI_SMOKE=1`을 전달한다. 그 경우 무작위 새 개발 닉네임을 생성하고 빈 매물 화면, 앱 재실행 후 Keychain 세션 복원, 네이티브 MapKit 표시, 로그아웃을 확인한다. 기본 테스트는 로그인 요청을 보내지 않는다. 실제 실행 결과는 `BUILD_REPORT.md`가 기준이다.

실기기 설치는 Xcode의 Signing & Capabilities에서 사용자의 Apple Team을 선택하고 고유 Bundle Identifier를 설정한 다음, 연결된 iPhone에서 개발자 모드/기기 신뢰를 승인해야 한다. Apple 계정·서명 인증서는 이 저장소에 포함하지 않는다. 위치 허용/거부, 실제 사진 선택, 파일 앱 저장, 공유 대상 앱까지의 수동 검증은 실기기에서 별도로 실행한다.
