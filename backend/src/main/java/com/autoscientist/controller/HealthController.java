package com.autoscientist.controller;

import com.autoscientist.dto.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight health-check endpoint, publicly accessible (see SecurityConfig.permitAll).
 * Returns a minimal status payload suitable for Render's HTTP health checks.
 * Does not disclose secrets, environment details, or application data.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class HealthController {

    private final DataSource dataSource;
    private final com.autoscientist.util.FileStorageService fileStorageService;
    private final ObjectProvider<Flyway> flywayProvider;

    @GetMapping("/api/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        return ResponseEntity.ok(ApiResponse.success(status("UP", null, null, null)));
    }

    /**
     * Readiness probe: proves datasource + storage backend are reachable and
     * that Flyway has no pending or failed migrations when Flyway is enabled.
     * Returns no connection details or exception text.
     */
    @GetMapping("/api/health/ready")
    public ResponseEntity<ApiResponse<Map<String, Object>>> readiness() {
        boolean databaseUp = databaseIsReachable();
        boolean storageUp = fileStorageService.isHealthy();
        Flyway flyway = flywayProvider.getIfAvailable();
        boolean migrationsUp = flyway == null || migrationsAreCurrent(flyway);
        String migrationStatus = flyway == null ? "SKIPPED" : (migrationsUp ? "UP" : "DOWN");
        boolean allUp = databaseUp && storageUp && migrationsUp;
        Map<String, Object> status = status(allUp ? "UP" : "DOWN",
                databaseUp ? "UP" : "DOWN",
                storageUp ? "UP" : "DOWN",
                migrationStatus);
        if (allUp) {
            return ResponseEntity.ok(ApiResponse.success(status));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.<Map<String, Object>>builder()
                        .success(false)
                        .message("Service is not ready.")
                        .data(status)
                        .build());
    }

    private boolean databaseIsReachable() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            return true;
        } catch (SQLException e) {
            log.warn("Readiness check failed: datasource is not reachable ({})", e.getClass().getSimpleName());
            return false;
        }
    }

    private boolean migrationsAreCurrent(Flyway flyway) {
        try {
            MigrationInfoService info = flyway.info();
            MigrationInfo current = info.current();
            return current != null
                    && current.getState() == MigrationState.SUCCESS
                    && info.pending().length == 0;
        } catch (RuntimeException e) {
            log.warn("Readiness check failed: Flyway state is unavailable ({})", e.getClass().getSimpleName());
            return false;
        }
    }

    private Map<String, Object> status(String status, String database, String storage, String migrations) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        if (database != null) {
            payload.put("database", database);
        }
        if (storage != null) {
            payload.put("storage", storage);
        }
        if (migrations != null) {
            payload.put("migrations", migrations);
        }
        payload.put("timestamp", Instant.now().toString());
        return payload;
    }
}
