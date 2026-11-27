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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    /** BCrypt only consumes the first 72 bytes; reject longer inputs explicitly. */
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;
    private final ConcurrentHashMap<String, AtomicBoolean> requestsInFlight = new ConcurrentHashMap<>();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetNotifier notifier;
    private final PlatformTransactionManager transactionManager;

    @Value("${app.password-reset.token-ttl-minutes:30}")
    private long tokenTtlMinutes;

    @Value("${app.password-reset.min-interval-seconds:60}")
    private long minIntervalSeconds;

    /**
     * Requests a reset link. Token creation is committed in its own transaction;
     * SMTP/outbox I/O is triggered only after that commit succeeds, so a slow
     * mail provider cannot hold a database connection and a rollback can never
     * deliver a link for a token that does not exist.
     *
     * <p>The response is intentionally identical for known and unknown addresses
     * so the endpoint cannot be used to enumerate users.</p>
     */
    public void requestReset(String email) {
        String normalized = normalizeEmail(email);
        if (normalized.isEmpty()) {
            return;
        }

        AtomicBoolean inFlight = requestsInFlight.computeIfAbsent(normalized, key -> new AtomicBoolean());
        if (!inFlight.compareAndSet(false, true)) {
            return;
        }
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                Optional<User> userOpt = userRepository.findByEmailForUpdate(normalized)
                        .or(() -> userRepository.findByEmailIgnoreCaseForUpdate(normalized));
                if (userOpt.isEmpty()) {
                    return;
                }

                User user = userOpt.get();
                LocalDateTime now = LocalDateTime.now();
                long ttl = Math.max(1, tokenTtlMinutes);
                tokenRepository.deleteByExpiresAtBefore(now.minusDays(1));

                Optional<PasswordResetToken> latest = tokenRepository.findFirstByUserOrderByCreatedAtDesc(user);
                if (latest.isPresent() && latest.get().getCreatedAt() != null
                        && latest.get().getCreatedAt().isAfter(now.minusSeconds(Math.max(0, minIntervalSeconds)))) {
                    return;
                }

                tokenRepository.deleteByUser(user);

                String rawToken = generateToken();
                PasswordResetToken token = tokenRepository.save(PasswordResetToken.builder()
                        .user(user)
                        .tokenHash(hash(rawToken))
                        .expiresAt(now.plusMinutes(ttl))
                        .build());

                Long tokenId = token.getId();
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        deliverAfterCommit(user, rawToken, ttl, tokenId);
                    }
                });
            });
        } finally {
            inFlight.set(false);
        }
    }

    private void deliverAfterCommit(User user, String rawToken, long ttlMinutes, Long tokenId) {
        boolean delivered;
        try {
            delivered = notifier.send(user, rawToken, ttlMinutes);
        } catch (RuntimeException e) {
            // A notifier implementation must never be able to break the generic
            // public response or leave a usable token behind. Log only the type.
            log.error("Password reset notifier failed for user id {} ({})",
                    user.getId(), e.getClass().getSimpleName());
            delivered = false;
        }
        if (!delivered) {
            // The token is already committed, so remove it in a fresh transaction.
            // Delete by id only: a newer token for this user must not be removed.
            try {
                // REQUIRES_NEW is essential: during afterCommit the original
                // transaction is still bound to this thread until cleanup, so a
                // default REQUIRED template would join it and never commit.
                TransactionTemplate cleanup = new TransactionTemplate(transactionManager);
                cleanup.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                cleanup.executeWithoutResult(status -> tokenRepository.deleteById(tokenId));
            } catch (RuntimeException e) {
                log.error("Could not remove undelivered password reset token for user id {} ({})",
                        user.getId(), e.getClass().getSimpleName());
            }
            log.warn("Password reset instructions could not be delivered for user id {}", user.getId());
        }
    }

    /**
     * Consumes a reset token with a single conditional UPDATE. Only one caller can
     * flip {@code usedAt} from null to a timestamp, so concurrent requests cannot
     * both pass validation and change the same password.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        String tokenValue = rawToken == null ? "" : rawToken.trim();
        if (tokenValue.isEmpty()) {
            throw invalidToken();
        }
        if (newPassword != null
                && newPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new BadRequestException("Password must be at most 72 bytes (UTF-8).");
        }

        String tokenHash = hash(tokenValue);
        LocalDateTime now = LocalDateTime.now();
        if (tokenRepository.consumeIfValid(tokenHash, now) != 1) {
            throw invalidToken();
        }

        PasswordResetToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(PasswordResetService::invalidToken);
        User user = token.getUser();
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            // Throwing rolls back the claim so a reused password does not burn the token.
            throw new BadRequestException("Choose a password that is different from your current password.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
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

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static BadRequestException invalidToken() {
        return new BadRequestException(
                "This password reset link is invalid or has expired. Please request a new one.");
    }
}
