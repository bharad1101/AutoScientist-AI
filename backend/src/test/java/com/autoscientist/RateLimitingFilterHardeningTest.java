package com.autoscientist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.autoscientist.security.RateLimitingFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitingFilterHardeningTest {

    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitingFilter(new ObjectMapper().registerModule(new JavaTimeModule()));
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "capacity", 1L);
        ReflectionTestUtils.setField(filter, "refillTokens", 1L);
        ReflectionTestUtils.setField(filter, "refillMinutes", 60L);
        ReflectionTestUtils.setField(filter, "maxBuckets", 2);
        ReflectionTestUtils.setField(filter, "trustedProxyCount", 0);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void forwardedHeaderIsIgnoredWhenNoProxyIsTrusted() throws Exception {
        assertThat(perform("198.51.100.10", "203.0.113.99").getStatus()).isEqualTo(200);

        // Same remote address but a different spoofed forwarded address must
        // still hit the same bucket.
        assertThat(perform("198.51.100.10", "203.0.113.100").getStatus()).isEqualTo(429);
    }

    @Test
    void trustedProxyUsesRightmostClientAddressBeforeTheProxyChain() throws Exception {
        ReflectionTestUtils.setField(filter, "trustedProxyCount", 1);

        assertThat(perform("10.0.0.1", "203.0.113.50, 10.0.0.1").getStatus()).isEqualTo(200);
        assertThat(perform("10.0.0.2", "203.0.113.50, 10.0.0.2").getStatus()).isEqualTo(429);
        assertThat(perform("10.0.0.2", "203.0.113.51, 10.0.0.2").getStatus()).isEqualTo(200);
    }

    @Test
    void untrustedOrMalformedForwardedValueFallsBackToRemoteAddress() throws Exception {
        ReflectionTestUtils.setField(filter, "trustedProxyCount", 1);

        assertThat(perform("198.51.100.20", "not-an-ip, 10.0.0.1").getStatus()).isEqualTo(200);
        assertThat(perform("198.51.100.20", "not-an-ip, 10.0.0.1").getStatus()).isEqualTo(429);
        assertThat(perform("198.51.100.20", "203.0.113.77, 10.0.0.1").getStatus()).isEqualTo(200);
    }

    @Test
    void invalidRemoteAddressesShareOneBoundedFallbackBucket() throws Exception {
        assertThat(perform("not-an-ip", null).getStatus()).isEqualTo(200);
        assertThat(perform("another-invalid-host", null).getStatus()).isEqualTo(429);
    }

    @Test
    void fullStoreEvictsLeastRecentlyUsedBucketAndKeepsActiveBuckets() throws Exception {
        assertThat(perform("192.0.2.1", null).getStatus()).isEqualTo(200);
        assertThat(perform("192.0.2.2", null).getStatus()).isEqualTo(200);
        assertThat(perform("192.0.2.1", null).getStatus()).isEqualTo(429);
        assertThat(perform("192.0.2.3", null).getStatus()).isEqualTo(200);

        // 192.0.2.1 was used most recently and must survive; 192.0.2.2 was the
        // least recently used and must have been evicted.
        assertThat(perform("192.0.2.1", null).getStatus()).isEqualTo(429);
        assertThat(perform("192.0.2.2", null).getStatus()).isEqualTo(200);
    }

    @Test
    void authenticatedPrincipalIsKeyedByUserRatherThanAddress() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.org", null, List.of()));

        assertThat(perform("192.0.2.10", null).getStatus()).isEqualTo(200);
        assertThat(perform("192.0.2.11", null).getStatus()).isEqualTo(429);
    }

    private MockHttpServletResponse perform(String remoteAddress, String forwardedFor) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(remoteAddress);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
