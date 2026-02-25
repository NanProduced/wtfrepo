package com.wtfrepo.backend.admin.infra.support;

import com.wtfrepo.backend.admin.application.AdminUserBanStore;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminUserBanRecord;
import com.wtfrepo.backend.shared.security.UserBanPolicy;
import java.time.Instant;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/** Admin-backed implementation for checking active user bans. */
@Component
@ConditionalOnBean(AdminUserBanStore.class)
public class AdminUserBanPolicy implements UserBanPolicy {

  private final AdminUserBanStore adminUserBanStore;

  public AdminUserBanPolicy(AdminUserBanStore adminUserBanStore) {
    this.adminUserBanStore = adminUserBanStore;
  }

  @Override
  public Optional<UserBanSnapshot> findActiveBan(String userId) {
    Optional<AdminUserBanRecord> recordOpt = adminUserBanStore.findActiveByUserId(userId);
    if (recordOpt.isEmpty()) {
      return Optional.empty();
    }
    AdminUserBanRecord record = recordOpt.get();
    if (record.expiresAt() != null && record.expiresAt().isBefore(Instant.now())) {
      return Optional.empty();
    }
    return Optional.of(
        new UserBanSnapshot(
            record.userId(),
            record.banType() != null ? record.banType().name() : null,
            record.reason(),
            record.bannedAt(),
            record.expiresAt()));
  }
}
