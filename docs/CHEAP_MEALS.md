# 저렴한 한 끼 찾기 — 구현과 로컬 iPhone 검증

검증일: 2026-10-29, Asia/Seoul. 이 기능은 격리된 React Preview에 구현했다. 팀 dev 웹에는 배포하지 않았다.

## 소스와 보존

| 공간              | 위치/브랜치                                          | 상태                                             |
| ----------------- | ---------------------------------------------------- | ------------------------------------------------ |
| 상위 팀 저장소    | `../`, `456/feat-promotion`                          | 소스와 Git 메타데이터를 읽기만 함                |
| 개인 모바일       | `mobile-kmp`, `codex/cheap-meals`                    | 실행 스크립트·패치·검증·문서, 로컬 커밋          |
| 독립 React clone  | `.tools/thrifty-preview`, `codex/mobile-cheap-meals` | 기존 clone 재사용, 로컬 커밋, push URL 차단 유지 |
| 최신 develop 기준 | `021267b55ac699d02ff0ccbf0a24f7da7fa73e0e`           | 10월 29일 fetch 후 분기                          |
| 이전 알뜰 지도    | clone의 `codex/mobile-thrifty-preview` / `1855200`   | 이전 미커밋 소스와 테스트를 먼저 커밋해 보존     |

기존 `preview/thrifty.patch`도 남겼다. 새 소스는 `preview/cheap-meals.patch`와 `preview/manifest.json`으로 재현한다. 최신 develop의 Emotion·인증·라우터를 유지하고 예전 업소 기능만 이식했다. 과거 `/map`, 구형 인증 및 하단 네 탭은 복구하지 않았다. Xcode 프로젝트·Info.plist·서명 설정·기본 Debug/Release URL은 변경하지 않았다. 이번 작업에서는 push/merge/배포하지 않는다.

## 구현

- 홈의 GuideCard 다음 독립 카드 → `/cheap-meals`의 **전체**. 세 가지 선택 카드와 `전체 | 식당 | 편의점 | 배달` 탭, URL `tab` 상태. 선택 전 카테고리 API를 호출하지 않는다.
- 식당: 기존 실제 업소 API/네이버 지도, 주소 검색, 사용자가 누를 때만 GPS, 500m/1km/2km, 재검색, 마커·목록·상세·등록 메뉴 가격·전화/외부 길찾기. 음식점 외 업종도 원문대로 표시한다. 지도 인증 오류가 전체 페이지를 중단하지 않으며 지도 재시도 버튼을 제공한다. 인증에 실패한 SDK의 재초기화에는 페이지 전체 새로고침을 사용하며 이때 메모리 필터는 초기화된다.
- 편의점: 이마트24 기본, 세븐일레븐, 전체/1+1/2+1/할인/기타 행사. 3+1·골라담기와 UNKNOWN도 보존한다. 24개씩 더 보기, 이미지 실패 대체, 판매가격 원본, 상세 시트, 공식 출처·수집 시각·매장별 적용 안내. 미수집 0건과 오류를 구분한다.
- 배달: 전체/배민/쿠팡이츠, 실제 할인·최소 주문·조건·기간·한국 시간 확인 시각. null은 정보 없음. 최종 결제금액이나 주문 링크를 만들지 않는다.
- 방문한 카테고리는 유지해 필터·표시 개수·스크롤을 보존한다. 식당의 조회 위치·반경·확대도 유지한다. 상세는 browser history로 열어 뒤로가기가 우선 닫는다. 모달 시트는 배경 스크롤 잠금, 초점 복원, Escape·Safe Area·긴 문구 줄바꿈을 처리한다.
- 새 UI 라이브러리, UT 수집기, 분석 이벤트, 대시보드를 추가하지 않았다. Preview만 분석 환경변수를 비워 운영 트래픽과 섞이지 않게 한다.

주요 React 파일(독립 clone의 `frontend/` 기준):

