# 알뜰 지도 로컬 Preview

> 이 문서는 10월 27일 알뜰 지도 Preview의 과거 기록이다. 현재 manifest·실행 브랜치는 10월 29일 [저렴한 한 끼 찾기](CHEAP_MEALS.md)로 전환했다. 최신 실행법과 검증 상태는 해당 문서를 따른다.

## 범위와 소스

팀 dev 웹을 배포하지 않고 **실제 dev API + 최신 React + 알뜰 지도**를 개인 iPhone WKWebView에서 확인한다. 기본 앱은 계속 `https://dev.jachwi-sunbae.kr`을 사용한다. 알뜰 기능은 로컬 Preview에만 있다.

- 개인 작업 브랜치: `codex/thrifty-preview`. 앞선 `feat/webview-shell`의 구현과 개인 Xcode 설정을 이어받았다. 기능별 커밋과 검증 기록은 [작업 기록](WORK_LOG.md)에 남긴다.
- 팀 기준 커밋: `43b8d66352564e16af5db5c63585f565b7756056` (`origin/develop`, 2026-10-27 확인).
- 참고 프로토타입: `ec4aa5c139be781ac7942b4e60575f57820bd6db` (`codex/351-neighborhood-prototype`).
- 격리 작업 폴더: `.tools/thrifty-preview/`. 별도 clone이며 상위 팀 저장소의 worktree가 아니다. 해당 clone의 push URL은 `disabled://local-preview-read-only`다.
- 재현 가능한 변경: `preview/manifest.json`, `preview/thrifty.patch`. `.tools`를 Git에 추가하지 않는다.

프로토타입의 업소 API/모델, 알뜰 Hook, 마커, 목록 시트, 상세, 동네 검색 Dialog를 이식했다. 최신 `useMapPage`와 데스크탑/모바일 매물 지도는 유지하고 `/map?mode=thrifty`만 알뜰 화면으로 분기한다. 일반 지도에서 알뜰로 들어갈 때 중심과 반경을 전달하고, 시설 카테고리로 나올 때 중심/반경을 이어간다. 뒤로가기와 상세 선택은 React Router를 사용한다.

최신 develop에는 하단 탭이 없어 요청에 따라 **Preview의 React 공통 레이아웃에 홈·체크리스트·지도·마이 네 개**를 복원했다. 폼/상세와 데스크탑 화면은 기존 구성을 유지한다. 알뜰은 다섯 번째 탭이 아니며 네이티브 탭을 만들지 않았다. 최신 인증/매물/체크리스트 소스를 이전 브랜치로 되돌리지 않았다.

## 준비와 실행

팀 frontend의 `.nvmrc`에 맞는 **Node.js 22.23.x**를 권장한다. 이번 검사에는 22.23.0을 사용했다. Node 26에서는 jsdom의 localStorage 관련 테스트 환경 오류가 있어 지원 버전으로 다시 실행했다.

```sh
# mobile-kmp 루트
npm run preview:prepare
npm --prefix .tools/thrifty-preview/frontend ci

# 공개 빌드 설정을 복사한다. 원본은 읽기만 하며 내용은 로그/커밋에 남기지 않는다.
cp ../frontend/.env.local .tools/thrifty-preview/frontend/.env.local

# WEBVIEW_LOCAL_DEVELOPMENT.md에 따라 인증서와 기기 신뢰를 먼저 준비한다.
JACHWI_LOCAL_ORIGIN=https://<Mac-LAN-IP>:3443 npm run dev:preview -- --check
JACHWI_LOCAL_ORIGIN=https://<Mac-LAN-IP>:3443 npm run dev:preview
```

`preview:prepare`는 고정한 develop 커밋을 복제하고 패치를 적용한다. 이미 수정된 Preview를 reset하거나 덮어쓰지 않는다. 이미 패치가 적용되어 있으면 그대로 재사용한다. 팀의 새 develop을 가져올 때는 manifest의 기준을 검토하고 별도 복제에서 충돌을 해결해야 한다.

`dev:preview`는 `ENABLE_MSW=false`, `MAP_PROVIDER_MODE=naver`를 강제한다. 지도 키가 없으면 시작을 거절한다. 실제 API나 지도 인증 오류를 Mock/데모 지도로 대체하지 않는다. 사용자 원본 업소명·가격·메뉴·좌표는 API 그대로 표시한다. 데이터 기준일을 함께 표시하며 가격 문자열(범위·무료·누락)을 숫자로 임의 변환하지 않는다.

기존 Webpack `/api` 프록시를 재사용하면서 브라우저 API Base URL만 접속한 HTTPS origin으로 설정한다. iPhone이 `http://localhost:3000`을 호출하지 않는다. upstream은 `https://dev-api.jachwi-sunbae.kr`로 고정한다. Secure/HttpOnly 쿠키, TLS 검증, WSS HMR과 host allowlist를 유지한다. 빌드 출력과 캐시는 `.tools/thrifty-web/`에 있다.

