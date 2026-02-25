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
import com.wtfrepo.backend.economy.application.EconomyGameService;
import com.wtfrepo.backend.economy.application.support.EconomyConstants;
import com.wtfrepo.backend.shared.json.JsonUtils;
import com.wtfrepo.backend.shared.security.UserBanPolicy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = GameController.class)
class GameControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private EconomyGameService economyGameService;

  @MockBean private UserBanPolicy userBanPolicy;

  @MockBean private JsonUtils jsonUtils;

  @BeforeEach
  void setUpUserBanPolicy() {
    when(userBanPolicy.findActiveBan(anyString())).thenReturn(Optional.empty());
  }

  @Test
  void listTypes_shouldReturnConfiguredGames() throws Exception {
    when(economyGameService.listGameTypes(eq("usr_1")))
        .thenReturn(
            new EconomyGameService.GameTypesView(
                List.of(
                    new EconomyGameService.GameTypeView(
                        "FLAPPY_DUKE",
                        "Flappy Duke",
                        "Flappy Duke",
                        "控制 Duke 飞越障碍",
                        "🪫",
                        "ACTIVE",
                        "SCORE_EQUALS_BUG",
                        50,
                        Map.of())),
                2000,
                45L));

    mockMvc
        .perform(get("/api/v1/game/types").with(jwt().jwt(jwt -> jwt.subject("usr_1"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.games[0].gameType").value("FLAPPY_DUKE"))
        .andExpect(jsonPath("$.dailyGameBugCap").value(2000))
        .andExpect(jsonPath("$.dailyGameBugEarned").value(45));
  }

  @Test
  void submitScore_shouldReturnSettlementPayload() throws Exception {
    when(economyGameService.submitScore(eq("usr_1"), eq("idem-game-1"), any()))
        .thenReturn(
            new EconomyGameService.SubmitScoreResult(
                "gs_001",
                "FLAPPY_DUKE",
                12,
                12,
                2312,
                "VALID",
                null,
                3,
                45,
                2000));

    mockMvc
        .perform(
            post("/api/v1/game/score")
                .with(jwt().jwt(jwt -> jwt.subject("usr_1")))
                .header(EconomyConstants.Header.IDEMPOTENCY_KEY, "idem-game-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new GameScoreRequest(
                            "FLAPPY_DUKE",
                            12,
                            45000,
                            "gs_client_001",
                            Map.of("obstacles_passed", 12)))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessionId").value("gs_001"))
        .andExpect(jsonPath("$.bugEarned").value(12))
        .andExpect(jsonPath("$.dailyGameBugCap").value(2000));
  }

  @Test
  void submitScore_shouldRejectInvalidPayload() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/game/score")
                .with(jwt().jwt(jwt -> jwt.subject("usr_1")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new GameScoreRequest("FLAPPY_DUKE", -1, 0, "", Map.of()))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    verifyNoInteractions(economyGameService);
  }
}
