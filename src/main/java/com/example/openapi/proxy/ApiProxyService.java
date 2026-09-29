package com.example.openapi.proxy;

import com.example.openapi.catalog.ApiCatalog;
import com.example.openapi.catalog.ApiCatalogService;
import com.example.openapi.config.UpstreamConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 카탈로그에 정의된 엔드포인트만 mapservice-rest 로 중계한다.
 * 정의에 없는 경로·파라미터는 올려보내지 않으므로, 여기가 사실상 공개 범위의 경계다.
 */
@Service
public class ApiProxyService {

    private static final Logger log = LoggerFactory.getLogger(ApiProxyService.class);

    private final ApiCatalogService catalogService;
    private final RestTemplate restTemplate;
    private final UpstreamConfig.UpstreamProperties properties;

    public ApiProxyService(ApiCatalogService catalogService,
                           @Qualifier("loadBalancedUpstreamRestTemplate") RestTemplate loadBalanced,
                           @Qualifier("directUpstreamRestTemplate") RestTemplate direct,
                           UpstreamConfig.UpstreamProperties upstreamProperties) {
        this.catalogService = catalogService;
        // 서비스 이름(MAPSERVICE-REST)이면 로드밸런서를, 실제 주소면 그대로 부르는 쪽을 쓴다.
        this.restTemplate = upstreamProperties.isServiceName() ? loadBalanced : direct;
        this.properties = upstreamProperties;
        log.info("원천 호출 대상: {} ({})", upstreamProperties.getBaseUrl(),
                upstreamProperties.isServiceName() ? "Eureka 서비스 이름" : "실제 주소");
    }

    public ResponseEntity<byte[]> relay(String requestPath, Map<String, String[]> queryParams) {
        ApiCatalog.ApiDefinition definition = catalogService.findByPath(requestPath)
                .orElseThrow(() -> new ApiProxyException(HttpStatus.NOT_FOUND, "UNKNOWN_API",
                        "제공하지 않는 경로입니다: " + requestPath + " (목록은 /open-api/catalog 참고)"));

        Map<String, String> pathVariables = catalogService.extractPathVariables(definition, requestPath);
        validateQueryParams(definition, queryParams);

        URI uri = buildUpstreamUri(definition, pathVariables, queryParams);
        try {
            ResponseEntity<byte[]> upstream = restTemplate.getForEntity(uri, byte[].class);
            return buildResponse(definition, upstream);
        } catch (HttpStatusCodeException e) {
            // 원천이 400·404 를 돌려주면 그대로 전달한다(시설물 상세의 "없는 ID" 등).
            return ResponseEntity.status(e.getStatusCode())
                    .contentType(resolveContentType(definition, e.getResponseHeaders() == null
                            ? null : e.getResponseHeaders().getContentType()))
                    .header("X-SjLab-Api", definition.id())
                    .body(e.getResponseBodyAsByteArray());
        } catch (RestClientException e) {
            log.warn("원천 호출 실패 api={} uri={} 원인={}", definition.id(), uri, e.getClass().getSimpleName());
            throw new ApiProxyException(HttpStatus.BAD_GATEWAY, "UPSTREAM_UNAVAILABLE",
                    "데이터 서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private void validateQueryParams(ApiCatalog.ApiDefinition definition, Map<String, String[]> queryParams) {
        List<String> allowed = definition.queryParams().stream().map(ApiCatalog.ApiParam::name).toList();

        String unknown = queryParams.keySet().stream()
                .filter(name -> !allowed.contains(name))
                .collect(Collectors.joining(", "));
        if (StringUtils.hasText(unknown)) {
            throw new ApiProxyException(HttpStatus.BAD_REQUEST, "UNKNOWN_PARAMETER",
                    "지원하지 않는 파라미터입니다: " + unknown
                            + (allowed.isEmpty() ? " (이 API 는 파라미터가 없습니다)" : " (사용 가능: " + String.join(", ", allowed) + ")"));
        }

        for (ApiCatalog.ApiParam param : definition.queryParams()) {
            if (param.required() && !hasValue(queryParams.get(param.name()))) {
                throw new ApiProxyException(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                        "필수 파라미터가 없습니다: " + param.name());
            }
        }
    }

    private boolean hasValue(String[] values) {
        return values != null && values.length > 0 && StringUtils.hasText(values[0]);
    }

    private URI buildUpstreamUri(ApiCatalog.ApiDefinition definition,
                                 Map<String, String> pathVariables,
                                 Map<String, String[]> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(properties.getBaseUrl())
                .path(definition.upstream());

        for (ApiCatalog.ApiParam param : definition.queryParams()) {
            String[] values = queryParams.get(param.name());
            if (hasValue(values)) {
                builder.queryParam(param.name(), values[0]);
            }
        }
        return builder.buildAndExpand(pathVariables).encode().toUri();
    }

    private ResponseEntity<byte[]> buildResponse(ApiCatalog.ApiDefinition definition,
                                                 ResponseEntity<byte[]> upstream) {
        return ResponseEntity.status(upstream.getStatusCode())
                .contentType(resolveContentType(definition, upstream.getHeaders().getContentType()))
                .header("X-SjLab-Api", definition.id())
                .header("Cache-Control", upstream.getHeaders().getCacheControl() == null
                        ? "public, max-age=60" : upstream.getHeaders().getCacheControl())
                .body(upstream.getBody());
    }

    private MediaType resolveContentType(ApiCatalog.ApiDefinition definition, MediaType upstreamType) {
        if (upstreamType != null) {
            return upstreamType;
        }
        return StringUtils.hasText(definition.responseType())
                ? MediaType.parseMediaType(definition.responseType())
                : MediaType.APPLICATION_JSON;
    }
}
