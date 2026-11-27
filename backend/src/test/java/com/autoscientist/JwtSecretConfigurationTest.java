package com.autoscientist;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class JwtSecretConfigurationTest {

    private static final Pattern SECRET_LINE = Pattern.compile("(?m)^\\s*secret:\\s*(\\S+)\\s*$");
    private static final Pattern BASE64_LITERAL = Pattern.compile("[A-Za-z0-9+/=_-]{24,}");

    @Test
    void baseConfigurationRequiresExternalSecret() throws IOException {
        String config = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(config).contains("secret: ${JWT_SECRET}");
        assertThat(config).doesNotMatch("(?s).*secret:\\s*[A-Za-z0-9+/=_-]{24,}.*");
    }

    @Test
    void mainResourcesDoNotPackageADevelopmentSigningKey() throws IOException {
        Path devConfig = Path.of("src/main/resources/application-dev.yml");
        String content = Files.readString(devConfig);

        assertThat(Files.exists(devConfig)).isTrue();
        assertThat(SECRET_LINE.matcher(content).find()).isFalse();
        assertThat(BASE64_LITERAL.matcher(content).find()).isFalse();
        assertThat(content).contains("DevJwtSecretInitializer");
        assertThat(Files.exists(Path.of(
                "src/main/java/com/autoscientist/config/DevJwtSecretInitializer.java"))).isTrue();
        assertThat(Files.readString(Path.of("src/main/resources/META-INF/spring.factories")))
                .contains("com.autoscientist.config.DevJwtSecretInitializer");
    }

    @Test
    void testFixturesRemainIsolatedFromEachOther() throws IOException {
        Path testDevConfig = Path.of("src/test/resources/application-dev.yml");
        Path testConfig = Path.of("src/test/resources/application.yml");
        Path testProfileConfig = Path.of("src/test/resources/application-test.yml");

        assertThat(Files.exists(testDevConfig)).isTrue();
        assertThat(Files.exists(testConfig)).isTrue();
        assertThat(Files.exists(testProfileConfig)).isTrue();
        assertThat(secretValue(testDevConfig)).isNotEqualTo(secretValue(testConfig));
        assertThat(secretValue(testConfig)).isNotEqualTo(secretValue(testProfileConfig));
    }

    private String secretValue(Path path) throws IOException {
        Matcher matcher = SECRET_LINE.matcher(Files.readString(path));
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
