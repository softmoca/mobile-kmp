# Mac 로컬 React를 iPhone에서 보기

알뜰 지도 통합은 [THRIFTY_PREVIEW](THRIFTY_PREVIEW.md)의 `npm run dev:preview`를 사용한다. 아래 `dev:web`는 상위 팀 frontend를 읽는 일반 모드이고, `dev:preview`는 개인 저장소의 격리 clone을 읽는다.

## 기본 모드를 먼저 사용

Xcode Debug는 별도 설정 없이 `https://dev.jachwi-sunbae.kr`을 연다. 웹을 dev에 배포하고 새로고침하면 앱 재빌드 없이 반영된다. 이 방식이 지도 도메인·쿠키·인증서 설정이 가장 단순하다.

선택적 로컬 모드는 개인 저장소의 `scripts/local-web.cjs`로 **팀 frontend 소스/설정을 읽기만** 한다. 팀 `.env.local`에 이미 있는 공개 빌드 설정은 읽되 값을 출력하지 않는다. 팀 파일을 바꾸거나 팀 저장소에서 npm 설치/format/build를 실행하지 않는다. frontend 의존성이 이미 설치되어 있어야 한다.

## HTTPS 준비

Refresh Cookie는 Secure/HttpOnly다. `http://Mac-IP:3000`은 인증 세션 검증에 사용할 수 없고 앱도 허용하지 않는다. HTTPS에서 같은 origin `/api` 프록시를 사용해 SameSite/third-party cookie 문제도 피한다.

1. Mac과 iPhone을 같은 Wi-Fi에 연결하고 Mac의 LAN IP를 확인한다. 게스트 AP의 기기 격리가 켜져 있으면 연결되지 않는다. iPhone의 `localhost`는 iPhone 자체다.
2. 신뢰 가능한 개발 CA로 해당 IP 또는 `.local` 호스트의 인증서를 만든다. 예를 들어 설치된 [mkcert](https://github.com/FiloSottile/mkcert)를 사용하는 경우 다음을 실행한다.

```sh
# mobile-kmp 루트. 예시 IP는 실제 Mac LAN IP로 바꾼다.
mkdir -p .tools/certs
mkcert -cert-file .tools/certs/local.pem -key-file .tools/certs/local-key.pem 192.168.0.10 localhost 127.0.0.1
```

3. 테스트 iPhone에는 **공개 rootCA.pem만** 전달하고 프로파일을 설치한 뒤 설정 → 일반 → 정보 → 인증서 신뢰 설정에서 해당 CA를 신뢰한다. 실제 기기의 인증서 신뢰 결정은 사용자가 한다. [Apple 공식 절차](https://support.apple.com/102390)를 따른다. 개인키 `rootCA-key.pem`과 `local-key.pem`은 기기/원격 저장소에 전달하지 않는다. `.tools`, `*.pem`, `*.key`는 Git에서 제외된다.
4. 서버 인증서의 SAN에 실제 접속 IP/호스트가 포함되어야 한다. IP가 바뀌면 인증서와 아래 URL도 갱신한다. 서버 신뢰 오류를 무시하는 앱 코드는 없다.

## 서버 실행

```sh
JACHWI_LOCAL_ORIGIN=https://192.168.0.10:3000 npm run dev:web
```

인증서 경로를 바꾸려면 `JACHWI_TLS_CERT`, `JACHWI_TLS_KEY`, frontend 위치를 바꾸려면 `JACHWI_FRONTEND_PATH`를 지정한다. 환경값은 셸에서만 설정하고 Git에 저장하지 않는다. 의존성/인증서/설정만 검사하려면 같은 환경으로 `npm run dev:web -- --check`를 실행한다.

어댑터는 다음을 설정한다.

- 기존 팀 Webpack entry, loader, plugin, 라우팅 fallback, HMR과 `/api` 프록시를 재사용.
- API upstream을 `https://dev-api.jachwi-sunbae.kr`로 고정. 운영 API·Mock 서버 사용 안 함.
- 팀 빌더가 proxy 활성화 시 `http://localhost:3000`으로 고정하는 공개 API 주소를 **이 로컬 빌드에서만** 접속 HTTPS origin으로 교체.
- 서버를 지정한 호스트에 바인딩하고 allowedHosts도 해당 호스트만 허용. HMR은 `wss://같은호스트:포트/ws`.
- 빌드 캐시와 산출물은 개인 저장소 `.tools/local-web` 안에 기록.
- 쿠키의 Secure/HttpOnly/SameSite, upstream TLS 검증은 유지. origin 제거는 팀의 기존 `/api` 프록시 동작 그대로다.

[Webpack devServer 공식 문서](https://webpack.js.org/configuration/dev-server/)의 HTTPS server/client.webSocketURL/allowedHosts 구성을 따른다. Mac 방화벽·iPhone 로컬 네트워크 권한을 확인한다. 외부 공개 터널·서비스 가입·공유기 포트 포워딩은 필요 없다.

## 앱에서 선택

Xcode → Product → Scheme → Edit Scheme → Run → Arguments → Environment Variables에 다음을 추가하고 체크한다.

```text
JACHWI_WEB_URL = https://192.168.0.10:3000
```

`iosApp` Debug를 Run한다. 환경변수는 Xcode 실행 시 적용되므로 로컬 모드를 확인할 때 Xcode에서 실행한다. 홈 화면에서 직접 실행하면 해당 실행 환경이 없을 수 있다. 변수를 끄면 다시 배포 dev 웹이다. 공유 scheme에 개인 IP를 커밋하지 않는다. 잘못된 로컬 URL은 조용히 다른 서버로 연결하지 않고 설정 오류를 표시한다.

Release는 `JACHWI_WEB_URL`을 컴파일 조건으로 무시한다. HTTP/임의 외부 호스트는 Debug 로컬 주소로 허용하지 않는다. ATS의 arbitrary loads 예외를 추가하지 않았다.

## 제약과 확인할 것

- 로컬 HTTPS origin과 배포 dev origin의 쿠키/익명 회원은 다르다. 자동 세션 이전은 없다. 로컬 호스트를 바꾸면 별도 쿠키가 생긴다.
- 네이버 Maps는 등록된 Web 서비스 URL만 허용한다. LAN IP/개발 호스트가 등록되지 않으면 지도 인증 오류가 발생할 수 있다. [네이버 공식 안내](https://www.ncloud.com/support/faq/all/1047?searchKeyword=map)에 따라 담당자가 개발 호스트를 등록해야 한다. 이번 작업에서는 콘솔 설정을 바꾸지 않는다.
- 팀 `.env.local`에 지도 키가 없으면 기존 Webpack의 demo 지도 설정일 수 있다. 실제 지도 검증은 배포 dev에서 한다. 키를 문서/로그에 복사하지 않는다.
- HTTPS 인증서와 iOS 신뢰 설정, 로컬 네트워크 허용은 서로 별개다. Mac curl 성공만으로 실제 iPhone 접근 성공이라고 판단하지 않는다.
- HMR은 기존 서버가 제공한다. 팀 파일은 이번 작업에서 수정하지 않으므로 실제 팀 React 수정 → iPhone HMR 반영은 후속 수동 확인 항목이다.
- HTTP 보안 우회나 외부 터널로 문제를 숨기지 않는다. 인증서·도메인 설정이 어렵다면 배포 dev 모드를 사용한다.

실제 실행한 범위는 [WEBVIEW_QA](WEBVIEW_QA.md)에 기록한다.
