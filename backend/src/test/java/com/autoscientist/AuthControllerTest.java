package com.autoscientist;

import com.autoscientist.controller.AuthController;
import com.autoscientist.dto.auth.AuthResponse;
import com.autoscientist.dto.auth.LoginRequest;
import com.autoscientist.dto.auth.RegisterRequest;
import com.autoscientist.dto.auth.UserDto;
import com.autoscientist.security.AuthEntryPointJwt;
import com.autoscientist.security.JwtUtils;
import com.autoscientist.security.UserDetailsServiceImpl;
import com.autoscientist.service.AuthService;
import com.autoscientist.service.PasswordResetService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private PasswordResetService passwordResetService;

    @MockBean
    private JwtUtils jwtUtils;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @MockBean
    private AuthEntryPointJwt authEntryPointJwt;

    @Test
    void testRegisterEndpoint() throws Exception {
        RegisterRequest req = new RegisterRequest("Marie Curie", "marie@curie.org", "radium123");
        UserDto userDto = new UserDto(1L, "Marie Curie", "marie@curie.org", "ROLE_USER", LocalDateTime.now());
        AuthResponse authResponse = new AuthResponse("sample.token", "Bearer", userDto);

        when(authService.register(any(RegisterRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").value("sample.token"))
                .andExpect(jsonPath("$.data.user.email").value("marie@curie.org"));
    }

    @Test
    void testLoginEndpoint() throws Exception {
        LoginRequest req = new LoginRequest("marie@curie.org", "radium123");
        UserDto userDto = new UserDto(1L, "Marie Curie", "marie@curie.org", "ROLE_USER", LocalDateTime.now());
        AuthResponse authResponse = new AuthResponse("sample.token", "Bearer", userDto);

        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").value("sample.token"));
    }

    @Test
    void testRegisterInvalidEmail() throws Exception {
        RegisterRequest req = new RegisterRequest("Marie Curie", "invalid-email", "123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
