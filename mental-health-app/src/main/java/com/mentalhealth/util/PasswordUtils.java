package com.mentalhealth.util;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

// Utility class for secure password hashing and verification using PBKDF2
public final class PasswordUtils {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 65_536;
    private static final int SALT_BYTES = 16;
    private static final int KEY_LENGTH_BITS = 256;
    private static final String PREFIX = "pbkdf2";

    private PasswordUtils() {}

    // Hashes raw password using PBKDF2 with random salt; returns "pbkdf2$iterations$salt$hash"
    public static String hashPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }

        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] hash = pbkdf2(rawPassword.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS);

        return PREFIX
                + "$" + ITERATIONS
                + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(hash);
    }

    // Verifies raw password against stored hash (or legacy plain-text fallback)
    public static boolean verifyPassword(String rawPassword, String storedValue) {
        if (rawPassword == null || storedValue == null || storedValue.isEmpty()) {
            return false;
        }

        if (!isHashedPassword(storedValue)) {
            return constantTimeEquals(rawPassword.getBytes(StandardCharsets.UTF_8),
                    storedValue.getBytes(StandardCharsets.UTF_8));
        }

        String[] parts = storedValue.split("\\$");
        if (parts.length != 4) {
            return false;
        }

        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[3]);
            byte[] actualHash = pbkdf2(rawPassword.toCharArray(), salt, iterations, expectedHash.length * 8);
            return constantTimeEquals(actualHash, expectedHash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // Returns true if stored value is legacy plain-text that should be re-hashed
    public static boolean needsRehash(String storedValue) {
        return storedValue != null && !storedValue.isEmpty() && !isHashedPassword(storedValue);
    }

    private static boolean isHashedPassword(String storedValue) {
        return storedValue.startsWith(PREFIX + "$");
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLengthBits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLengthBits);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
            return skf.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Password hashing failed", e);
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
}