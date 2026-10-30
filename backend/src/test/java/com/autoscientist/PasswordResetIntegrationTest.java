package com.autoscientist;

import com.autoscientist.dto.auth.ForgotPasswordRequest;
import com.autoscientist.dto.auth.LoginRequest;
import com.autoscientist.dto.auth.ResetPasswordRequest;
import com.autoscientist.entity.PasswordResetToken;
import com.autoscientist.entity.User;
import com.autoscientist.repository.PasswordResetTokenRepository;
import com.autoscientist.repository.UserRepository;
import com.autoscientist.service.PasswordResetNotifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.password-reset.min-interval-seconds=0",
        "app.password-reset.token-ttl-minutes=30"
})
@AutoConfigureMockMvc
class PasswordResetIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private PasswordResetNotifier notifier;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        when(notifier.send(any(User.class), anyString(), anyLong())).thenReturn(true);
    }

    @Test
    @DisplayName("Unknown email receives the same generic response and no email is sent")
    void unknownEmailDoesNotLeakAccountExistence() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ForgotPasswordRequest("nobody-" + System.nanoTime() + "@example.org"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(
                        "If an account exists for that email, a password reset link has been sent."));

        verify(notifier, never()).send(any(User.class), anyString(), anyLong());
    }

    @Test
    @DisplayName("Valid token resets the password, is stored hashed, and cannot be used twice")
    void validTokenResetsPasswordOnce() throws Exception {
        User user = createUser("reset.flow@example.org", "OldPassword123!");
        String rawToken = requestToken(user);

        assertThat(tokenRepository.count()).isEqualTo(1);
        PasswordResetToken stored = tokenRepository.findAll().get(0);
        assertThat(stored.getTokenHash()).isNotEqualTo(rawToken);
        assertThat(stored.getTokenHash()).hasSize(64);
        assertThat(stored.getExpiresAt()).isAfter(LocalDateTime.now());

        reset(rawToken, "NewPassword456!")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        User refreshed = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("NewPassword456!", refreshed.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("OldPassword123!", refreshed.getPasswordHash())).isFalse();
        assertThat(tokenRepository.count()).isZero();

        login("reset.flow@example.org", "NewPassword456!").andExpect(status().isOk());

        reset(rawToken, "AnotherPassword789!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Expired token is rejected and the password is unchanged")
    void expiredTokenIsRejected() throws Exception {
        User user = createUser("expired.token@example.org", "OldPassword123!");
        String rawToken = "expired-token-value";
        tokenRepository.save(PasswordResetToken.builder()
                .user(user)
                .tokenHash(sha256(rawToken))
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build());

        reset(rawToken, "NewPassword456!")
                .andExpect(status().isBadRequest());

        User refreshed = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("OldPassword123!", refreshed.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("Reusing the current password is rejected")
    void currentPasswordCannotBeReused() throws Exception {
        User user = createUser("same.password@example.org", "OldPassword123!");
        String rawToken = requestToken(user);

        reset(rawToken, "OldPassword123!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Choose a password that is different from your current password."));
    }

    @Test
    @DisplayName("A newer request invalidates older outstanding tokens")
    void newRequestInvalidatesPreviousToken() throws Exception {
        User user = createUser("rotate.token@example.org", "OldPassword123!");
        String first = requestToken(user);
        String second = requestToken(user);

        assertThat(first).isNotEqualTo(second);

        reset(first, "FirstNewPassword1!")
                .andExpect(status().isBadRequest());
        reset(second, "SecondNewPassword2!")
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Short passwords are rejected by validation before any change")
    void shortPasswordIsRejected() throws Exception {
        User user = createUser("short.password@example.org", "OldPassword123!");
        String rawToken = requestToken(user);

        reset(rawToken, "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));

        User refreshed = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("OldPassword123!", refreshed.getPasswordHash())).isTrue();
    }

    private User createUser(String email, String password) {
        return userRepository.save(User.builder()
                .name("Reset Test")
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .role("ROLE_USER")
                .build());
    }

    private String requestToken(User user) throws Exception {
        clearInvocations(notifier);
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest(user.getEmail()))))
                .andExpect(status().isOk());

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(notifier).send(any(User.class), token.capture(), anyLong());
        return token.getValue();
    }

    private org.springframework.test.web.servlet.ResultActions reset(String token, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ResetPasswordRequest(token, newPassword))));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, password))));
    }

    private static String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
