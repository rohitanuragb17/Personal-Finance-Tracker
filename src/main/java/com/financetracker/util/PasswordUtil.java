package com.financetracker.util;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordUtil {
    private static final int ITERATIONS = 600_000;
    private PasswordUtil() { }
    public static String hash(String password) throws Exception {
        byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
        return ITERATIONS + ":" + encode(salt) + ":" + encode(derive(password, salt, ITERATIONS));
    }
    public static boolean matches(String password, String stored) throws Exception {
        String[] parts = stored.split(":"); if (parts.length != 3) return false;
        byte[] expected = Base64.getDecoder().decode(parts[2]); byte[] actual = derive(password, Base64.getDecoder().decode(parts[1]), Integer.parseInt(parts[0]));
        return MessageDigest.isEqual(expected, actual);
    }
    private static byte[] derive(String password, byte[] salt, int iterations) throws Exception { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec(password.toCharArray(), salt, iterations, 256)).getEncoded(); }
    private static String encode(byte[] bytes) { return Base64.getEncoder().encodeToString(bytes); }
}
