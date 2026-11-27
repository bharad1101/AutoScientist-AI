package com.autoscientist;

import com.autoscientist.security.TokenRevocationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TokenRevocationServiceTest {

    private final TokenRevocationService service = new TokenRevocationService();

    @Test
    void revokedTokenIsRejectedButUnrelatedTokensStillWork() {
        String token = "eyJhbGciOiJIUzI1NiJ9.payload.signature";
        long expiresAt = System.currentTimeMillis() + 60_000;

        assertThat(service.isRevoked(token)).isFalse();
        service.revoke(token, expiresAt);

        assertThat(service.isRevoked(token)).isTrue();
        assertThat(service.isRevoked(token + "other")).isFalse();
    }

    @Test
    void onlyFingerprintsAreStoredNeverTheRawToken() {
        String token = "sensitive-raw-jwt-value";
        service.revoke(token, System.currentTimeMillis() + 60_000);

        @SuppressWarnings("unchecked")
        Map<String, java.time.Instant> store =
                (Map<String, java.time.Instant>) ReflectionTestUtils.getField(service, "revokedUntil");

        assertThat(store).isNotNull();
        assertThat(store).hasSize(1);
        assertThat(store.keySet().iterator().next()).hasSize(64).doesNotContain(token);
    }

    @Test
    void expiredRevocationsAreIgnored() {
        String token = "already-expired-token";
        service.revoke(token, System.currentTimeMillis() - 1_000);

        assertThat(service.isRevoked(token)).isFalse();
    }

    @Test
    void storeIsBoundedAndEvictsEarliestExpiry() {
        int limit = TokenRevocationService.MAX_REVOKED_TOKENS;
        long base = System.currentTimeMillis() + 120_000;
        for (int i = 0; i <= limit; i++) {
            service.revoke("token-" + i, base + i * 1_000L);
        }

        assertThat(service.isRevoked("token-0")).isFalse();
        assertThat(service.isRevoked("token-" + limit)).isTrue();
    }
}