- `src/pages/design-home/ui/CheapMealCard.tsx`, `mobile/HomeMobilePage.tsx`, `desktop/HomeDesktopPage.tsx`
- `src/app/router/AppRoutes.tsx`, `src/pages/cheap-meals/CheapMealsPage.tsx`
- `src/pages/cheap-meals/hooks/{useMealNavigation,useRestaurantMap}.ts`
- `src/pages/cheap-meals/ui/{RestaurantPanel,ConveniencePanel,DeliveryPanel,MealDetails,MealSheet,ProductImage}.tsx`
- `src/features/good-price/`, `src/features/meal-deals/`, `src/features/map/`의 SDK 실패 처리
- `test/pages/cheap-meals/`, `test/features/{good-price,meal-deals,map}/`

## Mac 서버 시작

아래 명령은 **개인 mobile-kmp 루트**에서 실행한다. Node 22를 사용한다. 기존 ignored `.env.local`과 인증서를 재사용하며 키 값을 문서·채팅·Git에 복사하지 않는다.

```sh
cd ~/Desktop/woowacourse/2026-jachwi-sunbae/mobile-kmp
export PATH="$PWD/.tools/refresh-20261027/node-v22.23.1-darwin-arm64/bin:$PATH"
npm run preview:prepare
npm --prefix .tools/thrifty-preview/frontend ci
JACHWI_LOCAL_ORIGIN=https://192.168.3.253:3443 npm run dev:preview
```

10월 29일 Mac IP는 `192.168.3.253`이다. IP가 바뀌면 `ipconfig getifaddr en0`로 확인하고 인증서 SAN·서버 origin·앱 URL·네이버 허용 호스트를 함께 맞춘다. 현재 leaf 인증서는 기존 사용자 신뢰 CA로 발급했고 이 IP를 포함한다. 개인키·인증서·환경설정은 `.tools/`에만 있다. 새 기기의 인증서 설치와 전체 신뢰는 사용자가 직접 수행한다. [HTTPS 설정 안내](WEBVIEW_LOCAL_DEVELOPMENT.md)를 참고한다.

실행 어댑터는 `ENABLE_MSW=false`, 실제 네이버 provider, dev API proxy만 허용한다. API base와 WSS HMR origin을 iPhone이 접속하는 HTTPS origin에 맞춘다. Secure/HttpOnly 쿠키 속성과 TLS 검증을 유지한다. 팀 frontend/webpack 설정이나 Release를 바꾸지 않는다.

## iPhone 연결과 수동 확인

1. iPhone 12 mini를 같은 Wi-Fi에 연결하고 Mac에 연결·잠금 해제한다.
2. Xcode에서 기존 `iosApp` scheme, Debug, 현재 Personal Team/Bundle Identifier를 유지한다. Run의 환경변수 `JACHWI_WEB_URL`을 위 HTTPS origin으로 설정한다. 기존 앱을 삭제하지 않는다.
3. 이미 설치된 앱은 아래 명령으로 이번 실행의 URL만 지정할 수 있다. 기기 ID는 `xcrun devicectl list devices`에서 확인한다.

```sh
DEVICECTL_CHILD_JACHWI_WEB_URL=https://192.168.3.253:3443 \
  xcrun devicectl device process launch --device <DEVICE-ID> \
  --terminate-existing kr.jachwisunbae.app.dev
```

4. 홈의 `오늘 한 끼, 저렴하게` → 전체 카드 세 개를 확인한다.
5. 편의점에서 실제 이미지/가격 → 상세 → 내부 스크롤 → 닫기/뒤로가기를 확인한다. 1+1 판매가격을 반값으로 바꾸지 않는지 확인한다. 목록 하단의 더 보기를 누른다.
6. 세븐일레븐·1+1 선택 → 배달 → 배민/쿠팡이츠 → 편의점 복귀 시 두 필터를 확인한다. 현재 세븐일레븐은 미수집 0건이다.
7. 배달 카드·상세의 금액/조건/null과 닫기를 확인한다. 실제 주문은 발생하지 않는다.
8. 식당에서 지도 타일/마커, 주소 검색, 세 반경, 목록·상세, 지도 드래그 후 재검색을 확인한다. GPS와 권한 변경은 사용자가 직접 확인한다. 전화 링크는 발신 전에 확인한다.
9. React 파일을 수정해 HMR/새로고침 반영을 확인한다. HMR의 전체 reload가 발생하면 필터는 초기화될 수 있다. 일반적인 카테고리 이동의 상태 보존과 구분한다.
10. 로컬 모드를 끝내려면 서버를 종료하고 Xcode 실행 환경변수를 끈 뒤 Run한다. 기본 dev 웹으로 돌아간다.

