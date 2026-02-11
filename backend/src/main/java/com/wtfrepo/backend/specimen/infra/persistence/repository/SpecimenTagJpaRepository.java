package com.wtfrepo.backend.specimen.infra.persistence.repository;

import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenTagJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenTagJpaId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecimenTagJpaRepository extends JpaRepository<SpecimenTagJpaEntity, SpecimenTagJpaId> {

  void deleteBySpecimenId(String specimenId);

  List<SpecimenTagJpaEntity> findBySpecimenId(String specimenId);
}
