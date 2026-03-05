package com.wtfrepo.backend.specimen.application.model;

import com.wtfrepo.backend.specimen.domain.SpecimenStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Shared application-layer records for specimen use cases and API mapping.
 */
public final class SpecimenModels {

  private SpecimenModels() {}

  public record LanguageItem(String name, Double percentage) {}

  public record TagAssignment(String dimensionKey, String tagKey) {}

  public record ReadmeExcerptInput(
      String excerptType,
      String candidateId,
      String text,
      String translatedTextZh,
      Object translationMeta,
      Integer priority) {

    public int resolvedPriority() {
      return priority == null ? 0 : priority;
    }
  }

  public record OfficialCommentaryInput(
      String oneLinerZh,
      String oneLinerEn,
      String arenaReasonZh,
      String arenaReasonEn) {}

  public record CodeHighlightInput(
      String title,
      String candidateId,
      String codeLanguage,
      String snippet,
      String explainText,
      Integer priority) {

    public int resolvedPriority() {
      return priority == null ? 0 : priority;
    }
  }

  public record RepoIdentityItem(
      String githubLogin,
      String githubUserId,
      String githubAvatarUrl,
      String githubHtmlUrl,
      Integer contributions) {}

  public record RepoIdentityInput(
      RepoIdentityItem owner,
      List<RepoIdentityItem> maintainers,
      List<RepoIdentityItem> contributors) {}

  public record FetchedMeta(
      String repoName, String owner, List<LanguageItem> languages, boolean readmeFetched) {}

  public record ReadmeCandidate(
      String candidateId,
      String candidateType,
      String heading,
      String text,
      Double score,
      String codeLanguage) {}

  public record RepoIdentityCandidates(RepoIdentityItem owner, List<RepoIdentityItem> contributors) {}

  public record LanguageCandidate(String name, long bytes, double percentage) {}

  public record ImportResult(
      String specimenId,
      SpecimenStatus status,
      FetchedMeta fetchedMeta,
      List<ReadmeCandidate> readmeCandidates,
      List<ReadmeCandidate> codeCandidates,
      RepoIdentityCandidates repoIdentityCandidates,
      List<LanguageCandidate> languageCandidates) {}

  public record SubmitCommand(
      List<LanguageItem> languages,
      List<TagAssignment> tags,
      List<ReadmeExcerptInput> readmeExcerpts,
      boolean readmeSnapshotEnabled,
      OfficialCommentaryInput officialCommentary,
      List<CodeHighlightInput> codeHighlights,
      RepoIdentityInput repoIdentity,
      String note) {}

  public record SubmitResult(String specimenId, SpecimenStatus status) {}

  public record TagUpdateResult(String specimenId, SpecimenStatus status) {}

  public record ReviewResult(
      String specimenId, SpecimenStatus status, String reviewedBy, Instant reviewedAt) {}

  public record TagResult(String tagKey, String nameZh, String nameEn, Object uiMeta) {}

  public record TagDimensionResult(
      String dimensionKey,
      String nameZh,
      String nameEn,
      String selectMode,
      boolean required,
      int sortOrder,
      Object uiMeta,
      List<TagResult> tags) {}

  public record TagListResult(String configVersion, List<TagDimensionResult> dimensions) {}

  public record WatchlistItemResult(
      String itemId,
      String specimenId,
      String repoFullName,
      int elo,
      double hype,
      Instant addedAt) {}

  public record WatchlistPage(List<WatchlistItemResult> items, String nextCursor, boolean hasMore) {}

  public record AddWatchlistResult(boolean added, String itemId) {}

  public record RemoveWatchlistResult(boolean removed) {}

  public record RepoIdentitiesResult(
      String specimenId,
      RepoIdentityItem owner,
      List<RepoIdentityItem> contributors,
      Instant syncedAt) {}

  public record ArchiveQuery(String cursor, Integer limit, String sort, List<String> tags, String q) {}

  public record ArchiveTagResult(String dimensionKey, String tagKey, String name) {}

  public record ArchiveGithubMetaResult(
      String repoHtmlUrl,
      String ownerLogin,
      String ownerAvatarUrl,
      List<LanguageItem> languages,
      long stargazersCount,
      Instant pushedAt,
      List<String> topics) {}

  public record ArchiveMetricsResult(int elo, double hype, long votes, int delta24h) {}

  public record ArchiveItemResult(
      String specimenId,
      String repoFullName,
      String oneLiner,
      ArchiveGithubMetaResult githubMeta,
      ArchiveMetricsResult metrics,
      List<ArchiveTagResult> tags) {}

  public record ArchivePage(List<ArchiveItemResult> items, String nextCursor, boolean hasMore) {}

