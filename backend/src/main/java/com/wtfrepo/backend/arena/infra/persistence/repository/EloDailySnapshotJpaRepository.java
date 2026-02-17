package com.wtfrepo.backend.arena.infra.persistence.repository;

import com.wtfrepo.backend.arena.infra.persistence.entity.EloDailySnapshotId;
import com.wtfrepo.backend.arena.infra.persistence.entity.EloDailySnapshotJpaEntity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EloDailySnapshotJpaRepository
    extends JpaRepository<EloDailySnapshotJpaEntity, EloDailySnapshotId> {

  Optional<EloDailySnapshotJpaEntity> findBySpecimenIdAndDate(String specimenId, LocalDate date);

  List<EloDailySnapshotJpaEntity> findAllBySpecimenIdInAndDateBetween(
      Collection<String> specimenIds, LocalDate fromInclusive, LocalDate toInclusive);

  long countByDate(LocalDate date);
}
