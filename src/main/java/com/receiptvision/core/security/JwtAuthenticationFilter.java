package com.receiptvision.core.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final TokenBlacklist tokenBlacklist;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService,
            TokenBlacklist tokenBlacklist) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.tokenBlacklist = tokenBlacklist;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            // Prefer explicit Bearer header (API clients); fall back to HttpOnly
            // access cookie rv_at (browser UI, XSS-safe storage).
            String token = bearerToken(request);
            boolean viaCookie = false;
            if (token == null) {
                token = cookieToken(request, "rv_at");
                viaCookie = token != null;
            }
            if (token != null && !token.isBlank()) {
                try {
                    // Single parse: verifies signature + expiry. Throws on invalid/expired.
                    io.jsonwebtoken.Claims claims = jwtService.parseAndValidate(token);
                    String jti = claims.getId();
                    if (jti != null && tokenBlacklist.isRevoked(jti)) {
                        // Revoked via /api/auth/logout: stay anonymous -> 401 downstream.
                    } else {
                        String username = claims.getSubject();
                        if (username != null && !username.isBlank()) {
                            if (viaCookie && isStateChanging(request)
                                    && !hasCsrfHeader(request) && !hasSameOrigin(request)) {
                                // Cookie-authenticated writes require AJAX marker or
                                // same-origin proof (SameSite=Strict is the primary
                                // defense; this is defense-in-depth).
                                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                response.setContentType("application/json");
                                response.getWriter().write(
                                        "{\"status\":403,\"error\":\"Forbidden\","
                                                + "\"message\":\"Missing CSRF protection header.\","
                                                + "\"path\":\"" + escapeJson(request.getRequestURI()) + "\"}");
                                return;
                            }
                            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(auth);
                            request.setAttribute("authViaCookie", viaCookie);
                        }
                    }
                } catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) {
                    // User deleted after token issuance: stay anonymous -> 401 downstream.
                    org.slf4j.LoggerFactory.getLogger(JwtAuthenticationFilter.class)
                            .debug("JWT for deleted user rejected");
                } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
                    org.slf4j.LoggerFactory.getLogger(JwtAuthenticationFilter.class)
                            .debug("Invalid JWT rejected: {}", e.getMessage());
                }
            }
        }
        chain.doFilter(request, response);
    }

    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }

    private static String cookieToken(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie c : request.getCookies()) {
            if (name.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return c.getValue();
            }
        }
        return null;
    }

    private static boolean isStateChanging(HttpServletRequest request) {
        String m = request.getMethod();
        return !"GET".equalsIgnoreCase(m) && !"HEAD".equalsIgnoreCase(m) && !"OPTIONS".equalsIgnoreCase(m);
    }

    private static boolean hasCsrfHeader(HttpServletRequest request) {
        String v = request.getHeader("X-Requested-With");
        return v != null && !v.isBlank();
    }

    private static boolean hasSameOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        String referer = request.getHeader("Referer");
        String host = request.getHeader("Host");
        if (host == null || host.isBlank()) {
            return false;
        }
        String hostOnly = host.trim().toLowerCase();
        if (origin != null && !origin.isBlank()) {
            try {
                String originHost = new java.net.URI(origin).getHost();
                if (originHost != null && originHost.equalsIgnoreCase(hostOnly.split(":")[0])) {
                    return true;
                }
            } catch (java.net.URISyntaxException ignored) {
                return false;
            }
            return false;
        }
        if (referer != null && !referer.isBlank()) {
            try {
                String refHost = new java.net.URI(referer).getHost();
                if (refHost != null && refHost.equalsIgnoreCase(hostOnly.split(":")[0])) {
                    return true;
                }
            } catch (java.net.URISyntaxException ignored) {
                return false;
            }
        }
        return false;
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
