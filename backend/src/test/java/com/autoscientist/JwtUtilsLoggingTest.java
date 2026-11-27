package com.autoscientist;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.autoscientist.security.JwtUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilsLoggingTest {

    @Test
    void malformedJwtIsLoggedWithAFixedMessageThatDoesNotIncludeTheToken() {
        JwtUtils jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", Base64.getEncoder().encodeToString(
                "a-jwt-signing-secret-that-is-long-enough".getBytes(StandardCharsets.UTF_8)));

        Logger logger = (Logger) LoggerFactory.getLogger(JwtUtils.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThat(jwtUtils.validateJwtToken("malformed.token.value")).isFalse();

            assertThat(appender.list).anySatisfy(event ->
                    assertThat(event.getFormattedMessage())
                            .isEqualTo("Invalid JWT token.")
                            .doesNotContain("malformed.token.value"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
