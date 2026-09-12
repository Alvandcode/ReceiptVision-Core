package com.receiptvision.core.security;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Simple in-memory per-IP rate limiter for brute-force / spam protection.
 * No external dependency (no Bucket4j/Redis) so it works in the single-jar
 * and Docker deployments. For multi-instance deployments put a reverse proxy
 * rate-limit (Caddy / nginx) in front as well.
 *
 * Limits (per IP, sliding window 60s):
 * - /api/auth/** : 20 req/min (login/register brute-force + enumeration)
 * - POST /api/receipts : 30 req/min (expensive Tesseract fork per request)
 * Others are not limited here.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final int authMaxPerMinute;
    private final int uploadMaxPerMinute;

    private final Map<String, Deque<Long>> authHits = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> uploadHits = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(
            @Value("${app.ratelimit.auth-per-minute:20}") int authMaxPerMinute,
            @Value("${app.ratelimit.upload-per-minute:30}") int uploadMaxPerMinute) {
        this.authMaxPerMinute = authMaxPerMinute;
        this.uploadMaxPerMinute = uploadMaxPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return true;
        }
        // Health must never be rate-limited (Docker HEALTHCHECK / Caddy).
        if (path.startsWith("/actuator/health")) {
            return true;
        }
        boolean isAuth = path.startsWith("/api/auth/");
        boolean isUpload = path.startsWith("/api/receipts") && "POST".equalsIgnoreCase(request.getMethod());
        return !(isAuth || isUpload);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean isAuth = path.startsWith("/api/auth/");
        Map<String, Deque<Long>> store = isAuth ? authHits : uploadHits;
        int max = isAuth ? authMaxPerMinute : uploadMaxPerMinute;

        String key = clientKey(request) + (isAuth ? ":auth" : ":upload");
        if (!allow(store, key, max, 60_000L)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.setHeader("Retry-After", "60");
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"Too Many Requests\","
                            + "\"message\":\"Too many requests. Please wait a minute and retry.\","
                            + "\"path\":\"" + escapeJson(path) + "\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static String clientKey(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        return ip == null ? "unknown" : ip;
    }

    private static boolean allow(Map<String, Deque<Long>> store, String key, int max, long windowMillis) {
        long now = System.currentTimeMillis();
        Deque<Long> deque = store.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && now - deque.peekFirst() > windowMillis) {
                deque.pollFirst();
            }
            if (deque.size() >= max) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
