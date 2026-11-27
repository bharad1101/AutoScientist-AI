package com.autoscientist.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;
import org.springframework.core.env.PropertySource;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

/**
 * Supplies an ephemeral HS256 signing key for the local {@code dev} profile only.
 *
 * <p>The application no longer ships a development signing key. When running
 * locally with no externally configured secret, this initializer generates a
 * fresh random key in memory so zero-configuration startup still works. The key
 * is never written to disk, never committed, and is discarded on restart, which
 * invalidates dev tokens at the same time. Production profiles are untouched:
 * {@link JwtSecretGuard} continues to require an externally supplied
 * {@code JWT_SECRET}.</p>
 */
public class DevJwtSecretInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    static final String SECRET_PROPERTY = "app.jwt.secret";
    private static final String PROPERTY_SOURCE_NAME = "devGeneratedJwtSecret";
    private static final int DEV_KEY_BYTES = 64;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment environment = context.getEnvironment();
        if (!environment.acceptsProfiles(Profiles.of("dev"))) {
            return;
        }
        if (hasConfiguredValue(environment.getProperty("JWT_SECRET"))) {
            return;
        }
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (hasConfiguredValue(String.valueOf(source.getProperty(SECRET_PROPERTY)))) {
                return;
            }
        }

        byte[] key = new byte[DEV_KEY_BYTES];
        RANDOM.nextBytes(key);
        environment.getPropertySources().addFirst(
                new MapPropertySource(PROPERTY_SOURCE_NAME,
                        Map.of(SECRET_PROPERTY, Base64.getEncoder().encodeToString(key))));
    }

    private static boolean hasConfiguredValue(String value) {
        return value != null && !value.isBlank() && !"null".equals(value) && !value.contains("${");
    }
}
