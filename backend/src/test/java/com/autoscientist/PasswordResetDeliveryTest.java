package com.autoscientist;

import com.autoscientist.entity.PasswordResetToken;
import com.autoscientist.entity.User;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reset_delivery_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.password-reset.min-interval-seconds=0"
})
@ActiveProfiles("test")
class PasswordResetDeliveryTest {

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
        when(notifier.send(any(User.class), anyString(), anyLong())).thenReturn(true);
    }

    @Test
    void rolledBackResetRequestNeverSendsMailOrLeavesAToken() {
        User user = createUser("rollback.delivery@example.org");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            passwordResetService.requestReset(user.getEmail());
            status.setRollbackOnly();
        });

        verify(notifier, never()).send(any(User.class), anyString(), anyLong());
        assertThat(tokenRepository.count()).isZero();
    }

    @Test
    void mailDeliveryHappensAfterTheTokenTransactionCommits() {
        User user = createUser("commit.delivery@example.org");
        AtomicBoolean committed = new AtomicBoolean(false);
        AtomicBoolean sentBeforeCommit = new AtomicBoolean(true);
        when(notifier.send(any(User.class), anyString(), anyLong())).thenAnswer(invocation -> {
            sentBeforeCommit.set(!committed.get());
            return true;
        });

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    committed.set(true);
                }
            });
            passwordResetService.requestReset(user.getEmail());
        });

        verify(notifier).send(any(User.class), anyString(), anyLong());
        assertThat(committed.get()).isTrue();
        assertThat(sentBeforeCommit.get()).isFalse();
        assertThat(tokenRepository.count()).isEqualTo(1);
    }

    @Test
    void failedDeliveryRemovesOnlyTheUndeliveredToken() {
        User failed = createUser("failed.delivery@example.org");
        User other = createUser("other.delivery@example.org");
        tokenRepository.save(PasswordResetToken.builder()
                .user(other)
                .tokenHash("unrelated-" + UUID.randomUUID())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build());
        when(notifier.send(any(User.class), anyString(), anyLong())).thenReturn(false);

        passwordResetService.requestReset(failed.getEmail());

        verify(notifier).send(any(User.class), anyString(), anyLong());
        assertThat(tokenRepository.count()).isEqualTo(1);
        assertThat(tokenRepository.findAll().get(0).getUser().getId()).isEqualTo(other.getId());
    }

    @Test
    void notifierExceptionStillRemovesTheCommittedToken() {
        User user = createUser("exception.delivery@example.org");
        when(notifier.send(any(User.class), anyString(), anyLong()))
                .thenThrow(new IllegalStateException("simulated transport bug"));

        passwordResetService.requestReset(user.getEmail());

        verify(notifier).send(any(User.class), anyString(), anyLong());
        assertThat(tokenRepository.count()).isZero();
    }

    private User createUser(String email) {
        return userRepository.save(User.builder()
                .name("Delivery Test")
                .email(email)
                .passwordHash(passwordEncoder.encode("OldPassword123!"))
                .role("ROLE_USER")
                .build());
    }
}
