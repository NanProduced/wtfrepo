package com.wtfrepo.backend.specimen.infra.persistence.repository;

import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenReadmeExcerptJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecimenReadmeExcerptJpaRepository
    extends JpaRepository<SpecimenReadmeExcerptJpaEntity, String> {

  void deleteBySpecimenId(String specimenId);

  List<SpecimenReadmeExcerptJpaEntity> findBySpecimenIdOrderByPriorityAsc(String specimenId);
}
