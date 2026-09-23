package ru.andrewb.charm.api.ratelimit;

import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(
            RateLimitService rateLimitService,
            ObjectMapper objectMapper
    ) {
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        for (RateLimitOperation operation : RateLimitOperation.values()) {
            if (operation.matches(request)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        RateLimitOperation operation = null;
        for (RateLimitOperation candidate : RateLimitOperation.values()) {
            if (candidate.matches(request)) {
                operation = candidate;
                break;
            }
        }
        if (operation == null) {
            filterChain.doFilter(request, response);
            return;
        }

        ConsumptionProbe probe;

        try {
            probe = rateLimitService.tryConsume(operation, request.getRemoteAddr());
        } catch (RuntimeException exception) {
            log.atError()
                    .addKeyValue("operation", operation.keyPart())
                    .addKeyValue("exceptionType", exception.getClass().getName())
                    .log("rate_limit_check_failed");

            writeProblem(
                    request,
                    response,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Service temporarily unavailable",
                    "Unable to check request rate limit"
            );
            return;
        }

        if (!probe.isConsumed()) {
            long waitNanos = probe.getNanosToWaitForRefill();

            long retryAfterSeconds = Math.max(
                    1,
                    waitNanos / 1_000_000_000L
                            + (waitNanos % 1_000_000_000L == 0 ? 0 : 1)
            );

            response.setHeader(
                    HttpHeaders.RETRY_AFTER,
                    Long.toString(retryAfterSeconds)
            );

            writeProblem(
                    request,
                    response,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many requests",
                    operation.limitExceededDetail()
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeProblem(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String title,
            String detail
    ) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);

        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
