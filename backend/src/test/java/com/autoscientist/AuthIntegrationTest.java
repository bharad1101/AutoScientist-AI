package com.autoscientist;

import com.autoscientist.dto.auth.LoginRequest;
import com.autoscientist.dto.auth.RegisterRequest;
import com.autoscientist.entity.User;
import com.autoscientist.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("1. Register a new user and log in with correct credentials")
    void testRegisterAndLoginFlow() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("Marie Curie", "curie.test@science.org", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.type").value("Bearer"))
                .andExpect(jsonPath("$.data.user.email").value("curie.test@science.org"))
                .andExpect(jsonPath("$.data.user.name").value("Marie Curie"));

        LoginRequest loginReq = new LoginRequest("curie.test@science.org", "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.type").value("Bearer"))
                .andExpect(jsonPath("$.data.user.email").value("curie.test@science.org"));
    }

    @Test
    @DisplayName("2. Wrong password is rejected safely with 401")
    void testLoginWithWrongPasswordFails() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("Rosalind Franklin", "franklin.test@science.org", "validPassword1");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        LoginRequest wrongPwReq = new LoginRequest("franklin.test@science.org", "wrongPassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongPwReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    @DisplayName("3. Unknown user is rejected safely with 401 without user enumeration")
    void testLoginWithUnknownUserFails() throws Exception {
        LoginRequest unknownReq = new LoginRequest("unknown.user.999@science.org", "anyPassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unknownReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    @DisplayName("4. Passwords are stored BCrypt encoded, never plaintext")
    void testPasswordsStoredEncodedNeverPlaintext() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("Niels Bohr", "bohr.test@science.org", "quantum123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail("bohr.test@science.org")
                .orElseThrow(() -> new AssertionError("User should exist"));

        assertThat(user.getPasswordHash()).isNotEqualTo("quantum123");
        assertThat(user.getPasswordHash()).startsWith("$2a$");
        assertThat(passwordEncoder.matches("quantum123", user.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("5. Case-insensitive email login succeeds")
    void testCaseInsensitiveEmailLogin() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("Ada Lovelace", "Ada.Lovelace@Science.Org", "computing123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        // Login with all lowercase
        LoginRequest loginLower = new LoginRequest("ada.lovelace@science.org", "computing123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginLower)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Login with mixed case
        LoginRequest loginMixed = new LoginRequest("ADA.lovelace@SCIENCE.org", "computing123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginMixed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("6. Email with leading/trailing whitespace is sanitized and logs in")
    void testEmailWithLeadingTrailingSpacesTrimming() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("Albert Einstein", "  einstein.test@physics.org  ", "relativity1");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        // Login with leading/trailing spaces in email
        LoginRequest loginReq = new LoginRequest("   einstein.test@physics.org   ", "relativity1");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("7. Dev profile seeded demo account logs in successfully")
    void testDevProfileSeededUserLogin() throws Exception {
        // jane@university.edu is initialized by DataInitializer in dev profile
        LoginRequest loginReq = new LoginRequest("jane@university.edu", "SecurePassword123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value("jane@university.edu"));
    }

    @Test
    @DisplayName("8. Duplicate registration is rejected with 400 Bad Request")
    void testDuplicateRegistrationFails() throws Exception {
        RegisterRequest req1 = new RegisterRequest("User One", "duplicate@science.org", "pass12345");
        RegisterRequest req2 = new RegisterRequest("User Two", "DUPLICATE@science.org", "pass12345");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("9. Malformed email validation returns 400 Bad Request with field error")
    void testMalformedEmailValidationFails() throws Exception {
        LoginRequest invalidReq = new LoginRequest("not-an-email", "somePassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.data.email").isNotEmpty());
    }
}
