package com.autoscientist;

import com.autoscientist.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilsCompatibilityTest {

    private static final String SECRET = TestJwtSecrets.BASE64_64_BYTES;

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 3600000L);
    }

    @Test
    void generatedTokenRoundTripsAndValidates() {
        String token = jwtUtils.generateTokenFromCredentials("alice@example.test", "hash-1");

        assertThat(jwtUtils.getUserNameFromJwtToken(token)).isEqualTo("alice@example.test");
        assertThat(jwtUtils.validateJwtToken(token)).isTrue();
        assertThat(jwtUtils.getCredentialFingerprintFromJwtToken(token))
                .isEqualTo(jwtUtils.credentialFingerprint("hash-1"))
                .isNotEqualTo(jwtUtils.credentialFingerprint("hash-2"));
    }

    @Test
    void whitespaceInExternalSecretRemainsCompatible() {
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", SECRET.substring(0, 20) + "\n" + SECRET.substring(20));
        String token = jwtUtils.generateTokenFromCredentials("bob@example.test", "hash-1");

        assertThat(jwtUtils.getUserNameFromJwtToken(token)).isEqualTo("bob@example.test");
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtUtils.generateTokenFromCredentials("alice@example.test", "hash-1");
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("aa") ? "bb" : "aa");

        assertThat(jwtUtils.validateJwtToken(tampered)).isFalse();
    }
}
