package com.example.openapi.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 카탈로그 JSON 을 기동 시 한 번 읽어 두고, 화면용 원문과 중계용 조회를 함께 제공한다.
 */
@Service
public class ApiCatalogService {

    private static final Logger log = LoggerFactory.getLogger(ApiCatalogService.class);
    private static final String CATALOG_PATH = "catalog/api-catalog.json";

    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private String rawJson;
    private ApiCatalog catalog;
    private List<ApiCatalog.ApiDefinition> definitions = List.of();

    public ApiCatalogService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() throws IOException {
        try (InputStream in = new ClassPathResource(CATALOG_PATH).getInputStream()) {
            rawJson = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        catalog = objectMapper.readValue(rawJson, ApiCatalog.class);

        List<ApiCatalog.ApiDefinition> all = new ArrayList<>();
        for (ApiCatalog.ApiGroup group : catalog.groups()) {
            all.addAll(group.apis());
        }
        definitions = List.copyOf(all);
        log.info("API 카탈로그 로드: 그룹 {}개, 엔드포인트 {}개", catalog.groups().size(), definitions.size());
    }

    /** 화면이 그대로 쓰는 원문 JSON */
    public String rawJson() {
        return rawJson;
    }

    public ApiCatalog catalog() {
        return catalog;
    }

    public List<ApiCatalog.ApiDefinition> definitions() {
        return definitions;
    }

    /**
     * 요청 경로(context-path 를 뺀 "/v1/...")에 해당하는 정의를 찾는다.
     * "/v1/facilities/{totalId}" 처럼 경로 변수가 있는 정의도 매칭한다.
     */
    public Optional<ApiCatalog.ApiDefinition> findByPath(String requestPath) {
        return definitions.stream()
                .filter(def -> pathMatcher.match(def.path(), requestPath))
                .findFirst();
    }

    /** 매칭된 정의에서 경로 변수 값을 뽑는다. */
    public Map<String, String> extractPathVariables(ApiCatalog.ApiDefinition definition, String requestPath) {
        return pathMatcher.extractUriTemplateVariables(definition.path(), requestPath);
    }
}
