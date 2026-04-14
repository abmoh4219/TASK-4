package com.registrarops.service;

import com.registrarops.entity.DeviceBinding;
import com.registrarops.entity.LoginAttempt;
import com.registrarops.entity.SecurityNotice;
import com.registrarops.entity.User;
import com.registrarops.repository.DeviceBindingRepository;
import com.registrarops.repository.LoginAttemptRepository;
import com.registrarops.repository.SecurityNoticeRepository;
import com.registrarops.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

/**
 * Authentication side-effects: failed-login tracking, account lockout (5 attempts /
 * 15 minutes), device binding, and unusual-login security notices.
 *
 * Spring Security calls these via {@link com.registrarops.security.AuthEventHandlers}
 * (failure / success handlers wired in {@link com.registrarops.config.SecurityConfig}).
 *
 * Lockout policy: a user is locked if 5 or more failed attempts have occurred in
 * the last 15 minutes. {@link CustomUserDetailsService} reads this flag at the
 * start of every login, so locked users are rejected BEFORE the password is even
 * compared (Spring Security throws LockedException → /login?locked).
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final int LOCKOUT_MINUTES = 15;

    private final LoginAttemptRepository loginAttemptRepository;
    private final UserRepository userRepository;
    private final DeviceBindingRepository deviceBindingRepository;
    private final SecurityNoticeRepository securityNoticeRepository;

    public AuthService(LoginAttemptRepository loginAttemptRepository,
                       UserRepository userRepository,
                       DeviceBindingRepository deviceBindingRepository,
                       SecurityNoticeRepository securityNoticeRepository) {
        this.loginAttemptRepository = loginAttemptRepository;
        this.userRepository = userRepository;
        this.deviceBindingRepository = deviceBindingRepository;
        this.securityNoticeRepository = securityNoticeRepository;
    }

    /** Insert a failed-login row for this username. */
    @Transactional
    public void trackFailedLogin(String username, String ipAddress) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.setUsername(username);
        attempt.setIpAddress(ipAddress);
        attempt.setAttemptedAt(LocalDateTime.now());
        loginAttemptRepository.save(attempt);
        log.info("failed login recorded for username={} ip={}", username, ipAddress);
    }

    /** True if this user has 5+ failed attempts in the last 15 minutes. */
    public boolean isLockedOut(String username) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(LOCKOUT_MINUTES);
        long recent = loginAttemptRepository.countRecentByUsername(username, since);
        return recent >= MAX_FAILED_ATTEMPTS;
    }

    /** Clear all failed-login rows for this user (called on successful login). */
    @Transactional
    public void clearAttempts(String username) {
        loginAttemptRepository.deleteByUsername(username);
    }

    /**
     * Compute SHA-256(IP + "|" + User-Agent) as the "device hash". On every
     * successful login: if the user has at least one prior binding and this hash
     * is not among them, we record an in-app SecurityNotice ("New device used to
     * sign in"). The first ever login for a user just adds the binding silently.
     */
    @Transactional
    public void detectUnusualLogin(String username, HttpServletRequest request) {
        if (request == null) return;
        userRepository.findByUsername(username).ifPresent(user -> {
            String hash = deviceHash(request);
            boolean known = deviceBindingRepository.existsByUserIdAndDeviceHash(user.getId(), hash);
            if (known) return;

            boolean hadAny = !deviceBindingRepository.findByUserId(user.getId()).isEmpty();

            // Bind this device for next time.
            DeviceBinding binding = new DeviceBinding();
            binding.setUserId(user.getId());
            binding.setDeviceHash(hash);
            binding.setLabel(truncate(request.getHeader("User-Agent"), 200));
            binding.setBoundAt(LocalDateTime.now());
            deviceBindingRepository.save(binding);

            if (hadAny) {
                // Unusual login: emit an in-app security notice.
                SecurityNotice notice = new SecurityNotice();
                notice.setUserId(user.getId());
                notice.setNoticeType("UNUSUAL_LOGIN");
                notice.setMessage("New device used to sign in from " + request.getRemoteAddr());
                notice.setIsRead(false);
                notice.setCreatedAt(LocalDateTime.now());
                securityNoticeRepository.save(notice);
                log.warn("unusual-login notice issued for user={} ip={}", username, request.getRemoteAddr());
            }
        });
    }

    private static String deviceHash(HttpServletRequest request) {
        String ip = request.getRemoteAddr() == null ? "" : request.getRemoteAddr();
        String ua = request.getHeader("User-Agent") == null ? "" : request.getHeader("User-Agent");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((ip + "|" + ua).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in every JRE — this branch is unreachable.
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
