package com.example.openapi.proxy;

import com.example.openapi.auth.AuthClient;
import com.example.openapi.catalog.ApiCatalog;
import com.example.openapi.catalog.ApiCatalogService;
import com.example.openapi.config.ApiKeyProperties;
import com.example.openapi.config.UpstreamConfig;
import com.example.openapi.key.ApiKeyRepository;
import com.example.openapi.key.ApiKeyService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 카탈로그에 정의된 엔드포인트만 mapservice-rest 로 중계한다.
 * 정의에 없는 경로·파라미터는 올려보내지 않으므로, 여기가 사실상 공개 범위의 경계다.
 */
@Service
public class ApiProxyService {

    private static final Logger log = LoggerFactory.getLogger(ApiProxyService.class);

    /** 키를 쿼리로 붙일 때 쓰는 이름. 이 값은 원천으로 올려보내지 않는다. */
    public static final String API_KEY_PARAM = "apiKey";

    private final ApiCatalogService catalogService;
    private final ApiKeyService keyService;
    private final ApiKeyProperties keyProperties;
    private final AuthClient authClient;
    private final RestTemplate restTemplate;
    private final UpstreamConfig.UpstreamProperties properties;

    public ApiProxyService(ApiCatalogService catalogService,
                           ApiKeyService keyService,
                           ApiKeyProperties keyProperties,
                           AuthClient authClient,
                           @Qualifier("loadBalancedUpstreamRestTemplate") RestTemplate loadBalanced,
                           @Qualifier("directUpstreamRestTemplate") RestTemplate direct,
                           UpstreamConfig.UpstreamProperties upstreamProperties) {
        this.catalogService = catalogService;
        this.keyService = keyService;
        this.keyProperties = keyProperties;
        this.authClient = authClient;
        // 서비스 이름(MAPSERVICE-REST)이면 로드밸런서를, 실제 주소면 그대로 부르는 쪽을 쓴다.
        this.restTemplate = upstreamProperties.isServiceName() ? loadBalanced : direct;
        this.properties = upstreamProperties;
        log.info("원천 호출 대상: {} ({})", upstreamProperties.getBaseUrl(),
                upstreamProperties.isServiceName() ? "Eureka 서비스 이름" : "실제 주소");
    }

