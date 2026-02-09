package com.wtfrepo.backend.auth.infra.persistence.repository;

import com.wtfrepo.backend.auth.infra.persistence.entity.OAuthStateJpaEntity;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthStateJpaRepository extends JpaRepository<OAuthStateJpaEntity, String> {

  void deleteByConsumedAtBefore(Instant threshold);
}