React 코드는 `.tools/thrifty-preview/frontend/src/`에서 편집한다. HMR이 모듈 경계를 갱신하지 못하면 웹 새로고침을 사용한다. 편집 결과를 개인 저장소에 보존하려면 다음을 실행한다.

```sh
npm run preview:save
git diff -- preview/thrifty.patch
```

`preview:save`는 격리 clone의 `frontend/src`, `frontend/test` 변경만 패치로 저장한다. `.env.local`, 키, 의존성, 빌드 결과는 포함하지 않는다. 커밋/push는 하지 않는다.

## 실제 dev API 검증

```sh
npm run verify:preview-api
```

이 명령은 dev health/info/OpenAPI, 비인증 401, 익명 로그인, Refresh Cookie로 같은 회원 복원, 3개 반경과 빈 결과, 잘못된 반경 400을 확인한다. 토큰과 쿠키는 메모리에서만 사용한다. 익명 로그인 외의 데이터 생성·수정·삭제는 하지 않는다. 반복 실행 시 QA 익명 회원이 생길 수 있으며 회원 삭제 API가 없어 남는다.

2026-10-27 결과: `/actuator/health` UP, `/actuator/info`의 배포 커밋이 기준 develop과 일치했다. `/v3/api-docs`의 Bearer 인증과 Controller/DTO를 대조했다.

| 기준점                 | 500m | 1km | 2km |
| ---------------------- | ---: | --: | --: |
| 서울 시청              |    2 |  22 |  75 |
| 강남역                 |    7 |  29 |  66 |
| 해상 빈 결과 확인 지점 |    0 |   0 |   0 |

데이터 기준일은 `2026-06-30`이다. 개수는 실행 당시 응답이며 고정 fixture가 아니다. [토큰을 제외한 결과](verification/thrifty-api-2026-10-27.json)를 보관한다.

## iPhone Preview와 dev 전환

