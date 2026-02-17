package com.wtfrepo.backend.auth.infra.persistence.adapter;

import com.wtfrepo.backend.auth.application.AuthContractProperties;
import com.wtfrepo.backend.auth.application.OAuthStateStore;
import com.wtfrepo.backend.auth.infra.persistence.entity.OAuthStateJpaEntity;
import com.wtfrepo.backend.auth.infra.persistence.repository.OAuthStateJpaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Primary
@ConditionalOnBean(OAuthStateJpaRepository.class)
public class JpaOAuthStateStore implements OAuthStateStore {

  private final OAuthStateJpaRepository repository;
  private final AuthContractProperties authContractProperties;

  public JpaOAuthStateStore(
      OAuthStateJpaRepository repository, AuthContractProperties authContractProperties) {
    this.repository = repository;
    this.authContractProperties = authContractProperties;
  }

  @Override
  @Transactional
  public boolean consumeOnce(String oauthState) {
    if (oauthState == null || oauthState.isBlank()) {
      return false;
    }

    // Best-effort cleanup keeps table size bounded by configured state TTL.
    repository.deleteByConsumedAtBefore(
        java.time.Instant.now().minus(authContractProperties.getOauthStateTtl()));

    try {
      // Unique key on state guarantees consume-once semantics.
      repository.save(OAuthStateJpaEntity.consumed(oauthState));
      return true;
    } catch (DataIntegrityViolationException ex) {
      return false;
    }
  }
}
