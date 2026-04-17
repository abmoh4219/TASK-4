package com.registrarops.unit;

import com.registrarops.entity.*;
import com.registrarops.repository.*;
import com.registrarops.security.ApiKeyAuthFilter;
import com.registrarops.security.CustomUserDetailsService;
import com.registrarops.service.*;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests (Mockito — no Spring context, no database) for the service
 * and security layers the prior audit flagged as lacking direct coverage:
 *   AuthService, AccountDeletionService, CatalogService, EvaluationService,
 *   PolicySettingService, GradeAccessPolicy, ApiKeyAuthFilter,
 *   CustomUserDetailsService.
 *
 * These run in the src/test/java/.../unit group — no DB, no HTTP — so they
 * execute in milliseconds and are cheap to run on every commit.
 */
class ServiceLayerUnitTest {

    // ---- AuthService ------------------------------------------------------

    @Test
    void authService_trackFailedLogin_persistsAttempt() {
        var lar = mock(LoginAttemptRepository.class);
        var ur  = mock(UserRepository.class);
        var dbr = mock(DeviceBindingRepository.class);
        var snr = mock(SecurityNoticeRepository.class);
        var ms  = mock(MessageService.class);
        var svc = new AuthService(lar, ur, dbr, snr, ms);

        svc.trackFailedLogin("alice", "127.0.0.1");

        ArgumentCaptor<LoginAttempt> cap = ArgumentCaptor.forClass(LoginAttempt.class);
        verify(lar).save(cap.capture());
        assertEquals("alice", cap.getValue().getUsername());
        assertEquals("127.0.0.1", cap.getValue().getIpAddress());
        assertNotNull(cap.getValue().getAttemptedAt());
    }

    @Test
    void authService_isLockedOut_returnsTrueAtThreshold() {
        var lar = mock(LoginAttemptRepository.class);
        when(lar.countRecentByUsername(eq("bob"), any())).thenReturn(5L);
        var svc = new AuthService(lar, mock(UserRepository.class), mock(DeviceBindingRepository.class),
                mock(SecurityNoticeRepository.class), mock(MessageService.class));
        assertTrue(svc.isLockedOut("bob"));
    }

    @Test
    void authService_isLockedOut_falseBelowThreshold() {
        var lar = mock(LoginAttemptRepository.class);
        when(lar.countRecentByUsername(eq("bob"), any())).thenReturn(4L);
        var svc = new AuthService(lar, mock(UserRepository.class), mock(DeviceBindingRepository.class),
                mock(SecurityNoticeRepository.class), mock(MessageService.class));
        assertFalse(svc.isLockedOut("bob"));
    }

    @Test
    void authService_clearAttempts_delegatesToRepo() {
        var lar = mock(LoginAttemptRepository.class);
        var svc = new AuthService(lar, mock(UserRepository.class), mock(DeviceBindingRepository.class),
                mock(SecurityNoticeRepository.class), mock(MessageService.class));
        svc.clearAttempts("carol");
        verify(lar).deleteByUsername("carol");
    }

    @Test
    void authService_detectUnusualLogin_noopWhenBindingDisabled() {
        var lar = mock(LoginAttemptRepository.class);
        var ur  = mock(UserRepository.class);
        var dbr = mock(DeviceBindingRepository.class);
        var snr = mock(SecurityNoticeRepository.class);
        var ms  = mock(MessageService.class);
        var u = new User(); u.setId(1L); u.setUsername("x"); u.setDeviceBindingEnabled(false);
        when(ur.findByUsername("x")).thenReturn(Optional.of(u));
        var svc = new AuthService(lar, ur, dbr, snr, ms);
        svc.detectUnusualLogin("x", new MockHttpServletRequest());
        verifyNoInteractions(dbr, snr, ms);
    }

