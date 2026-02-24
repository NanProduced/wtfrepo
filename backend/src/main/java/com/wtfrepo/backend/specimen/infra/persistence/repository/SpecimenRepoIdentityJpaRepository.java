package com.wtfrepo.backend.specimen.infra.persistence.repository;

import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenRepoIdentityJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecimenRepoIdentityJpaRepository
    extends JpaRepository<SpecimenRepoIdentityJpaEntity, String> {

  void deleteBySpecimenId(String specimenId);

  List<SpecimenRepoIdentityJpaEntity> findBySpecimenId(String specimenId);

  List<SpecimenRepoIdentityJpaEntity> findBySpecimenIdAndActiveTrue(String specimenId);

  java.util.Optional<SpecimenRepoIdentityJpaEntity>
      findBySpecimenIdAndGithubUserIdAndActiveTrue(String specimenId, String githubUserId);
}
