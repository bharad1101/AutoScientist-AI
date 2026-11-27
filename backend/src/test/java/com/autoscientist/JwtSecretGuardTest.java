package com.autoscientist;

import com.autoscientist.config.JwtSecretGuard;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtSecretGuardTest {

    private final JwtSecretGuard guard = new JwtSecretGuard();

    @Test
    void nonDevProfileFailsWhenJwtSecretIsMissing() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("mysql");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> guard.initialize(context(environment)));

        assertTrue(error.getMessage().contains("JWT_SECRET"));
    }

    @Test
    void nonDevProfileFailsWhenJwtSecretIsBlank() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("mysql");
        environment.setProperty("JWT_SECRET", "   ");

        assertThrows(IllegalStateException.class, () -> guard.initialize(context(environment)));
    }

    @Test
    void nonDevProfileRejectsMalformedSecret() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("mysql");
        environment.setProperty("JWT_SECRET", "not-base64!!!");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> guard.initialize(context(environment)));

        assertTrue(error.getMessage().contains("malformed"));
    }

    @Test
    void nonDevProfileRejectsSecretTooShortForHs256() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("mysql");
        environment.setProperty("JWT_SECRET", Base64.getEncoder().encodeToString(new byte[16]));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> guard.initialize(context(environment)));

        assertTrue(error.getMessage().contains("too short"));
        assertTrue(error.getMessage().contains("HS256"));
    }

    @Test
    void nonDevProfileRejectsLowEntropySecret() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("mysql");
        environment.setProperty("JWT_SECRET", TestJwtSecrets.LOW_ENTROPY_64_BYTES);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> guard.initialize(context(environment)));

        assertTrue(error.getMessage().contains("entropy"));
    }

    @Test
    void productionRejectsRetiredDevelopmentDefaultSecret() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("mysql");
        String retiredDevSecret = "nSeYbXJhKTYlRQtnQg0TqiLs45XBcUDQl7SHhIK1SQRyyUzQUD3z0cz6/o5V7o7ypfaigm2uSg0YiwfP6Crp3A==";
        environment.setProperty("JWT_SECRET", retiredDevSecret);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> guard.initialize(context(environment)));

        assertTrue(error.getMessage().contains("development/default"));
        assertFalse(error.getMessage().contains(retiredDevSecret));
    }

    @Test
    void productionRejectsTestFixtureSecret() throws IOException {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("JWT_SECRET", configuredSecret("src/test/resources/application-test.yml"));

        assertThrows(IllegalStateException.class, () -> guard.initialize(context(environment)));
    }

    @Test
    void nonDevProfileAcceptsStrongExternalSecret() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("mysql");
        environment.setProperty("JWT_SECRET", TestJwtSecrets.BASE64_64_BYTES);

        assertDoesNotThrow(() -> guard.initialize(context(environment)));
    }

    @Test
    void canonicalPropertiesTakePrecedenceOverEnvironmentVariableKeys() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("mysql");
        environment.setProperty("SPRING_DATASOURCE_URL", "jdbc:h2:file:./data/h2/prod");
        environment.setProperty("spring.datasource.url", "jdbc:mysql://db.example.test/app");
        environment.setProperty("JWT_SECRET", "weak-env-value");
        environment.setProperty("app.jwt.secret", TestJwtSecrets.BASE64_64_BYTES);

        assertDoesNotThrow(() -> guard.initialize(context(environment)));
    }

    @Test
    void nonDevProfileAcceptsStrongExternalPropertySecret() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("mysql");
        environment.setProperty("app.jwt.secret", TestJwtSecrets.BASE64_64_BYTES);

        assertDoesNotThrow(() -> guard.initialize(context(environment)));
    }

    @Test
    void nonDevProfileAcceptsSecretFromSpringConfigDataPropertySource() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("mysql");
        environment.getPropertySources().addLast(new MapPropertySource(
                "applicationConfig: [file:./config/application-prod.yml]",
                Map.of("app.jwt.secret", TestJwtSecrets.BASE64_64_BYTES)));

        assertDoesNotThrow(() -> guard.initialize(context(environment)));
    }

    @Test
    void nonMysqlProductionProfileAlsoRequiresJwtSecret() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThrows(IllegalStateException.class, () -> guard.initialize(context(environment)));
    }

    @Test
    void devProfileWorksWithoutExternalSecretWhenExplicitlyLocal() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        environment.setProperty("APP_ENVIRONMENT", "local");

        assertDoesNotThrow(() -> guard.initialize(context(environment)));
    }

    @Test
    void devProfileIsRejectedOnUnacknowledgedHost() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> guard.initialize(context(environment)));

        assertTrue(error.getMessage().contains("APP_ENVIRONMENT=local"));
    }

    @Test
    void devProfileIsRejectedWhenMysqlProfileIsAlsoActive() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev", "mysql");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> guard.initialize(context(environment)));

        assertTrue(error.getMessage().contains("dev"));
    }

    @Test
    void devProfileIsRejectedOnHostedRuntime() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        environment.setProperty("RENDER", "true");

        assertThrows(IllegalStateException.class, () -> guard.initialize(context(environment)));
    }

    @Test
    void devProfileIsRejectedWithNonLocalDatasource() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        environment.setProperty("SPRING_DATASOURCE_URL", "jdbc:mysql://db.example.test/app");

        assertThrows(IllegalStateException.class, () -> guard.initialize(context(environment)));
    }

    @Test
    void testProfileIsAllowedWithEmbeddedDatasourceOnlyWhenExplicitlyLocal() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        environment.setProperty("APP_ENVIRONMENT", "local");

        assertDoesNotThrow(() -> guard.initialize(context(environment)));
    }

    @Test
    void testProfileIsRejectedWithoutExplicitLocalOptIn() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> guard.initialize(context(environment)));

        assertTrue(error.getMessage().contains("test"));
        assertTrue(error.getMessage().contains("APP_ENVIRONMENT=local"));
    }

    @Test
    void testProfileIsRejectedWhenMysqlProfileIsAlsoActive() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test", "mysql");
        environment.setProperty("SPRING_DATASOURCE_URL", "jdbc:mysql://db.example.test/app");
        environment.setProperty("JWT_SECRET", TestJwtSecrets.BASE64_64_BYTES);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> guard.initialize(context(environment)));

        assertTrue(error.getMessage().contains("test"));
    }

    @Test
    void testProfileRejectsPersistentEmbeddedDatasource() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        environment.setProperty("APP_ENVIRONMENT", "local");
        environment.setProperty("SPRING_DATASOURCE_URL", "jdbc:h2:file:./data/h2/autoscientist_dev");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> guard.initialize(context(environment)));

        assertTrue(error.getMessage().contains("in-memory"));
    }

    @Test
    void testProfileIsRejectedOnHostedRuntime() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        environment.setProperty("APP_ENVIRONMENT", "local");
        environment.setProperty("RENDER_SERVICE_ID", "srv-123");

        assertThrows(IllegalStateException.class, () -> guard.initialize(context(environment)));
    }

    @Test
    void embeddedDatasourceIsRejectedBeforeSecretValidation() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("SPRING_DATASOURCE_URL", "jdbc:h2:file:./data/h2/prod");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> guard.initialize(context(environment)));

        assertTrue(error.getMessage().contains("embedded"));
    }

    private String configuredSecret(String path) throws IOException {
        Matcher matcher = Pattern.compile("(?m)^\\s*secret:\\s*(\\S+)\\s*$")
                .matcher(Files.readString(Path.of(path)));
        assertTrue(matcher.find(), "Expected a configured secret in " + path);
        return matcher.group(1);
    }

    private GenericApplicationContext context(MockEnvironment environment) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(environment);
        return context;
    }
}
