package com.registrarops.unit;

import com.registrarops.security.PasswordComplexityValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordValidatorTest {

    @Test
    void testValidPassword() {
        assertTrue(PasswordComplexityValidator.isValid("Admin@Registrar24!"));
        assertTrue(PasswordComplexityValidator.isValid("Hunter2!Hunter2!"));
    }

    @Test
    void testTooShort() {
        assertFalse(PasswordComplexityValidator.isValid("Aa1!Bb2@C"));   // 9 chars
        assertFalse(PasswordComplexityValidator.isValid("Aa1!Bb2@Cc3"));  // 11 chars
        assertNotNull(PasswordComplexityValidator.validate("short"));
    }

    @Test
    void testMissingUppercase() {
        assertFalse(PasswordComplexityValidator.isValid("noupperc4se!extra"));
        assertEquals("Password must contain at least one uppercase letter.",
                PasswordComplexityValidator.validate("noupperc4se!extra"));
    }

    @Test
    void testMissingLowercase() {
        assertFalse(PasswordComplexityValidator.isValid("ALLUPPER1234!@"));
    }

    @Test
    void testMissingDigit() {
        assertFalse(PasswordComplexityValidator.isValid("NoDigitsHere!@"));
        assertEquals("Password must contain at least one digit.",
                PasswordComplexityValidator.validate("NoDigitsHere!@"));
    }

    @Test
    void testMissingSpecialChar() {
        assertFalse(PasswordComplexityValidator.isValid("NoSpecialChar123"));
        assertEquals("Password must contain at least one special character.",
                PasswordComplexityValidator.validate("NoSpecialChar123"));
    }

    @Test
    void testExactly12Chars() {
        // 12 chars, has upper, lower, digit, special — should be the minimum valid case.
        assertTrue(PasswordComplexityValidator.isValid("Aa1!Bb2@Cc3#"));
    }

    @Test
    void testNullRejected() {
        assertFalse(PasswordComplexityValidator.isValid(null));
    }
}
