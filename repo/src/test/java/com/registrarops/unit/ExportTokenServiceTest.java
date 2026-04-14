package com.registrarops.unit;

import com.registrarops.service.ExportTokenService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExportTokenServiceTest {

    private final ExportTokenService svc = new ExportTokenService("unit-test-secret-key");

    @Test
    void roundTrip() {
        String t = svc.issue(42L);
        assertEquals(42L, svc.verify(t));
    }

    @Test
    void rejectsForgedHmac() {
        String t = svc.issue(42L);
        // Tamper the body part.
        String tampered = t.substring(0, t.indexOf('.')) + "X." + t.substring(t.indexOf('.') + 1);
        assertNull(svc.verify(tampered));
    }

    @Test
    void rejectsExpired() {
        String t = svc.issue(42L, -10); // expired 10s ago
        assertNull(svc.verify(t));
    }

    @Test
    void rejectsGarbage() {
        assertNull(svc.verify(null));
        assertNull(svc.verify(""));
        assertNull(svc.verify("not-a-token"));
        assertNull(svc.verify("nodot"));
    }

    @Test
    void differentSecretRejectsToken() {
        String t = svc.issue(42L);
        ExportTokenService other = new ExportTokenService("different-secret");
        assertNull(other.verify(t));
    }
}
