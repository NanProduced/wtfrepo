package com.wtfrepo.backend.achievements.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Catalog of MVP achievement definitions used by evaluator and query APIs. */
@Component
public class AchievementCatalog {

  public static final String CODE_NARRATOR_MODE_SWITCHER = "ACH_NARRATOR_MODE_SWITCHER";
  public static final String CODE_CHAOS_FULL_ON = "ACH_CHAOS_FULL_ON";
  public static final String CODE_TICKER_SCALPER = "ACH_TICKER_SCALPER";

  private static final int REWARD_BRONZE = 50;
  private static final int REWARD_SILVER = 120;

  private final List<Definition> orderedDefinitions;
  private final Map<String, Definition> definitionsByCode;

  public AchievementCatalog() {
    List<Definition> definitions =
        List.of(
            new Definition(
                CODE_NARRATOR_MODE_SWITCHER,
                "Narrator Mode Switcher",
                "首次切换 Narrator 模式",
                "/icons/ach/narrator-mode-switcher.svg",
                "BRONZE",
                REWARD_BRONZE,
                false,
                10),
            new Definition(
                CODE_CHAOS_FULL_ON,
                "Chaos Full On",
                "切换到 FULL 并连续使用 3 天",
                "/icons/ach/chaos-full-on.svg",
                "SILVER",
                REWARD_SILVER,
                false,
                20),
            new Definition(
                CODE_TICKER_SCALPER,
                "Ticker Scalper",
                "通过 Ticker 点击进入并完成一次投票",
                "/icons/ach/ticker-scalper.svg",
                "SILVER",
                REWARD_SILVER,
                false,
                30));
    this.orderedDefinitions = definitions;
    Map<String, Definition> byCode = new LinkedHashMap<>();
    for (Definition definition : definitions) {
      byCode.put(definition.achievementCode(), definition);
    }
    this.definitionsByCode = Map.copyOf(byCode);
  }

  public List<Definition> definitions() {
    return orderedDefinitions;
  }

  public Optional<Definition> findByCode(String achievementCode) {
    if (achievementCode == null || achievementCode.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(definitionsByCode.get(achievementCode.trim()));
  }

  public record Definition(
      String achievementCode,
      String displayName,
      String description,
      String iconUrl,
      String tier,
      int rewardBug,
      boolean secret,
      int sortOrder) {}
}
