package com.example.openapi.key;

import com.example.openapi.config.ApiKeyProperties;
import com.example.openapi.proxy.ApiProxyException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 키 저장소(DB)가 없을 때의 동작을 확인한다. 표 생성(DDL)은 담당자가 하므로,
 * 준비되지 않은 상태에서도 공개 API 조회는 막히지 않아야 한다.
 */
class ApiKeyServiceTests {

    /** DataSource 가 없을 때와 같은 상황 — JdbcTemplate 빈이 없다. */
    private static final ObjectProvider<JdbcTemplate> NO_JDBC = new ObjectProvider<>() {
        @Override
        public JdbcTemplate getObject(Object... args) {
            return null;
        }

        @Override
        public JdbcTemplate getObject() {
            return null;
        }

        @Override
        public JdbcTemplate getIfAvailable() {
            return null;
        }

        @Override
        public JdbcTemplate getIfUnique() {
            return null;
        }
    };

    private ApiKeyService service(boolean enabled) {
        ApiKeyProperties properties = new ApiKeyProperties();
        properties.setEnabled(enabled);
        return new ApiKeyService(new ApiKeyRepository(NO_JDBC, properties), properties);
    }

    @Test
    void 저장소가_없으면_준비되지_않은_상태다() {
        assertFalse(service(true).isReady());
        assertFalse(service(false).isReady());
    }

    @Test
    void 키_없이_부르면_그냥_통과한다() {
        assertTrue(service(false).verifyForCall(null).isEmpty());
        assertTrue(service(false).verifyForCall("   ").isEmpty());
    }

    @Test
    void 준비되지_않았는데_키를_붙이면_503() {
        ApiProxyException e = assertThrows(ApiProxyException.class,
                () -> service(false).verifyForCall("sjlab_whatever"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
        assertEquals("NOT_CONFIGURED", e.getCode());
    }

    @Test
    void 준비되지_않으면_발급도_503() {
        ApiProxyException e = assertThrows(ApiProxyException.class, () -> service(true).issue("tester", "내 키"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
    }
}
