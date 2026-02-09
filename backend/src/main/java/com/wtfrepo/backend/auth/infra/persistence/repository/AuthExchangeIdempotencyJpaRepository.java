package com.wtfrepo.backend.auth.infra.persistence.repository;

import com.wtfrepo.backend.auth.infra.persistence.entity.AuthExchangeIdempotencyJpaEntity;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthExchangeIdempotencyJpaRepository
    extends JpaRepository<AuthExchangeIdempotencyJpaEntity, String> {

  void deleteByCreatedAtBefore(Instant threshold);
}
