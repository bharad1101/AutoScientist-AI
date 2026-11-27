package com.autoscientist.security;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded, in-memory revocation list for JWTs explicitly logged out by the
 * client. Only a one-way fingerprint is retained, never the token itself.
 * Entries expire with the token they revoke, and the store is capped so a
 * client cannot grow it without limit.
 *
 * <p>This is process-local: a logout only invalidates a token on the instance
 * that handled it. A shared revocation store or short token lifetimes are
 * required for multi-instance deployments.</p>
 */
@Service
public class TokenRevocationService {

    public static final int MAX_REVOKED_TOKENS = 10_000;

    private final Map<String, Instant> revokedUntil = new ConcurrentHashMap<>();

    public void revoke(String token, long expiresAtEpochMillis) {
        if (token == null || token.isBlank() || expiresAtEpochMillis <= System.currentTimeMillis()) {
            return;
        }
        purgeExpired();
        if (revokedUntil.size() >= MAX_REVOKED_TOKENS) {
            revokedUntil.entrySet().stream()
                    .min(Comparator.comparing(Map.Entry::getValue))
                    .map(Map.Entry::getKey)
                    .ifPresent(revokedUntil::remove);
        }
        revokedUntil.put(fingerprint(token), Instant.ofEpochMilli(expiresAtEpochMillis));
    }

    public boolean isRevoked(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Instant expiresAt = revokedUntil.get(fingerprint(token));
        if (expiresAt == null) {
            return false;
        }
        if (!expiresAt.isAfter(Instant.now())) {
            revokedUntil.remove(fingerprint(token));
            return false;
        }
        return true;
    }

    int size() {
        return revokedUntil.size();
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        revokedUntil.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
    }

    private static String fingerprint(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
