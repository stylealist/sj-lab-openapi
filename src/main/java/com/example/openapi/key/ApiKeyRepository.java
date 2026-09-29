package com.example.openapi.key;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 키·사용량 표를 다룬다.
 *
 * <p>표가 아직 만들어지지 않았을 수 있으므로(DDL 실행은 담당자 몫) 존재 여부를 {@code to_regclass} 로
 * 확인해 캐시하고, 없으면 60초 뒤 다시 본다. mapservice-rest 의 내업 표 처리와 같은 방식이다.
 */
@Repository
public class ApiKeyRepository {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyRepository.class);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long RECHECK_INTERVAL_MS = 60_000L;

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    private Boolean tablesExist;
    private long lastCheckedAt;

    public ApiKeyRepository(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    /** 키 기능을 쓸 수 있는 상태인지(연결이 있고 표도 있는지) */
    public boolean isReady() {
        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        if (jdbc == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (tablesExist == null || (!tablesExist && now - lastCheckedAt > RECHECK_INTERVAL_MS)) {
            lastCheckedAt = now;
            boolean exists = checkTables(jdbc);
            if (tablesExist == null || tablesExist != exists) {
                log.info("API 키 표 확인: {}", exists ? "있음" : "없음 — 키 API 는 503, 공개 조회는 정상");
            }
            tablesExist = exists;
        }
        return tablesExist;
    }

    private boolean checkTables(JdbcTemplate jdbc) {
        try {
            Integer count = jdbc.queryForObject(
                    "select count(*) from (select to_regclass('map.openapi_api_key') a, "
                            + "to_regclass('map.openapi_api_usage') b) t where a is not null and b is not null",
                    Integer.class);
            return count != null && count > 0;
        } catch (DataAccessException e) {
            // 원인 메시지까지 남긴다 — "표가 없음"과 "DB 에 못 붙음"은 대응이 다르다.
            log.warn("API 키 표 확인 실패: {} - {}", e.getClass().getSimpleName(),
                    e.getMostSpecificCause().getMessage());
            return false;
        }
    }

    private JdbcTemplate jdbc() {
        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        if (jdbc == null) {
            throw new IllegalStateException("API 키 저장소가 설정되지 않았습니다");
        }
        return jdbc;
    }

    public long insertKey(String username, String keyPrefix, String keyHash, String label, int dailyQuota) {
        Long keyId = jdbc().queryForObject(
                "insert into map.openapi_api_key (owner_username, key_prefix, key_hash, label, daily_quota) "
                        + "values (?, ?, ?, ?, ?) returning key_id",
                Long.class, username, keyPrefix, keyHash, label, dailyQuota);
        return keyId == null ? 0L : keyId;
    }

    public List<ApiKeyRecord> findByOwner(String username) {
        return jdbc().query(
                "select k.key_id, k.key_prefix, k.label, k.daily_quota, k.reg_date, k.last_used_at, "
                        + "  (select count(*) from map.openapi_api_usage u "
                        + "     where u.key_id = k.key_id and u.called_date = current_date) as today_count "
                        + "from map.openapi_api_key k "
                        + "where k.owner_username = ? and k.use_yn = 'y' "
                        + "order by k.key_id desc",
                (rs, rowNum) -> new ApiKeyRecord(
                        rs.getLong("key_id"),
                        rs.getString("key_prefix"),
                        rs.getString("label"),
                        rs.getInt("daily_quota"),
                        rs.getInt("today_count"),
                        formatTimestamp(rs.getTimestamp("reg_date")),
                        formatTimestamp(rs.getTimestamp("last_used_at"))),
                username);
    }

    /** 폐기(소프트 삭제). 남의 키를 지울 수 없도록 계정까지 조건에 넣는다. */
    public boolean revoke(long keyId, String username) {
        return jdbc().update(
                "update map.openapi_api_key set use_yn = 'n' "
                        + "where key_id = ? and owner_username = ? and use_yn = 'y'",
                keyId, username) > 0;
    }

    /** 호출에 쓰인 키를 해시로 찾는다(원문은 저장돼 있지 않다). */
    public Optional<ApiKeyOwner> findActiveByHash(String keyHash) {
        List<ApiKeyOwner> found = jdbc().query(
                "select k.key_id, k.owner_username, k.daily_quota, "
                        + "  (select count(*) from map.openapi_api_usage u "
                        + "     where u.key_id = k.key_id and u.called_date = current_date) as today_count "
                        + "from map.openapi_api_key k where k.key_hash = ? and k.use_yn = 'y'",
                (rs, rowNum) -> new ApiKeyOwner(
                        rs.getLong("key_id"),
                        rs.getString("owner_username"),
                        rs.getInt("daily_quota"),
                        rs.getInt("today_count")),
                keyHash);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    public void recordUsage(long keyId, String apiId, int statusCode, int elapsedMs) {
        jdbc().update(
                "insert into map.openapi_api_usage (key_id, api_id, status_code, elapsed_ms) values (?, ?, ?, ?)",
                keyId, apiId, statusCode, elapsedMs);
        jdbc().update("update map.openapi_api_key set last_used_at = now() where key_id = ?", keyId);
    }

    private String formatTimestamp(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime().format(DATE_TIME);
    }

    /** 호출 검사에 필요한 최소 정보 */
    public record ApiKeyOwner(long keyId, String ownerUsername, int dailyQuota, int todayCount) {
    }
}