    @Test
    void authService_detectUnusualLogin_firstDeviceBindsSilently() {
        var lar = mock(LoginAttemptRepository.class);
        var ur  = mock(UserRepository.class);
        var dbr = mock(DeviceBindingRepository.class);
        var snr = mock(SecurityNoticeRepository.class);
        var ms  = mock(MessageService.class);
        var u = new User(); u.setId(5L); u.setUsername("y"); u.setDeviceBindingEnabled(true);
        when(ur.findByUsername("y")).thenReturn(Optional.of(u));
        when(dbr.existsByUserIdAndDeviceHash(eq(5L), anyString())).thenReturn(false);
        when(dbr.findByUserId(5L)).thenReturn(List.of());
        var svc = new AuthService(lar, ur, dbr, snr, ms);
        var req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        req.addHeader("User-Agent", "UnitTest");
        svc.detectUnusualLogin("y", req);
        verify(dbr).save(any(DeviceBinding.class));
        verifyNoInteractions(snr);
        verify(ms, never()).send(any(), any(), any(), any(), any(), any());
    }

    @Test
    void authService_detectUnusualLogin_secondDeviceEmitsSecurityNotice() {
        var lar = mock(LoginAttemptRepository.class);
        var ur  = mock(UserRepository.class);
        var dbr = mock(DeviceBindingRepository.class);
        var snr = mock(SecurityNoticeRepository.class);
        var ms  = mock(MessageService.class);
        var u = new User(); u.setId(9L); u.setUsername("z"); u.setDeviceBindingEnabled(true);
        when(ur.findByUsername("z")).thenReturn(Optional.of(u));
        when(dbr.existsByUserIdAndDeviceHash(eq(9L), anyString())).thenReturn(false);
        var existing = new DeviceBinding(); existing.setUserId(9L); existing.setDeviceHash("old-hash");
        when(dbr.findByUserId(9L)).thenReturn(List.of(existing));
        var svc = new AuthService(lar, ur, dbr, snr, ms);
        var req = new MockHttpServletRequest();
        req.setRemoteAddr("10.9.9.9");
        req.addHeader("User-Agent", "OtherAgent");
        svc.detectUnusualLogin("z", req);
        verify(snr).save(any(SecurityNotice.class));
        verify(ms).send(eq(9L), eq("SECURITY"), contains("New device"), any(), any(), any());
    }

    // ---- AccountDeletionService ------------------------------------------

    @Test
    void accountDeletion_resolveExportFile_returnsNullForUnknownUser() {
        var ur = mock(UserRepository.class);
        when(ur.findById(999L)).thenReturn(Optional.empty());
        var svc = new AccountDeletionService(ur, mock(OrderRepository.class),
                mock(StudentGradeRepository.class), mock(AuditService.class),
                new ExportTokenService("k"), "/tmp/exports");
        assertNull(svc.resolveExportFile(999L));
    }

    @Test
    void accountDeletion_resolveExportFile_returnsPathWhenSet() {
        var ur = mock(UserRepository.class);
        var u = new User(); u.setId(1L); u.setExportFilePath("/tmp/exports/x.json");
        when(ur.findById(1L)).thenReturn(Optional.of(u));
        var svc = new AccountDeletionService(ur, mock(OrderRepository.class),
                mock(StudentGradeRepository.class), mock(AuditService.class),
                new ExportTokenService("k"), "/tmp/exports");
        assertEquals("/tmp/exports/x.json", svc.resolveExportFile(1L).toString());
    }

    // ---- CatalogService ---------------------------------------------------

    @Test
    void catalogService_getCourseDelegatesToRepo() {
        var cr = mock(CourseRepository.class);
        var c = new Course(); c.setId(1L); c.setTitle("T");
        when(cr.findById(1L)).thenReturn(Optional.of(c));
        var svc = new CatalogService(cr, mock(CourseMaterialRepository.class),
                mock(CatalogRatingRepository.class), mock(SearchTermRepository.class),
                mock(SearchService.class), mock(AuditService.class));
        assertEquals("T", svc.getCourse(1L).orElseThrow().getTitle());
    }

