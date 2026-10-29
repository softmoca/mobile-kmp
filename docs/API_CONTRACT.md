# 확인한 API 계약

## 식비 절약 API 추가 확인 — 2026-10-29

dev 배포 커밋 `fb7144a51757ec969bab16b3f048c970a3625d05`, React 기준 develop `021267b55ac699d02ff0ccbf0a24f7da7fa73e0e`. `/actuator/info`·`/v3/api-docs` 및 실제 인증 응답을 확인했다. [검증 JSON](verification/cheap-meals-api-2026-10-29.json)과 `npm run verify:cheap-meals-api`로 재현한다.

- `GET /api/promotions?brand=EMART24|SEVEN_ELEVEN`: `data {brand,count,collectedAt,sourceUrl,products}`. 상품은 `{name,price,imageUrl,category,promotionType}`. price는 원 단위 정수이며 행사 가격을 임의 환산하지 않는다. category는 MEAL/SNACK/BEVERAGE/HOUSEHOLD_GOODS/UNKNOWN, promotionType은 ONE_PLUS_ONE/TWO_PLUS_ONE/THREE_PLUS_ONE/SALE/PICK_AND_MIX다. collectedAt은 오프셋 포함 시각 또는 null. 브랜드별 전체 목록이고 서버 검색/정렬/페이지네이션은 없다.
- `GET /api/delivery-discount-offers`: data 배열. `{id,platform,brandName,discountAmount,minOrderAmount,startDate,endDate,conditions,lastVerifiedAt}`. platform은 BAEMIN/COUPANG_EATS. 최소 주문/시작일/종료일/조건은 nullable, lastVerifiedAt은 한국 로컬 시각으로 반환된다. 주문 URL이 없으므로 프론트에서 만들지 않는다.
- `GET /api/maps/good-price-stores`: 아래 기존 업소 DTO 유지. 음식점 외 업종을 포함하며 menu.price는 숫자가 아닌 원본 문자열/null이다.
- 네 조회 모두 미인증 401, 익명 로그인·refresh 후 200. 시청 1km 22곳 / 이마트24 2,337개 / 세븐일레븐 0개·collectedAt=null / 배달 108개. 0건과 인증/서버 오류를 구분한다. 모든 필드는 공통 responseParsers와 인증 apiRequest로 파싱한다.
- WKWebView의 동일 HTTPS origin 프록시에서 네 endpoint와 Secure/HttpOnly refresh를 별도 XCTest로 검증했다. 토큰/회원 ID는 기록하지 않는다. 운영 API는 사용하지 않는다.

확인일: 2026-10-27 (Asia/Seoul). 현재 개발 서버와 앱의 연결 계약을 기록하는 문서이며 상위 팀 저장소를 수정하지 않는다.

## 알뜰 지도 추가 확인 — 2026-10-27 저녁

이번 Preview의 기준 develop과 실제 `/actuator/info` 커밋은 모두 `43b8d66352564e16af5db5c63585f565b7756056`이다. `/actuator/health`는 UP, `/v3/api-docs`는 200이다. 아래 기존 KMP 검증 시점의 SHA와 구분한다.

`GET /api/maps/good-price-stores?latitude=<number>&longitude=<number>&radius=<500|1000|2000>`는 Bearer 인증이 필요하다. 미인증은 401, 지원하지 않는 반경은 400이다. 성공 envelope의 `data`는 `center {latitude, longitude}`, `radius`, `count`, `dataAsOf: string|null`, `stores`다. 업소는 `id`, `name`, `businessType`, `phone: string|null`, `address`, `latitude`, `longitude`, `distanceMeters`, `menus [{name, price: string|null}]`를 가진다. 메뉴 가격은 원본 문자열이며 범위 가격과 null을 그대로 보존한다.

