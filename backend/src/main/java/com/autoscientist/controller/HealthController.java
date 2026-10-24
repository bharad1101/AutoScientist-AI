package com.autoscientist.controller;

import com.autoscientist.dto.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight health-check endpoint, publicly accessible (see SecurityConfig.permitAll).
 * Returns a minimal status payload suitable for Render's HTTP health checks.
 * Does not disclose secrets, environment details, or application data.
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", "UP");
        status.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(ApiResponse.success(status));
    }
}
