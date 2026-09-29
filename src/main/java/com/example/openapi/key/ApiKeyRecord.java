package com.example.openapi.key;

/** 화면에 보여 주는 키 정보. 키 원문은 발급 응답에만 담기고 여기에는 없다. */
public record ApiKeyRecord(
        long keyId,
        String keyPrefix,
        String label,
        int dailyQuota,
        int todayCount,
        String regDate,
        String lastUsedAt) {
}
