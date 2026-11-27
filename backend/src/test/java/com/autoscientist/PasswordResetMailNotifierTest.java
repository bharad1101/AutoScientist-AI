package com.autoscientist;

import com.autoscientist.entity.User;
import com.autoscientist.service.PasswordResetMailNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordResetMailNotifierTest {

    private ObjectProvider<JavaMailSender> senderProvider;
    private JavaMailSender sender;
    private PasswordResetMailNotifier notifier;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        senderProvider = mock(ObjectProvider.class);
        sender = mock(JavaMailSender.class);
        notifier = new PasswordResetMailNotifier(senderProvider);
        ReflectionTestUtils.setField(notifier, "frontendUrl", "https://staging.invalid");
        ReflectionTestUtils.setField(notifier, "fromAddress", "no-reply@example.invalid");
        ReflectionTestUtils.setField(notifier, "outboxEnabled", false);
        ReflectionTestUtils.setField(notifier, "outboxDir", "target/test-mail-outbox");
    }

    @Test
    void absentSmtpConfigurationReportsNotDelivered() {
        when(senderProvider.getIfAvailable()).thenReturn(null);

        assertThat(notifier.send(testUser(), "test-reset-token", 30)).isFalse();
    }

    @Test
    void smtpDeliveryFailureReportsNotDeliveredWithoutPropagatingMailException() {
        when(senderProvider.getIfAvailable()).thenReturn(sender);
        org.mockito.Mockito.doThrow(new MailSendException("simulated SMTP failure"))
                .when(sender).send(any(SimpleMailMessage.class));

        assertThat(notifier.send(testUser(), "test-reset-token", 30)).isFalse();
        verify(sender).send(any(SimpleMailMessage.class));
    }

    @Test
    void unexpectedRuntimeFailureIsContainedAndReportsNotDelivered() {
        when(senderProvider.getIfAvailable()).thenReturn(sender);
        org.mockito.Mockito.doThrow(new IllegalStateException("simulated transport misconfiguration"))
                .when(sender).send(any(SimpleMailMessage.class));

        assertThat(notifier.send(testUser(), "test-reset-token", 30)).isFalse();
        verify(sender).send(any(SimpleMailMessage.class));
    }

    @Test
    void successfulDeliveryUsesConfiguredFrontendAndSender() {
        when(senderProvider.getIfAvailable()).thenReturn(sender);

        assertThat(notifier.send(testUser(), "test-reset-token", 30)).isTrue();
        org.mockito.ArgumentCaptor<SimpleMailMessage> message =
                org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("no-reply@example.invalid");
        assertThat(message.getValue().getTo()).containsExactly("researcher@example.invalid");
        assertThat(message.getValue().getText())
                .contains("https://staging.invalid/reset-password?token=test-reset-token");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            " ",
            "not a url",
            "//example.com",
            "https:///reset",
            "javascript:alert(1)",
            "http://user:pass@example.com",
            "https://example.com/#fragment",
            "https://exa mple.com",
            "/reset-password"
    })
    void malformedFrontendUrlPreventsAnyResetLinkDelivery(String malformedUrl) {
        when(senderProvider.getIfAvailable()).thenReturn(sender);
        ReflectionTestUtils.setField(notifier, "frontendUrl", malformedUrl);

        assertThat(notifier.send(testUser(), "test-reset-token", 30)).isFalse();
        verify(sender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void trailingSlashInFrontendUrlProducesSingleCanonicalLink() {
        when(senderProvider.getIfAvailable()).thenReturn(sender);
        ReflectionTestUtils.setField(notifier, "frontendUrl", "https://staging.invalid/");

        assertThat(notifier.send(testUser(), "test-reset-token", 30)).isTrue();
        org.mockito.ArgumentCaptor<SimpleMailMessage> message =
                org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertThat(message.getValue().getText())
                .contains("https://staging.invalid/reset-password?token=test-reset-token")
                .doesNotContain("//reset-password");
    }

    private static User testUser() {
        return User.builder().id(17L).name("Test Researcher").email("researcher@example.invalid").build();
    }
}
