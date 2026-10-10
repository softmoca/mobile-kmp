# 확인한 API 계약

확인일: 2026-10-10 (Asia/Seoul). 신규 앱의 연결 계약을 기록하는 문서이며 상위 팀 저장소를 수정하지 않는다.

## 확인 근거와 연결 대상

- 개발: `https://dev-api.jachwi-sunbae.kr` (Debug).
- 운영: `https://api.jachwi-sunbae.kr` (Release). 운영 서버에 검증용 생성·수정·삭제 요청을 보내지 않았다.
- 개발 서버 `GET /v3/api-docs` → **200**, 제목 `자취 선배 API`, API 표기 버전 `v1`.
- 개발 서버 `GET /actuator/info` → **200**, 배포 애플리케이션 버전 `1.1.0`, 커밋 `fb5e371d7b56c2dee0df82d87c01205be8cf5a3f`.
- 읽기 전용으로 확인한 상위 저장소 HEAD도 같은 커밋이다. 아래 경로·메서드·주요 DTO 필드는 배포 OpenAPI와 현재 Controller/DTO 양쪽에서 확인했다.
- 최초 제한된 셸에서 DNS 조회가 실패했지만 네트워크 승인 후 공개 문서와 배포 정보를 정상 조회했다. 이것은 인증 기능의 실사용 검증을 뜻하지 않는다. 실제 실행 검증 범위는 `BUILD_REPORT.md`를 참조한다.

참고 소스: 상위 `backend/src/main/java/com/jachwisunbae/{auth,member,property,checklist,map}/controller`, 각 DTO, `common/web/ApiResponse.java`, `common/web/error/*`, `common/exception/errorcode/ErrorCode.java`, `auth/service/AuthService.java`, `property/service/PropertyChecklistService.java`, `checklist/service/UserChecklistService.java`, `property/entity/photo/PhotoFile.java`, `map/service/MapService.java`. 웹의 `frontend/src/features/{auth,property,checklist,map}/api`도 교차 확인했다.

## 발견한 차이와 선택

1. 개발 Swagger의 `servers[0].url`은 `http://dev-api.jachwi-sunbae.kr`로 표시된다. 실제 HTTPS 문서 접근은 정상이다. 앱은 명시적인 HTTPS 빌드 설정을 사용하며 HTTP·사용자 정보 포함 origin은 거부한다.
2. 웹 API 모듈에는 `GET /api/properties/export.csv`가 남아 있지만 배포 OpenAPI 및 현재 `PropertyExportController`에는 PDF API만 있다. CSV를 추측해서 구현하지 않는다.
3. 현재 사용자 체크리스트 생성·수정은 `systemCheckItemId` 목록만 받는다. 임의 질문 텍스트 생성 API는 없다. 앱은 시스템 항목 선택과 순서 변경을 제공한다. 과거 데이터에 시스템 항목 ID가 null일 수 있어 응답 DTO는 이를 허용한다.
4. 일반 API 컨벤션 문서의 수정 예시는 PATCH지만 현재 매물 기본 정보는 **PUT 전체 교체**다. 앱은 실제 Controller를 따른다.
5. 생성 응답은 `photoCount`, `representativePhoto`가 없고 상세 응답은 두 필드가 있다. 공통 DTO에서는 생성에만 적용할 기본값을 두었다. 수정 응답은 사진·진행률 없는 별도 DTO다.
6. 로그인 회원 객체의 식별자는 **memberId**, 현재 회원 조회의 식별자는 **id**다. 서로 다른 DTO로 읽고 인증 상태에만 `MemberDto`로 정규화한다.
7. 새 매물 생성 응답의 `overallProgress`는 0으로 작성된다. 서비스는 최초 두 단계 기본/선호 체크리스트를 별도로 적용한다. 실제 진행 현황은 목록·상세·체크 현황을 다시 조회하여 사용한다.

## 공통 응답·인증

JSON 성공: `{"code":"SUCCESS","message":"...","data":...}`. 오류: `{"code":"...","message":"...","errors":[{"field":"...","reason":"..."}]}`. `errors`는 보통 빈 배열이다. `204` 삭제·대표 사진 변경 응답과 사진/PDF 바이너리는 envelope가 없다.

