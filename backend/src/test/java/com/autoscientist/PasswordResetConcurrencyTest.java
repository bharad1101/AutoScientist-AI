package com.autoscientist;

import com.autoscientist.entity.PasswordResetToken;
import com.autoscientist.entity.User;
import com.autoscientist.exception.BadRequestException;
import com.autoscientist.repository.PasswordResetTokenRepository;
import com.autoscientist.repository.UserRepository;
import com.autoscientist.service.PasswordResetNotifier;
import com.autoscientist.service.PasswordResetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reset_concurrency_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;LOCK_TIMEOUT=30000",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.password-reset.min-interval-seconds=0"
})
@ActiveProfiles("test")
class PasswordResetConcurrencyTest {

    private static final String NEW_PASSWORD = "ConcurrentNewPassword123!";

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private PasswordResetNotifier notifier;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
    }

    @Test
    void onlyOneConcurrentRequestCanConsumeTheToken() throws Exception {
        User user = createUser("concurrent.reset@example.org");
        String rawToken = "concurrent-reset-" + UUID.randomUUID();
        tokenRepository.save(PasswordResetToken.builder()
                .user(user)
                .tokenHash(sha256(rawToken))
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build());

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    passwordResetService.resetPassword(rawToken, NEW_PASSWORD);
                    return null;
                }));
            }
            start.countDown();

            int successes = 0;
            int rejections = 0;
            for (Future<?> result : results) {
                try {
                    result.get(30, TimeUnit.SECONDS);
                    successes++;
                } catch (Exception e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    assertThat(cause).isInstanceOf(BadRequestException.class);
                    assertThat(cause.getMessage()).contains("invalid or has expired");
                    rejections++;
                }
            }

            assertThat(successes).isEqualTo(1);
            assertThat(rejections).isEqualTo(1);
            User refreshed = userRepository.findById(user.getId()).orElseThrow();
            assertThat(passwordEncoder.matches(NEW_PASSWORD, refreshed.getPasswordHash())).isTrue();
            assertThat(tokenRepository.count()).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void aClaimedTokenCannotBeConsumedAgainSequentially() {
        User user = createUser("sequential.reset@example.org");
        String rawToken = "sequential-reset-" + UUID.randomUUID();
        tokenRepository.save(PasswordResetToken.builder()
                .user(user)
                .tokenHash(sha256(rawToken))
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build());

        passwordResetService.resetPassword(rawToken, NEW_PASSWORD);

        assertThatThrownBy(() -> passwordResetService.resetPassword(rawToken, "AnotherPassword123!"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("invalid or has expired");
        assertThat(tokenRepository.count()).isZero();
    }

    private User createUser(String email) {
        return userRepository.save(User.builder()
                .name("Concurrency Test")
                .email(email)
                .passwordHash(passwordEncoder.encode("OldPassword123!"))
                .role("ROLE_USER")
                .build());
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
