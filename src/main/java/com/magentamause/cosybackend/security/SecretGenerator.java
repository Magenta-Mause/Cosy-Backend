package com.magentamause.cosybackend.security;

import java.security.SecureRandom;

public final class SecretGenerator {

    private static final String ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_";
    private static final int DEFAULT_LENGTH = 32; // ~192 bits of entropy

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static String generateSecret() {
        return generateSecret(DEFAULT_LENGTH);
    }

    public static String generateSecret(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("length must be > 0");
        }

        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = SECURE_RANDOM.nextInt(ALPHABET.length());
            sb.append(ALPHABET.charAt(index));
        }
        return sb.toString();
    }
}