네이버 콘솔의 같은 Maps Application → Web 서비스 URL에 **`http://192.168.3.253`** 등록이 필요하다(포트/경로 제외, 기존 URL 유지). 이전 IP의 등록으로 새 IP가 허용되지는 않는다. 이 설정은 사용자가 직접 확인한다.

## 실제 API 결과

배포 `/actuator/info` 커밋은 `fb7144a51757ec969bab16b3f048c970a3625d05`이며, 최신 프론트 기준과 다르지만 네 endpoint가 모두 배포돼 있다. `/v3/api-docs`와 실제 인증 응답을 함께 검사했다.

| 조회                       | 인증 응답 | 실제 데이터                   | 미인증 |
| -------------------------- | --------- | ----------------------------- | ------ |
| 시청 중심 1km 착한가격업소 | 200       | 22곳, 음식점 외 업종 1곳 포함 | 401    |
| 이마트24                   | 200       | 2,337개, 다섯 행사 유형       | 401    |
| 세븐일레븐                 | 200       | 0개, collectedAt=null, 미수집 | 401    |
| 배달 할인                  | 200       | 108개: 배민 71 / 쿠팡이츠 37  | 401    |

재현: `npm run verify:cheap-meals-api`. 별도 익명 QA 회원으로 로그인·refresh해 동일 회원 복원과 Secure/HttpOnly 쿠키를 검사한다. 토큰·쿠키·회원 ID는 출력/저장하지 않는다. 회원 삭제 API가 없어 QA 회원은 남는다. 업소/상품/할인 데이터는 읽기만 한다. [응답 요약 JSON](verification/cheap-meals-api-2026-10-29.json).

## 검증과 제약

- React 전체 100개 파일/680개 테스트 통과 후 지도 SDK 회귀 및 상세·로딩·위치 전환 테스트를 보완해 관련 21개 파일/184개 테스트도 통과했다. 마지막 지도 재시도 변경 뒤 페이지 13개 테스트를 재검증했다. lint·format·typecheck·production build 성공. 초기 추가 회귀 테스트는 로딩 대기와 상세 초점 복원에서 실패했으며 수정 후 통과했다. [빌드 보고서](BUILD_REPORT.md)를 참고한다.
- iOS Simulator `testCheapMealsLiveContract`, `testCheapMealsPreviewNavigation` **2/2 성공**. 실제 HTTPS WKWebView에서 네 API, 쿠키 속성, 동일 회원 refresh, 홈→전체→상품 상세→배달 상세→전체 흐름을 검사했다. 이는 실기기 자동 테스트가 아니다.
- Android 단위 테스트·APK·lint와 KMP iOS Simulator framework 빌드 성공. iOS Simulator 앱·테스트 빌드는 위 XCTest에 포함됐다. 이번에는 네이티브 앱 코드를 바꾸지 않아 기존 서명 iPhone 앱에서 새 웹을 열었다.
- 실제 iPhone 12 mini/iOS 27.0.1: 홈/전체, 실제 상품 이미지와 2,337개 목록, 상품 상세/스크롤/닫기, 세븐일레븐 미수집, 배달 108개·쿠팡이츠 37개·상세를 확인했다. 세븐일레븐 1+1과 쿠팡이츠 필터는 탭 왕복 후 유지됐다. React 수정 반영도 앱 재설치 없이 확인했다. 식당 판교 500m/1km/2km는 1/2/6곳으로 바뀌고 목록에서 업소 상세로 진입했다.
- 브라우저 375px에서 가로 넘침 없음, 24→48개 더 보기와 탭 왕복 후 스크롤 위치 유지, 서울시청 주소 검색 후 22곳을 확인했다. 1024px 데스크톱 홈 배치도 확인했다.
- **현재 새 IP에서 네이버 지도 인증이 실패한다.** 업소 API와 목록·상세는 동작한다. 사용자에게 허용 URL 등록을 요청했다. 이 상태를 지도 타일/마커/드래그 실기기 검증 완료로 표시하지 않는다.
- 세븐일레븐 수집을 임의 실행하거나 데이터를 채우지 않았다. 실제 GPS/권한 거부·재허용, 전화 발신, iOS 16, 큰 글꼴·가로 모드, Android 실기기는 미검증이다.

