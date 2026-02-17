package com.wtfrepo.backend.economy.domain;

import org.springframework.util.StringUtils;

/** Three-way prediction direction used by betting module. */
public enum BetDirection {
  UP,
  FLAT,
  DOWN;

  public static BetDirection parse(String rawValue) {
    if (!StringUtils.hasText(rawValue)) {
      return null;
    }
    try {
      return BetDirection.valueOf(rawValue.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }
}
