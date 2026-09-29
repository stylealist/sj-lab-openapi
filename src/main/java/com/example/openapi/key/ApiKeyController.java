package com.example.openapi.key;

import com.example.openapi.auth.AuthClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 내 API 키 관리. 모두 로그인(Authorization: Bearer)이 필요하며, 남의 키는 보이지도 지워지지도 않는다.
 * 키·사용량은 바로바로 달라지므로 응답을 캐시하지 않는다(no-store).
 */
@RestController
@RequestMapping("/keys")
public class ApiKeyController {

    private static final String NO_STORE = "no-store";

    private final ApiKeyService keyService;
    private final AuthClient authClient;
    private final ObjectMapper objectMapper;

    public ApiKeyController(ApiKeyService keyService, AuthClient authClient, ObjectMapper objectMapper) {
        this.keyService = keyService;
        this.authClient = authClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 본문을 {@code Map} 이 아니라 문자열로 받는 이유: Content-Type 을 빠뜨리고 POST 해도
     * 415(Unsupported Media Type) 대신 "로그인이 필요합니다"(401)처럼 진짜 원인이 보여야 한다.
     * 본문(JSON {"label": "..."})은 없어도 되고, 이름을 안 주면 기본값이 붙는다.
     */
    @PostMapping(consumes = MediaType.ALL_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> issue(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody(required = false) String rawBody) {
        String username = authClient.requireUsername(authorization);
        String label = readLabel(rawBody);
        return ResponseEntity.status(201)
                .header(HttpHeaders.CACHE_CONTROL, NO_STORE)
                .body(keyService.issue(username, label));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String username = authClient.requireUsername(authorization);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("items", keyService.list(username));
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, NO_STORE)
                .body(body);
    }

    @DeleteMapping("/{keyId}")
    public ResponseEntity<Void> revoke(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable("keyId") long keyId) {
        String username = authClient.requireUsername(authorization);
        keyService.revoke(keyId, username);
        return ResponseEntity.noContent()
                .header(HttpHeaders.CACHE_CONTROL, NO_STORE)
                .build();
    }

    /** 본문이 JSON 이면 label 을 꺼내고, 없거나 JSON 이 아니면 그냥 비운다(이름은 선택값이다). */
    private String readLabel(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(rawBody);
            JsonNode label = node.get("label");
            return label == null || label.isNull() ? null : label.asText();
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /** 키 기능을 쓸 수 있는 상태인지 — 화면이 키 영역을 보여 줄지 판단할 때 쓴다(로그인 불필요). */
    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> status() {
        boolean ready = keyService.isReady();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ready", ready);
        body.put("message", ready
                ? "키를 발급해 사용량을 확인할 수 있습니다."
                : "키 기능 준비 중입니다. 지금은 키 없이 공개 API 를 부를 수 있습니다.");
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, NO_STORE)
                .body(body);
    }
}
