-- ============================================================================
-- api.openapi_api_key — 공개 API 키
--
-- 목적: 로그인한 사용자가 자기 이름으로 API 키를 발급받아 공개 API(/open-api/v1/...)를
--       부를 수 있게 한다. 키를 붙여 부르면 호출이 기록되고 하루 한도가 적용된다.
--       키 없이도 부를 수 있지만(현재 정책), 그때는 기록되지 않는다.
--
-- 키 저장 주의: 키 원문은 저장하지 않는다. 발급 순간 한 번만 사용자에게 보여 주고,
--       DB 에는 SHA-256 해시(key_hash)와 앞 8자리(key_prefix, 화면 표시용)만 남긴다.
--       그래서 키를 잃어버리면 다시 볼 수 없고 새로 발급해야 한다.
--
-- 스키마: 공개 API 관련 표는 별도의 api 스키마에 모은다(2026-09-29 결정).
--       qfield 스키마는 sj-qfieldsync 워커가 관리하며, QField 프로젝트 이름 패턴이 아닌
--       테이블을 "삭제된 프로젝트 테이블"로 보고 DROP 하므로 절대 두지 말 것.
--       지도 데이터(map)와도 성격이 달라 분리한다.
--
-- 실행 주체: 에이전트는 실행하지 않는다(프로젝트 규칙: DB는 조회만).
--       DB 권한이 있는 담당자가 실행할 것.
--   예) psql -h <호스트> -p <포트> -U <계정> -d sjlab -f db/openapi_api_key.sql
--
-- 이 표가 없으면 키 관련 API 만 503(NOT_CONFIGURED)이고, 공개 API 조회는 그대로 동작한다.
-- 재실행해도 안전하다(IF NOT EXISTS).
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS api;

CREATE TABLE IF NOT EXISTS api.openapi_api_key (
    key_id         bigserial    PRIMARY KEY,
    owner_username varchar(200) NOT NULL,                       -- 로그인 계정(sj-lab-authserver 가 확인한 값)
    key_prefix     varchar(16)  NOT NULL,                       -- 화면에 보여 주는 앞부분(예: sjlab_ab12cd34)
    key_hash       varchar(64)  NOT NULL,                       -- 키 원문의 SHA-256 (hex 64자)
    label          varchar(100),                                -- 사용자가 붙인 이름(예: "테스트용")
    daily_quota    integer      NOT NULL DEFAULT 1000,          -- 하루 호출 한도
    use_yn         char(1)      NOT NULL DEFAULT 'y',           -- 폐기하면 'n' (행은 남긴다)
    reg_date       timestamp    NOT NULL DEFAULT now(),
    last_used_at   timestamp
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_openapi_api_key_hash
    ON api.openapi_api_key (key_hash);

CREATE INDEX IF NOT EXISTS ix_openapi_api_key_owner
    ON api.openapi_api_key (owner_username, use_yn);

COMMENT ON TABLE  api.openapi_api_key                IS '공개 API 키 (sj-lab-openapi)';
COMMENT ON COLUMN api.openapi_api_key.key_hash       IS '키 원문의 SHA-256 해시 — 원문은 저장하지 않는다';
COMMENT ON COLUMN api.openapi_api_key.key_prefix     IS '화면 표시용 앞부분';
COMMENT ON COLUMN api.openapi_api_key.daily_quota    IS '하루 호출 한도, 초과 시 429';
COMMENT ON COLUMN api.openapi_api_key.use_yn         IS 'y=사용, n=폐기';

-- 서비스 계정에 권한이 필요하면 함께 실행 (계정명은 환경에 맞게)
-- GRANT USAGE ON SCHEMA api TO <서비스 계정>;
-- GRANT SELECT, INSERT, UPDATE ON api.openapi_api_key TO <서비스 계정>;
-- GRANT USAGE, SELECT ON SEQUENCE api.openapi_api_key_key_id_seq TO <서비스 계정>;
-- 조회 계정이 필요하면
-- GRANT USAGE ON SCHEMA api TO mcp_readonly;
-- GRANT SELECT ON api.openapi_api_key TO mcp_readonly;
