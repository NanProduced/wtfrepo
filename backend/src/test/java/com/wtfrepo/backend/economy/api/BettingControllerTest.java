package com.wtfrepo.backend.economy.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtfrepo.backend.economy.application.BettingService;
import com.wtfrepo.backend.economy.application.support.BettingConstants;
import com.wtfrepo.backend.economy.application.support.BettingExceptions;
import com.wtfrepo.backend.shared.json.JsonUtils;
import com.wtfrepo.backend.shared.security.UserBanPolicy;
import com.wtfrepo.backend.shared.web.RequestIdConstants;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = BettingController.class)
class BettingControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private BettingService bettingService;

  @MockBean private UserBanPolicy userBanPolicy;

  @MockBean private JsonUtils jsonUtils;

  @BeforeEach
  void setUpUserBanPolicy() {
    when(userBanPolicy.findActiveBan(anyString())).thenReturn(Optional.empty());
  }

  @Test
  void placeBet_shouldReturnCreated() throws Exception {
    when(bettingService.placeBet(any(), eq("usr_1"), any(), any()))
        .thenReturn(
            new BettingService.PlaceBetResult(
                "bet_ord_123",
                "sp_001",
                "UP",
                200,
                new BigDecimal("1.73"),
                LocalDate.parse("2026-02-12"),
                "PENDING",
                900L));

    mockMvc
        .perform(
            post("/api/v1/bet")
                .with(jwt().jwt(jwt -> jwt.subject("usr_1")))
                .header(RequestIdConstants.HEADER_NAME, "req-bet-1")
                .header(BettingConstants.Header.IDEMPOTENCY_KEY, "idem-bet-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new BetPlaceRequest("sp_001", "UP", 200))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.orderId").value("bet_ord_123"))
        .andExpect(jsonPath("$.specimenId").value("sp_001"))
        .andExpect(jsonPath("$.walletBalanceAfter").value(900));
  }

  @Test
  void placeBet_shouldMapDomainError() throws Exception {
    when(bettingService.placeBet(any(), eq("usr_1"), any(), any()))
        .thenThrow(BettingExceptions.invalidDirection(BettingConstants.Message.BET_INVALID_DIRECTION));

    mockMvc
        .perform(
            post("/api/v1/bet")
                .with(jwt().jwt(jwt -> jwt.subject("usr_1")))
                .header(RequestIdConstants.HEADER_NAME, "req-bet-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new BetPlaceRequest("sp_001", "INVALID", 200))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BET_INVALID_DIRECTION"));
  }

  @Test
  void history_shouldReturnCursorPayload() throws Exception {
    when(bettingService.getHistory(eq("usr_1"), eq("bet_ord_456"), eq(20)))
        .thenReturn(
            new BettingService.HistoryBetsView(
                List.of(
                    new BettingService.HistoryBetItem(
                        "bet_ord_455",
                        "sp_003",
                        "wenyan-lang",
                        "DOWN",
                        500,
                        new BigDecimal("3.20"),
                        "WON",
                        1600L,
                        0L,
                        LocalDate.parse("2026-02-10"),
                        Instant.parse("2026-02-11T00:00:12Z"))),
                "bet_ord_454",
                true));

    mockMvc
        .perform(
            get("/api/v1/bet/history")
                .with(jwt().jwt(jwt -> jwt.subject("usr_1")))
                .param("cursor", "bet_ord_456")
                .param("limit", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orders[0].orderId").value("bet_ord_455"))
        .andExpect(jsonPath("$.nextCursor").value("bet_ord_454"))
        .andExpect(jsonPath("$.hasMore").value(true));
  }

  @Test
  void settlementToday_shouldReturnGroupedPayload() throws Exception {
    when(bettingService.getSettlementToday(eq("usr_1"), eq("bet_ord_456"), eq(20)))
        .thenReturn(
            new BettingService.SettlementTodayView(
                LocalDate.parse("2026-02-11"),
                List.of(
                    new BettingService.SettlementItem(
                        "sp_003",
                        "wenyan-lang",
                        1512,
                        1530,
                        18,
                        "UP",
                        true,
                        List.of(
                            new BettingService.SettlementOrderItem(
                                "bet_ord_455", "UP", 500, "WON", 1600L, 120L)))),
                "bet_ord_454",
                true));

    mockMvc
        .perform(
            get("/api/v1/settlement/today")
                .with(jwt().jwt(jwt -> jwt.subject("usr_1")))
                .param("cursor", "bet_ord_456")
                .param("limit", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.date").value("2026-02-11"))
        .andExpect(jsonPath("$.settlements[0].specimenId").value("sp_003"))
        .andExpect(jsonPath("$.settlements[0].specimenTitle").value("wenyan-lang"))
        .andExpect(jsonPath("$.settlements[0].eloOpen").value(1512))
        .andExpect(jsonPath("$.settlements[0].eloClose").value(1530))
        .andExpect(jsonPath("$.settlements[0].deltaR").value(18))
        .andExpect(jsonPath("$.settlements[0].outcome").value("UP"))
        .andExpect(jsonPath("$.settlements[0].isMoonDoom").value(true))
        .andExpect(jsonPath("$.settlements[0].myOrders[0].orderId").value("bet_ord_455"))
        .andExpect(jsonPath("$.settlements[0].myOrders[0].direction").value("UP"))
        .andExpect(jsonPath("$.settlements[0].myOrders[0].amount").value(500))
        .andExpect(jsonPath("$.settlements[0].myOrders[0].status").value("WON"))
        .andExpect(jsonPath("$.settlements[0].myOrders[0].payout").value(1600))
        .andExpect(jsonPath("$.settlements[0].myOrders[0].moonDoomBonus").value(120))
        .andExpect(jsonPath("$.nextCursor").value("bet_ord_454"))
        .andExpect(jsonPath("$.hasMore").value(true));
  }


  @Test
  void settlementToday_shouldRejectInvalidLimit() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/settlement/today")
                .with(jwt().jwt(jwt -> jwt.subject("usr_1")))
                .param("limit", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    mockMvc
        .perform(
            get("/api/v1/settlement/today")
                .with(jwt().jwt(jwt -> jwt.subject("usr_1")))
                .param("limit", "101"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    verifyNoInteractions(bettingService);
  }

  @Test
  void placeBet_shouldRejectInvalidPayload() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/bet")
                .with(jwt().jwt(jwt -> jwt.subject("usr_1")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BetPlaceRequest("sp_001", "UP", 0))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    verifyNoInteractions(bettingService);
  }
}
