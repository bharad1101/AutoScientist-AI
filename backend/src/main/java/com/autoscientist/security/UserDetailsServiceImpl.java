package com.autoscientist.security;

import com.autoscientist.entity.User;
import com.autoscientist.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        if (email == null || email.isBlank()) {
            throw new UsernameNotFoundException("Email cannot be empty");
        }
        String normalizedEmail = email.trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .or(() -> userRepository.findByEmail(normalizedEmail))
                .or(() -> userRepository.findByEmailIgnoreCase(normalizedEmail))
                .or(() -> userRepository.findByEmail(email.trim()))
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
        return UserDetailsImpl.build(user);
    }
}
