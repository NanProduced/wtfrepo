package com.wtfrepo.backend.economy.infra.persistence.repository;

import com.wtfrepo.backend.economy.domain.GameValidationStatus;
import com.wtfrepo.backend.economy.infra.persistence.entity.GameSessionJpaEntity;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for immutable game session records. */
public interface GameSessionJpaRepository extends JpaRepository<GameSessionJpaEntity, String> {

  Optional<GameSessionJpaEntity> findByIdempotencyKey(String idempotencyKey);

  long countByUserIdAndGameTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
      String userId, String gameType, Instant startInclusive, Instant endExclusive);

  @Query(
      """
      SELECT COALESCE(SUM(session.bugEarned), 0)
      FROM GameSessionJpaEntity session
      WHERE session.userId = :userId
        AND session.validationStatus = :validationStatus
        AND session.createdAt >= :startInclusive
        AND session.createdAt < :endExclusive
      """)
  long sumDailyBugEarned(
      @Param("userId") String userId,
      @Param("validationStatus") GameValidationStatus validationStatus,
      @Param("startInclusive") Instant startInclusive,
      @Param("endExclusive") Instant endExclusive);
}

