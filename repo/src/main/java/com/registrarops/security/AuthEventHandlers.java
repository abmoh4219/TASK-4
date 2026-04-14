package com.registrarops.security;

import com.registrarops.service.AuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Wires the form-login flow into AuthService.
 *
 *  - On FAILURE (BadCredentials, anything except LockedException): record a failed
 *    login attempt. After 5 attempts in 15 minutes the user is locked out at the
 *    next login (CustomUserDetailsService reads the flag).
 *  - On FAILURE with LockedException: redirect to /login?locked.
 *  - On SUCCESS: clear the failed-attempt counter and run unusual-login detection.
 *
 * These two beans are registered in SecurityConfig.
 */
@Component
public class AuthEventHandlers {

    private static final Logger log = LoggerFactory.getLogger(AuthEventHandlers.class);

    private final AuthService authService;

    public AuthEventHandlers(AuthService authService) {
        this.authService = authService;
    }

    public AuthenticationFailureHandler failureHandler() {
        return new AuthenticationFailureHandler() {
            @Override
            public void onAuthenticationFailure(HttpServletRequest request,
                                                HttpServletResponse response,
                                                AuthenticationException exception)
                    throws IOException, ServletException {
                String username = request.getParameter("username");
                if (username != null && !(exception instanceof LockedException)) {
                    authService.trackFailedLogin(username, request.getRemoteAddr());
                    // Re-check after this insert: if we just hit the lockout threshold,
                    // surface it on the same response so the user sees the locked banner.
                    if (authService.isLockedOut(username)) {
                        response.sendRedirect(request.getContextPath() + "/login?locked");
                        return;
                    }
                }
                if (exception instanceof LockedException) {
                    response.sendRedirect(request.getContextPath() + "/login?locked");
                    return;
                }
                response.sendRedirect(request.getContextPath() + "/login?error");
            }
        };
    }

    public AuthenticationSuccessHandler successHandler() {
        return new AuthenticationSuccessHandler() {
            @Override
            public void onAuthenticationSuccess(HttpServletRequest request,
                                                HttpServletResponse response,
                                                Authentication authentication)
                    throws IOException, ServletException {
                String username = authentication.getName();
                authService.clearAttempts(username);
                try {
                    authService.detectUnusualLogin(username, request);
                } catch (Exception e) {
                    log.warn("unusual-login detection failed for {}: {}", username, e.toString());
                }

                String target = request.getContextPath() + "/";
                for (GrantedAuthority a : authentication.getAuthorities()) {
                    String role = a.getAuthority();
                    if ("ROLE_ADMIN".equals(role))    { target = request.getContextPath() + "/"; break; }
                    if ("ROLE_FACULTY".equals(role))  { target = request.getContextPath() + "/"; break; }
                    if ("ROLE_REVIEWER".equals(role)) { target = request.getContextPath() + "/"; break; }
                    if ("ROLE_STUDENT".equals(role))  { target = request.getContextPath() + "/"; break; }
                }
                response.sendRedirect(target);
            }
        };
    }
}
