package com.autoscientist.config;

import io.jsonwebtoken.io.Decoders;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;
import org.springframework.core.env.PropertySource;

import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class JwtSecretGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    static final int MIN_HS256_KEY_BYTES = 32;
    static final int MIN_DISTINCT_KEY_BYTES = 16;

    private static final Set<String> DEVELOPMENT_SECRET_FINGERPRINTS = Set.of(
            "799c9274f1912eee8f78f2a350887db028e430fe7c7595a4ac06fcd7ce0171b7",
            "dac9e36a436f6625a097dc6b4abf122d43ad298495d64adf7fc726707f3b0bbc",
            "31db0bede18f1724128e77aa6c6bb68f8b51e65a5431d7fc48250d626d2a3ec1",
            "81212520c267224b1862d96db2d400903e660bfefe31f89841d4c005dbcdc4a9",
            "b3f6f5d4f9cee4e99fe5db63234b775a205edc23efbbff87acc308626b5fd24a"
    );

    private static final List<String> HOSTED_MARKERS = List.of(
            "RENDER",
            "RENDER_SERVICE_ID",
            "RENDER_EXTERNAL_URL",
            "DYNO",
            "FLY_APP_NAME",
            "RAILWAY_ENVIRONMENT",
            "K_SERVICE",
            "KUBERNETES_SERVICE_HOST",
            "ECS_CONTAINER_METADATA_URI_V4",
            "ECS_CONTAINER_METADATA_URI",
            "WEBSITE_SITE_NAME",
            "GAE_ENV",
            "CF_INSTANCE_INDEX",
            "VERCEL"
    );

    private static final List<String> NON_LOCAL_ENVIRONMENTS = List.of(
            "prod",
            "production",
            "staging",
            "stage",
            "deployed",
            "hosted"
    );

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment environment = context.getEnvironment();
        boolean devProfile = environment.acceptsProfiles(Profiles.of("dev"));
        boolean testProfile = environment.acceptsProfiles(Profiles.of("test"));

        if (devProfile) {
            if (environment.acceptsProfiles(Profiles.of("mysql"))
                    || isNonLocalEnvironment(environment)
                    || usesNonLocalDatasource(environment)
                    || !isExplicitLocalOptIn(environment)) {
                throw new IllegalStateException(
                        "Refusing to start with the 'dev' profile because it uses a local H2 database and a "
                                + "development-only JWT secret. For local work set APP_ENVIRONMENT=local. "
                                + "For deployments set SPRING_PROFILES_ACTIVE=mysql with externally supplied "
                                + "JWT_SECRET, SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME and "
                                + "SPRING_DATASOURCE_PASSWORD."
                );
            }
            return;
        }

        if (testProfile) {
            if (environment.acceptsProfiles(Profiles.of("mysql"))
                    || isNonLocalEnvironment(environment)
                    || usesNonLocalDatasource(environment)
                    || usesPersistentEmbeddedDatasource(environment)
                    || !isExplicitLocalOptIn(environment)) {
                throw new IllegalStateException(
                        "Refusing to start with the 'test' profile outside an explicitly local environment or with "
                                + "a persistent datasource. The test profile requires an isolated in-memory database. "
                                + "For local tests set APP_ENVIRONMENT=local. For deployments set "
                                + "SPRING_PROFILES_ACTIVE=mysql and supply JWT_SECRET, SPRING_DATASOURCE_URL, "
                                + "SPRING_DATASOURCE_USERNAME and SPRING_DATASOURCE_PASSWORD."
                );
            }
            return;
        }

        if (usesEmbeddedDatasource(environment)) {
            throw new IllegalStateException(
                    "Refusing to start with active profile(s) because the configured datasource is embedded. "
                            + "Production must use SPRING_PROFILES_ACTIVE=mysql with SPRING_DATASOURCE_URL."
            );
        }

        String secret = resolveExternalSecret(environment);
        if (!hasConfiguredValue(secret)) {
            throw new IllegalStateException(
                    "JWT_SECRET is required for non-development profiles. Provide a Base64-encoded HS256 signing key "
                            + "of at least " + MIN_HS256_KEY_BYTES + " decoded bytes (64 random bytes recommended)."
            );
        }

        String normalized = secret.replaceAll("\\s", "");
        byte[] decoded;
        try {
            decoded = Decoders.BASE64.decode(normalized);
        } catch (RuntimeException malformed) {
            throw new IllegalStateException(
                    "JWT_SECRET is malformed: expected a Base64-encoded HS256 signing key. "
                            + "Generate one outside the repository with `openssl rand -base64 64 | tr -d '\\n'`."
            );
        }

        if (decoded.length < MIN_HS256_KEY_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET is too short for HS256: at least " + MIN_HS256_KEY_BYTES
                            + " decoded bytes are required (64 random bytes recommended)."
            );
        }

        if (DEVELOPMENT_SECRET_FINGERPRINTS.contains(fingerprint(decoded))) {
            throw new IllegalStateException(
                    "JWT_SECRET is a known development/default signing key and is not allowed outside "
                            + "the dev/test profiles. Generate a fresh Base64 HS256 key outside the repository "
                            + "with `openssl rand -base64 64 | tr -d '\\n'`."
            );
        }

        if (!hasSufficientEntropy(decoded)) {
            throw new IllegalStateException(
                    "JWT_SECRET has insufficient entropy. Generate a fresh random Base64 HS256 key outside the "
                            + "repository with `openssl rand -base64 64 | tr -d '\\n'`; repeated or constant bytes "
                            + "are not accepted."
            );
        }
    }

    private boolean hasSufficientEntropy(byte[] decoded) {
        Set<Byte> distinct = new HashSet<>();
        for (byte value : decoded) {
            distinct.add(value);
        }
        return distinct.size() >= MIN_DISTINCT_KEY_BYTES;
    }

    private String fingerprint(byte[] decoded) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(decoded));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required to validate JWT_SECRET", impossible);
        }
    }

    private String resolveExternalSecret(ConfigurableEnvironment environment) {
        String direct = environment.getProperty("app.jwt.secret");
        if (hasConfiguredValue(direct)) {
            return direct;
        }
        String environmentVariable = environment.getProperty("JWT_SECRET");
        if (hasConfiguredValue(environmentVariable)) {
            return environmentVariable;
        }

        for (PropertySource<?> source : environment.getPropertySources()) {
            if (source.getName() == null) {
                continue;
            }
            Object value = source.getProperty("app.jwt.secret");
            if (value instanceof String text && hasConfiguredValue(text)) {
                return text;
            }
        }
        return null;
    }

    private boolean hasConfiguredValue(String value) {
        return value != null && !value.isBlank() && !value.contains("${");
    }

    private boolean isExplicitLocalOptIn(ConfigurableEnvironment environment) {
        return "local".equalsIgnoreCase(property(environment, "app.environment").trim())
                || "local".equalsIgnoreCase(property(environment, "APP_ENVIRONMENT").trim());
    }

    private boolean isNonLocalEnvironment(ConfigurableEnvironment environment) {
        String canonicalName = property(environment, "app.environment");
        String environmentName = (hasConfiguredValue(canonicalName)
                ? canonicalName
                : property(environment, "APP_ENVIRONMENT")).toLowerCase(Locale.ROOT);
        if (NON_LOCAL_ENVIRONMENTS.contains(environmentName)) {
            return true;
        }
        for (String marker : HOSTED_MARKERS) {
            String value = environment.getProperty(marker, "");
            if (value != null && !value.isBlank() && !"false".equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private String property(ConfigurableEnvironment environment, String key) {
        String value = environment.getProperty(key);
        return value == null ? "" : value;
    }

    private boolean usesNonLocalDatasource(ConfigurableEnvironment environment) {
        String url = datasourceUrl(environment);
        return hasConfiguredValue(url) && !isEmbeddedUrl(url);
    }

    private boolean usesEmbeddedDatasource(ConfigurableEnvironment environment) {
        String url = datasourceUrl(environment);
        return hasConfiguredValue(url) && isEmbeddedUrl(url);
    }

    private boolean usesPersistentEmbeddedDatasource(ConfigurableEnvironment environment) {
        String url = datasourceUrl(environment);
        return hasConfiguredValue(url) && isEmbeddedUrl(url) && !isInMemoryUrl(url);
    }

    private boolean isInMemoryUrl(String url) {
        return url.startsWith("jdbc:h2:mem:")
                || url.startsWith("jdbc:hsqldb:mem:")
                || url.startsWith("jdbc:derby:memory:");
    }

    private String datasourceUrl(ConfigurableEnvironment environment) {
        String url = environment.getProperty("spring.datasource.url");
        return hasConfiguredValue(url) ? url : environment.getProperty("SPRING_DATASOURCE_URL");
    }

    private boolean isEmbeddedUrl(String url) {
        return url.startsWith("jdbc:h2:")
                || url.startsWith("jdbc:hsqldb:")
                || url.startsWith("jdbc:derby:");
    }
}
