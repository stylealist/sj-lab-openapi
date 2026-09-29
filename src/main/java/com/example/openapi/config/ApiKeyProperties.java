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
