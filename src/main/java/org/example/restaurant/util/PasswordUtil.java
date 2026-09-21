package org.example.restaurant.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密码工具：随机 salt + SHA-256 摘要存储（不使用 Spring Security 等框架）。
 */
public final class PasswordUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {
    }

    public static String salt() {
        byte[] buf = new byte[16];
        RANDOM.nextBytes(buf);
        return hex(buf);
    }

    /** 摘要: SHA-256(salt + ":" + rawPassword) */
    public static String hash(String rawPassword, String salt) {
        return sha256(salt + ":" + rawPassword);
    }

    public static boolean matches(String rawPassword, String salt, String storedHash) {
        if (rawPassword == null || salt == null || storedHash == null) return false;
        return constantTimeEquals(storedHash, hash(rawPassword, salt));
    }

    public static String randomToken() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return hex(buf);
    }

    public static String randomDigits(int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(RANDOM.nextInt(10));
        return sb.toString();
    }

    private static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return hex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("不支持 SHA-256", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** 仅为对接既有外部系统的占位 hash（schema.sql 初始数据用）。 */
    public static String base64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
