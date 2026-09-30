package com.example.openapi.key;

import com.example.openapi.config.ApiKeyProperties;
import com.example.openapi.proxy.ApiProxyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 키 발급·조회·폐기와, 호출에 쓰인 키 검사. */
@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);
    private static final String KEY_PREFIX = "sjlab_";
    private static final int KEY_BYTES = 24;
    /**
     * 계정당 살아 있는 키 개수. 1 이다(2026-09-30) — 키가 쌓이면 어느 것이 어디에 쓰이는지 알 수 없고,
     * 폐기해도 쓰던 곳이 조용히 멈춘다. 새로 받으려면 쓰던 키를 먼저 폐기하게 해서
     * "지금 쓰는 키가 무엇인지"를 항상 한 개로 유지한다.
     * 폐기는 행을 지우지 않으므로(use_yn='n') 지난 키의 사용 기록은 남는다.
     */
    private static final int MAX_KEYS_PER_USER = 1;

    private final ApiKeyRepository repository;
    private final ApiKeyProperties properties;
    private final SecureRandom random = new SecureRandom();

    public ApiKeyService(ApiKeyRepository repository, ApiKeyProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public boolean isReady() {
        return properties.isEnabled() && repository.isReady();
    }

    private void requireReady() {
        if (!isReady()) {
            throw new ApiProxyException(HttpStatus.SERVICE_UNAVAILABLE, "NOT_CONFIGURED",
                    "API 키 기능이 아직 준비되지 않았습니다. 키 없이도 공개 API 조회는 그대로 됩니다.");
        }
    }

    /** 발급. 키 원문은 이 응답에만 담기고 DB 에는 해시만 남으므로, 다시 볼 수 없다. */
    public Map<String, Object> issue(String username, String label) {
        requireReady();
        if (repository.findByOwner(username).size() >= MAX_KEYS_PER_USER) {
            throw new ApiProxyException(HttpStatus.CONFLICT, "TOO_MANY_KEYS",
                    "키는 계정당 1개입니다. 이미 있는 키를 폐기한 뒤 새로 발급하세요.");
        }

        byte[] bytes = new byte[KEY_BYTES];
        random.nextBytes(bytes);
        String secret = KEY_PREFIX + HexFormat.of().formatHex(bytes);
        String prefix = secret.substring(0, KEY_PREFIX.length() + 8);
        String safeLabel = StringUtils.hasText(label) ? label.trim() : "내 키";
        if (safeLabel.length() > 100) {
            throw new ApiProxyException(HttpStatus.BAD_REQUEST, "INVALID_LABEL", "이름은 100자까지입니다.");
        }

        long keyId = repository.insertKey(username, prefix, hash(secret), safeLabel, properties.getDailyQuota());
        log.info("API 키 발급: 계정={} keyId={}", username, keyId);

        return Map.of(
                "keyId", keyId,
                "apiKey", secret,
                "keyPrefix", prefix,
                "label", safeLabel,
                "dailyQuota", properties.getDailyQuota(),
                "notice", "이 키는 지금 한 번만 보여 드립니다. 복사해 두세요.");
    }

    public List<ApiKeyRecord> list(String username) {
        requireReady();
        return repository.findByOwner(username);
    }

    public void revoke(long keyId, String username) {
        requireReady();
        if (!repository.revoke(keyId, username)) {
            throw new ApiProxyException(HttpStatus.NOT_FOUND, "KEY_NOT_FOUND", "그런 키가 없습니다.");
        }
        log.info("API 키 폐기: 계정={} keyId={}", username, keyId);
    }

    /**
     * 호출에 붙은 키를 검사한다. 키가 없으면 빈 값(현재는 키 없이도 부를 수 있음),
     * 키가 틀리면 401, 하루 한도를 넘었으면 429.
     */
    public Optional<ApiKeyRepository.ApiKeyOwner> verifyForCall(String presentedKey) {
        if (!StringUtils.hasText(presentedKey)) {
            return Optional.empty();
        }
        if (!isReady()) {
            throw new ApiProxyException(HttpStatus.SERVICE_UNAVAILABLE, "NOT_CONFIGURED",
                    "키 확인 기능이 준비되지 않았습니다. 지금은 키 없이 불러 주세요.");
        }
        ApiKeyRepository.ApiKeyOwner owner = repository.findActiveByHash(hash(presentedKey.trim()))
                .orElseThrow(() -> new ApiProxyException(HttpStatus.UNAUTHORIZED, "INVALID_API_KEY",
                        "쓸 수 없는 키입니다. 폐기됐거나 잘못 입력한 키가 아닌지 확인해 주세요."));

        if (owner.todayCount() >= owner.dailyQuota()) {
            throw new ApiProxyException(HttpStatus.TOO_MANY_REQUESTS, "QUOTA_EXCEEDED",
                    "오늘 호출 한도(" + owner.dailyQuota() + "회)를 다 썼습니다. 내일 다시 이용해 주세요.");
        }
        return Optional.of(owner);
    }

    /** 호출 기록. 기록에 실패해도 이미 만든 응답은 그대로 내보낸다. */
    public void recordUsage(ApiKeyRepository.ApiKeyOwner owner, String apiId, int statusCode, int elapsedMs) {
        try {
            repository.recordUsage(owner.keyId(), apiId, statusCode, elapsedMs);
        } catch (RuntimeException e) {
            log.warn("사용량 기록 실패 keyId={} api={} 원인={}", owner.keyId(), apiId, e.getClass().getSimpleName());
        }
    }

    private String hash(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없습니다", e);
        }
    }
}
