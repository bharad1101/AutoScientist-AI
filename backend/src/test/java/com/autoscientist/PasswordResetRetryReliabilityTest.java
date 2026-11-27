package com.autoscientist;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.autoscientist.entity.PasswordResetToken;
import com.autoscientist.entity.User;
import com.autoscientist.exception.BadRequestException;
import com.autoscientist.repository.PasswordResetTokenRepository;
import com.autoscientist.repository.UserRepository;
import com.autoscientist.service.PasswordResetNotifier;
import com.autoscientist.service.PasswordResetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reset_retry_reliability_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;LOCK_TIMEOUT=30000",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
class PasswordResetRetryReliabilityTest {

    private static final String NEW_PASSWORD = "RetryReliableNewPassword123!";

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
        clearInvocations(notifier);
        ReflectionTestUtils.setField(passwordResetService, "minIntervalSeconds", 60L);
        when(notifier.send(any(User.class), anyString(), anyLong())).thenReturn(true);
    }

    @Test
    void repeatedRequestsSendOnlyOneEmailAndKeepOneToken() {
        User user = createUser("repeated.retry@example.org");

        passwordResetService.requestReset(user.getEmail());
        passwordResetService.requestReset(user.getEmail());

        verify(notifier, times(1)).send(any(User.class), anyString(), anyLong());
        assertThat(tokenRepository.count()).isEqualTo(1);
    }

    @Test
    void retryAfterPersistenceDoesNotSendAnotherEmailOrDeleteTheNewerToken() {
        User user = createUser("persisted.retry@example.org");
        String existingHash = sha256("newer-token-" + UUID.randomUUID());
        tokenRepository.save(PasswordResetToken.builder()
                .user(user)
                .tokenHash(existingHash)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .createdAt(LocalDateTime.now())
                .build());

        passwordResetService.requestReset(user.getEmail());

        verify(notifier, never()).send(any(User.class), anyString(), anyLong());
        assertThat(tokenRepository.findAll()).singleElement()
                .extracting(PasswordResetToken::getTokenHash)
                .isEqualTo(existingHash);
    }

    @Test
    void retryAfterDeliveryFailureIsAllowedToSendAgain() {
        User user = createUser("failed.retry@example.org");
        when(notifier.send(any(User.class), anyString(), anyLong())).thenReturn(false);

        passwordResetService.requestReset(user.getEmail());
        assertThat(tokenRepository.count()).isZero();

        clearInvocations(notifier);
        when(notifier.send(any(User.class), anyString(), anyLong())).thenReturn(true);
        passwordResetService.requestReset(user.getEmail());

        verify(notifier, times(1)).send(any(User.class), anyString(), anyLong());
        assertThat(tokenRepository.count()).isEqualTo(1);
    }

    @Test
    void concurrentRequestsSendOnlyOneEmailEvenWithoutAMinimumInterval() throws Exception {
        ReflectionTestUtils.setField(passwordResetService, "minIntervalSeconds", 0L);
        User user = createUser("concurrent.retry@example.org");
        CountDownLatch notifierEntered = new CountDownLatch(1);
        CountDownLatch releaseNotifier = new CountDownLatch(1);
        when(notifier.send(any(User.class), anyString(), anyLong())).thenAnswer(invocation -> {
            notifierEntered.countDown();
            releaseNotifier.await(5, TimeUnit.SECONDS);
            return true;
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(executor.submit(() -> passwordResetService.requestReset(user.getEmail())));
            }
            assertThat(notifierEntered.await(5, TimeUnit.SECONDS)).isTrue();
            releaseNotifier.countDown();
            for (Future<?> result : results) {
                result.get(10, TimeUnit.SECONDS);
            }
        } finally {
            releaseNotifier.countDown();
            executor.shutdownNow();
        }

        verify(notifier, times(1)).send(any(User.class), anyString(), anyLong());
        assertThat(tokenRepository.count()).isEqualTo(1);
    }

    @Test
    void expiredTokenIsRejectedAndDoesNotChangeThePassword() {
        User user = createUser("expired.retry@example.org");
        String rawToken = "expired-retry-token-" + UUID.randomUUID();
        tokenRepository.save(PasswordResetToken.builder()
                .user(user)
                .tokenHash(sha256(rawToken))
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .createdAt(LocalDateTime.now().minusMinutes(40))
                .build());

        assertThatThrownBy(() -> passwordResetService.resetPassword(rawToken, NEW_PASSWORD))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("invalid or has expired");
        assertThat(passwordEncoder.matches("OldPassword123!", refreshedUser(user).getPasswordHash())).isTrue();
    }

    @Test
    void validTokenRemainsSingleUse() {
        User user = createUser("single.use@example.org");
        String rawToken = "single-use-token-" + UUID.randomUUID();
        tokenRepository.save(PasswordResetToken.builder()
                .user(user)
                .tokenHash(sha256(rawToken))
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .createdAt(LocalDateTime.now())
                .build());

        passwordResetService.resetPassword(rawToken, NEW_PASSWORD);

        assertThatThrownBy(() -> passwordResetService.resetPassword(rawToken, "AnotherPassword123!"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("invalid or has expired");
        assertThat(passwordEncoder.matches(NEW_PASSWORD, refreshedUser(user).getPasswordHash())).isTrue();
        assertThat(tokenRepository.count()).isZero();
    }

    @Test
    void logsDoNotContainTheRawResetToken() {
        User user = createUser("no.token.logging@example.org");
        Logger logger = (Logger) LoggerFactory.getLogger(PasswordResetService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            passwordResetService.requestReset(user.getEmail());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(notifier).send(any(User.class), tokenCaptor.capture(), anyLong());
        String rawToken = tokenCaptor.getValue();
        assertThat(appender.list).noneMatch(event -> event.getFormattedMessage().contains(rawToken));
    }

    private User createUser(String email) {
        return userRepository.save(User.builder()
                .name("Retry Reliability Test")
                .email(email)
                .passwordHash(passwordEncoder.encode("OldPassword123!"))
                .role("ROLE_USER")
                .build());
    }

    private User refreshedUser(User user) {
        return userRepository.findById(user.getId()).orElseThrow();
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
