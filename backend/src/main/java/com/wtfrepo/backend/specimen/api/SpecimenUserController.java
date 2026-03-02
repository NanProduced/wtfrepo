package com.wtfrepo.backend.specimen.api;

import com.wtfrepo.backend.shared.web.RequestIdConstants;
import com.wtfrepo.backend.specimen.api.SpecimenApiSupport.RepoIdentitiesResponse;
import com.wtfrepo.backend.specimen.api.SpecimenApiSupport.TagListResponse;
import com.wtfrepo.backend.specimen.api.SpecimenApiSupport.WatchlistAddRequest;
import com.wtfrepo.backend.specimen.api.SpecimenApiSupport.WatchlistAddResponse;
import com.wtfrepo.backend.specimen.api.SpecimenApiSupport.WatchlistListResponse;
import com.wtfrepo.backend.specimen.api.SpecimenApiSupport.WatchlistRemoveResponse;
import com.wtfrepo.backend.specimen.api.SpecimenApiSupport.ArchiveResponse;
import com.wtfrepo.backend.specimen.api.SpecimenApiSupport.ArchiveInsightsResponse;
import com.wtfrepo.backend.specimen.api.SpecimenApiSupport.ArchiveLeaderboardResponse;
import com.wtfrepo.backend.specimen.api.SpecimenApiSupport.DrawerResponse;
import com.wtfrepo.backend.specimen.api.SpecimenApiSupport.DetailResponse;
import com.wtfrepo.backend.specimen.application.SpecimenRepoIdentityService;
import com.wtfrepo.backend.specimen.application.SpecimenQueryService;
import com.wtfrepo.backend.specimen.application.SpecimenTagQueryService;
import com.wtfrepo.backend.specimen.application.SpecimenWatchlistService;
import com.wtfrepo.backend.specimen.application.SpecimenHypeService;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.AddWatchlistResult;
import com.wtfrepo.backend.specimen.application.SpecimenHypeService.HypeParticipationResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.RepoIdentitiesResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.TagListResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.WatchlistPage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public/user-facing specimen endpoints.
 */
@RestController
@Validated
@RequestMapping("/api/v1")
public class SpecimenUserController {

  private final SpecimenTagQueryService specimenTagQueryService;
  private final SpecimenWatchlistService specimenWatchlistService;
  private final SpecimenRepoIdentityService specimenRepoIdentityService;
  private final SpecimenQueryService specimenQueryService;
  private final SpecimenHypeService specimenHypeService;

  public SpecimenUserController(
      SpecimenTagQueryService specimenTagQueryService,
      SpecimenWatchlistService specimenWatchlistService,
      SpecimenRepoIdentityService specimenRepoIdentityService,
      SpecimenQueryService specimenQueryService,
      SpecimenHypeService specimenHypeService) {
    this.specimenTagQueryService = specimenTagQueryService;
    this.specimenWatchlistService = specimenWatchlistService;
    this.specimenRepoIdentityService = specimenRepoIdentityService;
    this.specimenQueryService = specimenQueryService;
    this.specimenHypeService = specimenHypeService;
  }

