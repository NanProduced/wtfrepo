package com.wtfrepo.backend.arena.api;

import com.wtfrepo.backend.arena.application.ArenaAdminService;
import com.wtfrepo.backend.arena.domain.ArenaMatchType;
import com.wtfrepo.backend.shared.web.RequestIdConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Arena admin endpoints for maintenance operations and diagnostics.
 *
 * <p>Phase C2 scope: {@code force-recalc} + {@code match-quality}. Other admin actions are kept
 * for follow-up phases.
 */
@RestController
@Validated
@RequestMapping("/api/v1/admin/arena")
public class ArenaAdminController {

  private final ArenaAdminService arenaAdminService;

  public ArenaAdminController(ArenaAdminService arenaAdminService) {
    this.arenaAdminService = arenaAdminService;
  }

  @PostMapping("/force-recalc")
  public ResponseEntity<ForceRecalcResponse> forceRecalc(
      @RequestHeader(RequestIdConstants.HEADER_NAME) String requestId,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody(required = false) ForceRecalcRequest request) {
    String adminUserId = ArenaAdminApiSupport.requireAdminUserId(jwt);
    String reason = request == null ? null : request.reason();
    ArenaAdminService.ForceRecalcResult result = arenaAdminService.forceRecalc(adminUserId, reason);
    return ResponseEntity.ok(ForceRecalcResponse.from(result));
  }

  @GetMapping("/match-quality")
  public ResponseEntity<MatchQualityResponse> matchQuality(
      @RequestHeader(name = RequestIdConstants.HEADER_NAME, required = false) String requestId,
      @AuthenticationPrincipal Jwt jwt) {
    ArenaAdminApiSupport.requireAdminUserId(jwt);
    ArenaAdminService.MatchQualityReport report = arenaAdminService.evaluateMatchQuality();
    return ResponseEntity.ok(MatchQualityResponse.from(report));
  }

  /**
   * Optional admin note for one-shot maintenance action.
   *
   * <p>Used only for audit/log context; business behavior remains deterministic.
   */
  public record ForceRecalcRequest(@Size(max = 200) String reason) {}

  public record ForceRecalcResponse(
      String rebuildReason,
      int activeSpecimens,
      int deletedPairs,
      int upsertedPairs,
      MatchQualityResponse matchQuality) {

    static ForceRecalcResponse from(ArenaAdminService.ForceRecalcResult result) {
      return new ForceRecalcResponse(
          result.rebuildReason(),
          result.activeSpecimens(),
          result.deletedPairs(),
          result.upsertedPairs(),
          MatchQualityResponse.from(result.matchQuality()));
    }
  }

  public record MatchQualityResponse(
      int activeSpecimenCount,
      int expectedPairCount,
      int availablePairCount,
      boolean pairCoverageComplete,
      BigDecimal pairCoverageRatio,
      int resetExcludeThreshold,
      String configuredProfileVersion,
      boolean profileVersionAligned,
      List<MatchTypeBreakdownResponse> matchTypeBreakdown,
      List<ProfileVersionBreakdownResponse> profileVersionBreakdown,
      ScoreSummaryResponse scoreSummary) {

    static MatchQualityResponse from(ArenaAdminService.MatchQualityReport report) {
      return new MatchQualityResponse(
          report.activeSpecimenCount(),
          report.expectedPairCount(),
          report.availablePairCount(),
          report.pairCoverageComplete(),
          report.pairCoverageRatio(),
          report.resetExcludeThreshold(),
          report.configuredProfileVersion(),
          report.profileVersionAligned(),
          report.matchTypeBreakdown().stream().map(MatchTypeBreakdownResponse::from).toList(),
          report.profileVersionBreakdown().stream().map(ProfileVersionBreakdownResponse::from).toList(),
          ScoreSummaryResponse.from(report.scoreSummary()));
    }
  }

  public record MatchTypeBreakdownResponse(String matchType, int pairCount) {

    static MatchTypeBreakdownResponse from(ArenaAdminService.MatchTypeBreakdown breakdown) {
      ArenaMatchType matchType = breakdown.matchType();
      String resolvedMatchType = matchType == null ? ArenaMatchType.CROSS.name() : matchType.name();
      return new MatchTypeBreakdownResponse(resolvedMatchType, breakdown.pairCount());
    }
  }

  public record ProfileVersionBreakdownResponse(String profileVersion, int pairCount) {

    static ProfileVersionBreakdownResponse from(ArenaAdminService.ProfileVersionBreakdown breakdown) {
      return new ProfileVersionBreakdownResponse(breakdown.profileVersion(), breakdown.pairCount());
    }
  }

  public record ScoreSummaryResponse(int minScore, int maxScore, BigDecimal averageScore) {

    static ScoreSummaryResponse from(ArenaAdminService.ScoreSummary summary) {
      return new ScoreSummaryResponse(summary.minScore(), summary.maxScore(), summary.averageScore());
    }
  }
}
