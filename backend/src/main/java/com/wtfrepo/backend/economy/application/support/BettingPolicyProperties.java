package com.wtfrepo.backend.economy.application.support;

import java.math.BigDecimal;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Property-backed betting policy used in economy MVP. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.betting.policy")
public class BettingPolicyProperties {

  /** Minimum allowed bet amount. */
  private int minBet = 100;

  /** Unified rake rate for three-way parimutuel pool. */
  private BigDecimal rakeRate = new BigDecimal("0.10");

  /** Default director fund budget for regular IPO specimens. */
  private int houseBudget = 2000;

  private BigDecimal houseWeightUp = new BigDecimal("0.40");
  private BigDecimal houseWeightFlat = new BigDecimal("0.30");
  private BigDecimal houseWeightDown = new BigDecimal("0.30");

  /** UTC trading cutoff for betting writes. */
  private LocalTime cutoffTimeUtc = LocalTime.of(23, 30);

  /** UTC trading open time for lazy pool creation. */
  private LocalTime openTimeUtc = LocalTime.of(0, 5);

  private int historyDefaultLimit = 20;
  private int historyMaxLimit = 100;

  /** Dead zone threshold D for UP/FLAT/DOWN settlement semantics. */
  private int deadZoneThreshold = 5;

  /** Sigma coefficient for Moon-or-Doom threshold formula. */
  private BigDecimal moonDoomSigmaMultiplier = new BigDecimal("1.2");

  private String policyVersion = "betting-policy-property-v1";
  private String policySource = "property";
}
