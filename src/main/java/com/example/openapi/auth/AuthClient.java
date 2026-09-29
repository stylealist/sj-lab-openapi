package com.example.openapi.auth;

import com.example.openapi.proxy.ApiProxyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 로그인 확인은 sj-lab-authserver 에 맡긴다({@code GET /auth/me}).
 * JWT 서명 키를 서비스마다 복사해 두지 않기 위해서다.
 */
@Component
public class AuthClient {

    private static final Logger log = LoggerFactory.getLogger(AuthClient.class);

    private final RestTemplate restTemplate;
    private final String authBaseUrl;

    public AuthClient(@Qualifier("loadBalancedUpstreamRestTemplate") RestTemplate loadBalanced,
                      @Qualifier("directUpstreamRestTemplate") RestTemplate direct,
                      @Value("${openapi.auth.base-url:http://SJ-LAB-AUTHSERVER}") String authBaseUrl) {
        this.authBaseUrl = authBaseUrl;
        boolean serviceName = !authBaseUrl.replaceFirst("^https?://", "").split("/")[0].matches(".*[.:].*")
                && !authBaseUrl.contains("localhost");
        this.restTemplate = serviceName ? loadBalanced : direct;
    }

    /** Authorization 헤더의 토큰이 누구 것인지 확인해 계정을 돌려준다. 아니면 401. */
    public String requireUsername(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)) {
            throw new ApiProxyException(HttpStatus.UNAUTHORIZED, "LOGIN_REQUIRED", "로그인이 필요합니다.");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);

        try {
            @SuppressWarnings("rawtypes")
            Map body = restTemplate.exchange(authBaseUrl + "/auth/me", HttpMethod.GET,
                    new HttpEntity<>(headers), Map.class).getBody();
            Object username = body == null ? null : body.get("username");
            if (username == null || !StringUtils.hasText(username.toString())) {
                throw new ApiProxyException(HttpStatus.UNAUTHORIZED, "LOGIN_REQUIRED", "로그인 정보를 확인하지 못했습니다.");
            }
            return username.toString();
        } catch (HttpStatusCodeException e) {
            throw new ApiProxyException(HttpStatus.UNAUTHORIZED, "LOGIN_REQUIRED",
                    "로그인이 만료됐거나 올바르지 않습니다. 다시 로그인해 주세요.");
        } catch (RestClientException e) {
            log.warn("로그인 서버 호출 실패: {}", e.getClass().getSimpleName());
            throw new ApiProxyException(HttpStatus.BAD_GATEWAY, "AUTH_UNAVAILABLE",
                    "로그인 서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }
}
