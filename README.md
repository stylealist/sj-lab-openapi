# sj-lab-openapi

sj-lab 플랫폼이 모아 둔 지도·시설물 데이터를 **외부에서 쓸 수 있게 열어 주는 서비스**입니다.
활용 페이지(`sj-lab-openapi-web`)가 이 서비스의 API 목록을 읽어 화면을 그리고, 실제 호출도 여기로 합니다.

- 운영 주소: `https://api.sj-lab.co.kr/open-api` (게이트웨이 경유, 배포 후)
- 로컬 주소: `http://localhost:8100/open-api`

## 무엇을 하나

| 일 | 설명 |
|---|---|
| API 목록 제공 | `GET /catalog` — 공개 API의 경로·설명·파라미터·예시를 JSON으로 내려줍니다 |
| 데이터 중계 | `GET /v1/...` — 요청을 검사해 `mapservice-rest`에서 데이터를 받아 그대로 전달합니다 |
| API 키 | `POST/GET/DELETE /keys` — 로그인한 사람이 자기 키를 발급·확인·폐기합니다 |
| 사용량·한도 | 키를 붙여 부른 호출을 기록하고 하루 한도를 넘으면 429를 돌려줍니다 |

데이터베이스를 직접 읽지 않습니다. 같은 SQL이 두 저장소에 생기면 한쪽만 고쳐져 답이 달라지기 때문에,
데이터는 항상 `mapservice-rest`에서 가져옵니다.

## 공개하는 API (12개, 모두 조회)

| 묶음 | 경로 | 내용 |
|---|---|---|
| 공공데이터 | `/v1/convenience-store`, `/v1/bus-stop`, `/v1/cctv`, `/v1/pharmacy`, `/v1/hospital`, `/v1/government-office` | 편의점·버스정류장·CCTV·약국·병원·관공서 (GeoJSON, **`bbox` 필수**·`limit` 선택) |
| 시설물 | `/v1/facilities`, `/v1/facilities/{totalId}`, `/v1/facility-icons` | 현장조사 시설물 목록·상세, 아이콘 설정 |
| 행정구역 | `/v1/admin-area/sido`, `/sgg`, `/emd` | 시·도 → 시·군·구 → 읍·면·동 코드와 지도 범위 |

**여는 범위는 `src/main/resources/catalog/api-catalog.json` 한 파일이 정합니다.** 화면에 보여 줄 문서와
중계 허용 목록이 같은 파일이라, 문서에만 있고 실제로는 안 되는 API가 생기지 않습니다. 새 API를 열려면
이 파일에 항목을 추가하면 됩니다(코드 수정 불필요).

**공공데이터 6종은 `bbox`(화면 범위)가 필수입니다**(카탈로그 v1.1, 2026-09-30). 없이 부르면
`400 MISSING_PARAMETER`입니다. 전국을 통째로 조립하면 응답이 수십 MB(실측 버스정류장 85MB·병원 61MB)가 되어
원천 호출이 읽기 시간(20초)을 넘기고, 외부 호출자 한 명이 서비스 전체를 흔들 수 있기 때문입니다.
`limit`은 선택이며 기본 3000·최대 20000이고, 상한을 넘으면 bbox 를 격자로 나눠 화면 전체에 고르게 퍼진 표본을 내려줍니다.

**브라우저에서 부를 수 있습니다.** 게이트웨이가 `/open-api/**` 에만 CORS 오리진을 열어 두어(2026-09-30)
남의 웹사이트에서도 호출됩니다 — `GET`·`OPTIONS` 만, 쿠키 없이(`allowCredentials: false`),
요청 헤더는 `Content-Type`·`X-API-Key` 만 허용합니다. 그 밖의 경로는 여전히 sj-lab 도메인만 허용됩니다.

닫아 둔 것: 내업 기록 등록·수정·삭제(쓰기), 내업 사진, 첨부 파일 중계(외부 계정 필요). 정의에 없는 경로는 404입니다.

## API 키

키 없이도 공개 API를 부를 수 있습니다. **키를 붙이면** 호출이 기록되고 하루 한도가 적용됩니다.

| 엔드포인트 | 설명 |
|---|---|
| `GET /keys/status` | 키 기능을 쓸 수 있는 상태인지(로그인 불필요) |
| `POST /keys` | 키 발급. 본문 `{"label":"이름"}`(선택). **키 원문은 이 응답에만** 담깁니다 |
| `GET /keys` | 내 키 목록 — 앞자리·이름·오늘 사용량·한도·마지막 사용 |
| `DELETE /keys/{keyId}` | 폐기(소프트 삭제) |

