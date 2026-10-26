package com.autoscientist.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeploymentProfileGuard implements ApplicationRunner {

    private final Environment environment;
    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        boolean devProfile = environment.acceptsProfiles(Profiles.of("dev"));
        boolean testProfile = environment.acceptsProfiles(Profiles.of("test"));

        if (devProfile) {
            if (isHostedRuntime()) {
                throw new IllegalStateException(
                        "Refusing to start with the 'dev' profile on a hosted runtime. "
                                + "The dev profile uses a local H2 database, so deployed accounts would differ from production. "
                                + "Set SPRING_PROFILES_ACTIVE=mysql and provide SPRING_DATASOURCE_URL, "
                                + "SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD and JWT_SECRET."
                );
            }
            log.info("Deployment guard: local dev profile accepted.");
            return;
        }

        if (testProfile) {
            return;
        }

        String databaseUrl;
        try (Connection connection = dataSource.getConnection()) {
            databaseUrl = connection.getMetaData().getURL();
        }

        if (databaseUrl != null && isEmbeddedDatabase(databaseUrl)) {
            throw new IllegalStateException(
                    "Refusing to start with active profile(s) "
                            + Arrays.toString(environment.getActiveProfiles())
                            + " because the datasource resolved to an embedded database ("
                            + databaseUrl
                            + "). Production must use the 'mysql' profile with SPRING_DATASOURCE_URL."
            );
        }

        log.info("Deployment guard: datasource is not embedded; active profile(s) {} accepted.",
                Arrays.toString(environment.getActiveProfiles()));
    }

    private boolean isHostedRuntime() {
        return Boolean.parseBoolean(environment.getProperty("RENDER", "false"))
                || !environment.getProperty("RENDER_SERVICE_ID", "").isBlank()
                || !environment.getProperty("RENDER_EXTERNAL_URL", "").isBlank();
    }

    private boolean isEmbeddedDatabase(String url) {
        return url.startsWith("jdbc:h2:")
                || url.startsWith("jdbc:hsqldb:")
                || url.startsWith("jdbc:derby:");
    }
}
