package com.wtfrepo.backend.shared.security;

import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/** Default no-op user ban policy used when admin module is unavailable. */
@Component
@ConditionalOnMissingBean(UserBanPolicy.class)
public class NoOpUserBanPolicy implements UserBanPolicy {

  @Override
  public Optional<UserBanSnapshot> findActiveBan(String userId) {
    return Optional.empty();
  }
}
