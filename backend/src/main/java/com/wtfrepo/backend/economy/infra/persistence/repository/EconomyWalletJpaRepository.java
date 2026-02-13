package com.wtfrepo.backend.economy.infra.persistence.repository;

import com.wtfrepo.backend.economy.infra.persistence.entity.EconomyWalletJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for economy wallet row-level updates. */
public interface EconomyWalletJpaRepository extends JpaRepository<EconomyWalletJpaEntity, String> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select wallet
      from EconomyWalletJpaEntity wallet
      where wallet.userId = :userId
      """)
  Optional<EconomyWalletJpaEntity> findByUserIdForUpdate(@Param("userId") String userId);
}

