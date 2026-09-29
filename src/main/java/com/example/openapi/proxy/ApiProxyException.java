package com.example.openapi.proxy;

import org.springframework.http.HttpStatus;

/** 중계 단계에서 발생한 오류. 코드·메시지를 그대로 JSON 으로 돌려준다. */
public class ApiProxyException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiProxyException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
