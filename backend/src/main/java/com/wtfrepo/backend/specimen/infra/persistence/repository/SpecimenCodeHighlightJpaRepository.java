package com.wtfrepo.backend.specimen.infra.persistence.repository;

import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenCodeHighlightJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecimenCodeHighlightJpaRepository
    extends JpaRepository<SpecimenCodeHighlightJpaEntity, String> {

  void deleteBySpecimenId(String specimenId);

  List<SpecimenCodeHighlightJpaEntity> findBySpecimenIdOrderByPriorityAsc(String specimenId);
}
