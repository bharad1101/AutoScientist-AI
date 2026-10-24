package com.autoscientist;

import com.autoscientist.security.AuthEntryPointJwt;
import com.autoscientist.security.JwtUtils;
import com.autoscientist.security.UserDetailsServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for /api/health.
 *
 * Verifies:
 * 1. Health endpoint is publicly accessible (no token required).
 * 2. Response contains status=UP.
 * 3. Protected endpoints still require authentication (regression guard).
 */
@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("1. GET /api/health returns 200 without authentication")
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("2. GET /api/health does not require a JWT token")
    void healthEndpointRequiresNoToken() throws Exception {
        // Explicit: no Authorization header
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("3. GET /api/dashboard/stats requires authentication (401 without token)")
    void protectedEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/dashboard/stats"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("4. GET /api/datasets requires authentication (401 without token)")
    void datasetsEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/datasets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}
