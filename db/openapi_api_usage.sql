-- ============================================================================
-- api.openapi_api_usage — 공개 API 호출 기록
--
-- 목적: 어떤 키로 어떤 API 를 언제 불렀는지 남겨, 사용량 화면과 하루 한도(429) 판정에 쓴다.
--       키를 붙이지 않은 호출은 기록하지 않는다(현재 정책).
--
-- 실행 순서: api.openapi_api_key 를 먼저 만들 것(이 표가 그 표를 FK 로 참조한다).
--
-- 쌓이는 양 주의: 호출마다 한 행이다. 오래된 기록은 주기적으로 지우거나 집계로 옮길 것.
--       (예: DELETE FROM api.openapi_api_usage WHERE called_at < now() - interval '90 days')
--
-- 실행 주체: 에이전트는 실행하지 않는다(프로젝트 규칙: DB는 조회만).
--       DB 권한이 있는 담당자가 실행할 것.
--   예) psql -h <호스트> -p <포트> -U <계정> -d sjlab -f db/openapi_api_usage.sql
--
-- 재실행해도 안전하다(IF NOT EXISTS).
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS api;

CREATE TABLE IF NOT EXISTS api.openapi_api_usage (
    usage_id    bigserial   PRIMARY KEY,
    key_id      bigint      NOT NULL REFERENCES api.openapi_api_key (key_id),
    api_id      varchar(60) NOT NULL,                        -- 카탈로그의 API id (예: convenience-store)
    status_code integer     NOT NULL,                        -- 응답 상태코드
    elapsed_ms  integer,                                     -- 원천 호출에 걸린 시간(ms)
    called_date date        NOT NULL DEFAULT current_date,   -- 하루 한도 계산용(인덱스)
    called_at   timestamp   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_openapi_api_usage_key_date
    ON api.openapi_api_usage (key_id, called_date);

COMMENT ON TABLE  api.openapi_api_usage             IS '공개 API 호출 기록 (sj-lab-openapi)';
COMMENT ON COLUMN api.openapi_api_usage.api_id      IS 'api-catalog.json 의 id';
COMMENT ON COLUMN api.openapi_api_usage.called_date IS '하루 한도 판정 기준일';

-- 서비스 계정에 권한이 필요하면 함께 실행 (계정명은 환경에 맞게)
-- GRANT SELECT, INSERT ON api.openapi_api_usage TO <서비스 계정>;
-- GRANT USAGE, SELECT ON SEQUENCE api.openapi_api_usage_usage_id_seq TO <서비스 계정>;
-- 조회 계정이 필요하면
-- GRANT SELECT ON api.openapi_api_usage TO mcp_readonly;