인증은 `Authorization: Bearer <accessToken>`. 로그인 요청에는 기존 토큰을 보내지 않는다. 토큰은 Android Keystore 기반 암호화 저장소/iOS Keychain에만 저장하고 비밀번호는 저장하지 않는다. 앱 시작 시 저장된 토큰으로 `GET /api/members/me`를 검증한다. 일시적 네트워크 오류 시 토큰을 보존하고 재시도를 제공한다. Bearer를 사용한 요청의 401은 메모리·저장소 토큰을 지우고 로그인으로 돌려보낸다. 닉네임 로그인 실패의 401은 서버의 닉네임·비밀번호 안내를 표시한다. 403은 토큰 만료로 간주하지 않는다. 이전 토큰의 지연된 401은 새 로그인 토큰을 삭제하지 않는다.

확인된 refresh·서버 logout API는 없다. 자동 refresh를 만들지 않으며 로그아웃은 로컬 토큰 제거다. `expiresIn`을 수신하지만 JWT 내용을 신뢰하여 서버 인증을 대체하지 않고 401을 최종 만료 판정으로 사용한다. 네트워크 오류에는 `NETWORK_ERROR`, 로컬 입력 오류에는 `CLIENT_VALIDATION`, 잘못된 응답에는 `INVALID_RESPONSE`, 보안 저장소 실패에는 `SECURE_STORAGE_ERROR`라는 앱 내부 코드를 사용한다. 코루틴 취소는 네트워크 오류로 변환하지 않는다. 자동 mutation 재시도를 하지 않는다.

## 인증·회원

| 메서드·경로 | 요청 | 성공 응답 data |
|---|---|---|
| POST `/api/auth/nickname` | `nickname`, nullable `password` | `accessToken`, `tokenType`, `expiresIn`(초), `newMember`, `member:{memberId,name,passwordProtected}` |
| GET `/api/members/me` | Bearer | `{id,name,passwordProtected}` |

닉네임은 trim 후 1~30자. 선택 비밀번호는 4~30자이며 앞뒤 공백을 보존한다. null/빈 비밀번호는 공유 회원 방식이다. 보호 회원과 공유 회원의 의미를 혼동하지 않아야 한다. 처음 사용하는 닉네임 로그인은 **회원 생성**이므로 단순 읽기 검증이 아니다.

## 매물·메모

| 메서드·경로 | 내용 | 성공 |
|---|---|---|
| GET `/api/properties` | 현재 회원 전체 목록, 서버 검색·페이지 파라미터 없음 | 200 `{totalCount,items:[...]}` |
| POST `/api/properties` | 기본 정보 생성 | 201 생성 매물 |
| GET `/api/properties/{id}` | 상세·사진·진행 현황 | 200 상세 매물 |
| PUT `/api/properties/{id}` | 기본 정보 전체 교체 | 200 수정 기본 정보 |
| DELETE `/api/properties/{id}` | 사진·체크·메모 종속 정보 포함 삭제 | 204 |
| GET `/api/properties/{id}/memo` | 자유 메모 조회 | 200 `{propertyId,freeMemo}` |
| PUT `/api/properties/{id}/memo` | `{freeMemo}` (빈 문자열은 비우기) | 200 `{propertyId,freeMemo}` |

생성/수정 필드: 필수 `name`(1~30), `depositAmount`·`monthlyRentAmount`(원 단위 Long, 0 이상); 선택 `address`(최대 255), `latitude`·`longitude`(함께 존재/부재), `availableMoveInDate`(YYYY-MM-DD), `maintenanceFeeAmount`(원, 0 이상), `visitScheduledAt`(시간대 없는 LocalDateTime), `discoverySource`(최대 500), `roomOptions`, `utilityOptions`. 전체 교체에서 null을 명시하여 선택 필드 삭제를 지원한다. 주소를 비우면 서버가 좌표도 제거한다. 금액을 Double로 계산하지 않는다. 회원당 최대 30개는 서버가 강제한다.

