package com.wtfrepo.backend.specimen.application;

import com.wtfrepo.backend.specimen.application.model.SpecimenModels.RepoIdentitiesResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.RepoIdentityItem;
import com.wtfrepo.backend.specimen.application.support.SpecimenConstants;
import com.wtfrepo.backend.specimen.application.support.SpecimenExceptions;
import com.wtfrepo.backend.specimen.domain.RepoIdentityRole;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenRepoIdentityJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenRepoIdentityJpaRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpecimenRepoIdentityService {

  private final SpecimenJpaRepository specimenJpaRepository;
  private final SpecimenRepoIdentityJpaRepository specimenRepoIdentityJpaRepository;

  public SpecimenRepoIdentityService(
      SpecimenJpaRepository specimenJpaRepository,
      SpecimenRepoIdentityJpaRepository specimenRepoIdentityJpaRepository) {
    this.specimenJpaRepository = specimenJpaRepository;
    this.specimenRepoIdentityJpaRepository = specimenRepoIdentityJpaRepository;
  }

  @Transactional(readOnly = true)
  public RepoIdentitiesResult getRepoIdentities(String specimenId) {
    if (!specimenJpaRepository.existsById(specimenId)) {
      throw SpecimenExceptions.notFound(SpecimenConstants.Message.SPECIMEN_NOT_FOUND);
    }

    List<SpecimenRepoIdentityJpaEntity> identities =
        specimenRepoIdentityJpaRepository.findBySpecimenIdAndActiveTrue(specimenId);

    RepoIdentityItem owner =
        identities.stream()
            .filter(identity -> identity.getRole() == RepoIdentityRole.OWNER)
            .findFirst()
            .map(this::toRepoIdentityItem)
            .orElse(null);

    List<RepoIdentityItem> contributors =
        identities.stream()
            .filter(
                identity ->
                    identity.getRole() == RepoIdentityRole.CONTRIBUTOR
                        || identity.getRole() == RepoIdentityRole.MAINTAINER)
            .sorted(
                Comparator.comparing(
                        SpecimenRepoIdentityJpaEntity::getContributions,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(SpecimenRepoIdentityJpaEntity::getGithubLogin))
            .map(this::toRepoIdentityItem)
            .toList();

    Instant syncedAt =
        identities.stream()
            .map(SpecimenRepoIdentityJpaEntity::getSyncedAt)
            .max(Comparator.naturalOrder())
            .orElse(null);

    return new RepoIdentitiesResult(specimenId, owner, contributors, syncedAt);
  }

  private RepoIdentityItem toRepoIdentityItem(SpecimenRepoIdentityJpaEntity entity) {
    return new RepoIdentityItem(
        entity.getGithubLogin(),
        entity.getGithubUserId(),
        entity.getGithubAvatarUrl(),
        entity.getGithubHtmlUrl(),
        entity.getContributions());
  }
}
