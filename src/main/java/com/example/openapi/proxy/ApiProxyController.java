package com.example.openapi.proxy;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 공개 API 입구. 실제 데이터는 mapservice-rest 에서 가져온다.
 * 경로 판정은 카탈로그가 하므로 엔드포인트마다 메서드를 만들지 않는다.
 */
@RestController
public class ApiProxyController {

    private final ApiProxyService proxyService;

    public ApiProxyController(ApiProxyService proxyService) {
        this.proxyService = proxyService;
    }

    /**
     * 호출자를 확인하는 방법은 두 가지입니다.
     * <ul>
     *   <li>API 키 — 헤더(<code>X-API-Key</code>)를 권하며, 주소창처럼 헤더를 넣기 어려운 곳을 위해
     *       쿼리(<code>?apiKey=</code>)도 받습니다.</li>
     *   <li>로그인 토큰(<code>Authorization: Bearer</code>) — 활용 페이지처럼 이미 로그인한 화면용입니다.
     *       키 원문은 저장하지 않아 화면이 원문을 모를 수 있으므로, 이 경우에도 호출이 되고
     *       사용량은 그 계정 키에 쌓입니다.</li>
     * </ul>
     * 둘 다 없으면 401 입니다(<code>openapi.api-key.required</code> 기본 true).
     */
    @GetMapping("/v1/**")
    public ResponseEntity<byte[]> relay(
            HttpServletRequest request,
            @RequestHeader(value = "X-API-Key", required = false) String apiKeyHeader,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String requestPath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        if (requestPath == null) {
            requestPath = request.getRequestURI().substring(request.getContextPath().length());
        }
        return proxyService.relay(requestPath, request.getParameterMap(), apiKeyHeader, authorization);
    }
}
