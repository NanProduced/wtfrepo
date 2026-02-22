package com.wtfrepo.backend.economy.infra.persistence.repository;

import com.wtfrepo.backend.economy.infra.persistence.entity.EconomyDailyClaimJpaEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for economy daily claim facts. */
public interface EconomyDailyClaimJpaRepository
    extends JpaRepository<EconomyDailyClaimJpaEntity, String> {

  Optional<EconomyDailyClaimJpaEntity> findByUserIdAndTradingDay(String userId, LocalDate tradingDay);

  List<EconomyDailyClaimJpaEntity>
      findTop30ByUserIdAndTradingDayLessThanEqualOrderByTradingDayDesc(
          String userId, LocalDate tradingDay);
}
