package com.autoscientist.service;

import com.autoscientist.dto.auth.AuthResponse;
import com.autoscientist.dto.auth.LoginRequest;
import com.autoscientist.dto.auth.RegisterRequest;
import com.autoscientist.dto.auth.UserDto;
import com.autoscientist.entity.User;
import com.autoscientist.exception.BadRequestException;
import com.autoscientist.exception.ResourceNotFoundException;
import com.autoscientist.repository.UserRepository;
import com.autoscientist.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    /** BCrypt only consumes the first 72 bytes; reject longer inputs explicitly. */
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.getPassword() != null
                && request.getPassword().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new BadRequestException("Password must be at most 72 bytes (UTF-8).");
        }

        String normalizedEmail = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new BadRequestException("Email is already registered: " + normalizedEmail);
        }

        User user = User.builder()
                .name(request.getName().trim())
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role("ROLE_USER")
                .build();

        try {
            // Flush now so a concurrent duplicate registration surfaces here as a
            // 400 instead of an opaque 500 at transaction commit.
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new BadRequestException("Email is already registered: " + normalizedEmail);
        }

        String token = jwtUtils.generateTokenFromUsername(user.getEmail());

        return AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .user(mapToDto(user))
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        String rawEmail = request.getEmail() != null ? request.getEmail().trim() : "";
        String normalizedEmail = normalizeEmail(rawEmail);

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        normalizedEmail,
                        request.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String token = jwtUtils.generateJwtToken(authentication);

        User user = userRepository.findByEmail(normalizedEmail)
                .or(() -> userRepository.findByEmailIgnoreCase(normalizedEmail))
                .or(() -> userRepository.findByEmail(rawEmail))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .user(mapToDto(user))
                .build();
    }

    @Transactional(readOnly = true)
    public UserDto getCurrentUser(String email) {
        User user = findUserByEmail(email);
        return mapToDto(user);
    }

    @Transactional(readOnly = true)
    public User getCurrentUserEntity(String email) {
        return findUserByEmail(email);
    }

    private User findUserByEmail(String email) {
        String normalized = normalizeEmail(email);
        return userRepository.findByEmail(email)
                .or(() -> userRepository.findByEmailIgnoreCase(normalized))
                .or(() -> userRepository.findByEmail(normalized))
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public UserDto mapToDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
