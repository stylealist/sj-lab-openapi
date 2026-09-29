package com.example.openapi.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * API 키 저장소(DB) 연결. <b>키 기능을 켰을 때만</b> 만든다 — 꺼져 있으면 DB 없이도 서비스가 뜬다.
 * 매퍼 XML 계층을 두지 않고 JdbcTemplate 을 쓰는 이유는 다루는 표가 두 개뿐이고,
 * 이 DataSource 자체가 조건부라 자동 설정에 얹기 어렵기 때문이다.
 */
@Configuration
@ConditionalOnProperty(prefix = "openapi.api-key", name = "enabled", havingValue = "true")
public class ApiKeyStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyStoreConfig.class);

    @Bean(destroyMethod = "close")
    public DataSource apiKeyDataSource(ApiKeyProperties properties) {
        ApiKeyProperties.Datasource config = properties.getDatasource();
        if (config.getUrl() == null || config.getUrl().isBlank()) {
            throw new IllegalStateException(
                    "openapi.api-key.enabled=true 인데 접속 주소가 없습니다. OPENAPI_DB_URL 을 넣어 주세요.");
        }
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(config.getUrl());
        dataSource.setUsername(config.getUsername());
        dataSource.setPassword(config.getPassword());
        dataSource.setMaximumPoolSize(5);
        dataSource.setPoolName("openapi-key-pool");
        log.info("API 키 저장소 연결 준비 완료 (하루 한도 {}회)", properties.getDailyQuota());
        return dataSource;
    }

    @Bean
    public JdbcTemplate apiKeyJdbcTemplate(DataSource apiKeyDataSource) {
        return new JdbcTemplate(apiKeyDataSource);
    }
}
