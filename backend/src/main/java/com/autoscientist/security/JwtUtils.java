package com.autoscientist.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtUtils {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    public static final String CREDENTIAL_FINGERPRINT_CLAIM = "crf";

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms:86400000}")
    private long jwtExpirationMs;

    private SecretKey key() {
        // Strip all whitespace: env-provided base64 secrets commonly pick up
        // stray breaks (e.g. `openssl rand -base64 64` wraps at 64 chars and
        // leaves an interior newline), which would otherwise crash every
        // auth call. Correct generation: `openssl rand -base64 64 | tr -d '\n'`.
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret.replaceAll("\\s", "")));
    }

    public String generateJwtToken(Authentication authentication) {
        org.springframework.security.core.userdetails.UserDetails principal =
                (org.springframework.security.core.userdetails.UserDetails) authentication.getPrincipal();
        return generateToken(principal.getUsername(), credentialFingerprint(principal.getPassword()));
    }

    public String generateTokenFromCredentials(String username, String passwordHash) {
        return generateToken(username, credentialFingerprint(passwordHash));
    }

    private String generateToken(String username, String credentialFingerprint) {
        return Jwts.builder()
                .subject(username)
                .claim(CREDENTIAL_FINGERPRINT_CLAIM, credentialFingerprint)
                .issuedAt(new Date())
                .expiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(key(), Jwts.SIG.HS256)
                .compact();
    }

    public String credentialFingerprint(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            return "";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(passwordHash.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public String getCredentialFingerprintFromJwtToken(String token) {
        try {
            Object value = Jwts.parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .get(CREDENTIAL_FINGERPRINT_CLAIM);
            return value instanceof String text ? text : null;
        } catch (JwtException | IllegalArgumentException invalid) {
            return null;
        }
    }

    public String getUserNameFromJwtToken(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public long getExpirationEpochMillis(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration()
                .getTime();
    }

    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parser().verifyWith(key()).build().parseSignedClaims(authToken);
            return true;
        } catch (MalformedJwtException e) {
            logger.error("Invalid JWT token.");
        } catch (ExpiredJwtException e) {
            logger.error("JWT token is expired.");
        } catch (UnsupportedJwtException e) {
            logger.error("JWT token is unsupported.");
        } catch (io.jsonwebtoken.security.SignatureException e) {
            logger.error("JWT signature does not match.");
        } catch (IllegalArgumentException e) {
            logger.error("JWT claims string is empty.");
        }
        return false;
    }
}
