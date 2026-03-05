package com.wtfrepo.backend.admin.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtfrepo.backend.admin.application.AdminPlatformService;
import com.wtfrepo.backend.admin.application.support.AdminExceptions;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminAuthResult;
import com.wtfrepo.backend.shared.json.JsonUtils;
import com.wtfrepo.backend.shared.security.IssuedToken;
import com.wtfrepo.backend.shared.security.UserBanPolicy;
import com.wtfrepo.backend.shared.web.RequestIdConstants;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminPlatformController.class)
class AdminPlatformControllerOAuthTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private AdminPlatformService adminPlatformService;

  @MockBean private UserBanPolicy userBanPolicy;

  @MockBean private JsonUtils jsonUtils;

  @BeforeEach
  void setUpUserBanPolicy() {
    when(userBanPolicy.findActiveBan(anyString())).thenReturn(Optional.empty());
  }

  @Test
  void authorizeOAuth_shouldIssueCodeForManager() throws Exception {
    when(
            adminPlatformService.issueOAuthAuthorizationCode(
                anyString(),
                nullable(String.class),
                anyString(),
                anyString(),
                anyString(),
                nullable(String.class),
                nullable(String.class)))
        .thenReturn(
            new AdminPlatformService.OAuthAuthorizeResult(
                "aoc_123",
                Instant.parse("2026-03-03T12:00:00Z"),
                "http://localhost:3001/auth/callback",
                "state_123",
                "usr_admin_1",
                "nan",
                List.of("ADMIN")));

    mockMvc
        .perform(
            post("/api/v1/admin/platform/oauth/authorize")
                .with(csrf())
                .with(jwt().jwt(jwt -> jwt.subject("usr_admin_1").claim("roles", List.of("MANAGER"))))
                .header(RequestIdConstants.HEADER_NAME, "req-oauth-authz-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AdminPlatformController.AdminOAuthAuthorizeRequest(
                            "http://localhost:3001/auth/callback", "state_123"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("aoc_123"))
        .andExpect(jsonPath("$.redirectUri").value("http://localhost:3001/auth/callback"))
        .andExpect(jsonPath("$.state").value("state_123"))
        .andExpect(jsonPath("$.user.userId").value("usr_admin_1"));
  }

  @Test
  void authorizeOAuth_shouldRejectWhenMissingJwt() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/platform/oauth/authorize")
                .with(csrf())
                .header(RequestIdConstants.HEADER_NAME, "req-oauth-authz-no-jwt")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AdminPlatformController.AdminOAuthAuthorizeRequest(
                            "http://localhost:3001/auth/callback", "state_123"))))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(adminPlatformService);
  }

  @Test
  void authorizeOAuth_shouldRejectBlankState() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/platform/oauth/authorize")
                .with(csrf())
                .with(jwt().jwt(jwt -> jwt.subject("usr_admin_1").claim("roles", List.of("MANAGER"))))
                .header(RequestIdConstants.HEADER_NAME, "req-oauth-authz-invalid-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AdminPlatformController.AdminOAuthAuthorizeRequest(
                            "http://localhost:3001/auth/callback", "  "))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    verifyNoInteractions(adminPlatformService);
  }

  @Test
  void authorizeOAuth_shouldReturnForbiddenWhenUserHasNoAdminRole() throws Exception {
    when(
            adminPlatformService.issueOAuthAuthorizationCode(
                anyString(),
                nullable(String.class),
                anyString(),
                anyString(),
                anyString(),
                nullable(String.class),
                nullable(String.class)))
        .thenThrow(AdminExceptions.forbidden("admin_role_required"));

    mockMvc
        .perform(
            post("/api/v1/admin/platform/oauth/authorize")
                .with(csrf())
                .with(jwt().jwt(jwt -> jwt.subject("usr_user_1").claim("roles", List.of("USER"))))
                .header(RequestIdConstants.HEADER_NAME, "req-oauth-authz-forbidden-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AdminPlatformController.AdminOAuthAuthorizeRequest(
                            "http://localhost:3001/auth/callback", "state_123"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"))
        .andExpect(jsonPath("$.message").value("admin_role_required"));
  }

  @Test
  void exchangeOAuthToken_shouldReturnAdminToken() throws Exception {
    AdminAuthResult authResult =
        new AdminAuthResult(
            new IssuedToken("admin_token_jwt", 28800, Instant.parse("2026-03-03T16:00:00Z")),
            "usr_admin_1",
            "nan",
            List.of("ADMIN"));
    when(
            adminPlatformService.exchangeOAuthAuthorizationCode(
                anyString(),
                nullable(String.class),
                anyString(),
                anyString(),
                anyString(),
                nullable(String.class),
                nullable(String.class)))
        .thenReturn(authResult);

    mockMvc
        .perform(
            post("/api/v1/admin/platform/oauth/token")
                .with(csrf())
                .with(jwt().jwt(jwt -> jwt.subject("usr_admin_1").claim("roles", List.of("USER"))))
                .header(RequestIdConstants.HEADER_NAME, "req-oauth-token-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AdminPlatformController.AdminOAuthTokenRequest(
                            "aoc_123",
                            "http://localhost:3001/auth/callback",
                            "state_123"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("admin_token_jwt"))
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.user.userId").value("usr_admin_1"))
        .andExpect(jsonPath("$.user.roles[0]").value("ADMIN"));
  }

  @Test
  void exchangeOAuthToken_shouldRejectInvalidAuthorizationCode() throws Exception {
    when(
            adminPlatformService.exchangeOAuthAuthorizationCode(
                anyString(),
                nullable(String.class),
                anyString(),
                anyString(),
                anyString(),
                nullable(String.class),
                nullable(String.class)))
        .thenThrow(AdminExceptions.forbidden("invalid_or_expired_authorization_code"));

    mockMvc
        .perform(
            post("/api/v1/admin/platform/oauth/token")
                .with(csrf())
                .with(jwt().jwt(jwt -> jwt.subject("usr_admin_1").claim("roles", List.of("USER"))))
                .header(RequestIdConstants.HEADER_NAME, "req-oauth-token-invalid-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AdminPlatformController.AdminOAuthTokenRequest(
                            "bad_code",
                            "http://localhost:3001/auth/callback",
                            "state_123"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"))
        .andExpect(jsonPath("$.message").value("invalid_or_expired_authorization_code"));
  }
}
