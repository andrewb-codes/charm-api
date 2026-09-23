package ru.andrewb.charm.api.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class HttpRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestLoggingFilter.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String suppliedId = request.getHeader(REQUEST_ID_HEADER);
        String requestId = suppliedId != null && suppliedId.matches("[a-zA-Z0-9_-]{1,64}")
                ? suppliedId : UUID.randomUUID().toString();
        String previousId = MDC.get("requestId");
        MDC.put("requestId", requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        long startedAt = System.nanoTime();
        boolean failed = false;

        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            failed = true;
            // Exception messages may contain credentials or signed storage URLs.
            log.atError()
                    .addKeyValue("exceptionType", exception.getClass().getName())
                    .log("http_request_failed");
            throw exception;
        } finally {
            try {
                int status = failed ? 500 : response.getStatus();
                var event = status >= 500 ? log.atError()
                        : status >= 400 ? log.atWarn() : log.atInfo();
                event.addKeyValue("method", request.getMethod())
                        .addKeyValue("path", request.getRequestURI())
                        .addKeyValue("status", status)
                        .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(
                                System.nanoTime() - startedAt))
                        .log("http_request_completed");
            } finally {
                if (previousId == null) {
                    MDC.remove("requestId");
                } else {
                    MDC.put("requestId", previousId);
                }
            }
        }
    }
}
