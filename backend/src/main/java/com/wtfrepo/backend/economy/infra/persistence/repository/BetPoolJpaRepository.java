package com.wtfrepo.backend.economy.infra.persistence.repository;

import com.wtfrepo.backend.economy.infra.persistence.entity.BetPoolId;
import com.wtfrepo.backend.economy.infra.persistence.entity.BetPoolJpaEntity;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BetPoolJpaRepository extends JpaRepository<BetPoolJpaEntity, BetPoolId> {

  @Query(
      """
      select pool
      from BetPoolJpaEntity pool
      where pool.id.specimenId = :specimenId
        and pool.id.date = :tradingDay
      """)
  Optional<BetPoolJpaEntity> findBySpecimenIdAndDate(
      @Param("specimenId") String specimenId, @Param("tradingDay") LocalDate tradingDay);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select pool
      from BetPoolJpaEntity pool
      where pool.id.specimenId = :specimenId
        and pool.id.date = :tradingDay
      """)
  Optional<BetPoolJpaEntity> findBySpecimenIdAndDateForUpdate(
      @Param("specimenId") String specimenId, @Param("tradingDay") LocalDate tradingDay);
}
