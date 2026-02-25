package com.wtfrepo.backend.auth.application;

import com.wtfrepo.backend.auth.infra.persistence.entity.AuthUserJpaEntity;
import com.wtfrepo.backend.auth.infra.persistence.repository.AuthUserJpaRepository;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Maintains auth-side wallet snapshot to avoid cross-module balance drift. */
@Service
@ConditionalOnBean(AuthUserJpaRepository.class)
public class AuthWalletSnapshotService {

  private final AuthUserJpaRepository authUserJpaRepository;

  public AuthWalletSnapshotService(AuthUserJpaRepository authUserJpaRepository) {
    this.authUserJpaRepository = authUserJpaRepository;
  }

  @Transactional
  public void applyBugBalance(String userId, long balanceAfter) {
    if (!StringUtils.hasText(userId)) {
      return;
    }
    String normalizedUserId = userId.trim();
    Optional<AuthUserJpaEntity> userOpt = authUserJpaRepository.findById(normalizedUserId);
    if (userOpt.isEmpty()) {
      return;
    }
    AuthUserJpaEntity user = userOpt.get();
    boolean changed = user.refreshBugBalance(balanceAfter);
    if (changed) {
      authUserJpaRepository.save(user);
    }
  }
}