- 로그인 확인은 `sj-lab-authserver`의 `GET /auth/me`에 맡깁니다(JWT 서명 키를 복사해 두지 않기 위해).
  `Authorization: Bearer <토큰>`이 없거나 틀리면 401입니다.
- 키는 **원문을 저장하지 않습니다.** DB에는 SHA-256 해시와 앞 8자리만 남으므로 잃어버리면 새로 발급해야 합니다.
- 호출할 때는 헤더 `X-API-Key: <키>`를 권합니다(헤더를 못 넣는 곳에서는 `?apiKey=` 도 됩니다).
  `apiKey` 쿼리는 원천으로 전달되지 않습니다.
- 한 사람당 키 5개, 기본 하루 1,000회. 한도를 넘으면 `429 QUOTA_EXCEEDED`, 폐기·오타 키는 `401 INVALID_API_KEY`.

**표가 없거나 기능이 꺼져 있으면 키 API 만 503(`NOT_CONFIGURED`)이고, 공개 API 조회는 그대로 됩니다.**
표는 `api` 스키마(`api.openapi_api_key`, `api.openapi_api_usage`)에 두며, `sj-lab/db/api/openapi_api_key.sql`·`sj-lab/db/api/openapi_api_usage.sql`로 만듭니다. **DDL 실행은 DB 담당자가** 합니다.
표가 생기면 재기동 없이 60초 안에 인식합니다.

## 요청 검사

- 카탈로그에 없는 경로 → `404 UNKNOWN_API`
- 정의에 없는 파라미터 → `400 UNKNOWN_PARAMETER` (오타를 바로 알 수 있게 통과시키지 않습니다)
- 필수 파라미터 누락 → `400 MISSING_PARAMETER`
- 데이터 서버 연결 실패 → `502 UPSTREAM_UNAVAILABLE`
- 원천이 돌려준 400·404는 그대로 전달합니다(예: 없는 시설물 ID)

오류는 `{"error": {"code": "...", "message": "..."}}` 형식입니다.

## 실행

```bash
mvnw.cmd clean package                  # 빌드 (target/sj-lab-openapi.jar)
mvnw.cmd test                           # 테스트
java -jar target/sj-lab-openapi.jar --spring.profiles.active=local
```

- Eureka(8761)와 `mapservice-rest`가 떠 있어야 데이터 중계가 동작합니다. 기동 순서는 허브 저장소의
  `docs/dev-environment.md`를 따릅니다.
- 포트는 기본 `0`(랜덤)이며 게이트웨이를 통해 접근합니다. 따로 확인하려면 `--server.port=8110`처럼 지정하세요.
- context-path는 `/open-api`입니다.

## 설정

| 키 / 환경변수 | 기본값 | 설명 |
|---|---|---|
| `OPENAPI_UPSTREAM_BASE_URL` | `http://MAPSERVICE-REST` | 데이터 원천. Eureka 서비스 이름이면 클라이언트 로드밸런서로, 실제 주소(`http://localhost:8100`)면 그대로 호출합니다 |
| `OPENAPI_API_KEY_ENABLED` | `false` | 키 기능 사용 여부. 켜려면 아래 DB 값이 필요합니다 |
| `OPENAPI_API_KEY_DAILY_QUOTA` | `1000` | 새 키의 하루 한도 |
| `OPENAPI_DB_URL` · `OPENAPI_DB_USERNAME` · `OPENAPI_DB_PASSWORD` | 없음 | 키·사용량 저장용 DB. **저장소에 적지 말 것**(public) |
| `OPENAPI_DB_SCHEMA` | `api` | 키·사용량 표가 있는 스키마 |
| `OPENAPI_AUTH_BASE_URL` | `http://SJ-LAB-AUTHSERVER` | 로그인 확인을 맡길 주소 |
| `openapi.upstream.connect-timeout-ms` | 3000 | 연결 제한 시간 |
| `openapi.upstream.read-timeout-ms` | 20000 | 응답 대기 시간(전국 데이터가 클 수 있어 넉넉히) |

비밀값은 없습니다. 운영 프로파일·Eureka 주소는 Helm에서 주입합니다.

## 현재 한계

- **키는 아직 선택입니다.** 키 없이도 부를 수 있고, 그때는 기록되지 않습니다. 나중에 필수로 바꿀 수 있습니다.
- 개발 DB에 표가 아직 없어 키 기능은 꺼 둔 상태입니다(`sj-lab/db/<스키마>/*.sql` 실행 후 켭니다).
- 조회(GET)만 엽니다. 쓰기 API를 열 계획은 없습니다.
- 응답은 원천 형식 그대로입니다(별도 가공·필드 이름 변경 없음).
