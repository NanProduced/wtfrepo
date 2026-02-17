package com.wtfrepo.backend.specimen.infra.persistence.repository;

import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenJpaEntity;
import com.wtfrepo.backend.specimen.domain.SpecimenStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecimenJpaRepository extends JpaRepository<SpecimenJpaEntity, String> {

  List<SpecimenJpaEntity> findByStatus(SpecimenStatus status);

  List<SpecimenJpaEntity> findByStatusAndRepoFullNameContainingIgnoreCase(
      SpecimenStatus status, String repoFullName);

  List<SpecimenJpaEntity> findBySpecimenIdIn(Collection<String> specimenIds);
}
