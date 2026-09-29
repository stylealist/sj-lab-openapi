package com.example.openapi.web;

import com.example.openapi.proxy.ApiProxyException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/** 오류도 사람이 읽을 수 있는 JSON 한 덩어리로 돌려준다. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiProxyException.class)
    public ResponseEntity<Map<String, Object>> handleProxyException(ApiProxyException e) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", e.getCode());
        error.put("message", e.getMessage());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);

        return ResponseEntity.status(e.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
