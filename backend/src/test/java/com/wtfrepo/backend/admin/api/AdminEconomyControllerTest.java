package com.wtfrepo.backend.admin.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtfrepo.backend.admin.application.AdminEconomyService;
import com.wtfrepo.backend.admin.application.AdminEconomyService.AdminGrantBugResult;
import com.wtfrepo.backend.admin.application.support.AdminConstants;
import com.wtfrepo.backend.shared.json.JsonUtils;
import com.wtfrepo.backend.shared.security.UserBanPolicy;
import com.wtfrepo.backend.shared.web.RequestIdConstants;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminEconomyController.class)
class AdminEconomyControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private AdminEconomyService adminEconomyService;

  @MockBean private UserBanPolicy userBanPolicy;

  @MockBean private JsonUtils jsonUtils;

  @BeforeEach
  void setUpUserBanPolicy() {
    when(userBanPolicy.findActiveBan(anyString())).thenReturn(Optional.empty());
  }

  @Test
  void grantBug_shouldAllowManagerRole() throws Exception {
    when(
            adminEconomyService.grantBug(
                any(), anyString(), anyString(), anyString(), anyInt(), anyString(), any(), any(), any()))
        .thenReturn(
            new AdminGrantBugResult(
                "ldg_admin_001", "usr_2", 300, "ADMIN_GRANT", 2300L, "req-admin-econ-1", "manual"));

    mockMvc
        .perform(
            post("/api/v1/admin/economy/grant-bug")
                .with(jwt().jwt(jwt -> jwt.subject("mgr_1").claim("roles", List.of("MANAGER"))))
                .header(RequestIdConstants.HEADER_NAME, "req-admin-econ-1")
                .header(AdminConstants.Header.IDEMPOTENCY_KEY, "idem-admin-econ-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AdminEconomyController.AdminGrantBugRequest(
                            "usr_2", 300, "ADMIN_GRANT", "manual"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ledgerId").value("ldg_admin_001"))
        .andExpect(jsonPath("$.userId").value("usr_2"))
        .andExpect(jsonPath("$.delta").value(300))
        .andExpect(jsonPath("$.reason").value("ADMIN_GRANT"))
        .andExpect(jsonPath("$.balanceAfter").value(2300));
  }

  @Test
  void grantBug_shouldRejectNonAdminRoles() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/economy/grant-bug")
                .with(jwt().jwt(jwt -> jwt.subject("usr_1").claim("roles", List.of("USER"))))
                .header(RequestIdConstants.HEADER_NAME, "req-admin-econ-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AdminEconomyController.AdminGrantBugRequest(
                            "usr_2", 300, "ADMIN_GRANT", "manual"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));

    verifyNoInteractions(adminEconomyService);
  }
}