    @Test
    void catalogService_getTrendingDelegates() {
        var str = mock(SearchTermRepository.class);
        var t = new SearchTerm(); t.setTerm("x"); t.setSearchCount(3);
        when(str.findTop10ByOrderBySearchCountDesc()).thenReturn(List.of(t));
        var svc = new CatalogService(mock(CourseRepository.class),
                mock(CourseMaterialRepository.class), mock(CatalogRatingRepository.class),
                str, mock(SearchService.class), mock(AuditService.class));
        var out = svc.getTrending();
        assertEquals(1, out.size());
        assertEquals("x", out.get(0).getTerm());
    }

    @Test
    void catalogService_rate_savesNewRating() {
        var crr = mock(CatalogRatingRepository.class);
        when(crr.findByUserIdAndItemTypeAndItemId(anyLong(), anyString(), anyLong()))
                .thenReturn(Optional.empty());
        when(crr.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var svc = new CatalogService(mock(CourseRepository.class),
                mock(CourseMaterialRepository.class), crr, mock(SearchTermRepository.class),
                mock(SearchService.class), mock(AuditService.class));
        var rating = svc.rate(1L, "alice", "course", 2L, 5);
        assertEquals(5, rating.getScore());
        verify(crr).save(any(CatalogRating.class));
    }

    // ---- EvaluationService ------------------------------------------------

    @Test
    void evaluationService_createCycle_persistsOpenCycle() {
        var cr = mock(EvaluationCycleRepository.class);
        when(cr.save(any())).thenAnswer(inv -> {
            var e = (EvaluationCycle) inv.getArgument(0);
            e.setId(42L);
            return e;
        });
        var svc = new EvaluationService(cr,
                mock(EvaluationIndicatorRepository.class),
                mock(EvidenceAttachmentRepository.class),
                mock(AuditService.class),
                "/tmp/evidence");
        var cyc = svc.createCycle(1L, 2L, "S1");
        assertEquals(42L, cyc.getId());
        assertEquals(EvaluationStatus.DRAFT, cyc.getStatus());
        assertEquals(2L, cyc.getFacultyId());
    }

    @Test
    void evaluationService_openCycle_rejectsNonDraft() {
        var cr = mock(EvaluationCycleRepository.class);
        var existing = new EvaluationCycle();
        existing.setId(1L); existing.setStatus(EvaluationStatus.OPEN); existing.setFacultyId(2L);
        when(cr.findById(1L)).thenReturn(Optional.of(existing));
        var svc = new EvaluationService(cr,
                mock(EvaluationIndicatorRepository.class),
                mock(EvidenceAttachmentRepository.class),
                mock(AuditService.class),
                "/tmp/evidence");
        assertThrows(IllegalStateException.class, () -> svc.openCycle(1L, 2L));
    }

    // ---- PolicySettingService --------------------------------------------

    @Test
    void policySettingService_rejectsUnknownKey() {
        var repo = mock(PolicySettingRepository.class);
        var svc = new PolicySettingService(repo, mock(AuditService.class));
        assertThrows(IllegalArgumentException.class,
                () -> svc.set("totally.unknown", "5", 1L, "u"));
    }

    @Test
    void policySettingService_rejectsInvalidValue() {
        var repo = mock(PolicySettingRepository.class);
        var svc = new PolicySettingService(repo, mock(AuditService.class));
        assertThrows(IllegalArgumentException.class,
                () -> svc.set("orders.refund_window_days", "-5", 1L, "u"));
    }

    @Test
    void policySettingService_setPersistsAndAudits() {
        var repo = mock(PolicySettingRepository.class);
        when(repo.findById("orders.refund_window_days")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var audit = mock(AuditService.class);
        var svc = new PolicySettingService(repo, audit);
        var saved = svc.set("orders.refund_window_days", "21", 1L, "admin");
        assertEquals("21", saved.getValue());
        verify(audit).log(eq(1L), eq("admin"), eq("POLICY_UPDATED"), any(), any(), any(), any(), any());
    }

    @Test
    void policySettingService_getIntReturnsFallbackOnMissing() {
        var repo = mock(PolicySettingRepository.class);
        when(repo.findById("x")).thenReturn(Optional.empty());
        var svc = new PolicySettingService(repo, mock(AuditService.class));
        assertEquals(99, svc.getInt("x", 99));
    }

    // ---- GradeAccessPolicy -----------------------------------------------

    @Test
    void gradeAccessPolicy_adminReviewerAlwaysAllowed() {
        var ur = mock(UserRepository.class);
        var cr = mock(GradeComponentRepository.class);
        var admin = new User(); admin.setId(1L); admin.setUsername("a"); admin.setRole(Role.ROLE_ADMIN);
        when(ur.findByUsername("a")).thenReturn(Optional.of(admin));
        var reviewer = new User(); reviewer.setId(2L); reviewer.setUsername("r"); reviewer.setRole(Role.ROLE_REVIEWER);
        when(ur.findByUsername("r")).thenReturn(Optional.of(reviewer));
        var p = new GradeAccessPolicy(ur, cr);
        assertDoesNotThrow(() -> p.assertCanReadStudent("a", 99L));
        assertDoesNotThrow(() -> p.assertCanReadCourse("r", 99L));
    }

    @Test
    void gradeAccessPolicy_studentSelfOk_otherDenied() {
        var ur = mock(UserRepository.class);
        var cr = mock(GradeComponentRepository.class);
        var s = new User(); s.setId(4L); s.setUsername("s"); s.setRole(Role.ROLE_STUDENT);
        when(ur.findByUsername("s")).thenReturn(Optional.of(s));
        var p = new GradeAccessPolicy(ur, cr);
        assertDoesNotThrow(() -> p.assertCanReadStudent("s", 4L));
        assertThrows(AccessDeniedException.class, () -> p.assertCanReadStudent("s", 7L));
        assertThrows(AccessDeniedException.class, () -> p.assertCanReadCourse("s", 1L));
    }

    @Test
    void gradeAccessPolicy_facultyScopedToGradedRows() {
        var ur = mock(UserRepository.class);
        var cr = mock(GradeComponentRepository.class);
        var f = new User(); f.setId(2L); f.setUsername("f"); f.setRole(Role.ROLE_FACULTY);
        when(ur.findByUsername("f")).thenReturn(Optional.of(f));
        when(cr.existsByStudentIdAndRecordedBy(10L, 2L)).thenReturn(true);
        when(cr.existsByStudentIdAndRecordedBy(11L, 2L)).thenReturn(false);
        when(cr.existsByCourseIdAndRecordedBy(1L, 2L)).thenReturn(true);
        when(cr.existsByCourseIdAndRecordedBy(2L, 2L)).thenReturn(false);
        var p = new GradeAccessPolicy(ur, cr);
        assertDoesNotThrow(() -> p.assertCanReadStudent("f", 10L));
        assertThrows(AccessDeniedException.class, () -> p.assertCanReadStudent("f", 11L));
        assertDoesNotThrow(() -> p.assertCanReadCourse("f", 1L));
        assertThrows(AccessDeniedException.class, () -> p.assertCanReadCourse("f", 2L));
    }

    @Test
    void gradeAccessPolicy_unknownPrincipalDenied() {
        var ur = mock(UserRepository.class);
        when(ur.findByUsername("ghost")).thenReturn(Optional.empty());
        var p = new GradeAccessPolicy(ur, mock(GradeComponentRepository.class));
        assertThrows(AccessDeniedException.class, () -> p.assertCanReadStudent("ghost", 1L));
    }

    // ---- ApiKeyAuthFilter -------------------------------------------------

    @BeforeEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void apiKeyFilter_noopForNonIntegrationPaths() throws Exception {
        var f = new ApiKeyAuthFilter("secret");
        var req = new MockHttpServletRequest("GET", "/admin/users");
        var res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        f.doFilter(req, res, chain);
        verify(chain).doFilter(req, res);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void apiKeyFilter_validKeyAuthenticatesWithAdminRole() throws Exception {
        var f = new ApiKeyAuthFilter("secret-key");
        var req = new MockHttpServletRequest("POST", "/api/v1/import/courses");
        req.addHeader("X-API-Key", "secret-key");
        var res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        f.doFilter(req, res, chain);
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(a);
        assertEquals("api-integration", a.getPrincipal());
        assertTrue(a.getAuthorities().stream().anyMatch(ga -> ga.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void apiKeyFilter_wrongKeyDoesNotAuthenticate() throws Exception {
        var f = new ApiKeyAuthFilter("real-key");
        var req = new MockHttpServletRequest("POST", "/api/v1/import/courses");
        req.addHeader("X-API-Key", "wrong-key");
        f.doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void apiKeyFilter_bearerTokenAccepted() throws Exception {
        var f = new ApiKeyAuthFilter("bear-key");
        var req = new MockHttpServletRequest("GET", "/api/v1/export/courses.csv");
        req.addHeader("Authorization", "Bearer bear-key");
        f.doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void apiKeyFilter_blankConfiguredKeyNoopEvenIfHeaderPresent() throws Exception {
        var f = new ApiKeyAuthFilter(""); // misconfigured: no key set
        var req = new MockHttpServletRequest("GET", "/api/v1/export/courses.csv");
        req.addHeader("X-API-Key", "whatever");
        f.doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    // ---- CustomUserDetailsService ----------------------------------------

    @Test
    void cuds_throwsWhenUserMissing() {
        var ur = mock(UserRepository.class);
        when(ur.findByUsername("nobody")).thenReturn(Optional.empty());
        var authService = mock(AuthService.class);
        var svc = new CustomUserDetailsService(ur, authService);
        assertThrows(UsernameNotFoundException.class, () -> svc.loadUserByUsername("nobody"));
    }

    @Test
    void cuds_rejectsSoftDeletedUsers() {
        var ur = mock(UserRepository.class);
        var u = new User();
        u.setUsername("gone"); u.setDeletedAt(java.time.LocalDateTime.now()); u.setIsActive(true);
        u.setPasswordHash("h"); u.setRole(Role.ROLE_STUDENT);
        when(ur.findByUsername("gone")).thenReturn(Optional.of(u));
        var svc = new CustomUserDetailsService(ur, mock(AuthService.class));
        assertThrows(UsernameNotFoundException.class, () -> svc.loadUserByUsername("gone"));
    }

    @Test
    void cuds_returnsLockedWhenAuthServiceSaysSo() {
        var ur = mock(UserRepository.class);
        var u = new User();
        u.setUsername("lock"); u.setIsActive(true); u.setPasswordHash("h"); u.setRole(Role.ROLE_STUDENT);
        when(ur.findByUsername("lock")).thenReturn(Optional.of(u));
        var as = mock(AuthService.class);
        when(as.isLockedOut("lock")).thenReturn(true);
        var svc = new CustomUserDetailsService(ur, as);
        UserDetails ud = svc.loadUserByUsername("lock");
        assertFalse(ud.isAccountNonLocked(), "locked user must report accountNonLocked=false");
    }

    @Test
    void cuds_returnsRoleAuthority() {
        var ur = mock(UserRepository.class);
        var u = new User();
        u.setUsername("adm"); u.setIsActive(true); u.setPasswordHash("h"); u.setRole(Role.ROLE_ADMIN);
        when(ur.findByUsername("adm")).thenReturn(Optional.of(u));
        var svc = new CustomUserDetailsService(ur, mock(AuthService.class));
        UserDetails ud = svc.loadUserByUsername("adm");
        assertTrue(ud.getAuthorities().stream().anyMatch(ga -> ga.getAuthority().equals("ROLE_ADMIN")));
        assertTrue(ud.isEnabled());
    }
}
