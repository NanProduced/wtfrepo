package com.wtfrepo.backend.specimen.api;

import com.wtfrepo.backend.shared.web.RequestIdConstants;
import com.wtfrepo.backend.specimen.application.SpecimenAdminService;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.ImportResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.ReviewResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.SubmitCommand;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.SubmitResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.TagUpdateResult;
import com.wtfrepo.backend.specimen.application.support.SpecimenConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin specimen curation endpoints.
 *
 * <p>TODO(M07-admin): `import` currently stays as placeholder flow and should be replaced by real
 * admin ingestion pipeline in M07.
 */
@RestController
@Validated
@RequestMapping("/api/v1/admin/specimens")
@Tag(
    name = "Admin: Specimen",
    description = "Admin endpoints for specimen ingestion, review, and lifecycle management.")
public class SpecimenAdminController {

  private final SpecimenAdminService specimenAdminService;

  public SpecimenAdminController(SpecimenAdminService specimenAdminService) {
    this.specimenAdminService = specimenAdminService;
  }

  @PostMapping("/import")
  @Operation(
      summary = "Import specimen",
      description = "Admin import via GitHub URL. Placeholder flow until M07 ingestion pipeline.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Specimen imported")})
  public ResponseEntity<AdminImportResponse> importSpecimen(
      @Parameter(
              in = ParameterIn.HEADER,
              name = RequestIdConstants.HEADER_NAME,
              description = "Request correlation id.",
              required = true)
          @RequestHeader(RequestIdConstants.HEADER_NAME)
          String requestId,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody AdminImportRequest request) {
    SpecimenApiSupport.requireAdminUserId(jwt);
    ImportResult result = specimenAdminService.importSpecimen(request.githubUrl());
    return ResponseEntity.ok(AdminImportResponse.from(result));
  }

  @PostMapping("/{specimenId}/submit")
  @Operation(summary = "Submit specimen", description = "Submit specimen for review.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Submission accepted")})
  public ResponseEntity<AdminSubmitResponse> submitSpecimen(
      @Parameter(
              in = ParameterIn.HEADER,
              name = RequestIdConstants.HEADER_NAME,
              description = "Request correlation id.",
              required = true)
          @RequestHeader(RequestIdConstants.HEADER_NAME)
          String requestId,
      @Parameter(
              in = ParameterIn.HEADER,
              name = SpecimenConstants.Header.IDEMPOTENCY_KEY,
              description = "Idempotency key for admin write operations.",
              required = true)
          @RequestHeader(SpecimenConstants.Header.IDEMPOTENCY_KEY)
          String idempotencyKey,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "Specimen id.", required = true) @PathVariable String specimenId,
      @Valid @RequestBody AdminSubmitRequest request) {
    String adminUserId = SpecimenApiSupport.requireAdminUserId(jwt);
    SubmitResult result =
        specimenAdminService.submit(adminUserId, specimenId, idempotencyKey, request.toCommand());
    return ResponseEntity.ok(AdminSubmitResponse.from(result));
  }

  @PostMapping("/{specimenId}/review")
  @Operation(
      summary = "Review specimen",
      description = "Approve or reject a specimen submission.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Review processed")})
  public ResponseEntity<AdminReviewResponse> reviewSpecimen(
      @Parameter(
              in = ParameterIn.HEADER,
              name = RequestIdConstants.HEADER_NAME,
              description = "Request correlation id.",
              required = true)
          @RequestHeader(RequestIdConstants.HEADER_NAME)
          String requestId,
      @Parameter(
              in = ParameterIn.HEADER,
              name = SpecimenConstants.Header.IDEMPOTENCY_KEY,
              description = "Idempotency key for admin write operations.",
              required = true)
          @RequestHeader(SpecimenConstants.Header.IDEMPOTENCY_KEY)
          String idempotencyKey,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "Specimen id.", required = true) @PathVariable String specimenId,
      @Valid @RequestBody AdminReviewRequest request) {
    String adminUserId = SpecimenApiSupport.requireAdminUserId(jwt);
    ReviewResult result =
        specimenAdminService.review(
            adminUserId,
            specimenId,
            idempotencyKey,
            SpecimenApiSupport.parseReviewAction(request.action()),
            request.reason());
    return ResponseEntity.ok(AdminReviewResponse.from(result));
  }

  @PostMapping("/{specimenId}/deactivate")
  @Operation(summary = "Deactivate specimen", description = "Admin offlines an active specimen.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Specimen deactivated")})
  public ResponseEntity<AdminReviewResponse> deactivateSpecimen(
      @Parameter(
              in = ParameterIn.HEADER,
              name = RequestIdConstants.HEADER_NAME,
              description = "Request correlation id.",
              required = true)
          @RequestHeader(RequestIdConstants.HEADER_NAME)
          String requestId,
      @Parameter(
              in = ParameterIn.HEADER,
              name = SpecimenConstants.Header.IDEMPOTENCY_KEY,
              description = "Idempotency key for admin write operations.",
              required = true)
          @RequestHeader(SpecimenConstants.Header.IDEMPOTENCY_KEY)
          String idempotencyKey,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "Specimen id.", required = true) @PathVariable String specimenId,
      @Valid @RequestBody AdminDeactivateRequest request) {
    String adminUserId = SpecimenApiSupport.requireAdminUserId(jwt);
    ReviewResult result =
        specimenAdminService.deactivate(adminUserId, specimenId, idempotencyKey, request.reason());
    return ResponseEntity.ok(AdminReviewResponse.from(result));
  }

  @PostMapping("/{specimenId}/tags")
  @Operation(
      summary = "Update specimen tags",
      description = "Replace specimen tags; update returns current status.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Tags updated")})
  public ResponseEntity<AdminTagUpdateResponse> updateSpecimenTags(
      @Parameter(
              in = ParameterIn.HEADER,
              name = RequestIdConstants.HEADER_NAME,
              description = "Request correlation id.",
              required = true)
          @RequestHeader(RequestIdConstants.HEADER_NAME)
          String requestId,
      @Parameter(
              in = ParameterIn.HEADER,
              name = SpecimenConstants.Header.IDEMPOTENCY_KEY,
              description = "Idempotency key for admin write operations.",
              required = true)
          @RequestHeader(SpecimenConstants.Header.IDEMPOTENCY_KEY)
          String idempotencyKey,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "Specimen id.", required = true) @PathVariable String specimenId,
      @Valid @RequestBody AdminTagUpdateRequest request) {
    String adminUserId = SpecimenApiSupport.requireAdminUserId(jwt);
    TagUpdateResult result =
        specimenAdminService.updateTags(
            adminUserId, specimenId, idempotencyKey, request.tags().stream().map(TagRequest::toModel).toList());
    return ResponseEntity.ok(AdminTagUpdateResponse.from(result));
  }

  public record AdminImportRequest(@NotBlank String githubUrl) {}

  public record AdminImportResponse(
      String specimenId,
      String status,
      FetchedMetaResponse fetchedMeta,
      List<ReadmeCandidateResponse> readmeCandidates,
      List<ReadmeCandidateResponse> codeCandidates,
      RepoIdentityCandidatesResponse repoIdentityCandidates,
      List<LanguageCandidateResponse> languageCandidates) {

    static AdminImportResponse from(ImportResult result) {
      return new AdminImportResponse(
          result.specimenId(),
          result.status().name(),
          FetchedMetaResponse.from(result.fetchedMeta()),
          result.readmeCandidates().stream().map(ReadmeCandidateResponse::from).toList(),
          result.codeCandidates().stream().map(ReadmeCandidateResponse::from).toList(),
          RepoIdentityCandidatesResponse.from(result.repoIdentityCandidates()),
          result.languageCandidates().stream().map(LanguageCandidateResponse::from).toList());
    }
  }

  public record FetchedMetaResponse(
      String repoName, String owner, List<LanguageResponse> languages, boolean readmeFetched) {

    static FetchedMetaResponse from(SpecimenModels.FetchedMeta fetchedMeta) {
      return new FetchedMetaResponse(
          fetchedMeta.repoName(),
          fetchedMeta.owner(),
          fetchedMeta.languages().stream().map(LanguageResponse::from).toList(),
          fetchedMeta.readmeFetched());
    }
  }

  public record LanguageResponse(String name, Double percentage) {

    static LanguageResponse from(SpecimenModels.LanguageItem languageItem) {
      return new LanguageResponse(languageItem.name(), languageItem.percentage());
    }
  }

  public record ReadmeCandidateResponse(
      String candidateId,
      String candidateType,
      String heading,
      String text,
      Double score,
      String codeLanguage) {

    static ReadmeCandidateResponse from(SpecimenModels.ReadmeCandidate candidate) {
      return new ReadmeCandidateResponse(
          candidate.candidateId(),
          candidate.candidateType(),
          candidate.heading(),
          candidate.text(),
          candidate.score(),
          candidate.codeLanguage());
    }
  }

  public record RepoIdentityCandidatesResponse(
      SpecimenApiSupport.RepoIdentityResponse owner,
      List<SpecimenApiSupport.RepoIdentityResponse> contributors) {

    static RepoIdentityCandidatesResponse from(SpecimenModels.RepoIdentityCandidates value) {
      return new RepoIdentityCandidatesResponse(
          value.owner() == null ? null : SpecimenApiSupport.RepoIdentityResponse.from(value.owner()),
          value.contributors() == null
              ? List.of()
              : value.contributors().stream().map(SpecimenApiSupport.RepoIdentityResponse::from).toList());
    }
  }

  public record LanguageCandidateResponse(String name, long bytes, double percentage) {

    static LanguageCandidateResponse from(SpecimenModels.LanguageCandidate candidate) {
      return new LanguageCandidateResponse(candidate.name(), candidate.bytes(), candidate.percentage());
    }
  }

  public record AdminSubmitRequest(
      @NotEmpty List<@Valid LanguageRequest> languages,
      @NotEmpty List<@Valid TagRequest> tags,
      @NotNull @Valid ReadmeCurationRequest readmeCuration,
      @Valid ReadmeSnapshotDecisionRequest readmeSnapshotDecision,
      @NotNull @Valid OfficialCommentaryRequest officialCommentary,
      List<@Valid CodeHighlightRequest> codeHighlights,
      @NotNull @Valid RepoIdentityRequest repoIdentity,
      String note) {

    SubmitCommand toCommand() {
      return new SubmitCommand(
          languages.stream().map(LanguageRequest::toModel).toList(),
          tags.stream().map(TagRequest::toModel).toList(),
          readmeCuration.excerpts().stream().map(ReadmeExcerptRequest::toModel).toList(),
          readmeSnapshotDecision != null && readmeSnapshotDecision.enabled(),
          officialCommentary.toModel(),
          codeHighlights == null
              ? List.of()
              : codeHighlights.stream().map(CodeHighlightRequest::toModel).toList(),
          repoIdentity.toModel(),
          note);
    }
  }

  public record LanguageRequest(@NotBlank String name, Double percentage) {

    SpecimenModels.LanguageItem toModel() {
      return new SpecimenModels.LanguageItem(name, percentage);
    }
  }

  public record TagRequest(@NotBlank String dimensionKey, @NotBlank String tagKey) {

    SpecimenModels.TagAssignment toModel() {
      return new SpecimenModels.TagAssignment(dimensionKey, tagKey);
    }
  }

  public record ReadmeCurationRequest(@NotEmpty List<@Valid ReadmeExcerptRequest> excerpts) {}

  public record ReadmeExcerptRequest(
      @NotBlank String excerptType,
      String candidateId,
      @NotBlank String text,
      String translatedTextZh,
      Map<String, Object> translationMeta,
      Integer priority) {

    SpecimenModels.ReadmeExcerptInput toModel() {
      return new SpecimenModels.ReadmeExcerptInput(
          excerptType, candidateId, text, translatedTextZh, translationMeta, priority);
    }
  }

  public record ReadmeSnapshotDecisionRequest(boolean enabled) {}

  public record OfficialCommentaryRequest(
      String oneLiner,
      String arenaReason,
      String oneLinerZh,
      String oneLinerEn,
      String arenaReasonZh,
      String arenaReasonEn) {

    SpecimenModels.OfficialCommentaryInput toModel() {
      String resolvedOneLinerZh = StringUtils.hasText(oneLinerZh) ? oneLinerZh : oneLiner;
      String resolvedOneLinerEn = StringUtils.hasText(oneLinerEn) ? oneLinerEn : oneLiner;
      String resolvedArenaReasonZh = StringUtils.hasText(arenaReasonZh) ? arenaReasonZh : arenaReason;
      String resolvedArenaReasonEn = StringUtils.hasText(arenaReasonEn) ? arenaReasonEn : arenaReason;
      return new SpecimenModels.OfficialCommentaryInput(
          resolvedOneLinerZh,
          resolvedOneLinerEn,
          resolvedArenaReasonZh,
          resolvedArenaReasonEn);
    }
  }

  public record CodeHighlightRequest(
      @NotBlank String title,
      String candidateId,
      @NotBlank String codeLanguage,
      @NotBlank String snippet,
      @NotBlank String explainText,
      Integer priority) {

    SpecimenModels.CodeHighlightInput toModel() {
      return new SpecimenModels.CodeHighlightInput(
          title, candidateId, codeLanguage, snippet, explainText, priority);
    }
  }

  public record RepoIdentityRequest(
      @NotNull @Valid RepoIdentityItemRequest owner,
      List<@Valid RepoIdentityItemRequest> maintainers,
      List<@Valid RepoIdentityItemRequest> contributors) {

    SpecimenModels.RepoIdentityInput toModel() {
      return new SpecimenModels.RepoIdentityInput(
          owner.toModel(),
          maintainers == null
              ? List.of()
              : maintainers.stream().map(RepoIdentityItemRequest::toModel).toList(),
          contributors == null
              ? List.of()
              : contributors.stream().map(RepoIdentityItemRequest::toModel).toList());
    }
  }

  public record RepoIdentityItemRequest(
      @NotBlank String githubLogin,
      @NotBlank String githubUserId,
      String githubAvatarUrl,
      String githubHtmlUrl,
      Integer contributions) {

    SpecimenModels.RepoIdentityItem toModel() {
      return new SpecimenModels.RepoIdentityItem(
          githubLogin, githubUserId, githubAvatarUrl, githubHtmlUrl, contributions);
    }
  }

  public record AdminSubmitResponse(String specimenId, String status) {

    static AdminSubmitResponse from(SubmitResult result) {
      return new AdminSubmitResponse(result.specimenId(), result.status().name());
    }
  }

  public record AdminTagUpdateRequest(@NotEmpty List<@Valid TagRequest> tags) {}

  public record AdminTagUpdateResponse(String specimenId, String status) {

    static AdminTagUpdateResponse from(TagUpdateResult result) {
      return new AdminTagUpdateResponse(result.specimenId(), result.status().name());
    }
  }

  public record AdminReviewRequest(@NotBlank String action, String reason) {}

  public record AdminDeactivateRequest(String reason) {}

  public record AdminReviewResponse(
      String specimenId, String status, String reviewedBy, Instant reviewedAt) {

    static AdminReviewResponse from(ReviewResult result) {
      return new AdminReviewResponse(
          result.specimenId(), result.status().name(), result.reviewedBy(), result.reviewedAt());
    }
  }
}
