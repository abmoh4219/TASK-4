package com.registrarops.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Self-contained HMAC-SHA256 export tokens for the post-deletion download window.
 *
 * Format: {@code base64Url(userId:expiryEpochSec) + "." + base64Url(hmac)}
 *
 * The token carries its own ownership (userId) and expiry, so the
 * {@code /account/export/{token}} endpoint can validate without an
 * authenticated session — required because account deletion soft-deletes
 * the user immediately and login is then blocked for the full 7-day window.
 */
@Service
public class ExportTokenService {

    public static final long DEFAULT_TTL_SECONDS = 7L * 24 * 60 * 60;

    private final byte[] key;

    public ExportTokenService(@Value("${registrarops.export-token-secret:${APP_ENCRYPTION_KEY:registrarops-aes-256-key-32bytes!}}") String secret) {
        this.key = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String issue(long userId) {
        return issue(userId, DEFAULT_TTL_SECONDS);
    }

    public String issue(long userId, long ttlSeconds) {
        long expiry = Instant.now().getEpochSecond() + ttlSeconds;
        String body = userId + ":" + expiry;
        String b64Body = b64(body.getBytes(StandardCharsets.UTF_8));
        String b64Sig = b64(hmac(body));
        return b64Body + "." + b64Sig;
    }

    /** @return the userId encoded in the token, or null if invalid/expired/forged. */
    public Long verify(String token) {
        if (token == null || token.indexOf('.') < 0) return null;
        String[] parts = token.split("\\.", 2);
        if (parts.length != 2) return null;
        String body;
        byte[] sig;
        try {
            body = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            sig  = Base64.getUrlDecoder().decode(parts[1]);
        } catch (IllegalArgumentException e) {
            return null;
        }
        byte[] expected = hmac(body);
        if (!constantTimeEquals(expected, sig)) return null;
        int colon = body.indexOf(':');
        if (colon < 0) return null;
        try {
            long userId = Long.parseLong(body.substring(0, colon));
            long expiry = Long.parseLong(body.substring(colon + 1));
            if (Instant.now().getEpochSecond() > expiry) return null;
            return userId;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private byte[] hmac(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failure", e);
        }
    }

    private static String b64(byte[] in) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(in);
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int r = 0;
        for (int i = 0; i < a.length; i++) r |= a[i] ^ b[i];
        return r == 0;
    }
}
