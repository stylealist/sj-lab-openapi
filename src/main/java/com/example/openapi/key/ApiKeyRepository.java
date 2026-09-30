package com.example.openapi.key;

import com.example.openapi.config.ApiKeyProperties;
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
 * 키·사용량 표를 다룬다. 표는 {@code api} 스키마에 둔다(지도 데이터와 분리, 스키마 이름은 설정값).
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
    /** 스키마는 설정값이라 SQL 에 끼워 넣지만, 형식 검증은 ApiKeyProperties 가 한다. */
    private final String keyTable;
    private final String usageTable;

    private Boolean tablesExist;
    private long lastCheckedAt;
    private Boolean plainColumnExists;
    private long plainCheckedAt;

    public ApiKeyRepository(ObjectProvider<JdbcTemplate> jdbcTemplateProvider, ApiKeyProperties properties) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.keyTable = properties.getSchema() + ".openapi_api_key";
        this.usageTable = properties.getSchema() + ".openapi_api_usage";
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
                log.info("API 키 표({}, {}) 확인: {}", keyTable, usageTable,
                        exists ? "있음" : "없음 — 키 API 는 503, 공개 조회는 정상");
            }
            tablesExist = exists;
        }
        return tablesExist;
    }

    private boolean checkTables(JdbcTemplate jdbc) {
        try {
            Integer count = jdbc.queryForObject(
                    "select count(*) from (select to_regclass(?) a, to_regclass(?) b) t "
                            + "where a is not null and b is not null",
                    Integer.class, keyTable, usageTable);
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

    /**
     * key_plain 컬럼이 있는지. 없으면 원문을 저장·조회하지 않고 예전처럼 앞자리만 보여 준다 —
     * 그래야 DDL(db/api/openapi_api_key_plain.sql)을 아직 돌리지 않은 DB 에서도 서비스가 그대로 뜬다.
     * 한 번 있다고 확인되면 다시 확인하지 않고, 없을 때만 주기적으로 다시 본다(표 확인과 같은 방식).
     */
    public boolean hasPlainColumn() {
        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        if (jdbc == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (plainColumnExists == null
                || (!plainColumnExists && now - plainCheckedAt > RECHECK_INTERVAL_MS)) {
            plainCheckedAt = now;
            boolean exists = checkPlainColumn(jdbc);
            if (plainColumnExists == null || plainColumnExists != exists) {
                log.info("키 원문 컬럼(key_plain) 확인: {}", exists
                        ? "있음 — 화면에 키 전체를 보여 준다"
                        : "없음 — 앞자리만 보여 준다(db/api/openapi_api_key_plain.sql 실행 필요)");
            }
            plainColumnExists = exists;
        }
        return plainColumnExists;
    }

    private boolean checkPlainColumn(JdbcTemplate jdbc) {
        try {
            String[] parts = keyTable.split("\\.", 2);
            Integer count = jdbc.queryForObject(
                    "select count(*) from information_schema.columns "
                            + "where table_schema = ? and table_name = ? and column_name = 'key_plain'",
                    Integer.class, parts[0], parts[1]);
            return count != null && count > 0;
        } catch (DataAccessException e) {
            log.warn("key_plain 컬럼 확인 실패: {} - {}", e.getClass().getSimpleName(),
                    e.getMostSpecificCause().getMessage());
            return false;
        }
    }

    public long insertKey(String username, String keyPrefix, String keyHash, String keyPlain,
                          String label, int dailyQuota) {
        if (hasPlainColumn()) {
            Long keyId = jdbc().queryForObject(
                    "insert into " + keyTable
                            + " (owner_username, key_prefix, key_hash, key_plain, label, daily_quota) "
                            + "values (?, ?, ?, ?, ?, ?) returning key_id",
                    Long.class, username, keyPrefix, keyHash, keyPlain, label, dailyQuota);
            return keyId == null ? 0L : keyId;
        }
        Long keyId = jdbc().queryForObject(
                "insert into " + keyTable + " (owner_username, key_prefix, key_hash, label, daily_quota) "
                        + "values (?, ?, ?, ?, ?) returning key_id",
                Long.class, username, keyPrefix, keyHash, label, dailyQuota);
        return keyId == null ? 0L : keyId;
    }

    public List<ApiKeyRecord> findByOwner(String username) {
        boolean withPlain = hasPlainColumn();
        return jdbc().query(
                "select k.key_id, k.key_prefix, k.label, k.daily_quota, k.reg_date, k.last_used_at, "
                        + (withPlain ? "k.key_plain, " : "null::varchar as key_plain, ")
                        + "  (select count(*) from " + usageTable + " u "
                        + "     where u.key_id = k.key_id and u.called_date = current_date) as today_count "
                        + "from " + keyTable + " k "
                        + "where k.owner_username = ? and k.use_yn = 'y' "
                        + "order by k.key_id desc",
                (rs, rowNum) -> new ApiKeyRecord(
                        rs.getLong("key_id"),
                        rs.getString("key_prefix"),
                        rs.getString("key_plain"),
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
                "update " + keyTable + " set use_yn = 'n' "
                        + "where key_id = ? and owner_username = ? and use_yn = 'y'",
                keyId, username) > 0;
    }

    /** 호출에 쓰인 키를 해시로 찾는다(원문은 저장돼 있지 않다). */
    public Optional<ApiKeyOwner> findActiveByHash(String keyHash) {
        List<ApiKeyOwner> found = jdbc().query(
                "select k.key_id, k.owner_username, k.daily_quota, "
                        + "  (select count(*) from " + usageTable + " u "
                        + "     where u.key_id = k.key_id and u.called_date = current_date) as today_count "
                        + "from " + keyTable + " k where k.key_hash = ? and k.use_yn = 'y'",
                (rs, rowNum) -> new ApiKeyOwner(
                        rs.getLong("key_id"),
                        rs.getString("owner_username"),
                        rs.getInt("daily_quota"),
                        rs.getInt("today_count")),
                keyHash);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    /**
     * 그 계정의 살아 있는 키를 찾는다(원문 없이). 로그인한 사용자가 활용 페이지에서 부를 때 쓴다 —
     * 키 원문은 저장하지 않으므로 화면이 원문을 몰라도 사용량을 이 키에 기록할 수 있어야 한다.
     * 계정당 1개이므로 결과는 최대 한 건이지만, 옛 데이터에 여러 건이 있을 수 있어 최신 것을 쓴다.
     */
    public Optional<ApiKeyOwner> findActiveByOwner(String username) {
        List<ApiKeyOwner> found = jdbc().query(
                "select k.key_id, k.owner_username, k.daily_quota, "
                        + "  (select count(*) from " + usageTable + " u "
                        + "     where u.key_id = k.key_id and u.called_date = current_date) as today_count "
                        + "from " + keyTable + " k where k.owner_username = ? and k.use_yn = 'y' "
                        + "order by k.key_id desc limit 1",
                (rs, rowNum) -> new ApiKeyOwner(
                        rs.getLong("key_id"),
                        rs.getString("owner_username"),
                        rs.getInt("daily_quota"),
                        rs.getInt("today_count")),
                username);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    public void recordUsage(long keyId, String apiId, int statusCode, int elapsedMs) {
        jdbc().update(
                "insert into " + usageTable + " (key_id, api_id, status_code, elapsed_ms) values (?, ?, ?, ?)",
                keyId, apiId, statusCode, elapsedMs);
        jdbc().update("update " + keyTable + " set last_used_at = now() where key_id = ?", keyId);
    }

    private String formatTimestamp(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime().format(DATE_TIME);
    }

    /** 호출 검사에 필요한 최소 정보 */
    public record ApiKeyOwner(long keyId, String ownerUsername, int dailyQuota, int todayCount) {
    }
}
