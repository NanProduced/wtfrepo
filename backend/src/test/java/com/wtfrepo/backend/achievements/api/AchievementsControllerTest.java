package com.wtfrepo.backend.achievements.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wtfrepo.backend.achievements.application.AchievementQueryService;
import com.wtfrepo.backend.shared.json.JsonUtils;
import com.wtfrepo.backend.shared.security.UserBanPolicy;
import com.wtfrepo.backend.shared.web.ApiException;
import com.wtfrepo.backend.shared.web.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AchievementsController.class)
class AchievementsControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private AchievementQueryService achievementQueryService;

  @MockBean private UserBanPolicy userBanPolicy;

  @MockBean private JsonUtils jsonUtils;

  @BeforeEach
  void setUpUserBanPolicy() {
    when(userBanPolicy.findActiveBan(anyString())).thenReturn(Optional.empty());
  }

  @Test
  void listShouldReturnAchievementPage() throws Exception {
    when(
            achievementQueryService.listMyAchievements(
                eq("usr_1"), eq(new AchievementQueryService.ListQuery("all", "idx_0", 20))))
        .thenReturn(
            new AchievementQueryService.ListResult(
                List.of(
                    new AchievementQueryService.ListItem(
                        "ACH_NARRATOR_MODE_SWITCHER",
                        "Narrator Mode Switcher",
                        "首次切换 Narrator 模式",
                        "/icons/ach/narrator-mode-switcher.svg",
                        "BRONZE",
                        false,
                        true,
                        Instant.parse("2026-02-28T10:00:00Z"),
                        50)),
                null,
                false));

    mockMvc
        .perform(
            get("/api/v1/me/achievements")
                .with(jwt().jwt(jwt -> jwt.subject("usr_1")))
                .param("status", "all")
                .param("cursor", "idx_0")
                .param("limit", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].achievementCode").value("ACH_NARRATOR_MODE_SWITCHER"))
        .andExpect(jsonPath("$.items[0].isUnlocked").value(true))
        .andExpect(jsonPath("$.items[0].rewardBug").value(50))
        .andExpect(jsonPath("$.hasMore").value(false));
  }

  @Test
  void summaryShouldReturnAggregateView() throws Exception {
    when(achievementQueryService.summary(eq("usr_1")))
        .thenReturn(
            new AchievementQueryService.SummaryResult(
                3,
                2,
                67,
                170L,
                new AchievementQueryService.LatestUnlock(
                    "ACH_TICKER_SCALPER", "Ticker Scalper", Instant.parse("2026-02-28T10:00:00Z")),
                Map.of(
                    "BRONZE", new AchievementQueryService.TierBreakdown(1, 1),
                    "SILVER", new AchievementQueryService.TierBreakdown(2, 1))));

    mockMvc
        .perform(get("/api/v1/me/achievements/summary").with(jwt().jwt(jwt -> jwt.subject("usr_1"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalAchievements").value(3))
        .andExpect(jsonPath("$.unlockedCount").value(2))
        .andExpect(jsonPath("$.totalBugEarned").value(170))
        .andExpect(jsonPath("$.latestUnlock.achievementCode").value("ACH_TICKER_SCALPER"))
        .andExpect(jsonPath("$.tierBreakdown.BRONZE.total").value(1))
        .andExpect(jsonPath("$.tierBreakdown.SILVER.unlocked").value(1));
  }

  @Test
  void listShouldRejectInvalidLimit() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/me/achievements")
                .with(jwt().jwt(jwt -> jwt.subject("usr_1")))
                .param("limit", "101"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    verifyNoInteractions(achievementQueryService);
  }

  @Test
  void summaryShouldRequireAuthentication() throws Exception {
    mockMvc
        .perform(get("/api/v1/me/achievements/summary"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void detailShouldReturnDetailPayload() throws Exception {
    when(achievementQueryService.detail(eq("usr_1"), eq("ACH_TICKER_SCALPER")))
        .thenReturn(
            new AchievementQueryService.DetailResult(
                "ACH_TICKER_SCALPER",
                "Ticker Scalper",
                "通过 Ticker 点击进入并完成一次投票",
                "/icons/ach/ticker-scalper.svg",
                "SILVER",
                120,
                12L,
                30.0d,
                true,
                Instant.parse("2026-02-28T11:00:00Z")));

    mockMvc
        .perform(
            get("/api/v1/me/achievements/ACH_TICKER_SCALPER")
                .with(jwt().jwt(jwt -> jwt.subject("usr_1"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.achievementCode").value("ACH_TICKER_SCALPER"))
        .andExpect(jsonPath("$.rewardBug").value(120))
        .andExpect(jsonPath("$.totalUnlockedCount").value(12))
        .andExpect(jsonPath("$.totalUnlockedPercentage").value(30.0))
        .andExpect(jsonPath("$.isUnlocked").value(true));
  }

  @Test
  void detailShouldReturn404WhenAchievementNotFound() throws Exception {
    when(achievementQueryService.detail(eq("usr_1"), eq("ACH_UNKNOWN")))
        .thenThrow(new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "ach_not_found"));

    mockMvc
        .perform(get("/api/v1/me/achievements/ACH_UNKNOWN").with(jwt().jwt(jwt -> jwt.subject("usr_1"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
  }
}
