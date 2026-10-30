package com.autoscientist.service;

import com.autoscientist.entity.PasswordResetToken;
import com.autoscientist.entity.User;
import com.autoscientist.exception.BadRequestException;
import com.autoscientist.repository.PasswordResetTokenRepository;
import com.autoscientist.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetNotifier notifier;

    @Value("${app.password-reset.token-ttl-minutes:30}")
    private long tokenTtlMinutes;

    @Value("${app.password-reset.min-interval-seconds:60}")
    private long minIntervalSeconds;

    /**
     * Requests a reset link. The response is intentionally identical for known
     * and unknown addresses so the endpoint cannot be used to enumerate users.
     */
    @Transactional
    public void requestReset(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return;
        }

        Optional<User> userOpt = userRepository.findByEmail(normalized)
                .or(() -> userRepository.findByEmailIgnoreCase(normalized));
        if (userOpt.isEmpty()) {
            return;
        }

        User user = userOpt.get();
        LocalDateTime now = LocalDateTime.now();
        tokenRepository.deleteByExpiresAtBefore(now.minusDays(1));

        Optional<PasswordResetToken> latest = tokenRepository.findFirstByUserOrderByCreatedAtDesc(user);
        if (latest.isPresent() && latest.get().getCreatedAt() != null
                && latest.get().getCreatedAt().isAfter(now.minusSeconds(Math.max(0, minIntervalSeconds)))) {
            return;
        }

        tokenRepository.deleteByUser(user);

        String rawToken = generateToken();
        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .tokenHash(hash(rawToken))
                .expiresAt(now.plusMinutes(Math.max(1, tokenTtlMinutes)))
                .build();
        tokenRepository.save(token);

        notifier.send(user, rawToken, Math.max(1, tokenTtlMinutes));
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        String tokenValue = rawToken == null ? "" : rawToken.trim();
        if (tokenValue.isEmpty()) {
            throw invalidToken();
        }

        PasswordResetToken token = tokenRepository.findByTokenHash(hash(tokenValue))
                .orElseThrow(PasswordResetService::invalidToken);

        if (token.getUsedAt() != null || token.getExpiresAt() == null
                || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw invalidToken();
        }

        User user = token.getUser();
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BadRequestException("Choose a password that is different from your current password.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        token.setUsedAt(LocalDateTime.now());
        tokenRepository.save(token);
        tokenRepository.deleteByUser(user);
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static BadRequestException invalidToken() {
        return new BadRequestException(
                "This password reset link is invalid or has expired. Please request a new one.");
    }
}
