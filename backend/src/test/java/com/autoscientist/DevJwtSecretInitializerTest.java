package com.autoscientist;

import com.autoscientist.config.DevJwtSecretInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.context.support.GenericApplicationContext;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class DevJwtSecretInitializerTest {

    private final DevJwtSecretInitializer initializer = new DevJwtSecretInitializer();

    @Test
    void devProfileWithoutExternalSecretGetsFreshStrongKey() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(environment);

        initializer.initialize(context);

        String generated = environment.getProperty("app.jwt.secret");
        assertThat(generated).isNotBlank();
        assertThat(Base64.getDecoder().decode(generated)).hasSizeGreaterThanOrEqualTo(32);
        assertThat(environment.getPropertySources().contains("devGeneratedJwtSecret")).isTrue();

        MockEnvironment secondEnvironment = new MockEnvironment();
        secondEnvironment.setActiveProfiles("dev");
        GenericApplicationContext secondContext = new GenericApplicationContext();
        secondContext.setEnvironment(secondEnvironment);
        initializer.initialize(secondContext);

        assertThat(secondEnvironment.getProperty("app.jwt.secret")).isNotEqualTo(generated);
    }

    @Test
    void externalSecretIsNeverOverridden() {
        String external = Base64.getEncoder().encodeToString(new byte[64]);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        environment.setProperty("JWT_SECRET", external);
        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(environment);

        initializer.initialize(context);

        assertThat(environment.getProperty("JWT_SECRET")).isEqualTo(external);
        assertThat(environment.getPropertySources().contains("devGeneratedJwtSecret")).isFalse();
    }

    @Test
    void configuredFixtureSecretIsNeverOverridden() {
        String fixture = Base64.getEncoder().encodeToString(new byte[64]);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        environment.getPropertySources().addFirst(
                new MapPropertySource("testFixture", java.util.Map.of("app.jwt.secret", fixture)));
        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(environment);

        initializer.initialize(context);

        assertThat(environment.getProperty("app.jwt.secret")).isEqualTo(fixture);
        assertThat(environment.getPropertySources().contains("devGeneratedJwtSecret")).isFalse();
    }

    @Test
    void nonDevProfileIsLeftUntouched() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("mysql");
        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(environment);

        initializer.initialize(context);

        assertThat(environment.getPropertySources().contains("devGeneratedJwtSecret")).isFalse();
    }
}