  public record ArchiveLeaderboardQuery(String metric, String cursor, Integer limit) {}

  public record ArchiveLeaderboardItemResult(
      String specimenId,
      String repoFullName,
      int rank,
      Integer rankDelta,
      double score,
      ArchiveMetricsResult metrics) {}

  public record ArchiveLeaderboardPage(
      String metric,
      List<ArchiveLeaderboardItemResult> items,
      String nextCursor,
      boolean hasMore,
      long total) {}

  public record ArchiveMomentumResult(int rising, int unchanged, int falling) {}

  public record ArchiveMomentumMoverResult(
      String specimenId,
      String repoFullName,
      int rank,
      int previousRank,
      int rankDelta,
      ArchiveMetricsResult metrics) {}

  public record ArchiveSummaryResult(
      long totalSpecimens,
      long totalVotes,
      long todayArenaBattles,
      double averageElo,
      double averageHype,
      String tradingDay) {}

  public record ArchiveInsightsResult(
      ArchiveSummaryResult summary,
      ArchiveMomentumResult momentum,
      List<ArchiveMomentumMoverResult> topRising,
      List<ArchiveMomentumMoverResult> topFalling) {}

  public record DrawerSpecimenResult(String specimenId, String repoFullName, String githubUrl) {}

  public record DrawerGithubMetaResult(
      String repoHtmlUrl,
      String ownerLogin,
      String ownerAvatarUrl,
      String description,
      List<LanguageItem> languages,
      long stargazersCount,
      Instant pushedAt,
      List<String> topics,
      Instant metadataSyncedAt) {}

  public record DrawerMetricsResult(int elo, double hype, long votes) {}

  public record DrawerReadmeExcerptResult(String excerptType, String text) {}

  public record DrawerTagResult(String dimensionKey, String tagKey, String name) {}

  public record DrawerGithubJumpWarningResult(String title, String body) {}

  public record TopRoastSummary(String commentId, int resonanceCount, boolean chiefConclusion) {}

  public record DrawerResult(
      DrawerSpecimenResult specimen,
      DrawerGithubMetaResult githubMeta,
      DrawerMetricsResult metrics,
      DrawerReadmeExcerptResult readmeExcerpt,
      List<DrawerTagResult> tags,
      Object topRoast,
      boolean canOpenInternalDetail,
      DrawerGithubJumpWarningResult githubJumpWarning) {}

  public record DetailSpecimenResult(String specimenId, String repoFullName, String publicStatus) {}

  public record DetailGithubOwnerResult(
      String login, String id, String avatarUrl, String htmlUrl) {}

  public record DetailGithubLicenseResult(String spdxId, String name) {}

  public record DetailLanguageResult(String name, Long bytes, Double percentage) {}

  public record DetailGithubMetaResult(
      Long repoId,
      String repoHtmlUrl,
      DetailGithubOwnerResult owner,
      String description,
      String homepage,
      String defaultBranch,
      List<DetailLanguageResult> languages,
      List<String> topics,
      DetailGithubLicenseResult license,
      String visibility,
      boolean archived,
      boolean fork,
      Instant createdAt,
      Instant updatedAt,
      Instant pushedAt,
      long stargazersCount,
      long forksCount,
      long openIssuesCount,
      Instant metadataSyncedAt) {}

  public record DetailMetricsResult(int elo, double hype, long votes, long comments) {}

  public record DetailReadmeExcerptResult(
      String excerptType, String text, String translatedTextZh, Object translationMeta) {}

  public record DetailReadmeResult(String snapshotId, List<DetailReadmeExcerptResult> excerpts) {}

  public record DetailOfficialCommentaryResult(Map<String, String> oneLiner, Map<String, String> arenaReason) {}

  public record DetailCodeHighlightResult(
      String title, String codeLanguage, String snippet, String explainText) {}

  public record DetailRepoIdentityUserResult(String githubLogin, String githubUserId) {}

  public record DetailRepoIdentityResult(
      DetailRepoIdentityUserResult owner, List<DetailRepoIdentityUserResult> contributors) {}

  public record DetailTagResult(String dimensionKey, String tagKey, String name) {}

  public record DetailSeoMetaResult(String title, String description) {}

  public record DetailResult(
      DetailSpecimenResult specimen,
      DetailGithubMetaResult githubMeta,
      DetailMetricsResult metrics,
      DetailReadmeResult readme,
      DetailOfficialCommentaryResult officialCommentary,
      List<DetailCodeHighlightResult> codeHighlights,
      DetailRepoIdentityResult repoIdentity,
      List<DetailTagResult> tags,
      DetailSeoMetaResult seoMeta) {}
}
