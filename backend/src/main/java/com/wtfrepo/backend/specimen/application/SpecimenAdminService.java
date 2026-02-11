package com.wtfrepo.backend.specimen.application;

import com.wtfrepo.backend.specimen.application.model.SpecimenModels.CodeHighlightInput;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.FetchedMeta;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.ImportResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.LanguageCandidate;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.LanguageItem;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.ReadmeCandidate;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.ReadmeExcerptInput;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.RepoIdentityCandidates;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.RepoIdentityInput;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.RepoIdentityItem;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.ReviewResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.SubmitCommand;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.SubmitResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.TagAssignment;
import com.wtfrepo.backend.specimen.application.support.SpecimenConstants;
import com.wtfrepo.backend.specimen.application.support.SpecimenExceptions;
import com.wtfrepo.backend.specimen.application.support.SpecimenJsonCodec;
import com.wtfrepo.backend.specimen.application.support.SpecimenRequestFingerprintCalculator;
import com.wtfrepo.backend.specimen.domain.RepoIdentityRole;
import com.wtfrepo.backend.specimen.domain.SpecimenAdminOperation;
import com.wtfrepo.backend.specimen.domain.SpecimenReviewAction;
import com.wtfrepo.backend.specimen.domain.SpecimenStatus;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenAdminActionIdempotencyJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenArenaMetricsJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenCodeHighlightJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenGithubMetadataJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenOfficialCommentaryJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenReadmeExcerptJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenRepoIdentityJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenTagJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenAdminActionIdempotencyJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenArenaMetricsJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenCodeHighlightJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenGithubMetadataJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenOfficialCommentaryJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenReadmeExcerptJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenRepoIdentityJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenTagJpaRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SpecimenAdminService {

  private static final Pattern GITHUB_URL_PATTERN =
      Pattern.compile("^https?://github\\.com/([^/]+)/([^/#?]+?)(?:\\.git)?/?$");

  private final SpecimenJpaRepository specimenJpaRepository;
  private final SpecimenArenaMetricsJpaRepository specimenArenaMetricsJpaRepository;
  private final SpecimenGithubMetadataJpaRepository specimenGithubMetadataJpaRepository;
  private final SpecimenTagJpaRepository specimenTagJpaRepository;
  private final SpecimenReadmeExcerptJpaRepository specimenReadmeExcerptJpaRepository;
  private final SpecimenOfficialCommentaryJpaRepository specimenOfficialCommentaryJpaRepository;
  private final SpecimenCodeHighlightJpaRepository specimenCodeHighlightJpaRepository;
  private final SpecimenRepoIdentityJpaRepository specimenRepoIdentityJpaRepository;
  private final SpecimenAdminActionIdempotencyJpaRepository specimenAdminActionIdempotencyJpaRepository;
  private final SpecimenContractProperties specimenContractProperties;
  private final SpecimenRequestFingerprintCalculator specimenRequestFingerprintCalculator;
  private final SpecimenJsonCodec specimenJsonCodec;

  public SpecimenAdminService(
      SpecimenJpaRepository specimenJpaRepository,
      SpecimenArenaMetricsJpaRepository specimenArenaMetricsJpaRepository,
      SpecimenGithubMetadataJpaRepository specimenGithubMetadataJpaRepository,
      SpecimenTagJpaRepository specimenTagJpaRepository,
      SpecimenReadmeExcerptJpaRepository specimenReadmeExcerptJpaRepository,
      SpecimenOfficialCommentaryJpaRepository specimenOfficialCommentaryJpaRepository,
      SpecimenCodeHighlightJpaRepository specimenCodeHighlightJpaRepository,
      SpecimenRepoIdentityJpaRepository specimenRepoIdentityJpaRepository,
      SpecimenAdminActionIdempotencyJpaRepository specimenAdminActionIdempotencyJpaRepository,
      SpecimenContractProperties specimenContractProperties,
      SpecimenRequestFingerprintCalculator specimenRequestFingerprintCalculator,
      SpecimenJsonCodec specimenJsonCodec) {
    this.specimenJpaRepository = specimenJpaRepository;
    this.specimenArenaMetricsJpaRepository = specimenArenaMetricsJpaRepository;
    this.specimenGithubMetadataJpaRepository = specimenGithubMetadataJpaRepository;
    this.specimenTagJpaRepository = specimenTagJpaRepository;
    this.specimenReadmeExcerptJpaRepository = specimenReadmeExcerptJpaRepository;
    this.specimenOfficialCommentaryJpaRepository = specimenOfficialCommentaryJpaRepository;
    this.specimenCodeHighlightJpaRepository = specimenCodeHighlightJpaRepository;
    this.specimenRepoIdentityJpaRepository = specimenRepoIdentityJpaRepository;
    this.specimenAdminActionIdempotencyJpaRepository = specimenAdminActionIdempotencyJpaRepository;
    this.specimenContractProperties = specimenContractProperties;
    this.specimenRequestFingerprintCalculator = specimenRequestFingerprintCalculator;
    this.specimenJsonCodec = specimenJsonCodec;
  }

  @Transactional
  public ImportResult importSpecimen(String githubUrl) {
    ParsedGithubUrl parsedGithubUrl = parseGithubUrl(githubUrl);
    String specimenId = nextSpecimenId();

    List<LanguageItem> suggestedLanguages =
        List.of(new LanguageItem("TypeScript", 72.5), new LanguageItem("Python", 19.4));
    List<LanguageCandidate> languageCandidates =
        List.of(
            new LanguageCandidate("TypeScript", 45000, 72.5),
            new LanguageCandidate("Python", 12000, 19.4),
            new LanguageCandidate("Shell", 800, 1.3));

    SpecimenJpaEntity specimen =
        SpecimenJpaEntity.createDraft(
            specimenId,
            parsedGithubUrl.owner() + "/" + parsedGithubUrl.repo(),
            "https://github.com/" + parsedGithubUrl.owner() + "/" + parsedGithubUrl.repo());
    specimenJpaRepository.save(specimen);
    // TODO(M01-arena): initialize metrics via arena-owned service after module boundary is settled.
    specimenArenaMetricsJpaRepository.save(SpecimenArenaMetricsJpaEntity.createDefault(specimenId));

    SpecimenGithubMetadataJpaEntity metadata =
        SpecimenGithubMetadataJpaEntity.createDraft(
            specimenId,
            parsedGithubUrl.owner(),
            numericStringHash(parsedGithubUrl.owner()),
            "https://avatars.githubusercontent.com/u/" + numericStringHash(parsedGithubUrl.owner()),
            "https://github.com/" + parsedGithubUrl.owner(),
            "https://github.com/" + parsedGithubUrl.owner() + "/" + parsedGithubUrl.repo(),
            specimenJsonCodec.write(suggestedLanguages),
            specimenJsonCodec.write(List.of("funny", "automation")));
    specimenGithubMetadataJpaRepository.save(metadata);

    RepoIdentityItem owner =
        new RepoIdentityItem(
            parsedGithubUrl.owner(),
            numericStringHash(parsedGithubUrl.owner()),
            "https://avatars.githubusercontent.com/u/" + numericStringHash(parsedGithubUrl.owner()),
            "https://github.com/" + parsedGithubUrl.owner(),
            null);

    return new ImportResult(
        specimenId,
        SpecimenStatus.DRAFT,
        new FetchedMeta(parsedGithubUrl.repo(), parsedGithubUrl.owner(), suggestedLanguages, true),
        List.of(
            new ReadmeCandidate(
                "cand_1", "TEXT", "Why", "This is an import-stage candidate excerpt", 0.92, null)),
        List.of(
            new ReadmeCandidate(
                "cand_code_1",
                "CODE",
                "Core Joke",
                "export const truth = Math.random() > 0.5",
                null,
                "typescript")),
        new RepoIdentityCandidates(owner, List.of()),
        languageCandidates);
  }

  @Transactional
  public SubmitResult submit(String adminUserId, String specimenId, String idempotencyKey,
      SubmitCommand submitCommand) {
    validateIdempotencyKey(idempotencyKey);
    validateSubmitCommand(submitCommand);

    String requestFingerprint = specimenRequestFingerprintCalculator.fingerprint(submitCommand);
    Optional<StoredActionResult> replayResult =
        findReplayResult(
            adminUserId,
            specimenId,
            SpecimenAdminOperation.SUBMIT,
            idempotencyKey,
            requestFingerprint);
    if (replayResult.isPresent()) {
      return new SubmitResult(replayResult.get().specimenId(), replayResult.get().status());
    }

    SpecimenJpaEntity specimen = requireSpecimen(specimenId);
    if (specimen.getStatus() != SpecimenStatus.DRAFT && specimen.getStatus() != SpecimenStatus.REJECTED) {
      throw SpecimenExceptions.conflict(SpecimenConstants.Message.INVALID_STATUS_TRANSITION);
    }

    specimen.submit(submitCommand.note());
    specimenJpaRepository.save(specimen);

    upsertLanguages(specimenId, submitCommand.languages());
    replaceSpecimenTags(specimenId, submitCommand.tags());
    replaceReadmeExcerpts(specimenId, adminUserId, submitCommand.readmeExcerpts());
    upsertOfficialCommentary(specimenId, adminUserId, submitCommand);
    replaceCodeHighlights(specimenId, submitCommand.codeHighlights());
    replaceRepoIdentities(specimenId, submitCommand.repoIdentity());

    storeReplayResult(
        adminUserId,
        specimenId,
        SpecimenAdminOperation.SUBMIT,
        idempotencyKey,
        requestFingerprint,
        new StoredActionResult(specimenId, specimen.getStatus(), null, null));

    return new SubmitResult(specimenId, specimen.getStatus());
  }

  @Transactional
  public ReviewResult review(
      String adminUserId,
      String specimenId,
      String idempotencyKey,
      SpecimenReviewAction action,
      String reason) {
    validateIdempotencyKey(idempotencyKey);
    String requestFingerprint =
        specimenRequestFingerprintCalculator.fingerprint(new ReviewPayload(action, reason));

    Optional<StoredActionResult> replayResult =
        findReplayResult(
            adminUserId,
            specimenId,
            SpecimenAdminOperation.REVIEW,
            idempotencyKey,
            requestFingerprint);
    if (replayResult.isPresent()) {
      StoredActionResult storedActionResult = replayResult.get();
      return new ReviewResult(
          storedActionResult.specimenId(),
          storedActionResult.status(),
          storedActionResult.reviewedBy(),
          storedActionResult.reviewedAt());
    }

    SpecimenJpaEntity specimen = requireSpecimen(specimenId);
    if (specimen.getStatus() != SpecimenStatus.PENDING) {
      throw SpecimenExceptions.conflict(SpecimenConstants.Message.INVALID_STATUS_TRANSITION);
    }

    if (action == SpecimenReviewAction.APPROVE) {
      specimen.approve(adminUserId);
    } else if (action == SpecimenReviewAction.REJECT) {
      specimen.reject(adminUserId);
    } else {
      throw SpecimenExceptions.validation(SpecimenConstants.Message.INVALID_ACTION);
    }
    specimenJpaRepository.save(specimen);

    StoredActionResult storedActionResult =
        new StoredActionResult(
            specimenId, specimen.getStatus(), specimen.getReviewedBy(), specimen.getReviewedAt());

    storeReplayResult(
        adminUserId,
        specimenId,
        SpecimenAdminOperation.REVIEW,
        idempotencyKey,
        requestFingerprint,
        storedActionResult);

    return new ReviewResult(
        storedActionResult.specimenId(),
        storedActionResult.status(),
        storedActionResult.reviewedBy(),
        storedActionResult.reviewedAt());
  }

  private void upsertLanguages(String specimenId, List<LanguageItem> languages) {
    SpecimenGithubMetadataJpaEntity metadata =
        specimenGithubMetadataJpaRepository
            .findById(specimenId)
            .orElseGet(
                () ->
                    SpecimenGithubMetadataJpaEntity.createDraft(
                        specimenId, null, null, null, null, null, specimenJsonCodec.write(languages), null));
    metadata.updateLanguagesJson(specimenJsonCodec.write(languages));
    specimenGithubMetadataJpaRepository.save(metadata);
  }

  private void replaceSpecimenTags(String specimenId, List<TagAssignment> tags) {
    specimenTagJpaRepository.deleteBySpecimenId(specimenId);
    List<SpecimenTagJpaEntity> entities =
        tags.stream().map(tag -> SpecimenTagJpaEntity.of(specimenId, tag.dimensionKey(), tag.tagKey())).toList();
    specimenTagJpaRepository.saveAll(entities);
  }

  private void replaceReadmeExcerpts(
      String specimenId, String adminUserId, List<ReadmeExcerptInput> readmeExcerpts) {
    specimenReadmeExcerptJpaRepository.deleteBySpecimenId(specimenId);
    List<SpecimenReadmeExcerptJpaEntity> entities =
        readmeExcerpts.stream()
            .map(
                excerpt ->
                    SpecimenReadmeExcerptJpaEntity.of(
                        specimenId,
                        excerpt.excerptType(),
                        excerpt.text(),
                        excerpt.candidateId(),
                        excerpt.resolvedPriority(),
                        adminUserId))
            .toList();
    specimenReadmeExcerptJpaRepository.saveAll(entities);
  }

  private void upsertOfficialCommentary(String specimenId, String adminUserId, SubmitCommand submitCommand) {
    SpecimenOfficialCommentaryJpaEntity entity =
        SpecimenOfficialCommentaryJpaEntity.createOrUpdate(
            specimenId,
            submitCommand.officialCommentary().oneLinerZh(),
            submitCommand.officialCommentary().oneLinerEn(),
            submitCommand.officialCommentary().arenaReasonZh(),
            submitCommand.officialCommentary().arenaReasonEn(),
            adminUserId);
    specimenOfficialCommentaryJpaRepository.save(entity);
  }

  private void replaceCodeHighlights(String specimenId, List<CodeHighlightInput> codeHighlights) {
    specimenCodeHighlightJpaRepository.deleteBySpecimenId(specimenId);
    if (codeHighlights == null || codeHighlights.isEmpty()) {
      return;
    }
    List<SpecimenCodeHighlightJpaEntity> entities =
        codeHighlights.stream()
            .map(
                highlight ->
                    SpecimenCodeHighlightJpaEntity.of(
                        specimenId,
                        highlight.title(),
                        highlight.codeLanguage(),
                        highlight.snippet(),
                        highlight.explainText(),
                        highlight.candidateId(),
                        highlight.resolvedPriority()))
            .toList();
    specimenCodeHighlightJpaRepository.saveAll(entities);
  }

  private void replaceRepoIdentities(String specimenId, RepoIdentityInput repoIdentityInput) {
    specimenRepoIdentityJpaRepository.deleteBySpecimenId(specimenId);

    List<SpecimenRepoIdentityJpaEntity> entities = new ArrayList<>();
    entities.add(
        SpecimenRepoIdentityJpaEntity.of(
            specimenId,
            repoIdentityInput.owner().githubUserId(),
            repoIdentityInput.owner().githubLogin(),
            repoIdentityInput.owner().githubAvatarUrl(),
            repoIdentityInput.owner().githubHtmlUrl(),
            RepoIdentityRole.OWNER,
            repoIdentityInput.owner().contributions()));

    if (repoIdentityInput.maintainers() != null) {
      for (RepoIdentityItem maintainer : repoIdentityInput.maintainers()) {
        if (Objects.equals(maintainer.githubUserId(), repoIdentityInput.owner().githubUserId())) {
          continue;
        }
        entities.add(
            SpecimenRepoIdentityJpaEntity.of(
                specimenId,
                maintainer.githubUserId(),
                maintainer.githubLogin(),
                maintainer.githubAvatarUrl(),
                maintainer.githubHtmlUrl(),
                RepoIdentityRole.MAINTAINER,
                maintainer.contributions()));
      }
    }

    if (repoIdentityInput.contributors() != null) {
      for (RepoIdentityItem contributor : repoIdentityInput.contributors()) {
        if (Objects.equals(contributor.githubUserId(), repoIdentityInput.owner().githubUserId())) {
          continue;
        }
        boolean alreadyAssignedAsMaintainer =
            repoIdentityInput.maintainers() != null
                && repoIdentityInput.maintainers().stream()
                    .anyMatch(item -> Objects.equals(item.githubUserId(), contributor.githubUserId()));
        if (alreadyAssignedAsMaintainer) {
          continue;
        }
        entities.add(
            SpecimenRepoIdentityJpaEntity.of(
                specimenId,
                contributor.githubUserId(),
                contributor.githubLogin(),
                contributor.githubAvatarUrl(),
                contributor.githubHtmlUrl(),
                RepoIdentityRole.CONTRIBUTOR,
                contributor.contributions()));
      }
    }

    specimenRepoIdentityJpaRepository.saveAll(entities);
  }

  private Optional<StoredActionResult> findReplayResult(
      String adminUserId,
      String specimenId,
      SpecimenAdminOperation operation,
      String idempotencyKey,
      String requestFingerprint) {
    specimenAdminActionIdempotencyJpaRepository.deleteByCreatedAtBefore(
        Instant.now().minus(specimenContractProperties.getAdminIdempotencyTtl()));

    String operationKey = computeOperationKey(adminUserId, specimenId, operation, idempotencyKey);
    Optional<SpecimenAdminActionIdempotencyJpaEntity> entityOptional =
        specimenAdminActionIdempotencyJpaRepository.findById(operationKey);

    if (entityOptional.isEmpty()) {
      return Optional.empty();
    }

    SpecimenAdminActionIdempotencyJpaEntity entity = entityOptional.get();
    if (!Objects.equals(entity.getRequestFingerprint(), requestFingerprint)) {
      throw SpecimenExceptions.conflict(SpecimenConstants.Message.IDEMPOTENCY_CONFLICT);
    }

    return Optional.of(
        new StoredActionResult(
            entity.getSpecimenId(),
            entity.getResponseStatus(),
            entity.getResponseReviewedBy(),
            entity.getResponseReviewedAt()));
  }

  private void storeReplayResult(
      String adminUserId,
      String specimenId,
      SpecimenAdminOperation operation,
      String idempotencyKey,
      String requestFingerprint,
      StoredActionResult result) {
    String operationKey = computeOperationKey(adminUserId, specimenId, operation, idempotencyKey);

    SpecimenAdminActionIdempotencyJpaEntity entity =
        SpecimenAdminActionIdempotencyJpaEntity.of(
            operationKey,
            adminUserId,
            specimenId,
            operation,
            idempotencyKey,
            requestFingerprint,
            result.status(),
            result.reviewedBy(),
            result.reviewedAt());
    specimenAdminActionIdempotencyJpaRepository.save(entity);
  }

  private void validateIdempotencyKey(String idempotencyKey) {
    if (!StringUtils.hasText(idempotencyKey) || idempotencyKey.length() > 128) {
      throw SpecimenExceptions.validation(SpecimenConstants.Message.INVALID_IDEMPOTENCY_KEY);
    }
  }

  private void validateSubmitCommand(SubmitCommand submitCommand) {
    if (submitCommand.languages() == null || submitCommand.languages().isEmpty()) {
      throw SpecimenExceptions.validation("invalid_languages");
    }

    if (submitCommand.tags() == null || submitCommand.tags().isEmpty()) {
      throw SpecimenExceptions.validation(SpecimenConstants.Message.INVALID_TAG);
    }
    validateTagAssignments(submitCommand.tags());

    if (submitCommand.readmeExcerpts() == null || submitCommand.readmeExcerpts().isEmpty()) {
      throw SpecimenExceptions.validation(SpecimenConstants.Message.INVALID_EXCERPT);
    }

    if (submitCommand.officialCommentary() == null
        || !StringUtils.hasText(submitCommand.officialCommentary().oneLinerZh())
        || !StringUtils.hasText(submitCommand.officialCommentary().oneLinerEn())
        || !StringUtils.hasText(submitCommand.officialCommentary().arenaReasonZh())
        || !StringUtils.hasText(submitCommand.officialCommentary().arenaReasonEn())) {
      throw SpecimenExceptions.validation(SpecimenConstants.Message.INVALID_COMMENTARY);
    }

    if (submitCommand.repoIdentity() == null || submitCommand.repoIdentity().owner() == null) {
      throw SpecimenExceptions.validation(SpecimenConstants.Message.INVALID_IDENTITY_BINDING);
    }

    if (!StringUtils.hasText(submitCommand.repoIdentity().owner().githubLogin())
        || !StringUtils.hasText(submitCommand.repoIdentity().owner().githubUserId())) {
      throw SpecimenExceptions.validation(SpecimenConstants.Message.INVALID_IDENTITY_BINDING);
    }

    if (submitCommand.codeHighlights() != null) {
      for (CodeHighlightInput codeHighlight : submitCommand.codeHighlights()) {
        if (!StringUtils.hasText(codeHighlight.codeLanguage())) {
          throw SpecimenExceptions.validation("invalid_code_language");
        }
      }
    }
  }

  private void validateTagAssignments(List<TagAssignment> tags) {
    Set<String> keys = new LinkedHashSet<>();
    for (TagAssignment tag : tags) {
      if (!StringUtils.hasText(tag.dimensionKey()) || !StringUtils.hasText(tag.tagKey())) {
        throw SpecimenExceptions.validation(SpecimenConstants.Message.INVALID_TAG);
      }
      String key = tag.dimensionKey() + "::" + tag.tagKey();
      if (!keys.add(key)) {
        throw SpecimenExceptions.validation(SpecimenConstants.Message.INVALID_TAG);
      }
    }
  }

  private SpecimenJpaEntity requireSpecimen(String specimenId) {
    return specimenJpaRepository
        .findById(specimenId)
        .orElseThrow(() -> SpecimenExceptions.notFound(SpecimenConstants.Message.SPECIMEN_NOT_FOUND));
  }

  private ParsedGithubUrl parseGithubUrl(String githubUrl) {
    if (!StringUtils.hasText(githubUrl)) {
      throw SpecimenExceptions.validation("invalid_github_url");
    }
    Matcher matcher = GITHUB_URL_PATTERN.matcher(githubUrl.trim());
    if (!matcher.matches()) {
      throw SpecimenExceptions.validation("invalid_github_url");
    }
    return new ParsedGithubUrl(matcher.group(1), matcher.group(2));
  }

  private String computeOperationKey(
      String adminUserId,
      String specimenId,
      SpecimenAdminOperation operation,
      String idempotencyKey) {
    String source = adminUserId + "|" + specimenId + "|" + operation.name() + "|" + idempotencyKey;
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(source.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder(hash.length * 2);
      for (byte value : hash) {
        builder.append(String.format("%02x", value));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw SpecimenExceptions.validation("invalid_operation_key");
    }
  }

  private String nextSpecimenId() {
    String encoded =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
    return "sp_" + encoded.substring(0, 12);
  }

  private String numericStringHash(String value) {
    long numeric = Math.abs((long) Objects.hashCode(value));
    return String.valueOf(numeric);
  }

  private record ParsedGithubUrl(String owner, String repo) {}

  private record StoredActionResult(
      String specimenId, SpecimenStatus status, String reviewedBy, Instant reviewedAt) {}

  private record ReviewPayload(SpecimenReviewAction action, String reason) {}
}

