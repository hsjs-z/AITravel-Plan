package com.travelplatform.common.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.stream.Collectors;

public final class IdempotencyKeys {

    private IdempotencyKeys() {
    }

    /**
     * Stable, collision-resistant key from the given parts.
     */
    public static String of(Object... parts) {
        String joined = Arrays.stream(parts)
                .map(String::valueOf)
                .collect(Collectors.joining(":"));
        return sha256(joined);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
