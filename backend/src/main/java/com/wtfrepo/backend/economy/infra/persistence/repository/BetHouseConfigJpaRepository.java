package com.wtfrepo.backend.economy.infra.persistence.repository;

import com.wtfrepo.backend.economy.infra.persistence.entity.BetHouseConfigJpaEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BetHouseConfigJpaRepository
    extends JpaRepository<BetHouseConfigJpaEntity, String> {

  Optional<BetHouseConfigJpaEntity> findBySpecimenId(String specimenId);
}
