package com.autoscientist;

import com.autoscientist.config.DataInitializer;
import com.autoscientist.entity.User;
import com.autoscientist.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DataInitializerProfileTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private Environment environment;

    @InjectMocks
    private DataInitializer dataInitializer;

    @Test
    @DisplayName("In non-dev profile (e.g. prod/mysql), seeder must NOT run even if repository is empty")
    void testSeederDoesNotRunInProductionProfile() {
        when(environment.acceptsProfiles(Profiles.of("dev"))).thenReturn(false);

        dataInitializer.run();

        // Must never save any user in production
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("In dev profile, if user repository already contains accounts, seeder must NOT overwrite them")
    void testSeederDoesNotOverwriteExistingAccounts() {
        when(environment.acceptsProfiles(Profiles.of("dev"))).thenReturn(true);
        when(userRepository.count()).thenReturn(5L);

        dataInitializer.run();

        // Must not insert or overwrite existing accounts
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("In dev profile, if user repository is empty (count = 0), seeder creates demo and admin accounts")
    void testSeederRunsOnlyWhenDevProfileAndEmpty() {
        when(environment.acceptsProfiles(Profiles.of("dev"))).thenReturn(true);
        when(userRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode(any())).thenReturn("hashedPassword");

        dataInitializer.run();

        // Seeds exactly 2 accounts: demo user and admin
        verify(userRepository, times(2)).save(any(User.class));
    }
}
