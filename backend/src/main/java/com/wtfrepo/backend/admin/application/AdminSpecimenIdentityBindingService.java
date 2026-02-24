package com.wtfrepo.backend.admin.application;

import com.wtfrepo.backend.auth.domain.OAuthProvider;
import com.wtfrepo.backend.auth.infra.persistence.entity.AuthIdentityJpaEntity;
import com.wtfrepo.backend.auth.infra.persistence.repository.AuthIdentityJpaRepository;
import com.wtfrepo.backend.comments.application.CommentAuthorRoleResolver;
import com.wtfrepo.backend.comments.domain.CommentAuthorRepoRoleSnapshot;
import com.wtfrepo.backend.specimen.domain.RepoIdentityRole;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenRepoIdentityJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenRepoIdentityJpaRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Resolves specimen identity bindings for internal consumers (M05). */
@Service("directCommentAuthorRoleResolver")
public class AdminSpecimenIdentityBindingService implements CommentAuthorRoleResolver {

  private final AuthIdentityJpaRepository authIdentityRepository;
  private final SpecimenRepoIdentityJpaRepository specimenRepoIdentityRepository;

  public AdminSpecimenIdentityBindingService(
      AuthIdentityJpaRepository authIdentityRepository,
      SpecimenRepoIdentityJpaRepository specimenRepoIdentityRepository) {
    this.authIdentityRepository = authIdentityRepository;
    this.specimenRepoIdentityRepository = specimenRepoIdentityRepository;
  }

  public SpecimenIdentityBindingRecord resolveBinding(String specimenId, String userId) {
    String normalizedSpecimenId = normalize(specimenId);
    String normalizedUserId = normalize(userId);
    if (!StringUtils.hasText(normalizedSpecimenId) || !StringUtils.hasText(normalizedUserId)) {
      return SpecimenIdentityBindingRecord.empty(specimenId, userId);
    }

    Optional<AuthIdentityJpaEntity> identityOpt =
        authIdentityRepository.findFirstByUserIdAndProvider(
            normalizedUserId, OAuthProvider.GITHUB);
    if (identityOpt.isEmpty()) {
      return SpecimenIdentityBindingRecord.empty(normalizedSpecimenId, normalizedUserId);
    }

    String githubUserId = identityOpt.get().getProviderSubject();
    SpecimenRepoIdentityJpaEntity repoIdentity =
        specimenRepoIdentityRepository
            .findBySpecimenIdAndGithubUserIdAndActiveTrue(normalizedSpecimenId, githubUserId)
            .orElse(null);
    if (repoIdentity == null) {
      return SpecimenIdentityBindingRecord.empty(normalizedSpecimenId, normalizedUserId);
    }

    CommentAuthorRepoRoleSnapshot roleSnapshot = toSnapshot(repoIdentity.getRole());
    return new SpecimenIdentityBindingRecord(
        normalizedSpecimenId,
        normalizedUserId,
        roleSnapshot,
        repoIdentity.getSource(),
        repoIdentity.isActive(),
        githubUserId,
        repoIdentity.getGithubLogin());
  }

  @Override
  public CommentAuthorRepoRoleSnapshot resolveRole(String specimenId, String userId) {
    return resolveBinding(specimenId, userId).roleSnapshot();
  }

  private CommentAuthorRepoRoleSnapshot toSnapshot(RepoIdentityRole role) {
    if (role == null) {
      return CommentAuthorRepoRoleSnapshot.NONE;
    }
    return switch (role) {
      case OWNER -> CommentAuthorRepoRoleSnapshot.OWNER;
      case MAINTAINER -> CommentAuthorRepoRoleSnapshot.MAINTAINER;
      case CONTRIBUTOR -> CommentAuthorRepoRoleSnapshot.NONE;
    };
  }

  private String normalize(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return value.trim();
  }

  public record SpecimenIdentityBindingRecord(
      String specimenId,
      String userId,
      CommentAuthorRepoRoleSnapshot roleSnapshot,
      String source,
      boolean active,
      String githubUserId,
      String githubLogin) {

    static SpecimenIdentityBindingRecord empty(String specimenId, String userId) {
      return new SpecimenIdentityBindingRecord(
          specimenId,
          userId,
          CommentAuthorRepoRoleSnapshot.NONE,
          null,
          false,
          null,
          null);
    }
  }
}