방 옵션: `AIR_CONDITIONER`, `REFRIGERATOR`, `WASHING_MACHINE`, `SINK`, `GAS_STOVE`, `MICROWAVE`, `SHOE_CABINET`, `WARDROBE`, `BED`, `DESK`, `TV`, `INDUCTION`. 관리비 포함 공과금: `WATER`, `ELECTRICITY`, `GAS`, `INTERNET`.

목록 항목: `{id,name,depositAmount,monthlyRentAmount,discoverySource,address,latitude,longitude,photoCount,representativePhoto,overallProgress,stages}`. 상세는 전체 기본 정보에 `photoCount,photos,representativePhoto,overallProgress,createdAt,updatedAt`을 포함한다. `createdAt/updatedAt`은 ISO Instant, 입주·방문은 각각 날짜/로컬 일시다. `updatedAt`은 현재 배포 DTO 설명상 created_at과 같은 값이므로 동기화 버전/낙관적 잠금으로 사용하지 않는다.

진행 현황: `{totalCount,completedCount,goodCount,cautionCount,unconfirmedCount,progressRate}`. 상태 완료 수는 GOOD+CAUTION이며 비율은 서버 정수 값이다. 총 0이면 진행률 0이다. 메모 최대 2,000자.

## 사진

| 메서드·경로 | 계약 |
|---|---|
| GET `/api/properties/{p}/photos` | `{propertyId,totalCount,items}` |
| POST `/api/properties/{p}/photos` | multipart `file` 한 장, 201 사진 객체 |
| GET `/api/properties/{p}/photos/{photoId}` | 인증된 이미지 바이트, 실제 image Content-Type |
| DELETE `/api/properties/{p}/photos/{photoId}` | 204 |
| PUT `/api/properties/{p}/photos/{photoId}/representative` | 본문 없음, 204 |

사진 객체: `{id,propertyId,url,contentType,sizeBytes,representative,createdAt}`. 상세에 포함된 사진에는 `propertyId`가 없다. 대표 사진 요약은 `{id,url,contentType}`. URL은 `/api/properties/.../photos/...`의 인증 경로이며 공개 스토리지 URL이 아니다. 앱은 외부 URL에 Bearer를 붙이지 않고 검증한 ID로 API origin의 바이트를 가져온다.

허용 MIME: JPEG/PNG/WebP/HEIC/HEIF. 서버 `PhotoFile`은 1바이트~5MiB와 콘텐츠 형식을 검증한다. 앱은 업로드 전에 크기·MIME을 확인하며 실제 파일 바이트 형식 검증은 서버가 맡는다. 저장 파일명을 multipart 헤더에 안전하게 넣기 위해 경로/제어 문자/따옴표를 제거한다.

## 사용자 체크리스트·매물 적용

단계는 `ON_SITE`, `PRE_CONTRACT`; 항목 유형은 `CORE`, `OPTIONAL`; 체크 상태는 `UNCONFIRMED`, `GOOD`, `CAUTION`.

| 메서드·경로 | 요청·응답 |
|---|---|
| GET `/api/check-items?stage=...&query=...` | stage 필수, query 선택 최대200; `[{id,stage,itemType,question}]` |
| GET `/api/checklists?stage=...` | stage 선택; `{totalCount,items:[{id,name,stage,itemCount}]}` |
| POST `/api/checklists` | `{name,stage,items:[{systemCheckItemId}]}`, 201 상세 |
| GET `/api/checklists/{id}` | `{id,name,stage,itemCount,items}` |
| PUT `/api/checklists/{id}` | `{name,items:[{systemCheckItemId}]}` 전체 교체, 단계는 변경하지 않음 |
| DELETE `/api/checklists/{id}` | 204, 서버 논리 삭제 |
| GET `/api/properties/{p}/checklists` | `{propertyId,overallProgress,stages}` |
| GET `/api/properties/{p}/checklists/{c}` | 적용 스냅샷 `{id,propertyId,sourceChecklistId,checklistName,stage,items}` |
| PUT `/api/properties/{p}/checklists/{stage}` | `{sourceType:"USER",checklistId}` 또는 `{sourceType:"SYSTEM_DEFAULT",checklistId:null}` |
| PATCH `/api/properties/{p}/checklists/{c}/items/{i}/status` | `{status}` → `{item:{id,status}}` |
| PATCH `/api/properties/{p}/checklists/{c}/items/{i}/memo` | `{memo}` 최대500, 빈 문자열 허용 → `{item:{id,memo}}` |

