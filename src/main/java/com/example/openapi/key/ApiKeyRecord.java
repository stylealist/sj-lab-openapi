package com.example.openapi.key;

/**
 * 화면에 보여 주는 키 정보.
 *
 * <p>{@code apiKey} 는 키 원문이다(2026-10-01부터 저장한다 — 본인이 언제든 보고 복사할 수 있어야 해서).
 * DB 에 {@code key_plain} 컬럼이 없거나 옛 데이터라면 {@code null} 이고, 그때는 화면이
 * {@code keyPrefix} 만 보여 준다. 검증은 이 값이 아니라 항상 해시로 한다.
 */
public record ApiKeyRecord(
        long keyId,
        String keyPrefix,
        String apiKey,
        String label,
        int dailyQuota,
        int todayCount,
        String regDate,
        String lastUsedAt) {
}