최신 익명 login과 Secure/HttpOnly Refresh Cookie로 같은 회원을 복원한 뒤 실제 인증 조회를 수행했다. 기준일 2026-06-30의 시청 주변 500m/1km/2km 결과는 2/22/75건, 강남역은 7/29/66건이었다. 빈 지역은 0건이며 Mock으로 대체하지 않는다. [재현 명령과 응답 증거](THRIFTY_PREVIEW.md#실제-dev-api-검증)를 참고한다.

## 확인 근거와 연결 대상

- 개발: `https://dev-api.jachwi-sunbae.kr` (Debug).
- 운영: `https://api.jachwi-sunbae.kr` (Release). 운영 서버에 검증용 생성·수정·삭제 요청을 보내지 않았다.
- 개발 서버 `GET /v3/api-docs` → **200**, 제목 `자취 선배 API`, API 표기 버전 `v1`.
- 개발 서버 `GET /actuator/info` → **200**, 배포 애플리케이션 버전 **2.1.0**, 커밋 `7e3fa9aa18f67bc1db75da92fd10df660cf3ca9f`.
- 계약 비교 시점에 읽기 전용으로 확인한 상위 저장소 HEAD는 `e7494a02b6a8c3081807b8b8755e7fec8b1e5c69`다. 배포 SHA와 다르므로 현재 Controller/DTO를 참고하되 배포 OpenAPI와 실제 dev 응답을 연결 기준으로 삼았다. 상위 저장소 fetch·수정·push는 하지 않았다.
- 2026-10-10의 v1.1.0 닉네임 계약은 현재 앱에서 사용하지 않는다. `/api/auth/nickname`이 제거되고 `/api/auth/login`, `/api/auth/refresh` 및 익명 회원 DTO로 바뀌었다.

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

일반 보호 API는 `Authorization: Bearer <accessToken>`을 사용한다. Access Token은 메모리에서만 사용한다. Refresh Cookie는 Android Keystore/iOS Keychain에 회원 ID·origin과 함께 버전 2 JSON으로 보관하고 인증 endpoint에만 전달한다. 사진/PDF 등 일반 API에는 Cookie를 보내지 않는다. HTTP 로그에 인증 정보나 응답 본문을 출력하지 않는다.

## 인증·회원

| 메서드·경로              | 요청                                                                              | 성공 응답 data                                                                                |
| ------------------------ | --------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------- |
| POST `/api/auth/login`   | 본문 없음. 유효 Refresh Cookie가 있으면 기존 회원, 없거나 무효이면 신규 익명 회원 | `accessToken`, `tokenType`, `expiresIn`(초), `newMember`, `member:{memberId}` 및 `Set-Cookie` |
| POST `/api/auth/refresh` | `Cookie: refresh_token=...`. 없거나 만료·무효이면 401 `REFRESH_TOKEN_INVALID`     | 위 Login DTO, 동일 회원, `newMember=false`, `Set-Cookie`                                      |
| GET `/api/members/me`    | Bearer                                                                            | `{id}`                                                                                        |

응답 쿠키는 `refresh_token`, Secure, HttpOnly, Path `/api/auth`다. 현재 서버는 기존 Refresh Token 값의 만료를 연장하며, 앱은 응답의 Set-Cookie가 교체되어도 저장하도록 구현했다. 별도 서버 logout·회원 삭제·닉네임 복원 API는 확인되지 않았다.

`/login`은 읽기 요청이 아니다. 무효 쿠키를 전달해도 새 회원을 만들 수 있으므로 앱은 **실제 빈 저장소의 최초 실행** 또는 **사용자가 새 기록을 명시적으로 확인한 경우**에만 호출한다. 저장된 회원은 `/refresh`로만 복원하고 응답 회원 ID가 같은지 검사한다. 구버전 Access Token 전용 값, 저장소 손상, 네트워크 장애를 빈 저장소로 해석하지 않는다. 마이에는 닉네임·비밀번호·로그아웃 UI가 없다.

일반 요청의 401은 같은 세션에서 refresh 한 번·원 요청 재전송 한 번으로 제한한다. 동시 401은 갱신 결과와 실패를 공유한다. 403·일반 네트워크 실패는 자동 재전송하지 않는다. refresh의 401과 갱신 후 두 번째 401은 메모리 인증을 종료하고 재실행 시에도 사용자 확인이 필요한 marker를 보관한다. 서버에 있는 기존 기록을 삭제하는 기능은 아니다.

앱 내부 코드에는 `NETWORK_ERROR`, `CLIENT_VALIDATION`, `INVALID_RESPONSE`, `SECURE_STORAGE_ERROR`, `LEGACY_SESSION`, `SESSION_RESET_REQUIRED`, `SESSION_CHANGED`, `AUTH_REQUIRED`가 있다. 인증 교환/보안 저장은 화면 취소에 의해 중간 저장이 끊기지 않도록 처리하며 인증 네트워크 대기는 30초 상한이다. 보호 API의 일반 취소는 그대로 전파한다.

## 매물·메모

| 메서드·경로                     | 내용                                                | 성공                           |
| ------------------------------- | --------------------------------------------------- | ------------------------------ |
| GET `/api/properties`           | 현재 회원 전체 목록, 서버 검색·페이지 파라미터 없음 | 200 `{totalCount,items:[...]}` |
| POST `/api/properties`          | 기본 정보 생성                                      | 201 생성 매물                  |
| GET `/api/properties/{id}`      | 상세·사진·진행 현황                                 | 200 상세 매물                  |
| PUT `/api/properties/{id}`      | 기본 정보 전체 교체                                 | 200 수정 기본 정보             |
| DELETE `/api/properties/{id}`   | 사진·체크·메모 종속 정보 포함 삭제                  | 204                            |
| GET `/api/properties/{id}/memo` | 자유 메모 조회                                      | 200 `{propertyId,freeMemo}`    |
| PUT `/api/properties/{id}/memo` | `{freeMemo}` (빈 문자열은 비우기)                   | 200 `{propertyId,freeMemo}`    |

생성/수정 필드: 필수 `name`(1~30), `depositAmount`·`monthlyRentAmount`(원 단위 Long, 0 이상); 선택 `address`(최대 255), `latitude`·`longitude`(함께 존재/부재), `availableMoveInDate`(YYYY-MM-DD), `maintenanceFeeAmount`(원, 0 이상), `visitScheduledAt`(시간대 없는 LocalDateTime), `discoverySource`(최대 500), `roomOptions`, `utilityOptions`. 전체 교체에서 null을 명시하여 선택 필드 삭제를 지원한다. 주소를 비우면 서버가 좌표도 제거한다. 금액을 Double로 계산하지 않는다. 회원당 최대 30개는 서버가 강제한다.

방 옵션: `AIR_CONDITIONER`, `REFRIGERATOR`, `WASHING_MACHINE`, `SINK`, `GAS_STOVE`, `MICROWAVE`, `SHOE_CABINET`, `WARDROBE`, `BED`, `DESK`, `TV`, `INDUCTION`. 관리비 포함 공과금: `WATER`, `ELECTRICITY`, `GAS`, `INTERNET`.

목록 항목: `{id,name,depositAmount,monthlyRentAmount,discoverySource,address,latitude,longitude,photoCount,representativePhoto,overallProgress,stages}`. 상세는 전체 기본 정보에 `photoCount,photos,representativePhoto,overallProgress,createdAt,updatedAt`을 포함한다. `createdAt/updatedAt`은 ISO Instant, 입주·방문은 각각 날짜/로컬 일시다. `updatedAt`은 현재 배포 DTO 설명상 created_at과 같은 값이므로 동기화 버전/낙관적 잠금으로 사용하지 않는다.

진행 현황: `{totalCount,completedCount,goodCount,cautionCount,unconfirmedCount,progressRate}`. 상태 완료 수는 GOOD+CAUTION이며 비율은 서버 정수 값이다. 총 0이면 진행률 0이다. 메모 최대 2,000자.

## 사진

| 메서드·경로                                               | 계약                                          |
| --------------------------------------------------------- | --------------------------------------------- |
| GET `/api/properties/{p}/photos`                          | `{propertyId,totalCount,items}`               |
| POST `/api/properties/{p}/photos`                         | multipart `file` 한 장, 201 사진 객체         |
| GET `/api/properties/{p}/photos/{photoId}`                | 인증된 이미지 바이트, 실제 image Content-Type |
| DELETE `/api/properties/{p}/photos/{photoId}`             | 204                                           |
| PUT `/api/properties/{p}/photos/{photoId}/representative` | 본문 없음, 204                                |

사진 객체: `{id,propertyId,url,contentType,sizeBytes,representative,createdAt}`. 상세에 포함된 사진에는 `propertyId`가 없다. 대표 사진 요약은 `{id,url,contentType}`. URL은 `/api/properties/.../photos/...`의 인증 경로이며 공개 스토리지 URL이 아니다. 앱은 외부 URL에 Bearer를 붙이지 않고 검증한 ID로 API origin의 바이트를 가져온다.

허용 MIME: JPEG/PNG/WebP/HEIC/HEIF. 서버 `PhotoFile`은 1바이트~5MiB와 콘텐츠 형식을 검증한다. 앱은 업로드 전에 크기·MIME을 확인하며 실제 파일 바이트 형식 검증은 서버가 맡는다. 저장 파일명을 multipart 헤더에 안전하게 넣기 위해 경로/제어 문자/따옴표를 제거한다.

## 사용자 체크리스트·매물 적용

단계는 `ON_SITE`, `PRE_CONTRACT`; 항목 유형은 `CORE`, `OPTIONAL`; 체크 상태는 `UNCONFIRMED`, `GOOD`, `CAUTION`.

| 메서드·경로                                                 | 요청·응답                                                                               |
| ----------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| GET `/api/check-items?stage=...&query=...`                  | stage 필수, query 선택 최대200; `[{id,stage,itemType,question}]`                        |
| GET `/api/checklists?stage=...`                             | stage 선택; `{totalCount,items:[{id,name,stage,itemCount}]}`                            |
| POST `/api/checklists`                                      | `{name,stage,items:[{systemCheckItemId}]}`, 201 상세                                    |
| GET `/api/checklists/{id}`                                  | `{id,name,stage,itemCount,items}`                                                       |
| PUT `/api/checklists/{id}`                                  | `{name,items:[{systemCheckItemId}]}` 전체 교체, 단계는 변경하지 않음                    |
| DELETE `/api/checklists/{id}`                               | 204, 서버 논리 삭제                                                                     |
| GET `/api/properties/{p}/checklists`                        | `{propertyId,overallProgress,stages}`                                                   |
| GET `/api/properties/{p}/checklists/{c}`                    | 적용 스냅샷 `{id,propertyId,sourceChecklistId,checklistName,stage,items}`               |
| PUT `/api/properties/{p}/checklists/{stage}`                | `{sourceType:"USER",checklistId}` 또는 `{sourceType:"SYSTEM_DEFAULT",checklistId:null}` |
| PATCH `/api/properties/{p}/checklists/{c}/items/{i}/status` | `{status}` → `{item:{id,status}}`                                                       |
| PATCH `/api/properties/{p}/checklists/{c}/items/{i}/memo`   | `{memo}` 최대500, 빈 문자열 허용 → `{item:{id,memo}}`                                   |

사용자 체크리스트 상세 항목: `{id,systemCheckItemId,itemType,question,displayOrder,active}`. 적용 항목: `{id,systemCheckItemId,question,displayOrder,status,memo}`. `systemCheckItemId`는 현재 생성에는 필수지만 과거 데이터 응답에서는 null을 허용한다. 목록 `stages`와 현황 `stages` 항목은 `{stage,applied,propertyChecklistId,checklistName,sourceChecklistId,progress}`이며 미적용 단계의 ID/이름은 null이다.

사용자 이름 trim 후 1~~30자, 항목 1~~30개, 중복 금지, 요청 순서가 표시 순서다. 같은 단계 시스템 항목만 사용한다. 비활성 항목은 기존 체크리스트에 있던 항목을 유지할 수 있지만 새로 추가할 수 없다. 시스템 기본은 해당 단계의 활성 CORE 항목이다. 적용/교체는 스냅샷이며 공통 systemCheckItemId의 상태·메모를 승계한다. 사용자 체크리스트 수정이 이미 적용된 스냅샷을 자동 수정하지 않는다.

## 주소·주변 시설

모든 좌표는 WGS84. 위도 -90~~90, 경도 -180~~180, 유한 숫자다. 앱은 서버 반환 좌표/집계를 표시하고 가상의 시설을 생성하지 않는다.

| 메서드·경로                                                                 | 계약                                                                   |
| --------------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| GET `/api/maps/geocode?query=...`                                           | 검색어 필수 최대200, `[{roadAddress,jibunAddress,latitude,longitude}]` |
| GET `/api/maps/reverse-geocode?latitude=...&longitude=...`                  | 주소 객체 한 개, 도로명주소 없으면 404 `MAP_ADDRESS_NOT_FOUND`         |
| GET `/api/maps/nearby?latitude=...&longitude=...&radius=...&categories=...` | radius 500/1000/2000m, categories 쉼표 구분/선택                       |

카테고리: `HOSPITAL`, `SCHOOL`, `CONVENIENCE`, `AGENCY`. 기존 `TRANSPORT`는 이 endpoint에서 제거되어 전송하면 400이다. 앱의 교통 필터는 별도 `GET /api/maps/bus-stops?latitude=...&longitude=...&radius=...`를 사용한다. 이 응답은 `{center,radius,count,busStops:[{nodeId,name,latitude,longitude,distanceMeters}]}`이며, 앱은 일반 주변 시설 결과와 합쳐 지도·집계를 표시한다. 버스정류장 ID는 일반 시설과 충돌하지 않게 구분하고 응답 count·중복 ID를 검사한다. 주변 응답: `{center:{latitude,longitude},radius,counts:{카테고리:개수},places:[{providerPlaceId,name,category,address,latitude,longitude,distanceMeters}]}`. `counts`는 반환된 장소 기준이며 지도 전체 시설의 완전한 총량이 아니다. 외부 공급자 장애는 `MAP_PROVIDER_UNAVAILABLE`로 표시한다.

## 비교 PDF

`POST /api/properties/export.pdf`에 `{"propertyIds":[1,2]}`. 소유 매물의 서로 다른 양수 ID 2~5개. 응답은 200 `application/pdf`, `Content-Disposition: attachment; filename="jachwi-sunbae-property-comparison.pdf"`이다. 앱은 Content-Type·`%PDF-` 시작 바이트를 확인한 후 플랫폼 미리보기/저장/공유로 전달한다. 사용자 기록을 비교하며 서버가 점수·추천을 생성하지 않는다. `POST /api/properties/comparison-views`는 현재 소스에서 행동 기록에 사용하는 경로다. 이번 작업은 사용자 행동 분석이 제외되어 해당 API를 호출하지 않는다.

## 오류 처리와 검증 범위

대표 실제 서버 코드: `INVALID_REQUEST`, `REFRESH_TOKEN_INVALID`, `ACCESS_TOKEN_INVALID`, `MEMBER_NOT_FOUND`, `PROPERTY_INPUT_INVALID`, `PROPERTY_LOCATION_INVALID`, `PROPERTY_NOT_FOUND`, `PROPERTY_LIMIT_EXCEEDED`, `PROPERTY_MEMO_INVALID`, `PROPERTY_CHECKLIST_STAGE_MISMATCH`, `PROPERTY_CHECKLIST_NOT_FOUND`, `PROPERTY_CHECKLIST_ITEM_NOT_FOUND`, `PROPERTY_CHECK_RESULT_INVALID`, `PROPERTY_COMPARISON_EXPORT_FAILED`, `INVALID_SYSTEM_CHECK_ITEM`, `USER_CHECKLIST_NAME_INVALID`, `CHECKLIST_NOT_FOUND`, `CHECKLIST_ITEMS_INVALID`, `CHECKLIST_INACTIVE_ITEM_NOT_ALLOWED`, `DUPLICATE_CHECK_ITEM`, `CHECKLIST_ITEM_COUNT_OUT_OF_RANGE`, `PHOTO_NOT_FOUND`, `PHOTO_LIMIT_EXCEEDED`, `PHOTO_CONTENT_TYPE_UNSUPPORTED`, `PHOTO_FILE_SIZE_INVALID`, `PHOTO_FILE_TOO_LARGE`, `PHOTO_FILE_READ_FAILURE`, `PHOTO_STORAGE_FAILURE`, `MAP_QUERY_INVALID`, `MAP_ADDRESS_NOT_FOUND`, `MAP_PROVIDER_UNAVAILABLE`.

HTTP 상태는 서버 예외 타입이 정한다. 입력·업무 규칙 400, 인증401, 권한403, 없음404, 내부500, 외부 공급자502, 업로드 한도413. 코드만으로 HTTP 상태를 추정하지 않는다. 앱은 실제 응답 `message`와 필드별 오류를 보존한다. 서버 JSON 오류가 아니면 안전한 일반 오류 메시지를 표시한다. 상세 내부 응답이나 JWT를 UI·로그에 출력하지 않는다.

공통 MockEngine은 쿠키 형식·범위·Access Token 비영속 저장, 최초 익명 진입, 기존 회원 refresh 복원, legacy·origin 불일치 보호, refresh 실패·동시401·지연응답 경합, 저장소 오류·취소, 요청 validation을 검증한다. UI 상태 테스트와 단위 Fixture는 앱의 실제 데이터 경로와 분리되어 있다.

실제 dev API 검사는 `RUN_DEV_API_SMOKE=1`일 때만 신규 익명 회원에서 실행한다. 새 ApiClient로 Refresh Cookie 복원, 동일 회원 갱신, 테스트 interceptor가 한 번 보낸 무효 Bearer의 실제 401 후 자동 갱신을 검사한다. 이어서 매물·메모·사용자 체크리스트·사진 CRUD, 적용 상태·메모·집계, PDF, 주소·주변 시설을 검증한다. 고유 `KMP QA` 이름을 사용하고 매물·체크리스트 삭제 및 빈 목록까지 확인한다. cleanup 실패는 테스트 실패로 처리하며 회원 삭제 API가 없어 익명 회원은 남는다. 최신 성공/실패 수치와 기기 실행 범위는 [BUILD_REPORT](BUILD_REPORT.md)를 참조한다.
