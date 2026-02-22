package com.wtfrepo.backend.economy.infra.persistence.repository;

import com.wtfrepo.backend.economy.domain.GameTypeStatus;
import com.wtfrepo.backend.economy.infra.persistence.entity.GameTypeConfigJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for configurable game type metadata. */
public interface GameTypeConfigJpaRepository extends JpaRepository<GameTypeConfigJpaEntity, String> {

  List<GameTypeConfigJpaEntity> findByStatusOrderBySortOrderAscGameTypeKeyAsc(GameTypeStatus status);

  List<GameTypeConfigJpaEntity> findAllByOrderBySortOrderAscGameTypeKeyAsc();
}

