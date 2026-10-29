# 자취선배 KMP 모바일 앱

이 디렉터리는 상위 팀 모노레포와 분리된 개인 Git 저장소다.

- 모든 수정·빌드 설정·문서·테스트는 이 저장소 내부에 작성한다. 상위 backend/frontend/mobile/docs/qa는 읽기 전용 참고 자료이며 상위 Git 메타데이터도 수정하지 않는다.
- develop이 존재하면 develop에서 작업 브랜치를 분기한다. main/develop에 직접 작업·푸시하지 않는다. 최초 생성 시 develop은 아직 커밋이 없는 브랜치였으며 codex/kmp-mobile에서 시작했다.
- 커밋은 `<type>: 한국어 변경 내용`, PR은 `[Mobile][작업 종류] 작업 내용` 형식이다. 사용자 승인 없이 원격에 push하지 않는다.
- 공통 Kotlin UI/로직은 composeApp/src/commonMain, 플랫폼 API는 androidMain/iosMain 및 iosApp에 둔다. 기능별 패키지와 간결한 책임 분리를 유지한다.
- API 변경은 백엔드 Controller/DTO와 개발 서버 OpenAPI를 함께 확인한다. docs/API_CONTRACT.md에 근거와 차이를 기록한다. JSON 필드·상태·메서드를 추측하지 않는다.
- Debug는 개발 API, Release는 운영 API다. 운영 API로 테스트용 생성·수정·삭제 요청을 보내지 않는다. 실제 개발 API 연동을 우선하며 MockEngine/Fixture는 단위 테스트에만 둔다. 별도 Mock 서버나 앱 데모 모드를 만들지 않는다. 실서버 테스트는 별도 익명 회원의 고유 QA 데이터만 생성하고 정리 결과를 확인한다.
- 토큰·키·실제 .env 내용은 출력·문서화·커밋하지 않는다. Refresh Cookie와 회원 ID·origin을 Android Keystore와 iOS Keychain에 보관하고 Access Token은 메모리에서만 사용한다. 저장된 세션은 refresh로 복원하며 복원 실패를 자동 신규 익명 회원 생성으로 대체하지 않는다.
- 변경 범위의 테스트와 실제 플랫폼 빌드를 실행한다. 실패·미검증은 docs/BUILD_REPORT.md와 docs/IMPLEMENTATION_STATUS.md에 사실대로 기록한다.
- 최소 검사: ./gradlew :composeApp:testDebugUnitTest :composeApp:assembleDebug :composeApp:lintDebug. macOS/Xcode에서는 iOS Simulator 프레임워크 및 Xcode 앱도 빌드한다.
- 기능·실행법 변경 시 README와 관련 docs도 수정한다. AGENTS.md와 CLAUDE.md는 항상 동일하게 유지한다.
- 작업 단위마다 docs/WORK_LOG.md에 변경 내용·검증일과 결과·남은 확인 항목을 기록하고 기능별 커밋으로 보존한다. 원격 push는 사용자 승인이 있는 범위에서 수행한다.

## 문서 포맷팅

상위 팀 저장소는 읽기 전용이므로 이 개인 저장소 루트에서 문서 검사를 실행한다. `.prettierrc.json`은 팀 설정과 동일하며 Prettier 3.9.6을 고정한다. 상위 저장소에서 일괄 쓰기 명령을 실행하지 않는다.

```sh
npm ci
npm run format:docs
npm run format:docs:check
```

Markdown 작성·수정 뒤 format:docs를 실행하고 diff를 확인한다. PR 생성·갱신 전 format:docs:check를 통과시킨다. AGENTS.md와 CLAUDE.md의 내용은 동일하게 유지한다.
