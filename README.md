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

데이터베이스를 직접 읽지 않습니다. 같은 SQL이 두 저장소에 생기면 한쪽만 고쳐져 답이 달라지기 때문에,
데이터는 항상 `mapservice-rest`에서 가져옵니다.

## 공개하는 API (12개, 모두 조회)

| 묶음 | 경로 | 내용 |
|---|---|---|
| 공공데이터 | `/v1/convenience-store`, `/v1/bus-stop`, `/v1/cctv`, `/v1/pharmacy`, `/v1/hospital`, `/v1/government-office` | 편의점·버스정류장·CCTV·약국·병원·관공서 (GeoJSON, `bbox`·`limit`) |
| 시설물 | `/v1/facilities`, `/v1/facilities/{totalId}`, `/v1/facility-icons` | 현장조사 시설물 목록·상세, 아이콘 설정 |
| 행정구역 | `/v1/admin-area/sido`, `/sgg`, `/emd` | 시·도 → 시·군·구 → 읍·면·동 코드와 지도 범위 |

**여는 범위는 `src/main/resources/catalog/api-catalog.json` 한 파일이 정합니다.** 화면에 보여 줄 문서와
중계 허용 목록이 같은 파일이라, 문서에만 있고 실제로는 안 되는 API가 생기지 않습니다. 새 API를 열려면
이 파일에 항목을 추가하면 됩니다(코드 수정 불필요).

닫아 둔 것: 내업 기록 등록·수정·삭제(쓰기), 내업 사진, 첨부 파일 중계(외부 계정 필요). 정의에 없는 경로는 404입니다.

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
| `openapi.upstream.connect-timeout-ms` | 3000 | 연결 제한 시간 |
| `openapi.upstream.read-timeout-ms` | 20000 | 응답 대기 시간(전국 데이터가 클 수 있어 넉넉히) |

비밀값은 없습니다. 운영 프로파일·Eureka 주소는 Helm에서 주입합니다.

## 현재 한계

- **API 키·사용량 제한이 아직 없습니다**(다음 단계). 지금은 누구나 호출할 수 있고 호출 기록도 남기지 않습니다.
- 조회(GET)만 엽니다. 쓰기 API를 열 계획은 없습니다.
- 응답은 원천 형식 그대로입니다(별도 가공·필드 이름 변경 없음).
