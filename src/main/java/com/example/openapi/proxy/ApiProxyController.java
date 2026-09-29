package com.example.openapi.proxy;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/v1/**")
    public ResponseEntity<byte[]> relay(HttpServletRequest request) {
        String requestPath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        if (requestPath == null) {
            requestPath = request.getRequestURI().substring(request.getContextPath().length());
        }
        return proxyService.relay(requestPath, request.getParameterMap());
    }
}
