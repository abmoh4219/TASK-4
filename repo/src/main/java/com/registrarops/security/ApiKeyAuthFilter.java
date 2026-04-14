package com.registrarops.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Machine-to-machine API key auth for /api/v1/import/** and /api/v1/export/**.
 *
 * Browsers continue to use form-login + session + CSRF; non-browser clients
 * present {@code X-API-Key: <key>} (or {@code Authorization: Bearer <key>}) and
 * are authenticated as a synthetic {@code api-integration} principal with the
 * {@code ROLE_ADMIN} authority — so existing @PreAuthorize("hasRole('ADMIN')")
 * checks continue to apply uniformly.
 *
 * Set the key via {@code REGISTRAROPS_API_KEY} env var (or
 * {@code registrarops.api-key} property). When unset the filter is a no-op,
 * so existing browser flows are never broken by mis-configuration.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";
    public static final String PRINCIPAL = "api-integration";

    private final String configuredKey;

    public ApiKeyAuthFilter(@Value("${registrarops.api-key:${REGISTRAROPS_API_KEY:}}") String configuredKey) {
        this.configuredKey = configuredKey == null ? "" : configuredKey.trim();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path != null && (path.startsWith("/api/v1/import") || path.startsWith("/api/v1/export"))) {
            String presented = request.getHeader(HEADER);
            if (presented == null || presented.isBlank()) {
                String auth = request.getHeader("Authorization");
                if (auth != null && auth.startsWith("Bearer ")) {
                    presented = auth.substring("Bearer ".length()).trim();
                }
            }
            if (presented != null && !presented.isBlank()
                    && !configuredKey.isEmpty()
                    && constantTimeEquals(presented, configuredKey)) {
                UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                        PRINCIPAL, "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
                SecurityContextHolder.getContext().setAuthentication(token);
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }
}
