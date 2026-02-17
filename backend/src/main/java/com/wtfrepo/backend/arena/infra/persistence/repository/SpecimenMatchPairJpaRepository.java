package com.wtfrepo.backend.arena.infra.persistence.repository;

import com.wtfrepo.backend.arena.infra.persistence.entity.SpecimenMatchPairId;
import com.wtfrepo.backend.arena.infra.persistence.entity.SpecimenMatchPairJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpecimenMatchPairJpaRepository
    extends JpaRepository<SpecimenMatchPairJpaEntity, SpecimenMatchPairId> {

  List<SpecimenMatchPairJpaEntity> findAllByOrderByMatchScoreDescLeftSpecimenIdAscRightSpecimenIdAsc();

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      delete from SpecimenMatchPairJpaEntity pair
      where pair.leftSpecimenId = :specimenId
         or pair.rightSpecimenId = :specimenId
      """)
  int deleteAllBySpecimenId(@Param("specimenId") String specimenId);
}
