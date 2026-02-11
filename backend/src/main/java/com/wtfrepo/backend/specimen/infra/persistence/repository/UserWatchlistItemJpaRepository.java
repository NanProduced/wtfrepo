package com.wtfrepo.backend.specimen.infra.persistence.repository;

import com.wtfrepo.backend.specimen.infra.persistence.entity.UserWatchlistItemJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserWatchlistItemJpaRepository
    extends JpaRepository<UserWatchlistItemJpaEntity, String> {

  long countByUserId(String userId);

  boolean existsByUserIdAndSpecimenId(String userId, String specimenId);

  void deleteByUserIdAndSpecimenId(String userId, String specimenId);

  List<UserWatchlistItemJpaEntity> findByUserId(String userId);
}