1. Mac과 iPhone을 같은 LAN에 연결한다. 서버 IP가 바뀌면 인증서 SAN과 URL을 갱신한다.
2. iPhone에서 개발 CA 공개 인증서만 설치하고 **설정 → 일반 → 정보 → 인증서 신뢰 설정**에서 사용자가 직접 신뢰한다. [Apple 공식 안내](https://support.apple.com/102390). CA 개인키를 iPhone이나 원격 저장소에 전달하지 않는다.
3. Xcode에서 기존 `iosApp` scheme, Debug, Personal Team, Bundle Identifier, iPhone을 유지한다. Run → Arguments → Environment Variables에 `JACHWI_WEB_URL=https://<Mac-LAN-IP>:3443`을 체크하고 Run한다.
4. 하단 지도 → 알뜰 → 주소 검색 또는 현재 위치 → 반경 → 가게 목록/핀 → 상세를 확인한다.
5. **기존 dev 모드로 돌아가려면 `JACHWI_WEB_URL`을 끄고 Xcode에서 다시 Run**한다. Release는 로컬 URL을 무시한다.

앱을 업데이트 설치할 때 기존 앱을 삭제하지 않는다. 환경변수는 실행 시 적용되므로 iPhone 홈 화면에서 별도로 실행하면 기본 dev 모드가 될 수 있다. Preview cookie는 dev 웹과 별개이므로 익명 기록도 별개다. 기존 Compose Keychain 세션을 자동 이전하지 않는다.

CLI로 이미 설치된 앱을 실행할 수도 있다. Bundle ID는 서명된 앱에서 읽는다. 기기 UDID와 개인 서명값은 문서에 저장하지 않는다.

```sh
BUNDLE_ID=$(/usr/libexec/PlistBuddy -c 'Print CFBundleIdentifier' .tools/WebViewDeviceData/Build/Products/Debug-iphoneos/JachwiSunbae.app/Info.plist)
DEVICECTL_CHILD_JACHWI_WEB_URL=https://<Mac-LAN-IP>:3443 \
  xcrun devicectl device process launch --device <iPhone-UDID> --terminate-existing "$BUNDLE_ID"
# dev 복귀: 위 환경변수 없이 동일 launch 명령 실행
```

Mac 브라우저도 CA 신뢰가 필요하다. 인증서 경고를 우회하지 않는다. 이번에 임시로 제공한 CA는 `WebView Isolated QA CA`, 2026-11-26 만료다. iPhone에서 테스트를 끝낸 후 프로파일을 삭제해 신뢰를 제거할 수 있다. 공개 프로파일 전달 서버는 인증서만 있는 별도 폴더를 제공하며 소스/개인키를 노출하지 않는다.

## 네이버 지도 서비스 URL

기존 지도 키에 로컬 호스트가 허용되어 있어야 한다. 콘솔의 Maps Application → Web 서비스 URL에 `http://<Mac-LAN-IP>`를 추가한다. **포트와 경로는 제외**하고 기존 서비스 URL을 유지한다. 네이버는 http/https를 구분하지 않는다고 안내한다. [서비스 URL 등록](https://guide.ncloud-docs.com/docs/maps-app), [인증 오류 안내](https://www.ncloud.com/support/faq/all/1047?searchKeyword=map).

처음에는 인증 실패를 재현했으나, 사용자가 현재 Maps Application의 동일 Client ID에 호스트 등록을 확인·저장한 후 새 세션에서 Mac/iPhone 지도 타일과 마커가 정상 표시됐다. dev 업소 조회 성공과 지도 SDK 인증 성공은 별개다. SDK 인증이 늦게 실패하면서 내부 객체를 폐기해도 지도 영역만 오류를 표시하고 목록·상세·반경 조회는 유지한다. 변경 후 앱이나 웹을 새로고침한다. Referer 위조, 외부 터널, 보안 검증 해제, 데모 지도 대체는 사용하지 않는다.

## 2026-10-27 확인 결과

- 실제 iPhone 12 mini: 홈·체크리스트·지도·마이 탭, 지도 내부 알뜰 전환, 실제 지도와 업소 핀, 목록/상세/메뉴·가격, 500m·1km·2km 조회. 판교 기준 1/2/6곳. 길찾기를 누르면 카카오맵 앱으로 전환되는 것도 확인했다.
- Mac 브라우저: 신뢰된 HTTPS, 서울시청 주소 검색, 3개 반경 2/22/75곳, 지도 드래그 후 이 위치에서 찾기 69곳, 업소 상세와 전화/길찾기 링크. 최종 브라우저 오류 로그 0건.
- iOS UI 자동화: 홈 → 지도 → 알뜰 → 실제 주소 검색 → 상세/링크 → 목록 → 500m 전환 통과(1개, 실패 0).
- 실제 WKWebView 쿠키 검사: Secure/HttpOnly, 같은 회원 refresh, 재로드 후 복원, 실제 3개 반경 조회 통과.
- 웹 테스트 100파일/627개, lint·format:check·build/typecheck 통과. iOS Simulator/서명 iPhone 빌드와 기존 Android/KMP 필수 검사 통과.
- React 수정이 앱 재빌드 없이 iPhone에 반영됐다. HMR이 전체 새로고침을 수행할 수 있으므로 미저장 입력 상태 보존은 보장하지 않는다.
- 공개 인증서 전달 서버는 종료했다. 테스트 후 iPhone 프로파일과 Mac 키체인의 해당 개발 CA를 제거할 수 있다. HTTPS Preview는 Mac 서버 실행 중, 같은 LAN에서 사용할 수 있다.

[실기기 지도](verification/thrifty-iphone-map-2026-10-27.png) · [목록](verification/thrifty-iphone-list-2026-10-27.png) · [상세](verification/thrifty-iphone-detail-2026-10-27.png) · [Mac 상세](verification/thrifty-mac-detail-2026-10-27.png)

## 검사

```sh
npm --prefix .tools/thrifty-preview/frontend run lint
npm --prefix .tools/thrifty-preview/frontend run format:check
npm --prefix .tools/thrifty-preview/frontend test
npm --prefix .tools/thrifty-preview/frontend run build
npm run test:webview
npm run format:docs
npm run format:docs:check
```

웹 단위/라우팅 테스트에서만 MSW를 사용한다. 실행 Preview와 실제 API 검증에는 MSW를 사용하지 않는다. iOS 테스트의 `testThriftyPreviewUI`, `testThriftyPreviewCookiesAndContract`는 HTTPS Preview가 준비되고 테스트 runner에 `JACHWI_PREVIEW_URL`이 지정된 경우만 실행한다. 기본 dev smoke에서는 skip한다. `.tools/ThriftySimulatorData`의 xctestrun을 별도로 복사하여 개인 URL을 지정하며 공유 scheme에 개인 IP를 넣지 않는다.

사진·PDF·위치 권한·외부 URL 처리는 기존 WebView 구현을 재사용한다. 길찾기 외부 앱 전환은 확인했고, 실제 GPS/권한 거부·재허용, 사진 선택/HEIC, PDF 공유/Files, 전화 발신은 미검증이다. 이번 기능이 기존 미검증 항목을 자동으로 해결하지는 않는다. 실제 확인 범위와 남은 항목은 [BUILD_REPORT](BUILD_REPORT.md), [WEBVIEW_QA](WEBVIEW_QA.md)에 기록한다.
