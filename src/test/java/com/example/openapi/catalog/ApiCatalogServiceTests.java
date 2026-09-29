package com.example.openapi.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 카탈로그 파일이 깨지면 서비스 전체가 못 뜨므로, 파일 자체를 검증한다. */
class ApiCatalogServiceTests {

    private ApiCatalogService service;

    @BeforeEach
    void setUp() throws IOException {
        service = new ApiCatalogService(new ObjectMapper());
        service.load();
    }

    @Test
    void 카탈로그가_로드되고_엔드포인트가_있다() {
        assertFalse(service.definitions().isEmpty());
        assertFalse(service.catalog().groups().isEmpty());
    }

    @Test
    void 모든_엔드포인트에_필수값이_있고_경로가_겹치지_않는다() {
        Set<String> paths = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (ApiCatalog.ApiDefinition def : service.definitions()) {
            assertTrue(def.path().startsWith("/v1/"), "공개 경로는 /v1/ 로 시작해야 한다: " + def.path());
            assertTrue(def.upstream().startsWith("/map/"), "원천 경로는 /map/ 이어야 한다: " + def.upstream());
            assertEquals("GET", def.method(), "지금은 조회만 연다: " + def.id());
            assertTrue(paths.add(def.path()), "경로가 중복됐다: " + def.path());
            assertTrue(ids.add(def.id()), "id 가 중복됐다: " + def.id());
        }
    }

    @Test
    void 경로변수가_있는_정의도_매칭된다() {
        ApiCatalog.ApiDefinition def = service.findByPath("/v1/facilities/abc-123").orElseThrow();
        assertEquals("facility-detail", def.id());
        assertEquals("abc-123", service.extractPathVariables(def, "/v1/facilities/abc-123").get("totalId"));
    }

    @Test
    void 정의에_없는_경로는_찾지_못한다() {
        assertTrue(service.findByPath("/v1/office-works").isEmpty());
    }
}
