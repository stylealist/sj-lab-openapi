package com.example.openapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 데이터 원천(mapservice-rest) 호출 설정.
 *
 * <p>기본값은 Eureka 서비스 이름(<code>http://MAPSERVICE-REST</code>)이라 클라이언트 로드밸런서가 필요하지만,
 * 설정으로 실제 주소(예: <code>http://localhost:8100</code>)를 넣을 수도 있다. 로드밸런서가 붙은
 * RestTemplate 은 호스트를 서비스 이름으로 해석하므로 실제 주소에서는 쓸 수 없어, 두 개를 만들어 두고
 * {@link UpstreamProperties#isServiceName()} 결과로 고른다.
 */
@Configuration
public class UpstreamConfig {

    @Bean
    @ConfigurationProperties(prefix = "openapi.upstream")
    public UpstreamProperties upstreamProperties() {
        return new UpstreamProperties();
    }

    /** 서비스 이름(lb) 호출용 */
    @Bean
    @LoadBalanced
    public RestTemplate loadBalancedUpstreamRestTemplate(RestTemplateBuilder builder, UpstreamProperties properties) {
        return build(builder, properties);
    }

    /** 실제 주소 호출용 */
    @Bean
    public RestTemplate directUpstreamRestTemplate(RestTemplateBuilder builder, UpstreamProperties properties) {
        return build(builder, properties);
    }

    private RestTemplate build(RestTemplateBuilder builder, UpstreamProperties properties) {
        return builder
                .setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .build();
    }

    public static class UpstreamProperties {
        private String baseUrl = "http://MAPSERVICE-REST";
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 20000;

        /** 호스트가 Eureka 서비스 이름인지(점·포트가 없고 localhost 가 아니면 서비스 이름으로 본다) */
        public boolean isServiceName() {
            String host = baseUrl.replaceFirst("^https?://", "");
            int slash = host.indexOf('/');
            if (slash >= 0) {
                host = host.substring(0, slash);
            }
            return !host.contains(".") && !host.contains(":") && !"localhost".equalsIgnoreCase(host);
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }
    }
}
