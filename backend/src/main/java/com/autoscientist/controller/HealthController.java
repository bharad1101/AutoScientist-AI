package com.autoscientist.controller;

import com.autoscientist.dto.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @GetMapping("/api/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        return ResponseEntity.ok(ApiResponse.success(status("UP", null, null)));
    }

    /**
     * Readiness probe: proves datasource + storage backend are reachable.
     * Returns no connection details or exception text.
     */
    @GetMapping("/api/health/ready")
    public ResponseEntity<ApiResponse<Map<String, Object>>> readiness() {
        boolean databaseUp = databaseIsReachable();
        boolean storageUp = fileStorageService.isHealthy();
        boolean allUp = databaseUp && storageUp;
        Map<String, Object> status = status(allUp ? "UP" : "DOWN",
                databaseUp ? "UP" : "DOWN",
                storageUp ? "UP" : "DOWN");
        status.put("storageBackend", fileStorageService.getBackendDescription());
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

    private Map<String, Object> status(String status, String database, String storage) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        if (database != null) {
            payload.put("database", database);
        }
        if (storage != null) {
            payload.put("storage", storage);
        }
        payload.put("timestamp", Instant.now().toString());
        return payload;
    }
}
