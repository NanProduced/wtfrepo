package com.wtfrepo.backend.achievements.application;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Runtime policy for M08 evaluator behavior. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.achievements")
public class AchievementsPolicyProperties {

  /** Lookback window for correlating ticker click to vote completion. */
  private Duration tickerToVoteWindow = Duration.ofMinutes(5);

  /** Max ticker click events scanned per vote-correlation evaluation. */
  private int tickerClickLookbackSize = 50;

  /** Max narrator preference history events scanned for FULL streak calculation. */
  private int narratorHistoryLookbackSize = 64;
}
