package com.registrarops.security;

/**
 * Local password complexity rules required by the business prompt:
 *   - minimum 12 characters
 *   - at least one uppercase letter
 *   - at least one lowercase letter
 *   - at least one digit
 *   - at least one special character (!@#$%^&*()_+-=[]{}|;:'",.&lt;&gt;/?`~)
 *
 * This is a static utility (called from AuthService and from JSR-303 validators
 * via {@link com.registrarops.security.StrongPassword}). Pure logic, no
 * dependencies on Spring — so it is trivially unit-testable in
 * {@code com.registrarops.unit.PasswordValidatorTest}.
 */
public final class PasswordComplexityValidator {

    public static final int MIN_LENGTH = 12;
    private static final String SPECIAL_CHARS = "!@#$%^&*()_+-=[]{}|;:'\",.<>/?`~\\";

    private PasswordComplexityValidator() { /* static utility */ }

    public static boolean isValid(String password) {
        return validate(password) == null;
    }

    /**
     * @return null if valid, otherwise a human-readable explanation of the first failed rule.
     */
    public static String validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return "Password must be at least " + MIN_LENGTH + " characters long.";
        }
        boolean hasUpper = false, hasLower = false, hasDigit = false, hasSpecial = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else if (SPECIAL_CHARS.indexOf(c) >= 0) hasSpecial = true;
        }
        if (!hasUpper)   return "Password must contain at least one uppercase letter.";
        if (!hasLower)   return "Password must contain at least one lowercase letter.";
        if (!hasDigit)   return "Password must contain at least one digit.";
        if (!hasSpecial) return "Password must contain at least one special character.";
        return null;
    }
}
