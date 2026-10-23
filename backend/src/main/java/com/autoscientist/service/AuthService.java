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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String rawEmail = request.getEmail() != null ? request.getEmail().trim() : "";
        String normalizedEmail = rawEmail.toLowerCase();

        if (userRepository.existsByEmail(request.getEmail()) ||
                (!rawEmail.isEmpty() && userRepository.existsByEmail(rawEmail)) ||
                (!normalizedEmail.isEmpty() && userRepository.existsByEmailIgnoreCase(normalizedEmail))) {
            throw new BadRequestException("Email is already registered: " + request.getEmail());
        }

        User user = User.builder()
                .name(request.getName().trim())
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role("ROLE_USER")
                .build();

        user = userRepository.save(user);

        String token = jwtUtils.generateTokenFromUsername(user.getEmail());

        return AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .user(mapToDto(user))
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        String rawEmail = request.getEmail() != null ? request.getEmail().trim() : "";
        String normalizedEmail = rawEmail.toLowerCase();

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        normalizedEmail,
                        request.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String token = jwtUtils.generateJwtToken(authentication);

        User user = userRepository.findByEmail(request.getEmail().trim().toLowerCase())
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
        String normalized = email != null ? email.trim().toLowerCase() : "";
        User user = userRepository.findByEmail(email)
                .or(() -> userRepository.findByEmailIgnoreCase(normalized))
                .or(() -> userRepository.findByEmail(normalized))
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        return mapToDto(user);
    }

    @Transactional(readOnly = true)
    public User getCurrentUserEntity(String email) {
        String normalized = email != null ? email.trim().toLowerCase() : "";
        return userRepository.findByEmail(email)
                .or(() -> userRepository.findByEmailIgnoreCase(normalized))
                .or(() -> userRepository.findByEmail(normalized))
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
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
