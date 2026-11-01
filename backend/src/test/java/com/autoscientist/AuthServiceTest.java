package com.autoscientist;

import com.autoscientist.dto.auth.AuthResponse;
import com.autoscientist.dto.auth.LoginRequest;
import com.autoscientist.dto.auth.RegisterRequest;
import com.autoscientist.dto.auth.UserDto;
import com.autoscientist.entity.User;
import com.autoscientist.exception.BadRequestException;
import com.autoscientist.repository.UserRepository;
import com.autoscientist.security.JwtUtils;
import com.autoscientist.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtUtils jwtUtils;

    @InjectMocks
    private AuthService authService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .name("Dr. Ada Lovelace")
                .email("ada@scientific.org")
                .passwordHash("encoded_secret")
                .role("ROLE_USER")
                .build();
    }

    @Test
    void testRegisterSuccess() {
        RegisterRequest req = new RegisterRequest("Dr. Ada Lovelace", "ada@scientific.org", "science123");

        when(userRepository.existsByEmailIgnoreCase("ada@scientific.org")).thenReturn(false);
        when(passwordEncoder.encode("science123")).thenReturn("encoded_secret");
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(sampleUser);
        when(jwtUtils.generateTokenFromUsername("ada@scientific.org")).thenReturn("mocked.jwt.token");

        AuthResponse resp = authService.register(req);

        assertNotNull(resp);
        assertEquals("mocked.jwt.token", resp.getToken());
        assertEquals("ada@scientific.org", resp.getUser().getEmail());
        assertEquals("Dr. Ada Lovelace", resp.getUser().getName());
        verify(userRepository, times(1)).saveAndFlush(any(User.class));
    }

    @Test
    void testRegisterNormalizesEmailWithRootLocale() {
        RegisterRequest req = new RegisterRequest("Dr. Ada Lovelace", "  ADA@SCIENTIFIC.ORG ", "science123");

        when(userRepository.existsByEmailIgnoreCase("ada@scientific.org")).thenReturn(false);
        when(passwordEncoder.encode("science123")).thenReturn("encoded_secret");
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(sampleUser);
        when(jwtUtils.generateTokenFromUsername("ada@scientific.org")).thenReturn("mocked.jwt.token");

        authService.register(req);

        verify(userRepository).existsByEmailIgnoreCase("ada@scientific.org");
        verify(userRepository).saveAndFlush(argThat(user -> "ada@scientific.org".equals(user.getEmail())));
    }

    @Test
    void testRegisterDuplicateEmailThrowsException() {
        RegisterRequest req = new RegisterRequest("Dr. Ada Lovelace", "ada@scientific.org", "science123");
        when(userRepository.existsByEmailIgnoreCase("ada@scientific.org")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> authService.register(req));
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void testRegisterRejectsPasswordOverBcryptByteLimit() {
        String tooLong = "😀".repeat(25);
        RegisterRequest req = new RegisterRequest("Dr. Ada Lovelace", "ada@scientific.org", tooLong);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.register(req));

        assertTrue(ex.getMessage().contains("72 bytes"));
        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void testRegisterConvertsDuplicateRaceToBadRequest() {
        RegisterRequest req = new RegisterRequest("Dr. Ada Lovelace", "ada@scientific.org", "science123");

        when(userRepository.existsByEmailIgnoreCase("ada@scientific.org")).thenReturn(false);
        when(passwordEncoder.encode("science123")).thenReturn("encoded_secret");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.register(req));

        assertTrue(ex.getMessage().contains("already registered"));
    }

    @Test
    void testLoginSuccess() {
        LoginRequest req = new LoginRequest("ada@scientific.org", "science123");
        Authentication auth = mock(Authentication.class);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(jwtUtils.generateJwtToken(auth)).thenReturn("mocked.jwt.token");
        when(userRepository.findByEmail("ada@scientific.org")).thenReturn(Optional.of(sampleUser));

        AuthResponse resp = authService.login(req);

        assertNotNull(resp);
        assertEquals("mocked.jwt.token", resp.getToken());
        assertEquals(sampleUser.getEmail(), resp.getUser().getEmail());
    }

    @Test
    void testGetCurrentUser() {
        when(userRepository.findByEmail("ada@scientific.org")).thenReturn(Optional.of(sampleUser));

        UserDto userDto = authService.getCurrentUser("ada@scientific.org");

        assertNotNull(userDto);
        assertEquals(1L, userDto.getId());
        assertEquals("ada@scientific.org", userDto.getEmail());
    }
}
