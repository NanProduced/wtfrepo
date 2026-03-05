package com.wtfrepo.backend.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.wtfrepo.backend.admin.application.model.AdminModels.AdminRoleRecord;
import com.wtfrepo.backend.admin.application.support.AdminConstants;
import com.wtfrepo.backend.admin.application.support.AdminRequestFingerprintCalculator;
import com.wtfrepo.backend.admin.domain.AdminRole;
import com.wtfrepo.backend.auth.application.AuthUserRecord;
import com.wtfrepo.backend.auth.application.AuthUserStore;
import com.wtfrepo.backend.auth.domain.OAuthProvider;
import com.wtfrepo.backend.notifications.application.NotificationBroadcastAdminService;
import com.wtfrepo.backend.shared.idempotency.IdempotentOperationExecutor;
import com.wtfrepo.backend.shared.idempotency.IdempotentOperationResult;
import com.wtfrepo.backend.shared.security.IssuedToken;
import com.wtfrepo.backend.shared.security.TokenBlacklistStore;
import com.wtfrepo.backend.shared.security.TokenService;
import com.wtfrepo.backend.shared.web.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminPlatformServiceOAuthTest {

  @Mock private AdminRoleStore adminRoleStore;
  @Mock private AdminBootstrapStore adminBootstrapStore;
  @Mock private AdminAuditLogStore adminAuditLogStore;
  @Mock private AdminSafetyTicketStore adminSafetyTicketStore;
  @Mock private AdminAlertStore adminAlertStore;
  @Mock private AdminUserBanStore adminUserBanStore;
  @Mock private AuthUserStore authUserStore;
  @Mock private NotificationBroadcastAdminService broadcastAdminService;
  @Mock private TokenService tokenService;
  @Mock private TokenBlacklistStore tokenBlacklistStore;
  @Mock private AdminRequestFingerprintCalculator requestFingerprintCalculator;
  @Mock private IdempotentOperationExecutor idempotentExecutor;

  private AdminPlatformService service;

  @BeforeEach
  void setUp() {
    AdminPlatformProperties properties = new AdminPlatformProperties();
    properties.getOauth().setCodeExpiration(Duration.ofMinutes(5));
    properties.getOauth().setAllowedRedirectUris(List.of("http://localhost:3001/auth/callback"));

    lenient()
        .when(requestFingerprintCalculator.fingerprint(anyString()))
        .thenAnswer(invocation -> "fp_" + Math.abs(invocation.getArgument(0, String.class).hashCode()));
    lenient()
        .when(tokenService.issueToken(any()))
        .thenReturn(new IssuedToken("admin_token_jwt", 28800, Instant.parse("2026-03-04T10:00:00Z")));
    lenient()
        .when(idempotentExecutor.execute(anyString(), anyString(), anyString(), any()))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Supplier<Object> operation = (Supplier<Object>) invocation.getArgument(3);
              return new IdempotentOperationResult<>(operation.get(), false);
            });

    service =
        new AdminPlatformService(
            adminRoleStore,
            adminBootstrapStore,
            adminAuditLogStore,
            adminSafetyTicketStore,
            adminAlertStore,
            adminUserBanStore,
            authUserStore,
            broadcastAdminService,
            tokenService,
            tokenBlacklistStore,
            requestFingerprintCalculator,
            idempotentExecutor,
            properties);
  }

  @Test
  void issueOAuthAuthorizationCode_shouldRejectRedirectOutsideAllowList() {
    assertThatThrownBy(
            () ->
                service.issueOAuthAuthorizationCode(
                    "req-1",
                    "idem-1",
                    "usr_admin_1",
                    "https://evil.example.com/callback",
                    "state_1",
                    "127.0.0.1",
                    "test-agent"))
        .isInstanceOf(ApiException.class)
        .satisfies(
            throwable -> {
              ApiException apiException = (ApiException) throwable;
              assertThat(apiException.getMessage())
                  .isEqualTo(AdminConstants.Message.INVALID_REDIRECT_URI);
            });
  }

  @Test
  void exchangeOAuthAuthorizationCode_shouldConsumeCodeOnce() {
    when(authUserStore.findByUserId("usr_admin_1"))
        .thenReturn(Optional.of(userRecord("usr_admin_1")));
    when(adminRoleStore.findActiveRoles("usr_admin_1"))
        .thenReturn(List.of(activeRole("usr_admin_1", AdminRole.ADMIN)));

    AdminPlatformService.OAuthAuthorizeResult authorizeResult =
        service.issueOAuthAuthorizationCode(
            "req-authz-1",
            "idem-authz-1",
            "usr_admin_1",
            "http://localhost:3001/auth/callback/",
            "state_123",
            "127.0.0.1",
            "test-agent");

    assertThat(authorizeResult.code()).startsWith("aoc_");
    assertThat(authorizeResult.redirectUri()).isEqualTo("http://localhost:3001/auth/callback");
    assertThat(authorizeResult.state()).isEqualTo("state_123");

    var authResult =
        service.exchangeOAuthAuthorizationCode(
            "req-token-1",
            "idem-token-1",
            authorizeResult.code(),
            "http://localhost:3001/auth/callback",
            "state_123",
            "127.0.0.1",
            "test-agent");

    assertThat(authResult.issuedToken().accessToken()).isEqualTo("admin_token_jwt");
    assertThat(authResult.userId()).isEqualTo("usr_admin_1");
    assertThat(authResult.roles()).containsExactly("ADMIN");

    assertThatThrownBy(
            () ->
                service.exchangeOAuthAuthorizationCode(
                    "req-token-2",
                    "idem-token-2",
                    authorizeResult.code(),
                    "http://localhost:3001/auth/callback",
                    "state_123",
                    "127.0.0.1",
                    "test-agent"))
        .isInstanceOf(ApiException.class)
        .satisfies(
            throwable -> {
              ApiException apiException = (ApiException) throwable;
              assertThat(apiException.getMessage())
                  .isEqualTo(AdminConstants.Message.INVALID_OR_EXPIRED_AUTHORIZATION_CODE);
            });
  }

  @Test
  void exchangeOAuthAuthorizationCode_shouldNotConsumeCodeWhenStateMismatch() {
    when(authUserStore.findByUserId("usr_admin_1"))
        .thenReturn(Optional.of(userRecord("usr_admin_1")));
    when(adminRoleStore.findActiveRoles("usr_admin_1"))
        .thenReturn(List.of(activeRole("usr_admin_1", AdminRole.ADMIN)));

    AdminPlatformService.OAuthAuthorizeResult authorizeResult =
        service.issueOAuthAuthorizationCode(
            "req-authz-2",
            "idem-authz-2",
            "usr_admin_1",
            "http://localhost:3001/auth/callback",
            "state_abc",
            "127.0.0.1",
            "test-agent");

    assertThatThrownBy(
            () ->
                service.exchangeOAuthAuthorizationCode(
                    "req-token-bad-state",
                    "idem-token-bad-state",
                    authorizeResult.code(),
                    "http://localhost:3001/auth/callback",
                    "wrong_state",
                    "127.0.0.1",
                    "test-agent"))
        .isInstanceOf(ApiException.class)
        .satisfies(
            throwable -> {
              ApiException apiException = (ApiException) throwable;
              assertThat(apiException.getMessage())
                  .isEqualTo(AdminConstants.Message.INVALID_OR_EXPIRED_AUTHORIZATION_CODE);
            });

    var authResult =
        service.exchangeOAuthAuthorizationCode(
            "req-token-good-state",
            "idem-token-good-state",
            authorizeResult.code(),
            "http://localhost:3001/auth/callback",
            "state_abc",
            "127.0.0.1",
            "test-agent");

    assertThat(authResult.userId()).isEqualTo("usr_admin_1");
    assertThat(authResult.roles()).containsExactly("ADMIN");
  }

  private AuthUserRecord userRecord(String userId) {
    return new AuthUserRecord(
        userId,
        OAuthProvider.GITHUB,
        "github_subject_" + userId,
        "nan",
        false,
        1000L,
        false);
  }

  private AdminRoleRecord activeRole(String userId, AdminRole role) {
    return new AdminRoleRecord(
        userId, role, true, "usr_bootstrap", Instant.parse("2026-03-03T00:00:00Z"), null, null);
  }
}
