package com.autoscientist;

import com.autoscientist.dto.common.ApiResponse;
import com.autoscientist.exception.BadRequestException;
import com.autoscientist.exception.GlobalExceptionHandler;
import com.autoscientist.exception.ResourceLimitExceededException;
import com.autoscientist.exception.ResourceNotFoundException;
import com.autoscientist.exception.SafeErrorMessages;
import com.autoscientist.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerSecurityTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void illegalArgumentExceptionMessageIsNotReturnedToTheClient() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgument(
                new IllegalArgumentException("path traversal denied for /secret/config/key=abc123"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage())
                .isEqualTo("The request could not be processed.")
                .doesNotContain("/secret/config/key", "abc123", "path traversal");
    }

    @Test
    void pathVariableTypeMismatchIsRejectedAsBadRequest() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleTypeMismatch(
                new MethodArgumentTypeMismatchException("abc", Long.class, "id", null,
                        new IllegalArgumentException()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorMessage(response))
                .isEqualTo("The request contains a parameter with an invalid format.")
                .doesNotContain("abc", "Long");
    }

    @Test
    void apiExceptionMessagesWithSensitiveDetailsAreReplacedWithGenericText() {
        assertThat(errorMessage(handler.handleNotFound(
                new ResourceNotFoundException("Cannot read /var/lib/autoscientist/secret.txt"))))
                .isEqualTo(SafeErrorMessages.GENERIC);
        assertThat(errorMessage(handler.handleBadRequest(
                new BadRequestException("token=reset-secret-123 is invalid"))))
                .isEqualTo(SafeErrorMessages.GENERIC);
        assertThat(errorMessage(handler.handleUnauthorized(
                new UnauthorizedException("Bearer eyJhbGciOiJIUzI1NiJ9.e30.signature is not allowed"))))
                .isEqualTo(SafeErrorMessages.GENERIC);
        assertThat(errorMessage(handler.handleResourceLimit(
                new ResourceLimitExceededException("JDBC SQLState 22001: value too large"))))
                .isEqualTo(SafeErrorMessages.GENERIC);
    }

    @Test
    void resourceLimitViolationsReturnHttp413() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleResourceLimit(
                new ResourceLimitExceededException("Sheet exceeds the configured row limit of 100."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(errorMessage(response)).contains("row limit");
    }

    @Test
    void benignApiExceptionMessagesRemainAvailableToClients() {
        assertThat(errorMessage(handler.handleNotFound(
                new ResourceNotFoundException("Dataset not found with id: 42"))))
                .isEqualTo("Dataset not found with id: 42");
        assertThat(errorMessage(handler.handleBadRequest(
                new BadRequestException("Variable 'height' has no data in this dataset."))))
                .isEqualTo("Variable 'height' has no data in this dataset.");
    }

    @Test
    void genericExceptionResponseNeverIncludesMessageOrStackTrace() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleGenericException(
                new IllegalStateException("secret=/tmp/key at com.example.Secret.load(Secret.java:42)"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(errorMessage(response))
                .isEqualTo("An internal server error occurred.")
                .doesNotContain("secret", "/tmp/key", "Secret.java", "com.example");
    }

    @Test
    void logSanitizerRedactsCredentialsAndTokens() {
        String sanitized = SafeErrorMessages.forLog(
                "password=hunter2 token=abc123 jwt=eyJhbGciOiJIUzI1NiJ9.e30.sig "
                        + "failed loading /tmp/key with JDBC SQLState 28000");

        assertThat(sanitized)
                .contains("[redacted]")
                .doesNotContain("hunter2", "abc123", "eyJhbGciOiJIUzI1NiJ9", "/tmp/key", "SQLState", "JDBC");
    }

    private String errorMessage(ResponseEntity<ApiResponse<Void>> response) {
        assertThat(response.getBody()).isNotNull();
        return response.getBody().getMessage();
    }
}
