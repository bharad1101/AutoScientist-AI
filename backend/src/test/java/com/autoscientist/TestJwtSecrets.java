package com.autoscientist;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public final class TestJwtSecrets {

    public static final String BASE64_64_BYTES = highEntropyKey(64);
    public static final String LOW_ENTROPY_64_BYTES = lowEntropyKey(64);

    private TestJwtSecrets() {
    }

    public static String freshBase64Key() {
        byte[] key = new byte[64];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private static String highEntropyKey(int length) {
        byte[] key = new byte[length];
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int offset = 0; offset < length; offset += digest.getDigestLength()) {
                byte[] block = digest.digest(("autoscientist-test-key-" + offset).getBytes(StandardCharsets.UTF_8));
                System.arraycopy(block, 0, key, offset, Math.min(block.length, length - offset));
            }
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
        return Base64.getEncoder().encodeToString(key);
    }

    private static String lowEntropyKey(int length) {
        byte[] key = new byte[length];
        for (int i = 0; i < length; i++) {
            key[i] = (byte) (i % 2);
        }
        return Base64.getEncoder().encodeToString(key);
    }
}
