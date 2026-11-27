package com.autoscientist.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns the cryptic "Driver ... claims to not accept jdbcUrl, ${...}"
 * failure into an actionable message when the mysql profile is selected
 * without its required environment variables.
 */
public class MysqlConfigurationGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final List<RequiredSetting> REQUIRED = List.of(
            new RequiredSetting("SPRING_DATASOURCE_URL", "spring.datasource.url"),
            new RequiredSetting("SPRING_DATASOURCE_USERNAME", "spring.datasource.username"),
            new RequiredSetting("SPRING_DATASOURCE_PASSWORD", "spring.datasource.password"),
            new RequiredSetting("JWT_SECRET", "app.jwt.secret"),
            new RequiredSetting("APP_CORS_ALLOWED_ORIGINS", "app.cors.allowed-origins"),
            new RequiredSetting("FRONTEND_URL", "app.password-reset.frontend-url")
    );

    private static final Set<String> SENSITIVE_ACTUATOR_ENDPOINTS = Set.of(
            "beans", "conditions", "configprops", "env", "heapdump", "httptrace",
            "info", "loggers", "mappings", "scheduledtasks", "shutdown", "threaddump"
    );

    private static final Set<String> SAFE_SSL_MODES = Set.of("required", "verify_ca", "verify_identity");

    private static final Set<String> HOSTED_RUNTIME_MARKERS = Set.of(
            "RENDER", "RENDER_SERVICE_ID", "RENDER_EXTERNAL_URL", "DYNO", "FLY_APP_NAME",
            "RAILWAY_ENVIRONMENT", "K_SERVICE", "KUBERNETES_SERVICE_HOST",
            "ECS_CONTAINER_METADATA_URI_V4", "ECS_CONTAINER_METADATA_URI",
            "WEBSITE_SITE_NAME", "GAE_ENV", "CF_INSTANCE_INDEX", "VERCEL"
    );

    private static final List<ForbiddenOverride> FORBIDDEN_OVERRIDES = List.of(
            new ForbiddenOverride("SPRING_JPA_HIBERNATE_DDL_AUTO", "spring.jpa.hibernate.ddl-auto",
                    Set.of("validate"), "Flyway owns the production schema; ddl-auto must remain 'validate'."),
            new ForbiddenOverride("SPRING_FLYWAY_ENABLED", "spring.flyway.enabled",
                    Set.of("true"), "Flyway must stay enabled so the production schema stays versioned."),
            new ForbiddenOverride("RATE_LIMIT_ENABLED", "app.rate-limit.enabled",
                    Set.of("true"), "The API rate limiter must stay enabled in production.")
    );

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment environment = context.getEnvironment();
        if (!environment.acceptsProfiles(Profiles.of("mysql"))) {
            return;
        }

        List<String> missing = new ArrayList<>();
        for (RequiredSetting setting : REQUIRED) {
            if (!isConfigured(environment, setting)) {
                missing.add(setting.environmentVariable());
            }
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "The 'mysql' profile is active but required configuration is missing: "
                            + String.join(", ", missing)
                            + ". For local development use the default dev profile "
                            + "(`cd backend && ./mvnw spring-boot:run`) or run "
                            + "`./scripts/run_backend.sh`, which loads backend/.env. "
                            + "For production, set these variables in the deployment environment."
            );
        }

        List<String> unsafe = new ArrayList<>();
        for (ForbiddenOverride override : FORBIDDEN_OVERRIDES) {
            String value = resolvedValue(environment, override);
            if (value != null && !override.allowedValues().contains(value.toLowerCase(Locale.ROOT))) {
                unsafe.add(override.environmentVariable() + "=" + value + " (" + override.reason() + ")");
            }
        }
        validateTopologyAndManagementOverrides(environment, unsafe);
        validateDeploymentUrls(environment, unsafe);
        if (!unsafe.isEmpty()) {
            throw new IllegalStateException(
                    "The 'mysql' profile is active but these production safeguards were overridden: "
                            + String.join("; ", unsafe)
                            + ". Restore the safe values or use an explicitly local profile for experiments."
            );
        }
    }

    private void validateTopologyAndManagementOverrides(ConfigurableEnvironment environment, List<String> unsafe) {
        boolean proxyOptIn = Boolean.parseBoolean(environment.getProperty("ALLOW_PROXY_HEADERS", "false"));

        String forwarded = resolve(environment, "FORWARD_HEADERS_STRATEGY", "server.forward-headers-strategy");
        if (hasValue(forwarded) && !"none".equalsIgnoreCase(forwarded.trim()) && !proxyOptIn) {
            unsafe.add("FORWARD_HEADERS_STRATEGY (proxy trust requires ALLOW_PROXY_HEADERS=true"
                    + " and an edge that strips untrusted forwarded headers)");
        }

        String proxyCount = resolve(environment, "TRUSTED_PROXY_COUNT", "app.rate-limit.trusted-proxy-count");
        if (hasValue(proxyCount) && !"0".equals(proxyCount.trim()) && !proxyOptIn) {
            unsafe.add("TRUSTED_PROXY_COUNT (proxy trust requires ALLOW_PROXY_HEADERS=true"
                    + " and a verified proxy topology)");
        }

        String actuatorExposure = resolve(environment, "MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE",
                "management.endpoints.web.exposure.include");
        if (hasValue(actuatorExposure)) {
            Set<String> exposed = java.util.Arrays.stream(actuatorExposure.toLowerCase(Locale.ROOT).split(","))
                    .map(String::trim)
                    .collect(java.util.stream.Collectors.toSet());
            if (!java.util.Collections.disjoint(SENSITIVE_ACTUATOR_ENDPOINTS, exposed)) {
                unsafe.add("MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE includes sensitive endpoints");
            }
        }

        String infoEnv = resolve(environment, "MANAGEMENT_INFO_ENV_ENABLED", "management.info.env.enabled");
        if (hasValue(infoEnv) && Boolean.parseBoolean(infoEnv.trim())) {
            unsafe.add("MANAGEMENT_INFO_ENV_ENABLED must remain false in production");
        }
    }

    private void validateDeploymentUrls(ConfigurableEnvironment environment, List<String> unsafe) {
        if (isDisposableMysqlTest(environment)) {
            return;
        }

        String datasourceUrl = resolve(environment, "SPRING_DATASOURCE_URL", "spring.datasource.url");
        if (datasourceUrl != null) {
            if (!datasourceUrl.toLowerCase(Locale.ROOT).startsWith("jdbc:mysql://")) {
                unsafe.add("SPRING_DATASOURCE_URL must be a MySQL JDBC URL");
            }
            if (containsLoopbackHost(datasourceUrl)) {
                unsafe.add("SPRING_DATASOURCE_URL must not point at a loopback host");
            }
            if (!hasRequiredTls(datasourceUrl)) {
                unsafe.add("SPRING_DATASOURCE_URL must require TLS with sslMode=REQUIRED, VERIFY_CA or VERIFY_IDENTITY");
            }
        }

        String corsOrigins = resolve(environment, "APP_CORS_ALLOWED_ORIGINS", "app.cors.allowed-origins");
        if (corsOrigins != null) {
            for (String origin : corsOrigins.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.toLowerCase(Locale.ROOT).startsWith("https://") || containsLoopbackHost(trimmed)) {
                    unsafe.add("APP_CORS_ALLOWED_ORIGINS must contain only deployed HTTPS origins (no localhost)");
                    break;
                }
            }
        }

        String frontendUrl = resolve(environment, "FRONTEND_URL", "app.password-reset.frontend-url");
        if (frontendUrl != null
                && (!frontendUrl.toLowerCase(Locale.ROOT).startsWith("https://") || containsLoopbackHost(frontendUrl))) {
            unsafe.add("FRONTEND_URL must be the deployed HTTPS frontend URL (no localhost)");
        }
    }

    private boolean hasRequiredTls(String datasourceUrl) {
        String lower = datasourceUrl.toLowerCase(Locale.ROOT);
        for (String mode : SAFE_SSL_MODES) {
            if (lower.matches("(?s).*(^|[?&])sslmode=" + mode + "(&.*|$).*")) {
                return true;
            }
        }
        return false;
    }

    private boolean containsLoopbackHost(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("localhost") || lower.contains("127.0.0.1")
                || lower.contains("[::1]") || lower.contains("0.0.0.0");
    }

    private boolean isDisposableMysqlTest(ConfigurableEnvironment environment) {
        String appEnvironment = environment.getProperty("APP_ENVIRONMENT", "");
        if (!"test-mysql-disposable".equalsIgnoreCase(appEnvironment.trim())) {
            return false;
        }
        if (!Boolean.parseBoolean(environment.getProperty("AS_TEST_MYSQL_ALLOW_SCHEMA_CREATE_DROP", "false"))) {
            return false;
        }
        for (String marker : HOSTED_RUNTIME_MARKERS) {
            String value = environment.getProperty(marker, "");
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String resolvedValue(ConfigurableEnvironment environment, ForbiddenOverride override) {
        return resolve(environment, override.environmentVariable(), override.propertyName());
    }

    private String resolve(ConfigurableEnvironment environment, String environmentVariable, String propertyName) {
        String direct = environment.getProperty(environmentVariable);
        if (hasValue(direct)) {
            return direct;
        }
        String property = environment.getProperty(propertyName);
        return hasValue(property) ? property : null;
    }

    private boolean isConfigured(ConfigurableEnvironment environment, RequiredSetting setting) {
        if (hasValue(environment.getProperty(setting.environmentVariable()))) {
            return true;
        }
        try {
            String resolved = environment.getProperty(setting.propertyName());
            return hasValue(resolved) && !resolved.contains("${");
        } catch (RuntimeException unresolvedPlaceholder) {
            return false;
        }
    }

    private boolean hasValue(String value) {
        return value != null && !value.isBlank() && !value.contains("${");
    }

    private record RequiredSetting(String environmentVariable, String propertyName) {
    }

    private record ForbiddenOverride(String environmentVariable, String propertyName,
                                     Set<String> allowedValues, String reason) {
    }
}
