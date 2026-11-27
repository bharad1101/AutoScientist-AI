package com.autoscientist;

import com.autoscientist.config.MysqlConfigurationGuard;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MysqlConfigurationGuardTest {

    private final MysqlConfigurationGuard guard = new MysqlConfigurationGuard();

    @Test
    void ignoresNonMysqlProfiles() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        assertDoesNotThrow(() -> guard.initialize(context(environment)));
    }

    @Test
    void failsFastWithActionableMessageWhenMysqlVariablesAreMissing() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("mysql");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> guard.initialize(context(environment)));

        assertTrue(error.getMessage().contains("SPRING_DATASOURCE_URL"));
        assertTrue(error.getMessage().contains("SPRING_DATASOURCE_USERNAME"));
        assertTrue(error.getMessage().contains("SPRING_DATASOURCE_PASSWORD"));
        assertTrue(error.getMessage().contains("JWT_SECRET"));
        assertTrue(error.getMessage().contains("APP_CORS_ALLOWED_ORIGINS"));
        assertTrue(error.getMessage().contains("FRONTEND_URL"));
        assertTrue(error.getMessage().contains("./mvnw spring-boot:run"));
    }

    @Test
    void acceptsFullyConfiguredMysqlProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("mysql");
        environment.setProperty("SPRING_DATASOURCE_URL", "jdbc:mysql://db.example.test:3306/autoscientist?sslMode=VERIFY_IDENTITY");
        environment.setProperty("SPRING_DATASOURCE_USERNAME", "user");
        environment.setProperty("SPRING_DATASOURCE_PASSWORD", "password");
        environment.setProperty("JWT_SECRET", "secret");
        environment.setProperty("APP_CORS_ALLOWED_ORIGINS", "https://example.test");
        environment.setProperty("FRONTEND_URL", "https://example.test");

        assertDoesNotThrow(() -> guard.initialize(context(environment)));
    }

    @Test
    void rejectsUnsafeDdlAutoOverride() {
        MockEnvironment environment = fullyConfigured();
        environment.setProperty("SPRING_JPA_HIBERNATE_DDL_AUTO", "update");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> guard.initialize(context(environment)));

        assertTrue(error.getMessage().contains("SPRING_JPA_HIBERNATE_DDL_AUTO"));
        assertTrue(error.getMessage().contains("validate"));
    }

    @Test
    void rejectsFlywayDisabledOverride() {
        MockEnvironment environment = fullyConfigured();
        environment.setProperty("SPRING_FLYWAY_ENABLED", "false");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> guard.initialize(context(environment)));

        assertTrue(error.getMessage().contains("SPRING_FLYWAY_ENABLED"));
    }

    @Test
    void rejectsRateLimitDisabledOverride() {
        MockEnvironment environment = fullyConfigured();
        environment.setProperty("RATE_LIMIT_ENABLED", "false");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> guard.initialize(context(environment)));

        assertTrue(error.getMessage().contains("RATE_LIMIT_ENABLED"));
    }

    @Test
    void rejectsForwardedHeaderTrustWithoutExplicitProxyTopologyOptIn() {
        MockEnvironment environment = fullyConfigured();
        environment.setProperty("FORWARD_HEADERS_STRATEGY", "framework");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> guard.initialize(context(environment)));

        assertTrue(error.getMessage().contains("FORWARD_HEADERS_STRATEGY"));
    }

    @Test
    void acceptsForwardedHeaderTrustWithExplicitProxyTopologyOptIn() {
        MockEnvironment environment = fullyConfigured();
        environment.setProperty("FORWARD_HEADERS_STRATEGY", "framework");
        environment.setProperty("TRUSTED_PROXY_COUNT", "1");
        environment.setProperty("ALLOW_PROXY_HEADERS", "true");

        assertDoesNotThrow(() -> guard.initialize(context(environment)));
    }

    @Test
    void rejectsSensitiveActuatorExposure() {
        MockEnvironment environment = fullyConfigured();
        environment.setProperty("MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE", "health,env,info");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> guard.initialize(context(environment)));

        assertTrue(error.getMessage().contains("MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE"));
    }

    @Test
    void rejectsActuatorInfoEnvironmentDetails() {
        MockEnvironment environment = fullyConfigured();
        environment.setProperty("MANAGEMENT_INFO_ENV_ENABLED", "true");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> guard.initialize(context(environment)));

        assertTrue(error.getMessage().contains("MANAGEMENT_INFO_ENV_ENABLED"));
    }

    @Test
    void acceptsExplicitlySafeProductionOverrides() {
        MockEnvironment environment = fullyConfigured();
        environment.setProperty("SPRING_JPA_HIBERNATE_DDL_AUTO", "validate");
        environment.setProperty("SPRING_FLYWAY_ENABLED", "true");
        environment.setProperty("RATE_LIMIT_ENABLED", "true");

        assertDoesNotThrow(() -> guard.initialize(context(environment)));
    }

    @Test
    void rejectsLoopbackDatasourceUrl() {
        MockEnvironment environment = fullyConfigured();
        environment.setProperty("SPRING_DATASOURCE_URL", "jdbc:mysql://localhost:3306/autoscientist?sslMode=REQUIRED");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> guard.initialize(context(environment)));

        assertTrue(error.getMessage().contains("SPRING_DATASOURCE_URL"));
        assertTrue(error.getMessage().contains("loopback"));
    }

    @Test
    void rejectsDatasourceUrlWithoutRequiredTls() {
        MockEnvironment environment = fullyConfigured();
        environment.setProperty("SPRING_DATASOURCE_URL", "jdbc:mysql://db.example.test:3306/autoscientist");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> guard.initialize(context(environment)));

        assertTrue(error.getMessage().contains("TLS"));
    }

    @Test
    void rejectsLocalhostCorsOrigin() {
        MockEnvironment environment = fullyConfigured();
        environment.setProperty("APP_CORS_ALLOWED_ORIGINS", "https://app.example.test,http://localhost:5173");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> guard.initialize(context(environment)));

        assertTrue(error.getMessage().contains("APP_CORS_ALLOWED_ORIGINS"));
    }

    @Test
    void rejectsLocalhostFrontendUrl() {
        MockEnvironment environment = fullyConfigured();
        environment.setProperty("FRONTEND_URL", "http://localhost:5173");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> guard.initialize(context(environment)));

        assertTrue(error.getMessage().contains("FRONTEND_URL"));
    }

    @Test
    void acceptsDisposableMysqlTestConfigurationWithoutTls() {
        MockEnvironment environment = fullyConfigured();
        environment.setProperty("SPRING_DATASOURCE_URL", "jdbc:mysql://127.0.0.1:3306/autoscientist?sslMode=DISABLED");
        environment.setProperty("APP_ENVIRONMENT", "test-mysql-disposable");
        environment.setProperty("AS_TEST_MYSQL_ALLOW_SCHEMA_CREATE_DROP", "true");

        assertDoesNotThrow(() -> guard.initialize(context(environment)));
    }

    private MockEnvironment fullyConfigured() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("mysql");
        environment.setProperty("SPRING_DATASOURCE_URL", "jdbc:mysql://db.example.test:3306/autoscientist?sslMode=VERIFY_IDENTITY");
        environment.setProperty("SPRING_DATASOURCE_USERNAME", "user");
        environment.setProperty("SPRING_DATASOURCE_PASSWORD", "password");
        environment.setProperty("JWT_SECRET", "secret");
        environment.setProperty("APP_CORS_ALLOWED_ORIGINS", "https://example.test");
        environment.setProperty("FRONTEND_URL", "https://example.test");
        return environment;
    }

    private GenericApplicationContext context(MockEnvironment environment) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(environment);
        return context;
    }
}
