package com.wtfrepo.backend.economy.infra.persistence.repository;

import com.wtfrepo.backend.economy.domain.EconomyLedgerType;
import com.wtfrepo.backend.economy.infra.persistence.entity.EconomyLedgerJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for wallet ledger entries. */
public interface EconomyLedgerJpaRepository extends JpaRepository<EconomyLedgerJpaEntity, String> {

  Optional<EconomyLedgerJpaEntity> findByIdempotencyKey(String idempotencyKey);

  @Query(
      """
      SELECT COALESCE(SUM(CASE WHEN ledger.delta > 0 THEN ledger.delta ELSE 0 END), 0)
      FROM EconomyLedgerJpaEntity ledger
      WHERE ledger.userId = :userId
      """)
  long sumEarnedByUserId(@Param("userId") String userId);

  @Query(
      """
      SELECT COALESCE(SUM(CASE WHEN ledger.delta < 0 THEN -ledger.delta ELSE 0 END), 0)
      FROM EconomyLedgerJpaEntity ledger
      WHERE ledger.userId = :userId
      """)
  long sumSpentByUserId(@Param("userId") String userId);

  @Query(
      """
      SELECT ledger
      FROM EconomyLedgerJpaEntity ledger
      WHERE ledger.userId = :userId
        AND (:entryType IS NULL OR ledger.entryType = :entryType)
        AND (
          :cursorCreatedAt IS NULL
          OR ledger.createdAt < :cursorCreatedAt
          OR (ledger.createdAt = :cursorCreatedAt AND ledger.ledgerId < :cursorLedgerId)
        )
      ORDER BY ledger.createdAt DESC, ledger.ledgerId DESC
      """)
  List<EconomyLedgerJpaEntity> findLedgerPage(
      @Param("userId") String userId,
      @Param("entryType") EconomyLedgerType entryType,
      @Param("cursorCreatedAt") Instant cursorCreatedAt,
      @Param("cursorLedgerId") String cursorLedgerId,
      Pageable pageable);
}
