package com.autoscientist.service;

import com.autoscientist.entity.User;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetMailNotifier implements PasswordResetNotifier {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.password-reset.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.password-reset.from:no-reply@autoscientist.local}")
    private String fromAddress;

    @Value("${app.password-reset.outbox-enabled:false}")
    private boolean outboxEnabled;

    @Value("${app.password-reset.outbox-dir:./data/mail-outbox}")
    private String outboxDir;

    /**
     * Logs whether a delivery transport is available without ever exposing SMTP
     * hosts, usernames, passwords, or sender secrets. This makes a missing
     * production SMTP configuration obvious in the startup logs.
     */
    @PostConstruct
    void logDeliveryConfiguration() {
        if (resolveValidatedBase() == null) {
            log.warn("Password reset frontend URL is not an absolute http(s) URL with a valid host; "
                    + "reset links will not be generated. Set FRONTEND_URL to the public frontend origin.");
        }

        boolean smtpConfigured = mailSenderProvider.getIfAvailable() != null;
        if (smtpConfigured) {
            log.info("Password reset email delivery is configured via SMTP.");
        } else if (outboxEnabled) {
            log.info("Password reset email delivery is configured via the local outbox (development only).");
        } else {
            log.warn("Password reset email delivery is NOT configured (no spring.mail.host and "
                    + "PASSWORD_RESET_OUTBOX_ENABLED=false). Reset requests will return the generic "
                    + "response but no email will be sent.");
        }
    }

    @Override
    public boolean send(User user, String rawToken, long ttlMinutes) {
        String base = resolveValidatedBase();
        if (base == null) {
            log.error("Refusing to build a password reset link: FRONTEND_URL is not a valid absolute http(s) URL");
            return false;
        }
        String link = buildLink(rawToken, base);
        String subject = "Reset your AutoScientist AI password";
        String body = buildBody(user, link, ttlMinutes);

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender != null) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(user.getEmail());
            message.setSubject(subject);
            message.setText(body);
            try {
                mailSender.send(message);
                return true;
            } catch (RuntimeException e) {
                // Catch every runtime failure (MailException, misconfigured
                // transport, provider timeouts) so a delivery problem can never
                // turn the public reset endpoint into a 500 or leak whether the
                // account exists. Only the exception type is logged.
                log.error("Password reset email delivery failed for user id {} ({})",
                        user.getId(), e.getClass().getSimpleName());
                return false;
            }
        }

        if (outboxEnabled) {
            return writeToOutbox(user, subject, body);
        }

        log.warn("Password reset requested but no mail transport is configured (user id {})", user.getId());
        return false;
    }

    /**
     * Strictly validates FRONTEND_URL instead of relying on a prefix check.
     * Prefix-only checks accept values such as {@code https:///evil} and
     * {@code https://user:pass@host}, which can produce misleading reset links.
     */
    private String resolveValidatedBase() {
        String base = frontendUrl == null ? "" : frontendUrl.trim();
        if (base.isEmpty() || !base.chars().allMatch(c -> c > 0x20 && c < 0x7f)) {
            return null;
        }
        try {
            URI uri = URI.create(base);
            String scheme = uri.getScheme();
            boolean validScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            if (!validScheme || uri.getHost() == null || uri.getHost().isBlank()
                    || uri.getUserInfo() != null || uri.getFragment() != null) {
                return null;
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private String buildLink(String rawToken, String base) {
        return base + "/reset-password?token=" + rawToken;
    }

    private String buildBody(User user, String link, long ttlMinutes) {
        String name = user.getName() == null || user.getName().isBlank() ? "there" : user.getName();
        return "Hello " + name + ",\n\n"
                + "A password reset was requested for your AutoScientist AI account.\n\n"
                + "Reset link (valid for " + ttlMinutes + " minutes):\n"
                + link + "\n\n"
                + "This link can be used once. If you did not request a reset, you can ignore this "
                + "email and your password will not change.\n\n"
                + "AutoScientist AI\n";
    }

    private boolean writeToOutbox(User user, String subject, String body) {
        try {
            Path dir = Path.of(outboxDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path file = dir.resolve("reset-" + System.currentTimeMillis() + "-" + UUID.randomUUID() + ".eml");
            String message = "To: " + user.getEmail() + "\r\n"
                    + "From: " + fromAddress + "\r\n"
                    + "Subject: " + subject + "\r\n"
                    + "Date: " + ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME) + "\r\n"
                    + "MIME-Version: 1.0\r\n"
                    + "Content-Type: text/plain; charset=UTF-8\r\n"
                    + "\r\n"
                    + body;
            Files.writeString(file, message, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            log.error("Could not write password reset email to the local outbox for user id {}", user.getId());
            return false;
        }
    }
}
