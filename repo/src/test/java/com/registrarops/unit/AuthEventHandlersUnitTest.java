package com.registrarops.unit;

import com.registrarops.security.AuthEventHandlers;
import com.registrarops.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthEventHandlers — wires the form-login flow into AuthService.
 * All dependencies are Mockito mocks; no Spring context, no network, no database.
 */
class AuthEventHandlersUnitTest {

    private AuthService authService;
    private AuthEventHandlers handlers;
    private AuthenticationFailureHandler failureHandler;
    private AuthenticationSuccessHandler successHandler;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        handlers = new AuthEventHandlers(authService);
        failureHandler = handlers.failureHandler();
        successHandler = handlers.successHandler();
    }

    // ---- failureHandler -------------------------------------------------

    @Test
    void failure_badCredentials_tracksAttemptAndRedirectsToError() throws Exception {
        when(authService.isLockedOut("alice")).thenReturn(false);

        MockHttpServletRequest req = req("alice");
        MockHttpServletResponse res = new MockHttpServletResponse();
        failureHandler.onAuthenticationFailure(req, res, new BadCredentialsException("bad"));

        verify(authService).trackFailedLogin(eq("alice"), anyString());
        assertRedirect(res, "/login?error");
    }

    @Test
    void failure_badCredentials_afterReachingThreshold_redirectsToLocked() throws Exception {
        // The same request that triggers the 5th failure should see ?locked.
        when(authService.isLockedOut("bob")).thenReturn(true);

        MockHttpServletRequest req = req("bob");
        MockHttpServletResponse res = new MockHttpServletResponse();
        failureHandler.onAuthenticationFailure(req, res, new BadCredentialsException("bad"));

        verify(authService).trackFailedLogin(eq("bob"), anyString());
        assertRedirect(res, "/login?locked");
    }

    @Test
    void failure_lockedException_redirectsToLockedWithoutTracking() throws Exception {
        MockHttpServletRequest req = req("carol");
        MockHttpServletResponse res = new MockHttpServletResponse();
        failureHandler.onAuthenticationFailure(req, res, new LockedException("locked"));

        verify(authService, never()).trackFailedLogin(anyString(), anyString());
        assertRedirect(res, "/login?locked");
    }

    @Test
    void failure_nullUsername_doesNotTrack() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        // no username parameter
        MockHttpServletResponse res = new MockHttpServletResponse();
        failureHandler.onAuthenticationFailure(req, res, new BadCredentialsException("bad"));

        verify(authService, never()).trackFailedLogin(anyString(), anyString());
        assertRedirect(res, "/login?error");
    }

    // ---- successHandler -------------------------------------------------

    @Test
    void success_clearsAttemptsAndRedirectsToRoot() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        var auth = new UsernamePasswordAuthenticationToken("dave", null,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));

        successHandler.onAuthenticationSuccess(req, res, auth);

        verify(authService).clearAttempts("dave");
        verify(authService).detectUnusualLogin(eq("dave"), eq(req));
        assertRedirect(res, "/");
    }

    @Test
    void success_adminRole_redirectsToRoot() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        var auth = new UsernamePasswordAuthenticationToken("admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        successHandler.onAuthenticationSuccess(req, res, auth);

        assertRedirect(res, "/");
        verify(authService).clearAttempts("admin");
    }

    @Test
    void success_detectUnusualLoginThrows_doesNotPropagateException() throws Exception {
        doThrow(new RuntimeException("db error"))
                .when(authService).detectUnusualLogin(anyString(), any());

        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        var auth = new UsernamePasswordAuthenticationToken("eve", null,
                List.of(new SimpleGrantedAuthority("ROLE_FACULTY")));

        // should complete without throwing
        assertDoesNotThrow(() -> successHandler.onAuthenticationSuccess(req, res, auth));
        verify(authService).clearAttempts("eve");
        assertRedirect(res, "/");
    }

    @Test
    void success_facultyRole_redirectsToRoot() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        var auth = new UsernamePasswordAuthenticationToken("fac", null,
                List.of(new SimpleGrantedAuthority("ROLE_FACULTY")));

        successHandler.onAuthenticationSuccess(req, res, auth);
        assertRedirect(res, "/");
    }

    @Test
    void success_reviewerRole_redirectsToRoot() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        var auth = new UsernamePasswordAuthenticationToken("rev", null,
                List.of(new SimpleGrantedAuthority("ROLE_REVIEWER")));

        successHandler.onAuthenticationSuccess(req, res, auth);
        assertRedirect(res, "/");
    }

    @Test
    void failure_contextPath_isIncludedInRedirect() throws Exception {
        when(authService.isLockedOut("x")).thenReturn(false);
        MockHttpServletRequest req = req("x");
        req.setContextPath("/myapp");
        MockHttpServletResponse res = new MockHttpServletResponse();

        failureHandler.onAuthenticationFailure(req, res, new BadCredentialsException("bad"));

        assertTrue(res.getRedirectedUrl().startsWith("/myapp"),
                "context path must be prepended to redirect URL");
    }

    @Test
    void success_contextPath_isPrependedToSuccessRedirect() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setContextPath("/registrar");
        MockHttpServletResponse res = new MockHttpServletResponse();
        var auth = new UsernamePasswordAuthenticationToken("admin2", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        successHandler.onAuthenticationSuccess(req, res, auth);

        assertNotNull(res.getRedirectedUrl());
        assertTrue(res.getRedirectedUrl().startsWith("/registrar"),
                "success redirect must include context path");
        verify(authService).clearAttempts("admin2");
    }

    @Test
    void failure_emptyUsername_stillTracksAttempt() throws Exception {
        // username="" is not null so the handler still calls trackFailedLogin
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addParameter("username", "");
        req.setRemoteAddr("10.0.0.1");
        when(authService.isLockedOut("")).thenReturn(false);
        MockHttpServletResponse res = new MockHttpServletResponse();

        failureHandler.onAuthenticationFailure(req, res, new BadCredentialsException("bad"));

        verify(authService).trackFailedLogin(eq(""), eq("10.0.0.1"));
        assertRedirect(res, "/login?error");
    }

    @Test
    void failure_lockedException_withUsername_stillNoTracking() throws Exception {
        // LockedException is always handled without calling trackFailedLogin,
        // even when a username is present in the request.
        MockHttpServletRequest req = req("frank");
        MockHttpServletResponse res = new MockHttpServletResponse();

        failureHandler.onAuthenticationFailure(req, res, new LockedException("account locked"));

        verify(authService, never()).trackFailedLogin(anyString(), anyString());
        verify(authService, never()).isLockedOut(anyString());
        assertRedirect(res, "/login?locked");
    }

    @Test
    void success_detectUnusualLoginCalledWithCorrectArgs() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        var auth = new UsernamePasswordAuthenticationToken("george", null,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));

        successHandler.onAuthenticationSuccess(req, res, auth);

        verify(authService).detectUnusualLogin(eq("george"), same(req));
    }

    // --- helpers ----------------------------------------------------------

    private static MockHttpServletRequest req(String username) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.addParameter("username", username);
        r.setRemoteAddr("127.0.0.1");
        return r;
    }

    private static void assertRedirect(MockHttpServletResponse res, String expectedSuffix) {
        String url = res.getRedirectedUrl();
        assertNotNull(url, "response must redirect");
        assertTrue(url.endsWith(expectedSuffix),
                "redirect '" + url + "' must end with '" + expectedSuffix + "'");
    }
}
