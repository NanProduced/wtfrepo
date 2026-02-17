package com.wtfrepo.backend.arena.infra.persistence.repository;

import com.wtfrepo.backend.arena.infra.persistence.entity.SpecimenRatingJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpecimenRatingJpaRepository
    extends JpaRepository<SpecimenRatingJpaEntity, String> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select rating
      from SpecimenRatingJpaEntity rating
      where rating.specimenId = :specimenId
      """)
  Optional<SpecimenRatingJpaEntity> findBySpecimenIdForUpdate(@Param("specimenId") String specimenId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select rating
      from SpecimenRatingJpaEntity rating
      where rating.specimenId in :specimenIds
      order by rating.specimenId asc
      """)
  List<SpecimenRatingJpaEntity> findAllBySpecimenIdInForUpdate(
      @Param("specimenIds") Collection<String> specimenIds);
}
