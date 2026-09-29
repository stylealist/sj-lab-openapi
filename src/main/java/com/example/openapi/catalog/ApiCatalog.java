package com.example.openapi.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 공개 API 카탈로그. 화면(문서·실행해보기)과 중계 허용 목록이 모두 이 한 파일에서 나온다.
 * 정의에 없는 경로·파라미터는 중계하지 않으므로, 새 API 를 열려면 api-catalog.json 에 먼저 추가할 것.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiCatalog(
        String version,
        String updatedAt,
        String notice,
        List<ApiGroup> groups) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiGroup(
            String id,
            String title,
            String description,
            List<ApiDefinition> apis) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiDefinition(
            String id,
            String title,
            String method,
            String path,
            String upstream,
            String summary,
            String responseType,
            List<ApiParam> params) {

        /** 쿼리 파라미터만(경로 변수 제외) */
        public List<ApiParam> queryParams() {
            return params == null ? List.of() : params.stream().filter(p -> !p.isPathParam()).toList();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiParam(
            String name,
            String type,
            boolean required,
            String in,
            String description,
            String example) {

        public boolean isPathParam() {
            return "path".equalsIgnoreCase(in);
        }
    }
}
