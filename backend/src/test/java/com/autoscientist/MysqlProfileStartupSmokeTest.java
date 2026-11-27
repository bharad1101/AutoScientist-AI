package com.autoscientist;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MysqlProfileStartupSmokeTest {

    @Test
    void mysqlProfileFailsWhenJwtSecretIsMissing() {
        assertThatThrownBy(() -> start(
                "--spring.profiles.active=mysql",
                "--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.flyway.enabled=true",
                "--spring.datasource.url=jdbc:mysql://db.invalid:3306/app?sslMode=VERIFY_IDENTITY",
                "--SPRING_DATASOURCE_URL=jdbc:mysql://db.invalid:3306/app?sslMode=VERIFY_IDENTITY",
                "--spring.datasource.username=user",
                "--spring.datasource.password=not-a-real-secret",
                "--app.cors.allowed-origins=https://app.invalid",
                "--app.password-reset.frontend-url=https://app.invalid"))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void mysqlProfileRejectsDevelopmentDefaultJwtSecret() {
        String retiredDevSecret = "nSeYbXJhKTYlRQtnQg0TqiLs45XBcUDQl7SHhIK1SQRyyUzQUD3z0cz6/o5V7o7ypfaigm2uSg0YiwfP6Crp3A==";
        assertThatThrownBy(() -> start(
                "--spring.profiles.active=mysql",
                "--JWT_SECRET=" + retiredDevSecret,
                "--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.flyway.enabled=true",
                "--spring.datasource.url=jdbc:mysql://db.invalid:3306/app?sslMode=VERIFY_IDENTITY",
                "--SPRING_DATASOURCE_URL=jdbc:mysql://db.invalid:3306/app?sslMode=VERIFY_IDENTITY",
                "--spring.datasource.username=user",
                "--spring.datasource.password=not-a-real-secret",
                "--app.cors.allowed-origins=https://app.invalid",
                "--app.password-reset.frontend-url=https://app.invalid"))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("development/default");
    }

    private void start(String... args) {
        try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(AutoScientistApplication.class)
                .web(WebApplicationType.NONE)
                .run(args)) {
            assertThat(ignored.isRunning()).isTrue();
        }
    }
}