  @GetMapping("/archive/specimens")
  public ResponseEntity<ArchiveResponse> listArchive(
      @RequestHeader(RequestIdConstants.HEADER_NAME) String requestId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) @Min(1) @Max(100) Integer limit,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) List<String> tags,
      @RequestParam(required = false) String q,
      @RequestHeader(value = "Accept-Language", required = false) String locale) {
    SpecimenModels.ArchivePage page =
        specimenQueryService.listArchive(new SpecimenModels.ArchiveQuery(cursor, limit, sort, tags, q), locale);
    return ResponseEntity.ok(ArchiveResponse.from(page));
  }

  @GetMapping("/archive/insights")
  public ResponseEntity<ArchiveInsightsResponse> getArchiveInsights(
      @RequestHeader(RequestIdConstants.HEADER_NAME) String requestId) {
    SpecimenModels.ArchiveInsightsResult result = specimenQueryService.getArchiveInsights();
    return ResponseEntity.ok(ArchiveInsightsResponse.from(result));
  }

  @GetMapping("/archive/leaderboard")
  public ResponseEntity<ArchiveLeaderboardResponse> listArchiveLeaderboard(
      @RequestHeader(RequestIdConstants.HEADER_NAME) String requestId,
      @RequestParam(required = false) String metric,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) @Min(1) @Max(100) Integer limit) {
    SpecimenModels.ArchiveLeaderboardPage page =
        specimenQueryService.listArchiveLeaderboard(
            new SpecimenModels.ArchiveLeaderboardQuery(metric, cursor, limit));
    return ResponseEntity.ok(ArchiveLeaderboardResponse.from(page));
  }

  @GetMapping("/specimens/{specimenId}/drawer")
  public ResponseEntity<DrawerResponse> getDrawer(
      @RequestHeader(RequestIdConstants.HEADER_NAME) String requestId,
      @PathVariable String specimenId,
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader(value = "Accept-Language", required = false) String locale) {
    SpecimenModels.DrawerResult result =
        specimenQueryService.getDrawer(
            specimenId, SpecimenApiSupport.resolveOptionalUserId(jwt), locale);
    return ResponseEntity.ok(DrawerResponse.from(result));
  }

  @GetMapping("/specimens/{specimenId}")
  public ResponseEntity<DetailResponse> getDetail(
      @RequestHeader(RequestIdConstants.HEADER_NAME) String requestId,
      @PathVariable String specimenId) {
    SpecimenModels.DetailResult result = specimenQueryService.getDetail(specimenId);
    return ResponseEntity.ok(DetailResponse.from(result));
  }

  @GetMapping("/tags")
  public ResponseEntity<TagListResponse> listTags() {
    TagListResult result = specimenTagQueryService.listTags();
    return ResponseEntity.ok(TagListResponse.from(result));
  }

  @GetMapping("/watchlist/items")
  public ResponseEntity<WatchlistListResponse> listWatchlist(
      @RequestHeader(RequestIdConstants.HEADER_NAME) String requestId,
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) @Min(1) @Max(100) Integer limit,
      @RequestParam(required = false) String sort) {
    String userId = SpecimenApiSupport.requireUserId(jwt);
    WatchlistPage page = specimenWatchlistService.listWatchlist(userId, cursor, limit, sort);
    return ResponseEntity.ok(WatchlistListResponse.from(page));
  }

  @PostMapping("/watchlist/items")
  public ResponseEntity<WatchlistAddResponse> addWatchlist(
      @RequestHeader(RequestIdConstants.HEADER_NAME) String requestId,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody WatchlistAddApiRequest request) {
    String userId = SpecimenApiSupport.requireUserId(jwt);
    WatchlistAddRequest payload = new WatchlistAddRequest(request.specimenId(), request.source());
    AddWatchlistResult result =
        specimenWatchlistService.addWatchlist(userId, payload.specimenId(), payload.source());
    return ResponseEntity.ok(new WatchlistAddResponse(result.added(), result.itemId()));
  }

  @DeleteMapping("/watchlist/items/{specimenId}")
  public ResponseEntity<WatchlistRemoveResponse> removeWatchlist(
      @RequestHeader(RequestIdConstants.HEADER_NAME) String requestId,
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String specimenId) {
    String userId = SpecimenApiSupport.requireUserId(jwt);
    SpecimenModels.RemoveWatchlistResult result =
        specimenWatchlistService.removeWatchlist(userId, specimenId);
    return ResponseEntity.ok(new WatchlistRemoveResponse(result.removed()));
  }

  @GetMapping("/specimens/{specimenId}/repo-identities")
  public ResponseEntity<RepoIdentitiesResponse> getRepoIdentities(@PathVariable String specimenId) {
    RepoIdentitiesResult result = specimenRepoIdentityService.getRepoIdentities(specimenId);
    return ResponseEntity.ok(RepoIdentitiesResponse.from(result));
  }

  /**
   * Placeholder hype endpoint required by v0.1 contract.
   *
   * <p>TODO(M01-arena): move real hype scoring, anti-spam and idempotency implementation into
   * arena module.
   */
  @PostMapping("/specimens/{specimenId}/hype")
  public ResponseEntity<HypeResponse> hypeSpecimen(
      @RequestHeader(RequestIdConstants.HEADER_NAME) String requestId,
      @RequestHeader("X-Idempotency-Key") @NotBlank String idempotencyKey,
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String specimenId,
      @Valid @RequestBody HypeRequest request) {
    String userId = SpecimenApiSupport.requireUserId(jwt);
    HypeParticipationResult result =
        specimenHypeService.participate(
            userId, specimenId, request.dimension(), request.clientTs(), idempotencyKey);
    return ResponseEntity.ok(
        new HypeResponse(result.hypeScore(), result.scoreDelta(), result.appliedWeight()));
  }

  public record WatchlistAddApiRequest(@NotBlank String specimenId, @Size(max = 32) String source) {}

  public record HypeRequest(@NotBlank String dimension, Instant clientTs, String idempotencyKey) {}

  public record HypeResponse(double hypeScore, double scoreDelta, double appliedWeight) {}
}
