package com.autoscientist;

import com.autoscientist.controller.AuthController;
import com.autoscientist.dto.common.ApiResponse;
import com.autoscientist.security.JwtUtils;
import com.autoscientist.security.TokenRevocationService;
import com.autoscientist.service.AuthService;
import com.autoscientist.service.PasswordResetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogoutAuthorizationTest {

    @Mock
    private AuthService authService;
    @Mock
    private PasswordResetService passwordResetService;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private TokenRevocationService tokenRevocationService;

    @InjectMocks
    private AuthController authController;

    @Test
    void logoutWithoutAnAuthenticatedPrincipalIsRejected() {
        ResponseEntity<ApiResponse<Void>> response = authController.logout(null, "Bearer some.token.value");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(jwtUtils, tokenRevocationService);
    }

    @Test
    void logoutDoesNotRevokeATokenBelongingToAnotherUser() {
        UserDetails principal = mock(UserDetails.class);
        when(principal.getUsername()).thenReturn("alice@example.org");
        when(jwtUtils.validateJwtToken("bob-token")).thenReturn(true);
        when(jwtUtils.getUserNameFromJwtToken("bob-token")).thenReturn("bob@example.org");

        ResponseEntity<ApiResponse<Void>> response = authController.logout(
                principal, "Bearer bob-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verifyNoInteractions(tokenRevocationService);
    }

    @Test
    void logoutRevokesTheAuthenticatedUsersOwnToken() {
        UserDetails principal = mock(UserDetails.class);
        when(principal.getUsername()).thenReturn("alice@example.org");
        when(jwtUtils.validateJwtToken("alice-token")).thenReturn(true);
        when(jwtUtils.getUserNameFromJwtToken("alice-token")).thenReturn("alice@example.org");
        when(jwtUtils.getExpirationEpochMillis("alice-token")).thenReturn(123456789L);

        ResponseEntity<ApiResponse<Void>> response = authController.logout(
                principal, "Bearer alice-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(tokenRevocationService).revoke("alice-token", 123456789L);
    }
}