    public ResponseEntity<byte[]> relay(String requestPath, Map<String, String[]> rawQueryParams,
                                        String presentedKey, String authorizationHeader) {
        ApiCatalog.ApiDefinition definition = catalogService.findByPath(requestPath)
                .orElseThrow(() -> new ApiProxyException(HttpStatus.NOT_FOUND, "UNKNOWN_API",
                        "제공하지 않는 경로입니다: " + requestPath + " (목록은 /open-api/catalog 참고)"));

        // apiKey 는 이 서비스가 쓰는 값이라 원천으로 올려보내지 않고, 모르는 파라미터로도 보지 않는다.
        Map<String, String[]> queryParams = new LinkedHashMap<>(rawQueryParams);
        String[] keyFromQuery = queryParams.remove(API_KEY_PARAM);
        String apiKey = StringUtils.hasText(presentedKey) || keyFromQuery == null || keyFromQuery.length == 0
                ? presentedKey
                : keyFromQuery[0];

        // 호출자를 정하는 순서:
        //   1) X-API-Key / apiKey — 밖에서 curl·코드로 부를 때
        //   2) Authorization: Bearer — 활용 페이지처럼 로그인한 화면에서 부를 때.
        //      키 원문은 저장하지 않으므로 화면이 원문을 모를 수 있다. 그 경우에도 호출이 되고
        //      사용량이 그 계정 키에 쌓이게 하려고 이 경로를 둔다(2026-10-01).
        //   3) 둘 다 없으면 openapi.api-key.required 가 정한다(기본 401).
        Optional<ApiKeyRepository.ApiKeyOwner> owner;
        if (StringUtils.hasText(apiKey)) {
            owner = keyService.verifyForCall(apiKey);
        } else if (StringUtils.hasText(authorizationHeader)) {
            owner = keyService.verifyForLoggedInUser(authClient.requireUsername(authorizationHeader));
        } else {
            requireKeyIfConfigured(null);
            owner = Optional.empty();
        }

        Map<String, String> pathVariables = catalogService.extractPathVariables(definition, requestPath);
        validatePathVariables(pathVariables);
        validateQueryParams(definition, queryParams);

        URI uri = buildUpstreamUri(definition, pathVariables, queryParams);
        long startedAt = System.currentTimeMillis();
        try {
            ResponseEntity<byte[]> upstream = restTemplate.getForEntity(uri, byte[].class);
            recordUsage(owner, definition, upstream.getStatusCode().value(), startedAt);
            return buildResponse(definition, upstream);
        } catch (HttpStatusCodeException e) {
            // 원천이 400·404 를 돌려주면 그대로 전달한다(시설물 상세의 "없는 ID" 등).
            recordUsage(owner, definition, e.getStatusCode().value(), startedAt);
            return ResponseEntity.status(e.getStatusCode())
                    .contentType(resolveContentType(definition, e.getResponseHeaders() == null
                            ? null : e.getResponseHeaders().getContentType()))
                    .header("X-SjLab-Api", definition.id())
                    .body(e.getResponseBodyAsByteArray());
        } catch (RestClientException e) {
            log.warn("원천 호출 실패 api={} uri={} 원인={}", definition.id(), uri, e.getClass().getSimpleName());
            recordUsage(owner, definition, HttpStatus.BAD_GATEWAY.value(), startedAt);
            throw new ApiProxyException(HttpStatus.BAD_GATEWAY, "UPSTREAM_UNAVAILABLE",
                    "데이터 서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    /**
     * 키 없는 호출을 막을지 결정한다(<code>openapi.api-key.required</code>).
     *
     * <p>막도록 설정했는데 키 저장소가 준비되지 않았으면 <b>통과시키지 않고 503</b> 을 낸다.
     * 키를 검증할 수 없는 상태에서 그냥 통과시키면 "필수"가 조용히 풀려 아무나 부를 수 있게 되기 때문이다.
     * 반대로 이 설정을 꺼 두면 키 없는 호출이 종전처럼 그대로 통과한다(사용량·한도만 키에 적용).
     */
    private void requireKeyIfConfigured(String apiKey) {
        if (!keyProperties.isRequired() || StringUtils.hasText(apiKey)) {
            return;
        }
        if (!keyService.isReady()) {
            throw new ApiProxyException(HttpStatus.SERVICE_UNAVAILABLE, "NOT_CONFIGURED",
                    "지금은 키를 확인할 수 없어 호출을 받지 못합니다. 잠시 후 다시 시도해 주세요.");
        }
        throw new ApiProxyException(HttpStatus.UNAUTHORIZED, "API_KEY_REQUIRED",
                "API 키가 필요합니다. 로그인해 키를 발급받은 뒤 X-API-Key 헤더(또는 apiKey 쿼리)로 보내세요.");
    }

    private void recordUsage(Optional<ApiKeyRepository.ApiKeyOwner> owner,
                             ApiCatalog.ApiDefinition definition, int statusCode, long startedAt) {
        owner.ifPresent(value -> keyService.recordUsage(value, definition.id(), statusCode,
                (int) (System.currentTimeMillis() - startedAt)));
    }

    /**
     * 경로 값이 비었거나 자리표시자(<code>{totalId}</code>)가 그대로 남아 있으면 400 으로 알려 준다.
     * 전에는 그 값을 그대로 원천에 넘겨 본문 없는 400 이 돌아와, 무엇이 빠졌는지 알 수 없었다.
     */
    private void validatePathVariables(Map<String, String> pathVariables) {
        for (Map.Entry<String, String> entry : pathVariables.entrySet()) {
            String value = entry.getValue();
            if (!StringUtils.hasText(value) || value.startsWith("{")) {
                throw new ApiProxyException(HttpStatus.BAD_REQUEST, "MISSING_PATH_PARAMETER",
                        "경로 값이 없습니다: " + entry.getKey());
            }
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
