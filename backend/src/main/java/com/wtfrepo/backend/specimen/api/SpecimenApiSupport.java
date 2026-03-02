package com.wtfrepo.backend.specimen.api;

import com.wtfrepo.backend.specimen.application.model.SpecimenModels;
import com.wtfrepo.backend.specimen.application.support.SpecimenConstants;
import com.wtfrepo.backend.specimen.application.support.SpecimenExceptions;
import com.wtfrepo.backend.specimen.domain.SpecimenReviewAction;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

/**
 * Shared API helper methods and DTOs for specimen controllers.
 */
final class SpecimenApiSupport {

  private SpecimenApiSupport() {}

  static String requireUserId(Jwt jwt) {
    if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
      throw SpecimenExceptions.unauthorized("unauthorized");
    }
    return jwt.getSubject();
  }

  static String resolveOptionalUserId(Jwt jwt) {
    if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
      return null;
    }
    return jwt.getSubject();
  }

  static String requireAdminUserId(Jwt jwt) {
    String userId = requireUserId(jwt);
    if (!hasAdminRole(jwt)) {
      throw SpecimenExceptions.forbidden(SpecimenConstants.Message.FORBIDDEN_ADMIN);
    }
    return userId;
  }

  static SpecimenReviewAction parseReviewAction(String action) {
    try {
      return SpecimenReviewAction.valueOf(action.toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw SpecimenExceptions.validation(SpecimenConstants.Message.INVALID_ACTION);
    }
  }

  private static boolean hasAdminRole(Jwt jwt) {
    Object rolesClaim = jwt.getClaim(SpecimenConstants.Claim.ROLES);

    if (rolesClaim instanceof Collection<?> roles) {
      return roles.stream().map(String::valueOf).anyMatch(SpecimenApiSupport::isAdminRole);
    }

    if (rolesClaim instanceof String roles) {
      return Stream.of(roles.split(","))
          .map(String::trim)
          .anyMatch(SpecimenApiSupport::isAdminRole);
    }

    return false;
  }

  private static boolean isAdminRole(String role) {
    return SpecimenConstants.Role.ADMIN.equalsIgnoreCase(role)
        || SpecimenConstants.Role.MANAGER.equalsIgnoreCase(role);
  }

  record TagListResponse(String configVersion, List<TagDimensionResponse> dimensions) {

    static TagListResponse from(SpecimenModels.TagListResult result) {
      List<TagDimensionResponse> dimensionResponses =
          result.dimensions().stream().map(TagDimensionResponse::from).toList();
      return new TagListResponse(result.configVersion(), dimensionResponses);
    }
  }

  record TagDimensionResponse(
      String dimensionKey,
      String nameZh,
      String nameEn,
      String selectMode,
      boolean required,
      int sortOrder,
      Object uiMeta,
      List<TagResponse> tags) {

    static TagDimensionResponse from(SpecimenModels.TagDimensionResult result) {
      List<TagResponse> tagResponses = result.tags().stream().map(TagResponse::from).toList();
      return new TagDimensionResponse(
          result.dimensionKey(),
          result.nameZh(),
          result.nameEn(),
          result.selectMode(),
          result.required(),
          result.sortOrder(),
          result.uiMeta(),
          tagResponses);
    }
  }

  record TagResponse(String tagKey, String nameZh, String nameEn, Object uiMeta) {

    static TagResponse from(SpecimenModels.TagResult result) {
      return new TagResponse(result.tagKey(), result.nameZh(), result.nameEn(), result.uiMeta());
    }
  }

  record RepoIdentityResponse(
      String githubLogin,
      String githubUserId,
      String githubAvatarUrl,
      String githubHtmlUrl,
      Integer contributions) {

    static RepoIdentityResponse from(SpecimenModels.RepoIdentityItem value) {
      return new RepoIdentityResponse(
          value.githubLogin(),
          value.githubUserId(),
          value.githubAvatarUrl(),
          value.githubHtmlUrl(),
          value.contributions());
    }
  }

  record RepoIdentitiesResponse(
      String specimenId,
      RepoIdentityResponse owner,
      List<RepoIdentityResponse> contributors,
      Instant syncedAt) {

    static RepoIdentitiesResponse from(SpecimenModels.RepoIdentitiesResult result) {
      return new RepoIdentitiesResponse(
          result.specimenId(),
          result.owner() == null ? null : RepoIdentityResponse.from(result.owner()),
          result.contributors().stream().map(RepoIdentityResponse::from).toList(),
          result.syncedAt());
    }
  }

  record WatchlistAddResponse(boolean added, String itemId) {}

  record WatchlistRemoveResponse(boolean removed) {}

  record WatchlistListResponse(
      List<WatchlistItemResponse> items,
      String nextCursor,
      boolean hasMore) {

    static WatchlistListResponse from(SpecimenModels.WatchlistPage page) {
      return new WatchlistListResponse(
          page.items().stream().map(WatchlistItemResponse::from).toList(),
          page.nextCursor(),
          page.hasMore());
    }
  }

  record WatchlistItemResponse(
      String specimenId,
      String repoFullName,
      MetricsResponse metrics,
      Instant addedAt) {

    static WatchlistItemResponse from(SpecimenModels.WatchlistItemResult item) {
      return new WatchlistItemResponse(
          item.specimenId(),
          item.repoFullName(),
          new MetricsResponse(item.elo(), item.hype()),
          item.addedAt());
    }
  }

  record MetricsResponse(int elo, double hype) {}

  record WatchlistAddRequest(String specimenId, String source) {}

  record ArchiveResponse(List<ArchiveItemResponse> items, String nextCursor, boolean hasMore) {

    static ArchiveResponse from(SpecimenModels.ArchivePage page) {
      return new ArchiveResponse(
          page.items().stream().map(ArchiveItemResponse::from).toList(),
          page.nextCursor(),
          page.hasMore());
    }
  }

  record ArchiveInsightsResponse(
      ArchiveSummaryResponse summary,
      ArchiveMomentumResponse momentum,
      List<ArchiveMomentumMoverResponse> topRising,
      List<ArchiveMomentumMoverResponse> topFalling) {

    static ArchiveInsightsResponse from(SpecimenModels.ArchiveInsightsResult value) {
      return new ArchiveInsightsResponse(
          ArchiveSummaryResponse.from(value.summary()),
          ArchiveMomentumResponse.from(value.momentum()),
          value.topRising().stream().map(ArchiveMomentumMoverResponse::from).toList(),
          value.topFalling().stream().map(ArchiveMomentumMoverResponse::from).toList());
    }
  }

  record ArchiveSummaryResponse(
      long totalSpecimens,
      long totalVotes,
      long todayArenaBattles,
      double averageElo,
      double averageHype,
      String tradingDay) {

    static ArchiveSummaryResponse from(SpecimenModels.ArchiveSummaryResult value) {
      return new ArchiveSummaryResponse(
          value.totalSpecimens(),
          value.totalVotes(),
          value.todayArenaBattles(),
          value.averageElo(),
          value.averageHype(),
          value.tradingDay());
    }
  }

  record ArchiveMomentumResponse(int rising, int unchanged, int falling) {

    static ArchiveMomentumResponse from(SpecimenModels.ArchiveMomentumResult value) {
      return new ArchiveMomentumResponse(value.rising(), value.unchanged(), value.falling());
    }
  }

  record ArchiveMomentumMoverResponse(
      String specimenId,
      String repoFullName,
      int rank,
      int previousRank,
      int rankDelta,
      ArchiveMetricsResponse metrics) {

    static ArchiveMomentumMoverResponse from(SpecimenModels.ArchiveMomentumMoverResult value) {
      return new ArchiveMomentumMoverResponse(
          value.specimenId(),
          value.repoFullName(),
          value.rank(),
          value.previousRank(),
          value.rankDelta(),
          ArchiveMetricsResponse.from(value.metrics()));
    }
  }

  record ArchiveLeaderboardResponse(
      String metric,
      List<ArchiveLeaderboardItemResponse> items,
      String nextCursor,
      boolean hasMore,
      long total) {

    static ArchiveLeaderboardResponse from(SpecimenModels.ArchiveLeaderboardPage value) {
      return new ArchiveLeaderboardResponse(
          value.metric(),
          value.items().stream().map(ArchiveLeaderboardItemResponse::from).toList(),
          value.nextCursor(),
          value.hasMore(),
          value.total());
    }
  }

  record ArchiveLeaderboardItemResponse(
      String specimenId,
      String repoFullName,
      int rank,
      Integer rankDelta,
      double score,
      ArchiveMetricsResponse metrics) {

    static ArchiveLeaderboardItemResponse from(SpecimenModels.ArchiveLeaderboardItemResult value) {
      return new ArchiveLeaderboardItemResponse(
          value.specimenId(),
          value.repoFullName(),
          value.rank(),
          value.rankDelta(),
          value.score(),
          ArchiveMetricsResponse.from(value.metrics()));
    }
  }

  record ArchiveItemResponse(
      String specimenId,
      String repoFullName,
      String oneLiner,
      ArchiveGithubMetaResponse githubMeta,
      ArchiveMetricsResponse metrics,
      List<ArchiveTagResponse> tags) {

    static ArchiveItemResponse from(SpecimenModels.ArchiveItemResult item) {
      return new ArchiveItemResponse(
          item.specimenId(),
          item.repoFullName(),
          item.oneLiner(),
          ArchiveGithubMetaResponse.from(item.githubMeta()),
          ArchiveMetricsResponse.from(item.metrics()),
          item.tags().stream().map(ArchiveTagResponse::from).toList());
    }
  }

  record ArchiveGithubMetaResponse(
      String repoHtmlUrl,
      String ownerLogin,
      String ownerAvatarUrl,
      List<LanguageResponse> languages,
      long stargazersCount,
      Instant pushedAt,
      List<String> topics) {

    static ArchiveGithubMetaResponse from(SpecimenModels.ArchiveGithubMetaResult value) {
      return new ArchiveGithubMetaResponse(
          value.repoHtmlUrl(),
          value.ownerLogin(),
          value.ownerAvatarUrl(),
          value.languages().stream().map(LanguageResponse::from).toList(),
          value.stargazersCount(),
          value.pushedAt(),
          value.topics());
    }
  }

  record ArchiveMetricsResponse(int elo, double hype, long votes, int delta24h) {

    static ArchiveMetricsResponse from(SpecimenModels.ArchiveMetricsResult value) {
      return new ArchiveMetricsResponse(value.elo(), value.hype(), value.votes(), value.delta24h());
    }
  }

  record ArchiveTagResponse(String dimensionKey, String tagKey, String name) {

    static ArchiveTagResponse from(SpecimenModels.ArchiveTagResult value) {
      return new ArchiveTagResponse(value.dimensionKey(), value.tagKey(), value.name());
    }
  }

  record DrawerResponse(
      DrawerSpecimenResponse specimen,
      DrawerGithubMetaResponse githubMeta,
      DrawerMetricsResponse metrics,
      DrawerReadmeExcerptResponse readmeExcerpt,
      List<DrawerTagResponse> tags,
      Object topRoast,
      boolean canOpenInternalDetail,
      DrawerGithubJumpWarningResponse githubJumpWarning) {

    static DrawerResponse from(SpecimenModels.DrawerResult value) {
      return new DrawerResponse(
          DrawerSpecimenResponse.from(value.specimen()),
          DrawerGithubMetaResponse.from(value.githubMeta()),
          DrawerMetricsResponse.from(value.metrics()),
          value.readmeExcerpt() == null ? null : DrawerReadmeExcerptResponse.from(value.readmeExcerpt()),
          value.tags().stream().map(DrawerTagResponse::from).toList(),
          value.topRoast(),
          value.canOpenInternalDetail(),
          DrawerGithubJumpWarningResponse.from(value.githubJumpWarning()));
    }
  }

  record DrawerSpecimenResponse(String specimenId, String repoFullName, String githubUrl) {

    static DrawerSpecimenResponse from(SpecimenModels.DrawerSpecimenResult value) {
      return new DrawerSpecimenResponse(value.specimenId(), value.repoFullName(), value.githubUrl());
    }
  }

  record DrawerGithubMetaResponse(
      String repoHtmlUrl,
      String ownerLogin,
      String ownerAvatarUrl,
      String description,
      List<LanguageResponse> languages,
      long stargazersCount,
      Instant pushedAt,
      List<String> topics,
      Instant metadataSyncedAt) {

    static DrawerGithubMetaResponse from(SpecimenModels.DrawerGithubMetaResult value) {
      return new DrawerGithubMetaResponse(
          value.repoHtmlUrl(),
          value.ownerLogin(),
          value.ownerAvatarUrl(),
          value.description(),
          value.languages().stream().map(LanguageResponse::from).toList(),
          value.stargazersCount(),
          value.pushedAt(),
          value.topics(),
          value.metadataSyncedAt());
    }
  }

  record DrawerMetricsResponse(int elo, double hype, long votes) {

    static DrawerMetricsResponse from(SpecimenModels.DrawerMetricsResult value) {
      return new DrawerMetricsResponse(value.elo(), value.hype(), value.votes());
    }
  }

  record DrawerReadmeExcerptResponse(String excerptType, String text) {

    static DrawerReadmeExcerptResponse from(SpecimenModels.DrawerReadmeExcerptResult value) {
      return new DrawerReadmeExcerptResponse(value.excerptType(), value.text());
    }
  }

  record DrawerTagResponse(String dimensionKey, String tagKey, String name) {

    static DrawerTagResponse from(SpecimenModels.DrawerTagResult value) {
      return new DrawerTagResponse(value.dimensionKey(), value.tagKey(), value.name());
    }
  }

  record DrawerGithubJumpWarningResponse(String title, String body) {

    static DrawerGithubJumpWarningResponse from(SpecimenModels.DrawerGithubJumpWarningResult value) {
      return new DrawerGithubJumpWarningResponse(value.title(), value.body());
    }
  }

  record DetailResponse(
      DetailSpecimenResponse specimen,
      DetailGithubMetaResponse githubMeta,
      DetailMetricsResponse metrics,
      DetailReadmeResponse readme,
      DetailOfficialCommentaryResponse officialCommentary,
      List<DetailCodeHighlightResponse> codeHighlights,
      DetailRepoIdentityResponse repoIdentity,
      List<DetailTagResponse> tags,
      DetailSeoMetaResponse seoMeta) {

    static DetailResponse from(SpecimenModels.DetailResult value) {
      return new DetailResponse(
          DetailSpecimenResponse.from(value.specimen()),
          DetailGithubMetaResponse.from(value.githubMeta()),
          DetailMetricsResponse.from(value.metrics()),
          DetailReadmeResponse.from(value.readme()),
          DetailOfficialCommentaryResponse.from(value.officialCommentary()),
          value.codeHighlights().stream().map(DetailCodeHighlightResponse::from).toList(),
          DetailRepoIdentityResponse.from(value.repoIdentity()),
          value.tags().stream().map(DetailTagResponse::from).toList(),
          DetailSeoMetaResponse.from(value.seoMeta()));
    }
  }

  record DetailSpecimenResponse(String specimenId, String repoFullName, String publicStatus) {

    static DetailSpecimenResponse from(SpecimenModels.DetailSpecimenResult value) {
      return new DetailSpecimenResponse(value.specimenId(), value.repoFullName(), value.publicStatus());
    }
  }

  record DetailGithubMetaResponse(
      Long repoId,
      String repoHtmlUrl,
      DetailGithubOwnerResponse owner,
      String description,
      String homepage,
      String defaultBranch,
      List<DetailLanguageResponse> languages,
      List<String> topics,
      DetailGithubLicenseResponse license,
      String visibility,
      boolean archived,
      boolean fork,
      Instant createdAt,
      Instant updatedAt,
      Instant pushedAt,
      long stargazersCount,
      long forksCount,
      long openIssuesCount,
      Instant metadataSyncedAt) {

    static DetailGithubMetaResponse from(SpecimenModels.DetailGithubMetaResult value) {
      return new DetailGithubMetaResponse(
          value.repoId(),
          value.repoHtmlUrl(),
          DetailGithubOwnerResponse.from(value.owner()),
          value.description(),
          value.homepage(),
          value.defaultBranch(),
          value.languages().stream().map(DetailLanguageResponse::from).toList(),
          value.topics(),
          value.license() == null ? null : DetailGithubLicenseResponse.from(value.license()),
          value.visibility(),
          value.archived(),
          value.fork(),
          value.createdAt(),
          value.updatedAt(),
          value.pushedAt(),
          value.stargazersCount(),
          value.forksCount(),
          value.openIssuesCount(),
          value.metadataSyncedAt());
    }
  }

  record DetailGithubOwnerResponse(String login, String id, String avatarUrl, String htmlUrl) {

    static DetailGithubOwnerResponse from(SpecimenModels.DetailGithubOwnerResult value) {
      return new DetailGithubOwnerResponse(value.login(), value.id(), value.avatarUrl(), value.htmlUrl());
    }
  }

  record DetailGithubLicenseResponse(String spdxId, String name) {

    static DetailGithubLicenseResponse from(SpecimenModels.DetailGithubLicenseResult value) {
      return new DetailGithubLicenseResponse(value.spdxId(), value.name());
    }
  }

  record DetailLanguageResponse(String name, Long bytes, Double percentage) {

    static DetailLanguageResponse from(SpecimenModels.DetailLanguageResult value) {
      return new DetailLanguageResponse(value.name(), value.bytes(), value.percentage());
    }
  }

  record DetailMetricsResponse(int elo, double hype, long votes, long comments) {

    static DetailMetricsResponse from(SpecimenModels.DetailMetricsResult value) {
      return new DetailMetricsResponse(value.elo(), value.hype(), value.votes(), value.comments());
    }
  }

  record DetailReadmeResponse(String snapshotId, List<DetailReadmeExcerptResponse> excerpts) {

    static DetailReadmeResponse from(SpecimenModels.DetailReadmeResult value) {
      return new DetailReadmeResponse(
          value.snapshotId(), value.excerpts().stream().map(DetailReadmeExcerptResponse::from).toList());
    }
  }

  record DetailReadmeExcerptResponse(String excerptType, String text) {

    static DetailReadmeExcerptResponse from(SpecimenModels.DetailReadmeExcerptResult value) {
      return new DetailReadmeExcerptResponse(value.excerptType(), value.text());
    }
  }

  record DetailOfficialCommentaryResponse(Map<String, String> oneLiner, Map<String, String> arenaReason) {

    static DetailOfficialCommentaryResponse from(SpecimenModels.DetailOfficialCommentaryResult value) {
      return new DetailOfficialCommentaryResponse(value.oneLiner(), value.arenaReason());
    }
  }

  record DetailCodeHighlightResponse(String title, String codeLanguage, String snippet, String explainText) {

    static DetailCodeHighlightResponse from(SpecimenModels.DetailCodeHighlightResult value) {
      return new DetailCodeHighlightResponse(
          value.title(), value.codeLanguage(), value.snippet(), value.explainText());
    }
  }

  record DetailRepoIdentityResponse(
      DetailRepoIdentityUserResponse owner,
      List<DetailRepoIdentityUserResponse> contributors) {

    static DetailRepoIdentityResponse from(SpecimenModels.DetailRepoIdentityResult value) {
      return new DetailRepoIdentityResponse(
          value.owner() == null ? null : DetailRepoIdentityUserResponse.from(value.owner()),
          value.contributors().stream().map(DetailRepoIdentityUserResponse::from).toList());
    }
  }

  record DetailRepoIdentityUserResponse(String githubLogin, String githubUserId) {

    static DetailRepoIdentityUserResponse from(SpecimenModels.DetailRepoIdentityUserResult value) {
      return new DetailRepoIdentityUserResponse(value.githubLogin(), value.githubUserId());
    }
  }

  record DetailTagResponse(String dimensionKey, String tagKey, String name) {

    static DetailTagResponse from(SpecimenModels.DetailTagResult value) {
      return new DetailTagResponse(value.dimensionKey(), value.tagKey(), value.name());
    }
  }

  record DetailSeoMetaResponse(String title, String description) {

    static DetailSeoMetaResponse from(SpecimenModels.DetailSeoMetaResult value) {
      return new DetailSeoMetaResponse(value.title(), value.description());
    }
  }

  record LanguageResponse(String name, Double percentage) {

    static LanguageResponse from(SpecimenModels.LanguageItem value) {
      return new LanguageResponse(value.name(), value.percentage());
    }
  }
}