사용자 체크리스트 상세 항목: `{id,systemCheckItemId,itemType,question,displayOrder,active}`. 적용 항목: `{id,systemCheckItemId,question,displayOrder,status,memo}`. `systemCheckItemId`는 현재 생성에는 필수지만 과거 데이터 응답에서는 null을 허용한다. 목록 `stages`와 현황 `stages` 항목은 `{stage,applied,propertyChecklistId,checklistName,sourceChecklistId,progress}`이며 미적용 단계의 ID/이름은 null이다.

사용자 이름 trim 후 1~30자, 항목 1~30개, 중복 금지, 요청 순서가 표시 순서다. 같은 단계 시스템 항목만 사용한다. 비활성 항목은 기존 체크리스트에 있던 항목을 유지할 수 있지만 새로 추가할 수 없다. 시스템 기본은 해당 단계의 활성 CORE 항목이다. 적용/교체는 스냅샷이며 공통 systemCheckItemId의 상태·메모를 승계한다. 사용자 체크리스트 수정이 이미 적용된 스냅샷을 자동 수정하지 않는다.

## 주소·주변 시설

모든 좌표는 WGS84. 위도 -90~90, 경도 -180~180, 유한 숫자다. 앱은 서버 반환 좌표/집계를 표시하고 가상의 시설을 생성하지 않는다.

| 메서드·경로 | 계약 |
|---|---|
| GET `/api/maps/geocode?query=...` | 검색어 필수 최대200, `[{roadAddress,jibunAddress,latitude,longitude}]` |
| GET `/api/maps/reverse-geocode?latitude=...&longitude=...` | 주소 객체 한 개, 도로명주소 없으면 404 `MAP_ADDRESS_NOT_FOUND` |
| GET `/api/maps/nearby?latitude=...&longitude=...&radius=...&categories=...` | radius 500/1000/2000m, categories 쉼표 구분/선택 |

카테고리: `HOSPITAL`, `TRANSPORT`, `SCHOOL`, `CONVENIENCE`, `AGENCY`. 주변 응답: `{center:{latitude,longitude},radius,counts:{카테고리:개수},places:[{providerPlaceId,name,category,address,latitude,longitude,distanceMeters}]}`. `counts`는 반환된 장소 기준이며 지도 전체 시설의 완전한 총량이 아니다. 외부 공급자 장애는 `MAP_PROVIDER_UNAVAILABLE`로 표시한다.

## 비교 PDF

`POST /api/properties/export.pdf`에 `{"propertyIds":[1,2]}`. 소유 매물의 서로 다른 양수 ID 2~5개. 응답은 200 `application/pdf`, `Content-Disposition: attachment; filename="jachwi-sunbae-property-comparison.pdf"`이다. 앱은 Content-Type·`%PDF-` 시작 바이트를 확인한 후 플랫폼 미리보기/저장/공유로 전달한다. 사용자 기록을 비교하며 서버가 점수·추천을 생성하지 않는다. 이전 `POST /comparison-views`는 현재 폐기 예정인 204 무동작 경로이므로 신규 앱에서 호출하지 않는다.

## 오류 처리와 검증 범위

