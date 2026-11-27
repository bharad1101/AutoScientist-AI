package com.autoscientist;

import com.autoscientist.config.WebConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebConfigCorsTest {

    @Test
    void wildcardAndMalformedOriginsAreRejectedAndUsefulHeadersAreExposed() {
        WebConfig webConfig = new WebConfig();
        ReflectionTestUtils.setField(webConfig, "allowedOrigins",
                "https://frontend.example, *, https://user:pass@bad.example, http://localhost:5173, not a url");

        CorsConfiguration cors = webConfig.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest());

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins())
                .containsExactly("https://frontend.example", "http://localhost:5173")
                .doesNotContain("*", "https://user:pass@bad.example", "not a url");
        assertThat(cors.getExposedHeaders()).contains(
                "Authorization", "X-Request-Id", "X-RateLimit-Limit", "X-RateLimit-Remaining", "Retry-After");
        assertThat(cors.getAllowCredentials()).isTrue();
    }

    @Test
    void noValidOriginsFailClosed() {
        WebConfig webConfig = new WebConfig();
        ReflectionTestUtils.setField(webConfig, "allowedOrigins", "*, not a url, https://user:pass@bad.example");

        assertThatThrownBy(() -> webConfig.corsConfigurationSource())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_CORS_ALLOWED_ORIGINS");
    }
}
