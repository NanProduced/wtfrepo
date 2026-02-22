package com.wtfrepo.backend.economy.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wtfrepo.backend.economy.application.EconomyWalletService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = WalletController.class)
class WalletControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private EconomyWalletService economyWalletService;

  @Test
  void wallet_shouldReturnSingleSourceFields() throws Exception {
    when(economyWalletService.getWallet(eq("usr_1")))
        .thenReturn(new EconomyWalletService.WalletView("usr_1", 2300L, 5800L, 3500L, true, 500, 100));

    mockMvc
        .perform(get("/api/v1/wallet").with(jwt().jwt(jwt -> jwt.subject("usr_1"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value("usr_1"))
        .andExpect(jsonPath("$.balance").value(2300))
        .andExpect(jsonPath("$.totalEarned").value(5800))
        .andExpect(jsonPath("$.totalSpent").value(3500))
        .andExpect(jsonPath("$.dailyClaimed").value(true))
        .andExpect(jsonPath("$.dailyAmount").value(500))
        .andExpect(jsonPath("$.voteCost").value(100));
  }

  @Test
  void ledger_shouldReturnCursorPayload() throws Exception {
    when(economyWalletService.getLedger(eq("usr_1"), eq("ldg_003"), eq(20), eq("VOTE")))
        .thenReturn(
            new EconomyWalletService.LedgerPageView(
                List.of(
                    new EconomyWalletService.LedgerItemView(
                        "ldg_001",
                        -100,
                        2200,
                        "VOTE",
                        "bat_123",
                        "BATTLE",
                        Instant.parse("2026-02-15T10:30:00Z"))),
                "ldg_002",
                true));

    mockMvc
        .perform(
            get("/api/v1/wallet/ledger")
                .with(jwt().jwt(jwt -> jwt.subject("usr_1")))
                .param("cursor", "ldg_003")
                .param("limit", "20")
                .param("reason", "VOTE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].ledgerId").value("ldg_001"))
        .andExpect(jsonPath("$.items[0].delta").value(-100))
        .andExpect(jsonPath("$.items[0].reason").value("VOTE"))
        .andExpect(jsonPath("$.nextCursor").value("ldg_002"))
        .andExpect(jsonPath("$.hasMore").value(true));
  }

  @Test
  void ledger_shouldRejectInvalidLimit() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/wallet/ledger")
                .with(jwt().jwt(jwt -> jwt.subject("usr_1")))
                .param("limit", "51"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    verifyNoInteractions(economyWalletService);
  }
}

