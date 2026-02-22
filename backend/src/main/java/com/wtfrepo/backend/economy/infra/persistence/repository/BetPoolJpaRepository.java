package com.wtfrepo.backend.economy.infra.persistence.repository;

import com.wtfrepo.backend.economy.domain.BetPoolStatus;
import com.wtfrepo.backend.economy.infra.persistence.entity.BetPoolId;
import com.wtfrepo.backend.economy.infra.persistence.entity.BetPoolJpaEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

  List<BetPoolJpaEntity> findAllByIdDateAndStatusOrderByIdSpecimenIdAsc(
      LocalDate tradingDay, BetPoolStatus status);

  /**
   * Cutoff fallback: force-close pools that have passed cutoff but still remain OPEN.
   *
   * <p>This query is idempotent because CLOSED/SETTLED rows are not touched.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update BetPoolJpaEntity pool
         set pool.status = :closedStatus
       where pool.id.date = :tradingDay
         and pool.status = :openStatus
         and pool.betCutoffAt <= :now
      """)
  int closeExpiredOpenPoolsForTradingDay(
      @Param("tradingDay") LocalDate tradingDay,
      @Param("openStatus") BetPoolStatus openStatus,
      @Param("closedStatus") BetPoolStatus closedStatus,
      @Param("now") Instant now);
}
