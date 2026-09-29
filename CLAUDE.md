# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

sj-lab 저장소를 넘나드는 작업의 총괄 기준 저장소는 `C:\developer\workspace\mapservice-rest`입니다.
전체 구조·API 계약·배포 경로는 그 저장소의 `docs/system-architecture.md`, 로컬 포트·기동 순서는
`docs/dev-environment.md`를 따릅니다.

## 이 서비스의 역할

Eureka에 등록되는 Spring Boot 3.3.2 / Java 17 마이크로서비스(`sj-lab-openapi`, context-path `/open-api`)로,
공개 API 카탈로그를 내려주고 요청을 검사해 `mapservice-rest`로 중계합니다. 게이트웨이 경로는 `/open-api/**`입니다.

**요청 흐름**: `ApiProxyController`(`/v1/**`) → `ApiProxyService`(카탈로그 조회 → 파라미터 검사 → 원천 호출)
→ `RestTemplate` → `mapservice-rest`. 카탈로그는 `ApiCatalogService`가 기동 시 한 번 읽습니다.

## 반드시 지킬 것

- **공개 범위는 `src/main/resources/catalog/api-catalog.json` 한 파일이 정합니다.** 화면(활용 페이지)이 그리는
  문서와 중계 허용 목록이 같은 파일이라, 새 API를 열 때는 이 파일에만 항목을 추가하면 되고 컨트롤러를 늘리지 않습니다.
  거꾸로 **여기에 없는 경로·파라미터는 원천으로 올라가지 않습니다** — 이게 공개 범위의 경계이므로 검사를 우회하는
  코드를 넣지 말 것.
- **조회(GET)만 엽니다.** 내업 기록 쓰기·사진 업로드·첨부 중계(`/map/qfield/.../media`)는 카탈로그에 넣지 말 것
  (쓰기 API이거나 외부 계정이 필요합니다). 테스트 `ApiCatalogServiceTests`가 `method: GET`과 경로 접두어를 검사합니다.
- **DB를 직접 읽지 말 것.** 같은 SQL이 두 저장소에 생기면 `mapservice-rest`의 뷰·쿼리가 바뀔 때 한쪽만 고쳐져
  답이 달라집니다. 데이터는 항상 원천 호출로 가져옵니다.
- **원천 경로가 바뀌면 카탈로그의 `upstream` 값도 같은 작업에서 고칠 것.** `mapservice-rest`의 컨트롤러 매핑
  (`/map/busStop-info` 등 표기가 제각각입니다)을 그대로 적어야 합니다.
- `RestTemplate`은 두 개입니다 — `loadBalancedUpstreamRestTemplate`(Eureka 서비스 이름용, `@LoadBalanced`)과
  `directUpstreamRestTemplate`(실제 주소용). `@LoadBalanced`가 붙은 쪽은 호스트를 **서비스 이름으로 해석**해서
  `http://localhost:8100` 같은 주소에는 쓸 수 없으므로, `UpstreamProperties.isServiceName()`으로 고르는 구조를
  유지할 것.
- 응답은 원천의 상태코드·본문·`Content-Type`을 그대로 전달합니다. 원천이 400·404를 주면 그대로 내보내고,
  연결 자체가 실패할 때만 502로 바꿉니다. **본문을 가공하지 말 것**(프론트가 원천 형식 그대로 파싱합니다).
- 비밀값이 없는 저장소입니다(public). DB 접속 정보·토큰을 넣지 말 것.

## 명령어

```
mvnw.cmd clean package     # 빌드 (target/sj-lab-openapi.jar)
mvnw.cmd test              # 테스트
java -jar target/sj-lab-openapi.jar --spring.profiles.active=local --server.port=8110
```

검증용으로 띄울 때는 8100(게이트웨이)·8761(Eureka)을 쓰지 말고 별도 포트를 쓰고, 확인 후 그 프로세스만 종료할 것.

## 현재 범위와 남은 작업

- 지금은 **카탈로그 + 중계**까지 구현돼 있습니다. **API 키 발급·검증·사용량 기록은 아직 없습니다**(다음 단계).
  키를 붙일 때 테이블은 `map` 스키마에 둘 것 — `qfield` 스키마의 비(非)프로젝트 테이블은 `sj-qfieldsync`가
  삭제합니다. DDL 실행은 에이전트가 하지 않고 담당자가 직접 합니다.
- 로그인 확인은 `sj-lab-authserver`의 `/auth/me`에 위임할 예정입니다(JWT 시크릿을 서비스마다 복사하지 않기 위해).
