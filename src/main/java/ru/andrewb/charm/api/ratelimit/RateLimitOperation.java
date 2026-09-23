package ru.andrewb.charm.api.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

public enum RateLimitOperation {
    LOGIN("/api/v1/auth/login", "Login"),
    REGISTRATION("/api/v1/users", "Registration"),
    REFRESH("/api/v1/auth/refresh", "Refresh");

    private final String path;
    private final String label;

    RateLimitOperation(String path, String label) {
        this.path = path;
        this.label = label;
    }

    public boolean matches(HttpServletRequest request) {
        return "POST".equals(request.getMethod())
                && (request.getContextPath() + path).equals(request.getRequestURI());
    }

    public String keyPart() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public String limitExceededDetail() {
        return label + " request rate limit exceeded";
    }
}
