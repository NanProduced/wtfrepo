package com.wtfrepo.backend.economy.infra.persistence.repository;

import com.wtfrepo.backend.economy.infra.persistence.entity.EconomyLedgerJpaEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for wallet ledger entries. */
public interface EconomyLedgerJpaRepository extends JpaRepository<EconomyLedgerJpaEntity, String> {

  Optional<EconomyLedgerJpaEntity> findByIdempotencyKey(String idempotencyKey);
}

