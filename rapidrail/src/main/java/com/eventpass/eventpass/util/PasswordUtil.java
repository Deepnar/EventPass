package com.eventpass.eventpass.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Minimal salted SHA-256 password / access-code hashing.
 * No extra dependencies; avoids storing plaintext secrets.
 * Stored format: hex(salt) + ":" + hex(sha256(salt + raw)).
 */
public final class PasswordUtil {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private PasswordUtil() {}

    public static String hash(String raw) {
        if (raw == null) throw new IllegalArgumentException("Secret is required");
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return toHex(salt) + ":" + sha256Hex(salt, raw);
    }

    public static boolean verify(String raw, String stored) {
        if (raw == null || stored == null) return false;
        int sep = stored.indexOf(':');
        // Legacy plaintext rows (pre-hash migration): compare directly.
        if (sep < 0) {
            return constantTimeEquals(stored, raw);
        }
        try {
            byte[] salt = fromHex(stored.substring(0, sep));
            String expected = sha256Hex(salt, raw);
            return constantTimeEquals(stored.substring(sep + 1), expected);
        } catch (Exception e) {
            return false;
        }
    }

    private static String sha256Hex(byte[] salt, String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            md.update(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return toHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
        }
        return sb.toString();
    }

    private static byte[] fromHex(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }
}