증거: [iPhone 전체](verification/cheap-meals-iphone-overview-2026-10-29.png), [상품](verification/cheap-meals-iphone-products-2026-10-29.png), [상품 상세](verification/cheap-meals-iphone-product-detail-2026-10-29.png), [세븐일레븐](verification/cheap-meals-iphone-seven-empty-2026-10-29.png), [배달](verification/cheap-meals-iphone-delivery-2026-10-29.png), [할인 상세](verification/cheap-meals-iphone-delivery-detail-2026-10-29.png). Device Hub의 실제 iPhone 미러링 원본이며 Simulator 이미지와 구분한다.

추가 상태와 검사 수치는 [검증 요약 JSON](verification/cheap-meals-results-2026-10-29.json)에 있다. 마지막 추가 UI 확인 중 Mac이 잠겨 작업을 멈췄으며 기기 잠금을 우회하지 않았다.

로그는 Git 제외 `.tools/cheap-meals-20261029/`의 `full-tests.log`, `final-targeted-tests.log`, `final-lint.log`, `final-format.log`, `final-typecheck.log`, `final-build.log`, `gradle.log`, `ios-tests.log`, `ios-tests.xcresult`에 있다.

## 작업 보존

React 수정 후 개인 루트에서 다음을 실행한다. 기준 develop 이후의 커밋과 미커밋 변경을 모두 패치에 담는다. `.env`, 의존성, 빌드 결과는 포함하지 않는다.

```sh
npm run preview:save
npm run format:docs
npm run format:docs:check
```

React clone의 SDK 보완 커밋은 `eee8af8`, 기능 구현 커밋은 `c4fb83c`다. 개인 저장소도 기능 단위 로컬 커밋으로 기록한다. `preview:prepare`는 기존 편집을 덮어쓰지 않으며 충돌 시 중단한다. 새 clone에서는 manifest의 기준 커밋에 패치를 적용한다. `.tools`를 삭제하기 전에 반드시 패치와 로컬 커밋 보존 여부를 확인한다.

## IP 변경 없이 사용하는 방법

IP가 매번 바뀌는 것은 아니지만 DHCP 임대 갱신·공유기·Wi-Fi 변경으로 달라질 수 있다. 같은 공유기에서는 **DHCP 예약(Address Reservation)** 으로 이 Mac에 항상 같은 IP를 배정하고 네이버 Web 서비스 URL을 한 번 등록하는 방법을 권장한다. 공유기 관리 권한이 필요하며 이 작업에서는 아직 설정하지 않았다. [제조사의 DHCP 예약 설명](https://kb.netgear.com/25722/How-do-I-reserve-an-IP-address-on-my-NETGEAR-router)을 참고한다.

Bonjour `.local` 이름은 일반적으로 IP 대신 쓸 수 있지만 현재 환경에서는 Mac HTTPS 200, iPhone 연결 실패를 확인했다. 따라서 이번 실행의 기본 주소로 채택하지 않고 기존 IP 모드로 복구했다. [Apple 로컬 호스트 이름 안내](https://support.apple.com/ko-kr/guide/mac-help/mchlp2322/mac). 시험한 설정은 임시였으며 공유기·Mac 네트워크 설정·TLS 검증은 바꾸지 않았다.

집/교육장/회사 등 다른 네트워크에서도 같은 URL이 필요하다면 고정 도메인의 개발 서버 또는 접근 제어된 고정 터널을 별도로 준비해야 한다. 이는 현재의 같은 Wi-Fi 로컬 Preview와 다른 운영 방식이다.
