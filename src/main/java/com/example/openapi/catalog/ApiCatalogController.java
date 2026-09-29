package com.example.openapi.catalog;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 활용 페이지가 화면을 그릴 때 쓰는 API 목록. 문서와 실제 동작이 어긋나지 않도록
 * 중계 허용 목록과 같은 파일을 그대로 내려준다.
 */
@RestController
public class ApiCatalogController {

    private final ApiCatalogService catalogService;

    public ApiCatalogController(ApiCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping(value = "/catalog", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getCatalog() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(catalogService.rawJson());
    }
}