대표 실제 서버 코드: `INVALID_REQUEST`, `NICKNAME_INVALID`, `NICKNAME_PASSWORD_INVALID`, `NICKNAME_AUTHENTICATION_FAILED`, `ACCESS_TOKEN_INVALID`, `MEMBER_NOT_FOUND`, `PROPERTY_INPUT_INVALID`, `PROPERTY_LOCATION_INVALID`, `PROPERTY_NOT_FOUND`, `PROPERTY_LIMIT_EXCEEDED`, `PROPERTY_MEMO_INVALID`, `PROPERTY_CHECKLIST_STAGE_MISMATCH`, `PROPERTY_CHECKLIST_NOT_FOUND`, `PROPERTY_CHECKLIST_ITEM_NOT_FOUND`, `PROPERTY_CHECK_RESULT_INVALID`, `PROPERTY_COMPARISON_EXPORT_FAILED`, `INVALID_SYSTEM_CHECK_ITEM`, `USER_CHECKLIST_NAME_INVALID`, `CHECKLIST_NOT_FOUND`, `CHECKLIST_ITEMS_INVALID`, `CHECKLIST_INACTIVE_ITEM_NOT_ALLOWED`, `DUPLICATE_CHECK_ITEM`, `CHECKLIST_ITEM_COUNT_OUT_OF_RANGE`, `PHOTO_NOT_FOUND`, `PHOTO_LIMIT_EXCEEDED`, `PHOTO_CONTENT_TYPE_UNSUPPORTED`, `PHOTO_FILE_SIZE_INVALID`, `PHOTO_FILE_TOO_LARGE`, `PHOTO_FILE_READ_FAILURE`, `PHOTO_STORAGE_FAILURE`, `MAP_QUERY_INVALID`, `MAP_ADDRESS_NOT_FOUND`, `MAP_PROVIDER_UNAVAILABLE`.

HTTP 상태는 서버 예외 타입이 정한다. 입력·업무 규칙 400, 인증401, 권한403, 없음404, 내부500, 외부 공급자502, 업로드 한도413. 코드만으로 HTTP 상태를 추정하지 않는다. 앱은 실제 응답 `message`와 필드별 오류를 보존한다. 서버 JSON 오류가 아니면 안전한 일반 오류 메시지를 표시한다. 상세 내부 응답이나 JWT를 UI·로그에 출력하지 않는다.

공통 MockEngine 테스트는 인증 성공/복원/만료/지연401, 빈 목록, 연결 실패와 토큰 보존·재시도, 취소, 잘못된 성공 envelope, 필드 오류, 메모 PUT/삭제204, PDF 요청/잘못된 MIME, 지도 쿼리, 비교·사진 제한을 검증한다. 보안 저장소의 읽기/쓰기 실패, 로그아웃을 시작한 화면 코루틴이 취소되어도 토큰 제거를 끝내는 동작도 검증한다. 순수 단위 테스트는 날짜/시간·윤년, 금액·좌표 쌍, 비밀번호 공백, 체크리스트 중복/개수, 비교 선택 순서/최대5개를 검증한다.

2026-10-10 Android JVM 실행에서 `ApiClientTest` **19/19**, `InputValidationTest` **5/5** 통과를 JUnit XML로 확인했다. 실제 개발 API 검증을 명시적으로 켠 opt-in 실행에서 `DevApiSmokeTest` **2/2**도 통과했다. 이후 일반 로컬 실행에서는 이 외부 API 테스트 2개가 의도적으로 제외된다. 새 임의 닉네임·비밀번호의 별도 테스트 회원만 사용했다. 확인한 범위는 로그인/복원/빈 목록, 매물 CRUD, 자유 메모, 사용자 체크리스트 CRUD, 적용·체크 상태·항목 메모, 사진 업로드/조회/대표 지정/삭제, PDF(63,280바이트), 주소 검색/역지오코딩/반경500m 주변 조회(173개 반환 장소)다. 테스트가 만든 매물·체크리스트는 finally 정리 경로가 있고 정상 실행에서는 삭제와 빈 목록도 확인한다. 회원 탈퇴 API가 없으므로 생성한 테스트 회원은 서버에 남는다. 테스트 자격증명은 코드·로그에 저장하지 않는다. 이 API 검증은 실제 모바일 기기의 사진 선택·위치 권한·파일 공유 UX 검증을 대체하지 않는다. 전체 빌드·UI 테스트 및 최신 실행 결과는 `BUILD_REPORT.md`를 참조한다.
