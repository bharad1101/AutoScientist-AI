package com.autoscientist;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.ConfigurableWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "AS_TEST_MYSQL_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AS_TEST_MYSQL_ENVIRONMENT", matches = "staging")
@EnabledIfEnvironmentVariable(named = "AS_TEST_MYSQL_USER", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AS_TEST_MYSQL_PASSWORD", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AS_TEST_MYSQL_ALLOW_SCHEMA_CREATE_DROP", matches = "true")
class MysqlProfileRuntimeSmokeTest {

    private static final String URL = System.getenv("AS_TEST_MYSQL_URL");
    private static final String USER = System.getenv("AS_TEST_MYSQL_USER");
    private static final String PASSWORD = System.getenv("AS_TEST_MYSQL_PASSWORD");

    private DataSource dataSource(String url) {
        org.springframework.jdbc.datasource.DriverManagerDataSource ds =
                new org.springframework.jdbc.datasource.DriverManagerDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setUrl(url);
        ds.setUsername(USER);
        ds.setPassword(PASSWORD);
        return ds;
    }

    private int scalar(DataSource ds, String sql) throws Exception {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void mysqlProfileMigratesAndServesReadinessProbe() throws Exception {
        String base = URL.trim();
        String schema = "as_it_smoke_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String location = base.contains("?") ? base.substring(0, base.indexOf('?')) : base;
        String query = base.contains("?") ? base.substring(base.indexOf('?') + 1) : "";
        if (query.isBlank()) query = "sslMode=REQUIRED";
        if (!query.matches("(?is).*(^|&)serverTimezone=.*")) query += "&serverTimezone=UTC";
        String schemaUrl = location.replaceAll("/$", "") + "/" + schema + "?" + query;
        DataSource root = dataSource(base.contains("?") ? base : location);
        boolean created = false;
        try (Connection c = root.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4");
            created = true;
        }
        try {
            String generatedSecret = TestJwtSecrets.freshBase64Key();
            try (ConfigurableApplicationContext context = new SpringApplicationBuilder(AutoScientistApplication.class)
                    .web(WebApplicationType.SERVLET)
                    .run(
                            "--spring.profiles.active=mysql",
                            "--server.port=0",
                            "--APP_ENVIRONMENT=test-mysql-disposable",
                            "--spring.datasource.url=" + schemaUrl,
                            "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                            "--spring.datasource.username=" + USER,
                            "--spring.datasource.password=" + PASSWORD,
                            "--spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
                            "--spring.jpa.hibernate.ddl-auto=validate",
                            "--spring.flyway.enabled=true",
                            "--app.upload.dir=" + java.nio.file.Path.of("target", "mysql-smoke-uploads").toAbsolutePath(),
                            "--app.jwt.secret=" + generatedSecret,
                            "--app.cors.allowed-origins=https://app.invalid",
                            "--app.password-reset.frontend-url=https://app.invalid")) {
                int port = ((ConfigurableWebServerApplicationContext) context).getWebServer().getPort();
                HttpRequest request = HttpRequest.newBuilder(
                                URI.create("http://127.0.0.1:" + port + "/api/health/ready"))
                        .GET().build();
                HttpResponse<String> response = HttpClient.newHttpClient()
                        .send(request, HttpResponse.BodyHandlers.ofString());

                assertThat(response.statusCode()).as("readiness probe must succeed").isEqualTo(200);
                assertThat(response.body()).contains("UP");
                assertThat(scalar(dataSource(schemaUrl),
                        "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL"))
                        .as("Flyway must record V1-V7").isEqualTo(7);
            }
        } finally {
            if (created) {
                try (Connection c = root.getConnection(); Statement s = c.createStatement()) {
                    s.executeUpdate("DROP DATABASE " + schema);
                }
            }
        }
    }
}
