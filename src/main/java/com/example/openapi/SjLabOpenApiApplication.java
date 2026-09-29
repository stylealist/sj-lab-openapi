package com.example.openapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * DataSource 자동 설정을 끈 이유: API 키 기능이 꺼져 있으면(기본값) DB 없이도 서비스가 떠야 한다.
 * 키 기능을 켰을 때만 {@link com.example.openapi.config.ApiKeyStoreConfig} 가 DataSource 를 만든다.
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@EnableDiscoveryClient
public class SjLabOpenApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SjLabOpenApiApplication.class, args);
    }
}
