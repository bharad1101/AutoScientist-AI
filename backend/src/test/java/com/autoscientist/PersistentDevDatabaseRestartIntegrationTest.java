package com.autoscientist;

import com.autoscientist.dto.auth.AuthResponse;
import com.autoscientist.dto.auth.LoginRequest;
import com.autoscientist.dto.auth.RegisterRequest;
import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.User;
import com.autoscientist.repository.DatasetRepository;
import com.autoscientist.repository.UserRepository;
import com.autoscientist.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies persistent development database behavior across actual application restart.
 * Simulates real backend shutdown and reboot against the same file database:
 *  1. Boots application context against a persistent file-backed H2 database.
 *  2. Verifies initial demo seed users created by DataInitializer.
 *  3. Registers a new researcher user and saves a dataset record.
 *  4. Completely closes and shuts down the application context (JVM shutdown simulation).
 *  5. Boots a brand new application context pointing to the exact same file database.
 *  6. Verifies that the registered researcher can log in and obtain a JWT token.
 *  7. Verifies that the uploaded dataset record survives with correct owner and metadata.
 *  8. Verifies that DataInitializer detects existing data and does not duplicate accounts.
 */
public class PersistentDevDatabaseRestartIntegrationTest {

    @Test
    @DisplayName("Verify true restart: register and create dataset in session 1, stop backend, restart in session 2 and authenticate")
    void testPersistentDevDatabaseSurvivesRestart(@TempDir Path tempDir) {
        Path dbPath = tempDir.resolve("persistent_dev_db");
        String fileUrl = "jdbc:h2:file:" + dbPath.toAbsolutePath() + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL";

        String[] appArgs = new String[] {
                "--spring.profiles.active=dev",
                "--spring.datasource.url=" + fileUrl,
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.datasource.username=sa",
                "--spring.datasource.password=",
                "--spring.jpa.hibernate.ddl-auto=update",
                "--spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "--app.upload.dir=" + tempDir.resolve("uploads").toAbsolutePath(),
                "--app.jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
                "--app.jwt.expiration-ms=86400000",
                "--server.port=0" // random port to avoid port binding conflicts
        };

        Long datasetId;
        Long registeredUserId;

        // =========================================================================
        // SESSION 1: INITIAL BOOT, DEMO SEED, REGISTRATION, AND DATASET CREATION
        // =========================================================================
        try (ConfigurableApplicationContext session1 = new SpringApplicationBuilder(AutoScientistApplication.class)
                .run(appArgs)) {

            UserRepository userRepo1 = session1.getBean(UserRepository.class);
            DatasetRepository datasetRepo1 = session1.getBean(DatasetRepository.class);
            AuthService authService1 = session1.getBean(AuthService.class);

            // Verify demo user was seeded because DB was initially empty
            assertThat(userRepo1.findByEmail("jane@university.edu")).isPresent();
            long initialUserCount = userRepo1.count();
            assertThat(initialUserCount).isGreaterThanOrEqualTo(2L);

            // Register a new test researcher
            RegisterRequest registerReq = new RegisterRequest(
                    "Dr. Marie Curie",
                    "dr.curie@physics.org",
                    "RadiumDiscovery123!"
            );
            AuthResponse regResp = authService1.register(registerReq);
            assertThat(regResp.getToken()).isNotEmpty();
            assertThat(regResp.getUser().getEmail()).isEqualTo("dr.curie@physics.org");

            User curie = userRepo1.findByEmail("dr.curie@physics.org").orElseThrow();
            registeredUserId = curie.getId();

            // Create and persist a test dataset owned by Dr. Curie
            Dataset dataset = Dataset.builder()
                    .name("Radionuclide Decay Curve")
                    .originalFilename("decay_curve.csv")
                    .storedFilename("stored_decay_123.csv")
                    .fileSize(1024L * 50)
                    .rowCount(1500)
                    .columnCount(8)
                    .datasetType("NUMERIC")
                    .sourceFormat("CSV")
                    .version(1)
                    .owner(curie)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            Dataset saved = datasetRepo1.save(dataset);
            datasetId = saved.getId();
            assertThat(datasetId).isNotNull();

            assertThat(userRepo1.count()).isEqualTo(initialUserCount + 1);
            assertThat(datasetRepo1.count()).isGreaterThanOrEqualTo(1);
        } // session1 is completely closed here: Hikari pool, EntityManager, and all beans terminated!

        // =========================================================================
        // SESSION 2: RESTART BACKEND AGAINST THE EXACT SAME FILE DATABASE
        // =========================================================================
        try (ConfigurableApplicationContext session2 = new SpringApplicationBuilder(AutoScientistApplication.class)
                .run(appArgs)) {

            UserRepository userRepo2 = session2.getBean(UserRepository.class);
            DatasetRepository datasetRepo2 = session2.getBean(DatasetRepository.class);
            AuthService authService2 = session2.getBean(AuthService.class);
            PasswordEncoder encoder2 = session2.getBean(PasswordEncoder.class);

            // 1. Verify registered user persisted across restart
            User curieAfterRestart = userRepo2.findByEmail("dr.curie@physics.org").orElse(null);
            assertThat(curieAfterRestart)
                    .as("User registered in session 1 must persist across restart")
                    .isNotNull();
            assertThat(curieAfterRestart.getId()).isEqualTo(registeredUserId);
            assertThat(curieAfterRestart.getName()).isEqualTo("Dr. Marie Curie");

            // Verify password hash is valid BCrypt and matches original password
            assertThat(curieAfterRestart.getPasswordHash()).startsWith("$2a$");
            assertThat(encoder2.matches("RadiumDiscovery123!", curieAfterRestart.getPasswordHash())).isTrue();

            // 2. Verify dataset persisted across restart with all metadata and relationship intact
            Dataset datasetAfterRestart = datasetRepo2.findById(datasetId).orElse(null);
            assertThat(datasetAfterRestart)
                    .as("Dataset uploaded in session 1 must persist across restart")
                    .isNotNull();
            assertThat(datasetAfterRestart.getName()).isEqualTo("Radionuclide Decay Curve");
            assertThat(datasetAfterRestart.getRowCount()).isEqualTo(1500);
            assertThat(datasetAfterRestart.getOwner().getId()).isEqualTo(registeredUserId);

            // 3. Verify user can log in with their credentials and receive a valid JWT session
            LoginRequest loginReq = new LoginRequest("dr.curie@physics.org", "RadiumDiscovery123!");
            AuthResponse loginResp = authService2.login(loginReq);
            assertThat(loginResp).isNotNull();
            assertThat(loginResp.getToken()).isNotEmpty();
            assertThat(loginResp.getUser().getEmail()).isEqualTo("dr.curie@physics.org");

            // 4. Verify DataInitializer did NOT duplicate demo accounts on restart
            // Jane and Admin count should not have doubled
            long userCountSession2 = userRepo2.count();
            assertThat(userCountSession2).isEqualTo(3L); // jane, admin, dr.curie
        }
    }
}
