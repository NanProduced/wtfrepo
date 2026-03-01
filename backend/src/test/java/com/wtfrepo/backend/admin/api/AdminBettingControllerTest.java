package com.wtfrepo.backend.admin.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtfrepo.backend.admin.application.AdminBettingService;
import com.wtfrepo.backend.admin.application.AdminBettingService.AdminForceSettleResult;
import com.wtfrepo.backend.admin.application.AdminBettingService.HouseConfigUpdateResult;
import com.wtfrepo.backend.admin.application.support.AdminConstants;
import com.wtfrepo.backend.economy.application.BettingService.HouseConfigRecord;
import com.wtfrepo.backend.shared.json.JsonUtils;
import com.wtfrepo.backend.shared.security.UserBanPolicy;
import com.wtfrepo.backend.shared.web.RequestIdConstants;
import java.math.BigDecimal;
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

@WebMvcTest(controllers = AdminBettingController.class)
class AdminBettingControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private AdminBettingService adminBettingService;

  @MockBean private UserBanPolicy userBanPolicy;

  @MockBean private JsonUtils jsonUtils;

  @BeforeEach
  void setUpUserBanPolicy() {
    when(userBanPolicy.findActiveBan(anyString())).thenReturn(Optional.empty());
  }

  @Test
  void forceSettle_shouldAllowAdminRole() throws Exception {
    when(adminBettingService.forceSettle(
            any(),
            eq("req-admin-bet-1"),
            eq("idem-admin-bet-1"),
            eq("sp_1"),
            eq("manual"),
            eq(true),
            any(),
            any()))
        .thenReturn(
            new AdminForceSettleResult(
                "2026-02-28", "FORCE_SETTLED", "OPEN", "SETTLED", 2, 1, 200L, true));

    mockMvc
        .perform(
            post("/api/v1/admin/betting/force-settle")
                .with(jwt().jwt(jwt -> jwt.subject("admin_1").claim("roles", List.of("ADMIN"))))
                .header(RequestIdConstants.HEADER_NAME, "req-admin-bet-1")
                .header(AdminConstants.Header.IDEMPOTENCY_KEY, "idem-admin-bet-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AdminBettingController.AdminForceSettleRequest(
                            "sp_1", "manual", true))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tradingDay").value("2026-02-28"))
        .andExpect(jsonPath("$.outcome").value("FORCE_SETTLED"))
        .andExpect(jsonPath("$.poolStatusBefore").value("OPEN"))
        .andExpect(jsonPath("$.poolStatusAfter").value("SETTLED"))
        .andExpect(jsonPath("$.cancelledOrders").value(2))
        .andExpect(jsonPath("$.refundedAmount").value(200))
        .andExpect(jsonPath("$.settled").value(true));
  }

  @Test
  void updateHouseConfig_shouldAllowAdminRole() throws Exception {
    HouseConfigRecord after =
        new HouseConfigRecord(
            "sp_1",
            5000L,
            new BigDecimal("0.4000"),
            new BigDecimal("0.3000"),
            new BigDecimal("0.3000"),
            "admin_1",
            Instant.parse("2026-02-28T10:00:00Z"),
            Instant.parse("2026-02-28T10:00:00Z"));
    when(adminBettingService.updateHouseConfig(
            any(),
            anyString(),
            anyString(),
            eq("sp_1"),
            eq(5000L),
            eq(new BigDecimal("0.4")),
            eq(new BigDecimal("0.3")),
            eq(new BigDecimal("0.3")),
            any(),
            any()))
        .thenReturn(new HouseConfigUpdateResult(null, after));

    mockMvc
        .perform(
            post("/api/v1/admin/betting/house-config")
                .with(jwt().jwt(jwt -> jwt.subject("admin_1").claim("roles", List.of("ADMIN"))))
                .header(RequestIdConstants.HEADER_NAME, "req-admin-bet-2")
                .header(AdminConstants.Header.IDEMPOTENCY_KEY, "idem-admin-bet-2")
        .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AdminBettingController.AdminHouseConfigRequest(
                            "sp_1",
                            5000L,
                            new BigDecimal("0.4"),
                            new BigDecimal("0.3"),
                            new BigDecimal("0.3")))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.after.specimenId").value("sp_1"))
        .andExpect(jsonPath("$.after.houseBudget").value(5000))
        .andExpect(jsonPath("$.after.weightUp").value(0.4))
        .andExpect(jsonPath("$.after.weightFlat").value(0.3))
        .andExpect(jsonPath("$.after.weightDown").value(0.3))
        .andExpect(jsonPath("$.after.updatedBy").value("admin_1"));
  }

  @Test
  void forceSettle_shouldRejectManagerRole() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/betting/force-settle")
                .with(jwt().jwt(jwt -> jwt.subject("mgr_1").claim("roles", List.of("MANAGER"))))
                .header(RequestIdConstants.HEADER_NAME, "req-admin-bet-3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AdminBettingController.AdminForceSettleRequest(
                            "sp_1", "manual", true))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));

    verifyNoInteractions(adminBettingService);
  }
}
