package com.wtfrepo.backend.arena.application.economy;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Temporary property-backed economy policy for arena module.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.arena.economy")
public class ArenaEconomyProperties {

  /**
   * Vote bug cost for duel participation.
   *
   * <p>TODO(M03-economy): source from unified policy publishing (DB + admin).
   */
  private int voteBugCost = 100;

  /**
   * Minimum allowed bet amount in bug unit.
   *
   * <p>TODO(M03-economy): source from unified policy publishing (DB + admin).
   */
  private int minBet = 100;

  /**
   * Platform rake rate (e.g. 0.10 = 10%).
   *
   * <p>TODO(M03-economy): source from unified policy publishing (DB + admin).
   */
  private BigDecimal rakeRate = new BigDecimal("0.10");

  /**
   * Published policy version identifier used for transaction snapshots.
   *
   * <p>TODO(M03-economy): replace with admin-published version id.
   */
  private String policyVersion = "arena-policy-property-v1";

  /**
   * Policy source marker persisted in snapshots.
   *
   * <p>TODO(M03-economy): move to policy metadata table after M03 integration.
   */
  private String policySource = "property";
}