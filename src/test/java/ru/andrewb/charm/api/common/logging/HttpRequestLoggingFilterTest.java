package ru.andrewb.charm.api.common.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpRequestLoggingFilterTest {

    private final HttpRequestLoggingFilter filter = new HttpRequestLoggingFilter();
    private final Logger logger = (Logger) LoggerFactory.getLogger(HttpRequestLoggingFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>() {
        @Override
        protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    };

    @BeforeEach
    void captureLogs() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void cleanup() {
        logger.detachAppender(appender);
        appender.stop();
        MDC.clear();
    }

    @Test
    void logsResponseWithRequestIdWithoutRequestSecrets() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.addHeader("X-Request-Id", "test-request-123");
        request.addHeader("Authorization", "Bearer secret-jwt");
        request.setQueryString("token=secret-query");
        request.setContent("secret-password".getBytes());
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get("requestId")).isEqualTo("test-request-123");
            response.setStatus(401);
        });

        assertThat(response.getHeader("X-Request-Id")).isEqualTo("test-request-123");
        assertThat(MDC.get("requestId")).isNull();
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getMDCPropertyMap()).containsEntry("requestId", "test-request-123");
            assertThat(event.getLevel().toString()).isEqualTo("WARN");
            assertThat(event.getKeyValuePairs()).anySatisfy(pair -> {
                assertThat(pair.key).isEqualTo("status");
                assertThat(pair.value).isEqualTo(401);
            });
            assertThat(event.getKeyValuePairs().toString())
                    .contains("/api/v1/auth/login", "durationMs")
                    .doesNotContain("secret-jwt", "secret-query", "secret-password");
        });
    }

    @Test
    void replacesUnsafeRequestIds() throws Exception {
        for (String value : new String[]{"bad\r\nvalue", "a".repeat(65), ""}) {
            var request = new MockHttpServletRequest("GET", "/health");
            request.addHeader("X-Request-Id", value);
            var response = new MockHttpServletResponse();

            filter.doFilter(request, response, (req, res) -> {});

            assertThat(UUID.fromString(response.getHeader("X-Request-Id"))).isNotNull();
        }
    }

    @Test
    void logsUnhandledFailureAndRestoresMdc() {
        MDC.put("requestId", "outer-context");
        var request = new MockHttpServletRequest("GET", "/failure");
        var response = new MockHttpServletResponse();
        var failure = new ServletException("secret-in-exception");

        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
            throw failure;
        })).isSameAs(failure);

        assertThat(MDC.get("requestId")).isEqualTo("outer-context");
        assertThat(UUID.fromString(response.getHeader("X-Request-Id"))).isNotNull();
        assertThat(appender.list).hasSize(2).allSatisfy(event -> {
            assertThat(event.getLevel().toString()).isEqualTo("ERROR");
            assertThat(event.getFormattedMessage()).doesNotContain("secret-in-exception");
            assertThat(event.getThrowableProxy()).isNull();
        });
        assertThat(appender.list.getLast().getKeyValuePairs()).anySatisfy(pair -> {
            assertThat(pair.key).isEqualTo("status");
            assertThat(pair.value).isEqualTo(500);
        });
    }
}
