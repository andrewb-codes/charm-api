package ru.andrewb.charm.api.ratelimit;

import io.github.bucket4j.ConsumptionProbe;
import io.lettuce.core.RedisConnectionException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    private final RateLimitService service = mock(RateLimitService.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final RateLimitFilter filter = new RateLimitFilter(service, mapper);
    private final FilterChain chain = mock(FilterChain.class);

    @ParameterizedTest
    @CsvSource({
            "LOGIN, /api/v1/auth/login",
            "REGISTRATION, /api/v1/users",
            "REFRESH, /api/v1/auth/refresh"
    })
    void failsClosedWithoutExposingRedisError(RateLimitOperation operation, String path)
            throws Exception {
        var request = request("POST", path);
        var response = new MockHttpServletResponse();
        when(service.tryConsume(operation, request.getRemoteAddr()))
                .thenThrow(new RedisConnectionException("secret-connection-details"));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(mapper.readTree(response.getContentAsString()).get("title").asText())
                .isEqualTo("Service temporarily unavailable");
        assertThat(response.getContentAsString()).doesNotContain("secret-connection-details");
        assertThat(response.getHeader("Retry-After")).isNull();
        verifyNoInteractions(chain);
    }

    @ParameterizedTest
    @CsvSource({
            "LOGIN, /api/v1/auth/login, 5200000000, 6",
            "REGISTRATION, /api/v1/users, 6000000000, 6",
            "REFRESH, /api/v1/auth/refresh, 1, 1",
            "LOGIN, /api/v1/auth/login, 0, 1"
    })
    void roundsRetryAfterUp(RateLimitOperation operation, String path,
                            long waitNanos, String expectedSeconds) throws Exception {
        var request = request("POST", path);
        var response = new MockHttpServletResponse();
        when(service.tryConsume(operation, request.getRemoteAddr()))
                .thenReturn(ConsumptionProbe.rejected(0, waitNanos, waitNanos));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo(expectedSeconds);
        assertThat(mapper.readTree(response.getContentAsString()).get("detail").asText())
                .isEqualTo(operation.limitExceededDetail());
        verifyNoInteractions(chain);
    }

    @Test
    void propagatesDownstreamErrorsInsteadOfReportingRedisFailure() throws Exception {
        var request = request("POST", "/api/v1/auth/login");
        var response = new MockHttpServletResponse();
        var failure = new ServletException("controller failure");
        when(service.tryConsume(RateLimitOperation.LOGIN, request.getRemoteAddr()))
                .thenReturn(ConsumptionProbe.consumed(1, 0));
        doThrow(failure).when(chain).doFilter(request, response);

        assertThatThrownBy(() -> filter.doFilter(request, response, chain)).isSameAs(failure);
        assertThat(response.getStatus()).isNotEqualTo(503);
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /api/v1/auth/login",
            "GET, /api/v1/users",
            "GET, /api/v1/auth/refresh",
            "POST, /api/v1/auth/logout",
            "PUT, /api/v1/users"
    })
    void leavesOtherRequestsUntouched(String method, String path) throws Exception {
        var request = request(method, path);
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verifyNoInteractions(service);
        verify(chain).doFilter(request, response);
    }

    @ParameterizedTest
    @CsvSource({
            "LOGIN, /api/v1/auth/login",
            "REGISTRATION, /api/v1/users",
            "REFRESH, /api/v1/auth/refresh"
    })
    void protectsOperationUnderContextPath(RateLimitOperation operation, String path)
            throws Exception {
        var request = request("POST", "/charm" + path);
        request.setContextPath("/charm");
        var response = new MockHttpServletResponse();
        when(service.tryConsume(operation, request.getRemoteAddr()))
                .thenReturn(ConsumptionProbe.consumed(1, 0));

        filter.doFilter(request, response, chain);

        verify(service).tryConsume(operation, request.getRemoteAddr());
        verify(chain).doFilter(request, response);
    }

    private MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }
}
