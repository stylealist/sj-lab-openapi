package com.example.openapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** API 키 기능 설정. 접속 정보는 환경변수로만 넣는다(저장소가 public). */
@Component
@ConfigurationProperties(prefix = "openapi.api-key")
public class ApiKeyProperties {

    /** false 면 키 관련 API 는 503(NOT_CONFIGURED), 공개 API 조회는 그대로 동작한다. */
    private boolean enabled = false;

    /** 새 키의 기본 하루 한도 */
    private int dailyQuota = 1000;

    /**
     * 키·사용량 표가 있는 스키마. 공개 API 관련 표는 지도 데이터(map)와 분리해 api 스키마에 모은다.
     * SQL 에 그대로 끼워 넣는 값이라 식별자 형식만 허용한다.
     */
    private String schema = "api";

    private final Datasource datasource = new Datasource();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getDailyQuota() {
        return dailyQuota;
    }

    public void setDailyQuota(int dailyQuota) {
        this.dailyQuota = dailyQuota;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        if (schema == null || !schema.matches("[a-z_][a-z0-9_]*")) {
            throw new IllegalArgumentException(
                    "openapi.api-key.schema 는 소문자·숫자·밑줄만 쓸 수 있습니다: " + schema);
        }
        this.schema = schema;
    }

    public Datasource getDatasource() {
        return datasource;
    }

    public static class Datasource {
        private String url = "";
        private String username = "";
        private String password = "";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
