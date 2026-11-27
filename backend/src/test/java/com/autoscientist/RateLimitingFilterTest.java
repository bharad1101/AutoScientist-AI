package com.autoscientist;

import com.autoscientist.security.RateLimitingFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.capacity=2",
        "app.rate-limit.refill-tokens=2",
        "app.rate-limit.refill-minutes=60"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitingFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void filterIsRegisteredOnlyInTheSecurityChain() {
        FilterRegistrationBean<?> registration =
                applicationContext.getBean("rateLimitingFilterRegistration", FilterRegistrationBean.class);

        assertInstanceOf(RateLimitingFilter.class, registration.getFilter());
        assertFalse(registration.isEnabled(),
                "Automatic servlet-container registration must be disabled so the filter runs only in the security chain");
    }

    @Test
    void oneTokenIsConsumedPerRequest() throws Exception {
        String ip = "203.0.113.21";
        String body = "{\"email\":\"nobody@example.com\",\"password\":\"wrong-password\"}";

        mockMvc.perform(post("/api/auth/login")
                        .with(request -> { request.setRemoteAddr(ip); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-RateLimit-Limit", "2"))
                .andExpect(header().string("X-RateLimit-Remaining", "1"));

        mockMvc.perform(post("/api/auth/login")
                        .with(request -> { request.setRemoteAddr(ip); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-RateLimit-Remaining", "0"));

        mockMvc.perform(post("/api/auth/login")
                        .with(request -> { request.setRemoteAddr(ip); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Remaining", "0"));
    }

    @Test
    void loginRequestsAreRejectedAfterCapacityIsExhausted() throws Exception {
        String ip = "203.0.113.10";
        String body = "{\"email\":\"nobody@example.com\",\"password\":\"wrong-password\"}";

        mockMvc.perform(post("/api/auth/login")
                        .with(request -> { request.setRemoteAddr(ip); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .with(request -> { request.setRemoteAddr(ip); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .with(request -> { request.setRemoteAddr(ip); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Too many requests")));
    }

    @Test
    void actuatorInfoIsNotPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthEndpointsAreNeverRateLimited() throws Exception {
        String ip = "203.0.113.11";
        for (int i = 0; i < 6; i++) {
            mockMvc.perform(get("/api/health")
                            .with(request -> { request.setRemoteAddr(ip); return request; }))
                    .andExpect(status().isOk());
        }
    }
}
