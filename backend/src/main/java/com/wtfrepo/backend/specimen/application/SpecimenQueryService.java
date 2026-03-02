package com.wtfrepo.backend.specimen.application;

import com.wtfrepo.backend.arena.application.support.ArenaTradingDayResolver;
import com.wtfrepo.backend.arena.infra.persistence.entity.EloDailySnapshotJpaEntity;
import com.wtfrepo.backend.arena.infra.persistence.repository.BattleVoteJpaRepository;
import com.wtfrepo.backend.arena.infra.persistence.repository.EloDailySnapshotJpaRepository;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.ArchiveGithubMetaResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.ArchiveInsightsResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.ArchiveItemResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.ArchiveLeaderboardItemResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.ArchiveLeaderboardPage;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.ArchiveLeaderboardQuery;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.ArchiveMetricsResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.ArchiveMomentumMoverResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.ArchiveMomentumResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.ArchivePage;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.ArchiveQuery;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.ArchiveSummaryResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.ArchiveTagResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.DetailCodeHighlightResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.DetailGithubLicenseResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.DetailGithubMetaResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.DetailGithubOwnerResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.DetailLanguageResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.DetailMetricsResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.DetailOfficialCommentaryResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.DetailReadmeExcerptResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.DetailReadmeResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.DetailRepoIdentityResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.DetailRepoIdentityUserResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.DetailResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.DetailSeoMetaResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.DetailSpecimenResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.DetailTagResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.TopRoastSummary;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.DrawerGithubJumpWarningResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.DrawerGithubMetaResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.DrawerMetricsResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.DrawerReadmeExcerptResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.DrawerResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.DrawerSpecimenResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.DrawerTagResult;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.LanguageItem;
import com.wtfrepo.backend.specimen.application.support.SpecimenConstants;
import com.wtfrepo.backend.specimen.application.support.SpecimenExceptions;
import com.wtfrepo.backend.specimen.application.support.SpecimenJsonCodec;
import com.wtfrepo.backend.specimen.domain.RepoIdentityRole;
import com.wtfrepo.backend.specimen.domain.SpecimenStatus;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenArenaMetricsJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenCodeHighlightJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenCommunityMetricsJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenGithubMetadataJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenOfficialCommentaryJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenReadmeExcerptJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenRepoIdentityJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenTagJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.entity.TagDefinitionJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenArenaMetricsJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenCodeHighlightJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenCommunityMetricsJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenGithubMetadataJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenOfficialCommentaryJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenReadmeExcerptJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenRepoIdentityJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenTagJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.TagDefinitionJpaRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * User-facing specimen query service for archive/list/detail read models.
 */
@Service
public class SpecimenQueryService {

  private static final String DEFAULT_LOCALE = "zh";

  private final SpecimenJpaRepository specimenJpaRepository;
  private final SpecimenGithubMetadataJpaRepository specimenGithubMetadataJpaRepository;
  private final SpecimenArenaMetricsJpaRepository specimenArenaMetricsJpaRepository;
  private final SpecimenCommunityMetricsJpaRepository specimenCommunityMetricsJpaRepository;
  private final SpecimenReadmeExcerptJpaRepository specimenReadmeExcerptJpaRepository;
  private final SpecimenOfficialCommentaryJpaRepository specimenOfficialCommentaryJpaRepository;
  private final SpecimenCodeHighlightJpaRepository specimenCodeHighlightJpaRepository;
  private final SpecimenRepoIdentityJpaRepository specimenRepoIdentityJpaRepository;
  private final SpecimenTagJpaRepository specimenTagJpaRepository;
  private final TagDefinitionJpaRepository tagDefinitionJpaRepository;
  private final BattleVoteJpaRepository battleVoteJpaRepository;
  private final EloDailySnapshotJpaRepository eloDailySnapshotJpaRepository;
  private final SpecimenContractProperties specimenContractProperties;
  private final SpecimenJsonCodec specimenJsonCodec;

  public SpecimenQueryService(
      SpecimenJpaRepository specimenJpaRepository,
      SpecimenGithubMetadataJpaRepository specimenGithubMetadataJpaRepository,
      SpecimenArenaMetricsJpaRepository specimenArenaMetricsJpaRepository,
      SpecimenCommunityMetricsJpaRepository specimenCommunityMetricsJpaRepository,
      SpecimenReadmeExcerptJpaRepository specimenReadmeExcerptJpaRepository,
      SpecimenOfficialCommentaryJpaRepository specimenOfficialCommentaryJpaRepository,
      SpecimenCodeHighlightJpaRepository specimenCodeHighlightJpaRepository,
      SpecimenRepoIdentityJpaRepository specimenRepoIdentityJpaRepository,
      SpecimenTagJpaRepository specimenTagJpaRepository,
      TagDefinitionJpaRepository tagDefinitionJpaRepository,
      BattleVoteJpaRepository battleVoteJpaRepository,
      EloDailySnapshotJpaRepository eloDailySnapshotJpaRepository,
      SpecimenContractProperties specimenContractProperties,
      SpecimenJsonCodec specimenJsonCodec) {
    this.specimenJpaRepository = specimenJpaRepository;
    this.specimenGithubMetadataJpaRepository = specimenGithubMetadataJpaRepository;
    this.specimenArenaMetricsJpaRepository = specimenArenaMetricsJpaRepository;
    this.specimenCommunityMetricsJpaRepository = specimenCommunityMetricsJpaRepository;
    this.specimenReadmeExcerptJpaRepository = specimenReadmeExcerptJpaRepository;
    this.specimenOfficialCommentaryJpaRepository = specimenOfficialCommentaryJpaRepository;
    this.specimenCodeHighlightJpaRepository = specimenCodeHighlightJpaRepository;
    this.specimenRepoIdentityJpaRepository = specimenRepoIdentityJpaRepository;
    this.specimenTagJpaRepository = specimenTagJpaRepository;
    this.tagDefinitionJpaRepository = tagDefinitionJpaRepository;
    this.battleVoteJpaRepository = battleVoteJpaRepository;
    this.eloDailySnapshotJpaRepository = eloDailySnapshotJpaRepository;
    this.specimenContractProperties = specimenContractProperties;
    this.specimenJsonCodec = specimenJsonCodec;
  }

  @Transactional(readOnly = true)
  public ArchivePage listArchive(ArchiveQuery query, String locale) {
    String normalizedLocale = normalizeLocale(locale);
    int resolvedLimit = resolveArchiveLimit(query.limit());
    int pageIndex = resolvePageIndex(query.cursor());
    ArchiveSort sort = resolveArchiveSort(query.sort());

    List<SpecimenJpaEntity> activeSpecimens =
        StringUtils.hasText(query.q())
            ? specimenJpaRepository.findByStatusAndRepoFullNameContainingIgnoreCase(
                SpecimenStatus.ACTIVE, query.q().trim())
            : specimenJpaRepository.findByStatus(SpecimenStatus.ACTIVE);

    if (activeSpecimens.isEmpty()) {
      return new ArchivePage(List.of(), null, false);
    }

    Set<String> specimenIds =
        activeSpecimens.stream().map(SpecimenJpaEntity::getSpecimenId).collect(
            LinkedHashSet::new,
            LinkedHashSet::add,
            LinkedHashSet::addAll);

    Map<String, SpecimenArenaMetricsJpaEntity> metricsBySpecimenId =
        specimenArenaMetricsJpaRepository.findAllById(specimenIds).stream()
            .collect(
                LinkedHashMap::new,
                (map, value) -> map.put(value.getSpecimenId(), value),
                LinkedHashMap::putAll);

    Map<String, SpecimenGithubMetadataJpaEntity> metadataBySpecimenId =
        specimenGithubMetadataJpaRepository.findAllById(specimenIds).stream()
            .collect(
                LinkedHashMap::new,
                (map, value) -> map.put(value.getSpecimenId(), value),
                LinkedHashMap::putAll);

    Map<String, SpecimenOfficialCommentaryJpaEntity> commentaryBySpecimenId =
        specimenOfficialCommentaryJpaRepository.findAllById(specimenIds).stream()
            .collect(
                LinkedHashMap::new,
                (map, value) -> map.put(value.getSpecimenId(), value),
                LinkedHashMap::putAll);

    Map<String, List<SpecimenTagJpaEntity>> tagsBySpecimenId = groupTagsBySpecimenId(specimenIds);
    Map<String, String> tagDisplayNames =
        resolveTagDisplayNames(
            tagsBySpecimenId.values().stream().flatMap(List::stream).map(SpecimenTagJpaEntity::getTagKey).toList(),
            normalizedLocale);

    List<ArchiveItemResult> items = new ArrayList<>();
    for (SpecimenJpaEntity specimen : activeSpecimens) {
      SpecimenArenaMetricsJpaEntity metrics = metricsBySpecimenId.get(specimen.getSpecimenId());
      SpecimenGithubMetadataJpaEntity metadata = metadataBySpecimenId.get(specimen.getSpecimenId());
      SpecimenOfficialCommentaryJpaEntity commentary = commentaryBySpecimenId.get(specimen.getSpecimenId());
      List<SpecimenTagJpaEntity> specimenTags =
          tagsBySpecimenId.getOrDefault(specimen.getSpecimenId(), List.of());

      if (!matchesTags(specimenTags, query.tags())) {
        continue;
      }

      List<ArchiveTagResult> tagResults =
          specimenTags.stream()
              .map(
                  tag ->
                      new ArchiveTagResult(
                          tag.getDimensionKey(),
                          tag.getTagKey(),
                          tagDisplayNames.getOrDefault(tag.getTagKey(), tag.getTagKey())))
              .toList();

      items.add(
          new ArchiveItemResult(
              specimen.getSpecimenId(),
              specimen.getRepoFullName(),
              resolveOneLiner(commentary, normalizedLocale),
              toArchiveGithubMeta(metadata, specimen.getGithubUrl()),
              toArchiveMetrics(metrics),
              tagResults));
    }

    sortArchive(items, sort);

    int startIndex = pageIndex * resolvedLimit;
    if (startIndex >= items.size()) {
      return new ArchivePage(List.of(), null, false);
    }

    int endIndex = Math.min(items.size(), startIndex + resolvedLimit);
    List<ArchiveItemResult> paged = items.subList(startIndex, endIndex);
    boolean hasMore = endIndex < items.size();
    String nextCursor = hasMore ? String.valueOf(pageIndex + 1) : null;

    return new ArchivePage(paged, nextCursor, hasMore);
  }

  @Transactional(readOnly = true)
  public ArchiveInsightsResult getArchiveInsights() {
    List<ArchiveAggregateCandidate> candidates = loadActiveAggregateCandidates();
    if (candidates.isEmpty()) {
      LocalDate tradingDay = ArenaTradingDayResolver.currentTradingDay(Instant.now());
      return new ArchiveInsightsResult(
          new ArchiveSummaryResult(0L, 0L, 0L, 0.0D, 0.0D, tradingDay.toString()),
          new ArchiveMomentumResult(0, 0, 0),
          List.of(),
          List.of());
    }

    LocalDate tradingDay = ArenaTradingDayResolver.currentTradingDay(Instant.now());
    Set<String> specimenIds =
        candidates.stream().map(ArchiveAggregateCandidate::specimenId).collect(
            LinkedHashSet::new,
            LinkedHashSet::add,
            LinkedHashSet::addAll);
    Map<String, Integer> previousRanks = resolvePreviousEloRankBySpecimenId(specimenIds, tradingDay);

    List<RankedArchiveCandidate> rankedByElo = rankArchiveCandidates(candidates, ArchiveLeaderboardMetric.ELO);
    List<ArchiveMomentumMoverResult> movers = new ArrayList<>();
    int rising = 0;
    int unchanged = 0;
    int falling = 0;

    for (RankedArchiveCandidate ranked : rankedByElo) {
      Integer previousRank = previousRanks.get(ranked.candidate().specimenId());
      if (previousRank == null) {
        continue;
      }
      int rankDelta = previousRank - ranked.rank();
      if (rankDelta > 0) {
        rising += 1;
      } else if (rankDelta < 0) {
        falling += 1;
      } else {
        unchanged += 1;
      }
      movers.add(
          new ArchiveMomentumMoverResult(
              ranked.candidate().specimenId(),
              ranked.candidate().repoFullName(),
              ranked.rank(),
              previousRank,
              rankDelta,
              ranked.candidate().metrics()));
    }

    List<ArchiveMomentumMoverResult> topRising =
        movers.stream()
            .filter(value -> value.rankDelta() > 0)
            .sorted(
                Comparator.comparingInt(ArchiveMomentumMoverResult::rankDelta)
                    .reversed()
                    .thenComparingInt(ArchiveMomentumMoverResult::rank))
            .limit(5)
            .toList();

    List<ArchiveMomentumMoverResult> topFalling =
        movers.stream()
            .filter(value -> value.rankDelta() < 0)
            .sorted(
                Comparator.comparingInt(ArchiveMomentumMoverResult::rankDelta)
                    .thenComparingInt(ArchiveMomentumMoverResult::rank))
            .limit(5)
            .toList();

    long totalVotes = candidates.stream().mapToLong(value -> value.metrics().votes()).sum();
    double averageElo =
        candidates.stream().mapToInt(value -> value.metrics().elo()).average().orElse(0.0D);
    double averageHype =
        candidates.stream().mapToDouble(value -> value.metrics().hype()).average().orElse(0.0D);

    Instant fromInclusive = tradingDay.atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant toExclusive = tradingDay.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    long todayArenaBattles =
        battleVoteJpaRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            fromInclusive, toExclusive);

    return new ArchiveInsightsResult(
        new ArchiveSummaryResult(
            candidates.size(),
            totalVotes,
            todayArenaBattles,
            averageElo,
            averageHype,
            tradingDay.toString()),
        new ArchiveMomentumResult(rising, unchanged, falling),
        topRising,
        topFalling);
  }

  @Transactional(readOnly = true)
  public ArchiveLeaderboardPage listArchiveLeaderboard(ArchiveLeaderboardQuery query) {
    ArchiveLeaderboardMetric metric = resolveArchiveLeaderboardMetric(query.metric());
    int resolvedLimit = resolveArchiveLimit(query.limit());
    int pageIndex = resolvePageIndex(query.cursor());

    List<ArchiveAggregateCandidate> candidates = loadActiveAggregateCandidates();
    if (candidates.isEmpty()) {
      return new ArchiveLeaderboardPage(metric.name(), List.of(), null, false, 0L);
    }

    List<RankedArchiveCandidate> rankedCandidates = rankArchiveCandidates(candidates, metric);
    Map<String, Integer> previousEloRanks = Map.of();
    if (metric == ArchiveLeaderboardMetric.ELO) {
      Set<String> specimenIds =
          candidates.stream().map(ArchiveAggregateCandidate::specimenId).collect(
              LinkedHashSet::new,
              LinkedHashSet::add,
              LinkedHashSet::addAll);
      previousEloRanks =
          resolvePreviousEloRankBySpecimenId(
              specimenIds, ArenaTradingDayResolver.currentTradingDay(Instant.now()));
    }

    List<ArchiveLeaderboardItemResult> items = new ArrayList<>(rankedCandidates.size());
    for (RankedArchiveCandidate ranked : rankedCandidates) {
      Integer rankDelta = null;
      if (metric == ArchiveLeaderboardMetric.ELO) {
        Integer previousRank = previousEloRanks.get(ranked.candidate().specimenId());
        if (previousRank != null) {
          rankDelta = previousRank - ranked.rank();
        }
      }
      items.add(
          new ArchiveLeaderboardItemResult(
              ranked.candidate().specimenId(),
              ranked.candidate().repoFullName(),
              ranked.rank(),
              rankDelta,
              ranked.score(),
              ranked.candidate().metrics()));
    }

    int startIndex = pageIndex * resolvedLimit;
    if (startIndex >= items.size()) {
      return new ArchiveLeaderboardPage(metric.name(), List.of(), null, false, items.size());
    }

    int endIndex = Math.min(items.size(), startIndex + resolvedLimit);
    List<ArchiveLeaderboardItemResult> paged = items.subList(startIndex, endIndex);
    boolean hasMore = endIndex < items.size();
    String nextCursor = hasMore ? String.valueOf(pageIndex + 1) : null;
    return new ArchiveLeaderboardPage(metric.name(), paged, nextCursor, hasMore, items.size());
  }

  @Transactional(readOnly = true)
  public DrawerResult getDrawer(String specimenId, String userId, String locale) {
    String normalizedLocale = normalizeLocale(locale);
    SpecimenJpaEntity specimen = requireActiveSpecimen(specimenId);
    SpecimenGithubMetadataJpaEntity metadata =
        specimenGithubMetadataJpaRepository.findById(specimenId).orElse(null);
    SpecimenArenaMetricsJpaEntity metrics = specimenArenaMetricsJpaRepository.findById(specimenId).orElse(null);
    SpecimenCommunityMetricsJpaEntity communityMetrics =
        specimenCommunityMetricsJpaRepository.findById(specimenId).orElse(null);

    SpecimenReadmeExcerptJpaEntity excerpt =
        specimenReadmeExcerptJpaRepository.findBySpecimenIdOrderByPriorityAsc(specimenId).stream()
            .findFirst()
            .orElse(null);

    List<SpecimenTagJpaEntity> tags = specimenTagJpaRepository.findBySpecimenId(specimenId);
    Map<String, String> tagDisplayNames =
        resolveTagDisplayNames(tags.stream().map(SpecimenTagJpaEntity::getTagKey).toList(), normalizedLocale);

    List<DrawerTagResult> drawerTags =
        tags.stream()
            .map(
                tag ->
                    new DrawerTagResult(
                        tag.getDimensionKey(),
                        tag.getTagKey(),
                        tagDisplayNames.getOrDefault(tag.getTagKey(), tag.getTagKey())))
            .toList();

    boolean canOpenInternalDetail =
        hasUserVotedForSpecimenInTradingDay(userId, specimenId, Instant.now());

    return new DrawerResult(
        new DrawerSpecimenResult(specimen.getSpecimenId(), specimen.getRepoFullName(), specimen.getGithubUrl()),
        toDrawerGithubMeta(metadata, specimen.getGithubUrl()),
        toDrawerMetrics(metrics),
        excerpt == null ? null : new DrawerReadmeExcerptResult(excerpt.getExcerptType(), excerpt.getText()),
        drawerTags,
        toTopRoastSummary(communityMetrics),
        canOpenInternalDetail,
        new DrawerGithubJumpWarningResult("⚠️ 警告：高辐射区域", "您即将进入 GitHub，请确认已穿戴防护服"));
  }

  @Transactional(readOnly = true)
  public DetailResult getDetail(String specimenId) {
    SpecimenJpaEntity specimen = requireActiveSpecimen(specimenId);
    SpecimenGithubMetadataJpaEntity metadata =
        specimenGithubMetadataJpaRepository.findById(specimenId).orElse(null);
    SpecimenArenaMetricsJpaEntity metrics = specimenArenaMetricsJpaRepository.findById(specimenId).orElse(null);
    SpecimenOfficialCommentaryJpaEntity commentary =
        specimenOfficialCommentaryJpaRepository.findById(specimenId).orElse(null);
    SpecimenCommunityMetricsJpaEntity communityMetrics =
        specimenCommunityMetricsJpaRepository.findById(specimenId).orElse(null);

    List<SpecimenReadmeExcerptJpaEntity> excerpts =
        specimenReadmeExcerptJpaRepository.findBySpecimenIdOrderByPriorityAsc(specimenId);
    List<SpecimenCodeHighlightJpaEntity> highlights =
        specimenCodeHighlightJpaRepository.findBySpecimenIdOrderByPriorityAsc(specimenId);
    List<SpecimenRepoIdentityJpaEntity> identities =
        specimenRepoIdentityJpaRepository.findBySpecimenIdAndActiveTrue(specimenId);
    List<SpecimenTagJpaEntity> tags = specimenTagJpaRepository.findBySpecimenId(specimenId);

    Map<String, String> tagDisplayNames =
        resolveTagDisplayNames(tags.stream().map(SpecimenTagJpaEntity::getTagKey).toList(), DEFAULT_LOCALE);

    DetailRepoIdentityUserResult owner =
        identities.stream()
            .filter(identity -> identity.getRole() == RepoIdentityRole.OWNER)
            .findFirst()
            .map(identity -> new DetailRepoIdentityUserResult(identity.getGithubLogin(), identity.getGithubUserId()))
            .orElse(null);

    List<DetailRepoIdentityUserResult> contributors =
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
            .map(identity -> new DetailRepoIdentityUserResult(identity.getGithubLogin(), identity.getGithubUserId()))
            .toList();

    return new DetailResult(
        new DetailSpecimenResult(specimen.getSpecimenId(), specimen.getRepoFullName(), specimen.getStatus().name()),
        toDetailGithubMeta(metadata, specimen.getGithubUrl()),
        new DetailMetricsResult(
            metrics == null ? 1200 : metrics.getElo(),
            metrics == null ? 0.0D : metrics.getHype(),
            metrics == null ? 0L : metrics.getVotes(),
            communityMetrics == null ? 0L : communityMetrics.getCommentCount()),
        new DetailReadmeResult(
            null,
            excerpts.stream()
                .map(value -> new DetailReadmeExcerptResult(value.getExcerptType(), value.getText()))
                .toList()),
        toDetailOfficialCommentary(commentary),
        highlights.stream()
            .map(
                value ->
                    new DetailCodeHighlightResult(
                        value.getTitle(), value.getCodeLanguage(), value.getSnippet(), value.getExplainText()))
            .toList(),
        new DetailRepoIdentityResult(owner, contributors),
        tags.stream()
            .map(
                value ->
                    new DetailTagResult(
                        value.getDimensionKey(),
                        value.getTagKey(),
                        tagDisplayNames.getOrDefault(value.getTagKey(), value.getTagKey())))
            .toList(),
        new DetailSeoMetaResult(
            specimen.getRepoFullName() + " - 病历详情",
            metadata == null ? null : metadata.getDescription()));
  }

  private SpecimenJpaEntity requireActiveSpecimen(String specimenId) {
    SpecimenJpaEntity specimen =
        specimenJpaRepository
            .findById(specimenId)
            .orElseThrow(() -> SpecimenExceptions.notFound(SpecimenConstants.Message.SPECIMEN_NOT_FOUND));
    if (specimen.getStatus() != SpecimenStatus.ACTIVE) {
      throw SpecimenExceptions.notFound(SpecimenConstants.Message.SPECIMEN_NOT_FOUND);
    }
    return specimen;
  }

  private ArchiveGithubMetaResult toArchiveGithubMeta(
      SpecimenGithubMetadataJpaEntity metadata, String fallbackRepoHtmlUrl) {
    return new ArchiveGithubMetaResult(
        metadata == null || !StringUtils.hasText(metadata.getRepoHtmlUrl())
            ? fallbackRepoHtmlUrl
            : metadata.getRepoHtmlUrl(),
        metadata == null ? null : metadata.getOwnerLogin(),
        metadata == null ? null : metadata.getOwnerAvatarUrl(),
        readLanguageItems(metadata == null ? null : metadata.getLanguagesJson()),
        metadata == null ? 0L : metadata.getStargazersCount(),
        metadata == null ? null : metadata.getPushedAt(),
        readStringList(metadata == null ? null : metadata.getTopicsJson()));
  }

  private ArchiveMetricsResult toArchiveMetrics(SpecimenArenaMetricsJpaEntity metrics) {
    return new ArchiveMetricsResult(
        metrics == null ? 1200 : metrics.getElo(),
        metrics == null ? 0.0D : metrics.getHype(),
        metrics == null ? 0L : metrics.getVotes(),
        metrics == null ? 0 : metrics.getDelta24h());
  }

  private DrawerGithubMetaResult toDrawerGithubMeta(
      SpecimenGithubMetadataJpaEntity metadata, String fallbackRepoHtmlUrl) {
    return new DrawerGithubMetaResult(
        metadata == null || !StringUtils.hasText(metadata.getRepoHtmlUrl())
            ? fallbackRepoHtmlUrl
            : metadata.getRepoHtmlUrl(),
        metadata == null ? null : metadata.getOwnerLogin(),
        metadata == null ? null : metadata.getOwnerAvatarUrl(),
        metadata == null ? null : metadata.getDescription(),
        readLanguageItems(metadata == null ? null : metadata.getLanguagesJson()),
        metadata == null ? 0L : metadata.getStargazersCount(),
        metadata == null ? null : metadata.getPushedAt(),
        readStringList(metadata == null ? null : metadata.getTopicsJson()),
        metadata == null ? null : metadata.getMetadataSyncedAt());
  }

  private DrawerMetricsResult toDrawerMetrics(SpecimenArenaMetricsJpaEntity metrics) {
    return new DrawerMetricsResult(
        metrics == null ? 1200 : metrics.getElo(),
        metrics == null ? 0.0D : metrics.getHype(),
        metrics == null ? 0L : metrics.getVotes());
  }

  private TopRoastSummary toTopRoastSummary(SpecimenCommunityMetricsJpaEntity metrics) {
    if (metrics == null || !StringUtils.hasText(metrics.getTopRoastCommentId())) {
      return null;
    }
    return new TopRoastSummary(
        metrics.getTopRoastCommentId(),
        metrics.getTopRoastResonanceCount(),
        metrics.isTopRoastChiefConclusion());
  }

  private DetailGithubMetaResult toDetailGithubMeta(
      SpecimenGithubMetadataJpaEntity metadata, String fallbackRepoHtmlUrl) {
    if (metadata == null) {
      return new DetailGithubMetaResult(
          null,
          fallbackRepoHtmlUrl,
          new DetailGithubOwnerResult(null, null, null, null),
          null,
          null,
          null,
          List.of(),
          List.of(),
          null,
          null,
          false,
          false,
          null,
          null,
          null,
          0L,
          0L,
          0L,
          null);
    }

    return new DetailGithubMetaResult(
        metadata.getRepoId(),
        StringUtils.hasText(metadata.getRepoHtmlUrl())
            ? metadata.getRepoHtmlUrl()
            : fallbackRepoHtmlUrl,
        new DetailGithubOwnerResult(
            metadata.getOwnerLogin(),
            metadata.getOwnerId(),
            metadata.getOwnerAvatarUrl(),
            metadata.getOwnerHtmlUrl()),
        metadata.getDescription(),
        metadata.getHomepage(),
        metadata.getDefaultBranch(),
        readDetailLanguages(metadata.getLanguagesJson()),
        readStringList(metadata.getTopicsJson()),
        StringUtils.hasText(metadata.getLicenseSpdxId()) || StringUtils.hasText(metadata.getLicenseName())
            ? new DetailGithubLicenseResult(metadata.getLicenseSpdxId(), metadata.getLicenseName())
            : null,
        metadata.getVisibility(),
        metadata.isArchived(),
        metadata.isFork(),
        metadata.getCreatedAt(),
        metadata.getUpdatedAtRemote(),
        metadata.getPushedAt(),
        metadata.getStargazersCount(),
        metadata.getForksCount(),
        metadata.getOpenIssuesCount(),
        metadata.getMetadataSyncedAt());
  }

  private DetailOfficialCommentaryResult toDetailOfficialCommentary(
      SpecimenOfficialCommentaryJpaEntity commentary) {
    if (commentary == null) {
      return new DetailOfficialCommentaryResult(Map.of(), Map.of());
    }
    Map<String, String> oneLiner = new LinkedHashMap<>();
    oneLiner.put("zh", commentary.getOneLinerZh());
    oneLiner.put("en", commentary.getOneLinerEn());
    Map<String, String> arenaReason = new LinkedHashMap<>();
    arenaReason.put("zh", commentary.getArenaReasonZh());
    arenaReason.put("en", commentary.getArenaReasonEn());
    return new DetailOfficialCommentaryResult(oneLiner, arenaReason);
  }

  private Map<String, List<SpecimenTagJpaEntity>> groupTagsBySpecimenId(Set<String> specimenIds) {
    Map<String, List<SpecimenTagJpaEntity>> grouped = new HashMap<>();
    for (String specimenId : specimenIds) {
      grouped.put(specimenId, specimenTagJpaRepository.findBySpecimenId(specimenId));
    }
    return grouped;
  }

  private Map<String, String> resolveTagDisplayNames(Collection<String> tagKeys, String locale) {
    if (tagKeys == null || tagKeys.isEmpty()) {
      return Map.of();
    }
    List<TagDefinitionJpaEntity> definitions =
        tagDefinitionJpaRepository.findByTagKeyInOrderByDisplayOrderAscTagKeyAsc(new LinkedHashSet<>(tagKeys));
    Map<String, String> names = new HashMap<>();
    for (TagDefinitionJpaEntity definition : definitions) {
      if ("en".equals(locale)) {
        names.put(definition.getTagKey(), definition.getNameEn());
      } else {
        names.put(definition.getTagKey(), definition.getNameZh());
      }
    }
    return names;
  }

  private boolean matchesTags(List<SpecimenTagJpaEntity> specimenTags, List<String> filterTags) {
    if (filterTags == null || filterTags.isEmpty()) {
      return true;
    }
    Set<String> assigned = specimenTags.stream().map(SpecimenTagJpaEntity::getTagKey).collect(
        LinkedHashSet::new,
        LinkedHashSet::add,
        LinkedHashSet::addAll);
    return assigned.containsAll(filterTags);
  }

  private List<LanguageItem> readLanguageItems(String languagesJson) {
    Object raw = specimenJsonCodec.readObject(languagesJson);
    if (!(raw instanceof List<?> values)) {
      return List.of();
    }
    List<LanguageItem> results = new ArrayList<>();
    for (Object value : values) {
      if (value instanceof Map<?, ?> map) {
        String name = map.get("name") == null ? null : String.valueOf(map.get("name"));
        Double percentage = toDouble(map.get("percentage"));
        if (StringUtils.hasText(name)) {
          results.add(new LanguageItem(name, percentage));
        }
      }
    }
    return results;
  }

  private List<DetailLanguageResult> readDetailLanguages(String languagesJson) {
    Object raw = specimenJsonCodec.readObject(languagesJson);
    if (!(raw instanceof List<?> values)) {
      return List.of();
    }
    List<DetailLanguageResult> results = new ArrayList<>();
    for (Object value : values) {
      if (value instanceof Map<?, ?> map) {
        String name = map.get("name") == null ? null : String.valueOf(map.get("name"));
        if (!StringUtils.hasText(name)) {
          continue;
        }
        Long bytes = toLong(map.get("bytes"));
        Double percentage = toDouble(map.get("percentage"));
        results.add(new DetailLanguageResult(name, bytes, percentage));
      }
    }
    return results;
  }

  private List<String> readStringList(String json) {
    Object raw = specimenJsonCodec.readObject(json);
    if (!(raw instanceof List<?> values)) {
      return List.of();
    }
    List<String> results = new ArrayList<>();
    for (Object value : values) {
      if (value != null) {
        results.add(String.valueOf(value));
      }
    }
    return results;
  }

  private String resolveOneLiner(SpecimenOfficialCommentaryJpaEntity commentary, String locale) {
    if (commentary == null) {
      return null;
    }
    if ("en".equals(locale)) {
      return StringUtils.hasText(commentary.getOneLinerEn())
          ? commentary.getOneLinerEn()
          : commentary.getOneLinerZh();
    }
    return StringUtils.hasText(commentary.getOneLinerZh())
        ? commentary.getOneLinerZh()
        : commentary.getOneLinerEn();
  }

  private List<ArchiveAggregateCandidate> loadActiveAggregateCandidates() {
    List<SpecimenJpaEntity> activeSpecimens = specimenJpaRepository.findByStatus(SpecimenStatus.ACTIVE);
    if (activeSpecimens.isEmpty()) {
      return List.of();
    }

    Set<String> specimenIds =
        activeSpecimens.stream().map(SpecimenJpaEntity::getSpecimenId).collect(
            LinkedHashSet::new,
            LinkedHashSet::add,
            LinkedHashSet::addAll);

    Map<String, SpecimenArenaMetricsJpaEntity> metricsBySpecimenId =
        specimenArenaMetricsJpaRepository.findAllById(specimenIds).stream()
            .collect(
                LinkedHashMap::new,
                (map, value) -> map.put(value.getSpecimenId(), value),
                LinkedHashMap::putAll);

    List<ArchiveAggregateCandidate> candidates = new ArrayList<>(activeSpecimens.size());
    for (SpecimenJpaEntity specimen : activeSpecimens) {
      candidates.add(
          new ArchiveAggregateCandidate(
              specimen.getSpecimenId(),
              specimen.getRepoFullName(),
              toArchiveMetrics(metricsBySpecimenId.get(specimen.getSpecimenId()))));
    }
    return candidates;
  }

  private List<RankedArchiveCandidate> rankArchiveCandidates(
      List<ArchiveAggregateCandidate> candidates, ArchiveLeaderboardMetric metric) {
    Comparator<ArchiveAggregateCandidate> comparator;
    if (metric == ArchiveLeaderboardMetric.ELO) {
      comparator =
          Comparator.comparing(
                  (ArchiveAggregateCandidate value) -> value.metrics().elo(),
                  Comparator.reverseOrder())
              .thenComparing(
                  (ArchiveAggregateCandidate value) -> value.metrics().hype(),
                  Comparator.reverseOrder())
              .thenComparing(
                  (ArchiveAggregateCandidate value) -> value.metrics().votes(),
                  Comparator.reverseOrder())
              .thenComparing(ArchiveAggregateCandidate::specimenId);
    } else {
      comparator =
          Comparator.comparing(
                  (ArchiveAggregateCandidate value) -> value.metrics().hype(),
                  Comparator.reverseOrder())
              .thenComparing(
                  (ArchiveAggregateCandidate value) -> value.metrics().votes(),
                  Comparator.reverseOrder())
              .thenComparing(
                  (ArchiveAggregateCandidate value) -> value.metrics().elo(),
                  Comparator.reverseOrder())
              .thenComparing(ArchiveAggregateCandidate::specimenId);
    }

    List<ArchiveAggregateCandidate> sorted = new ArrayList<>(candidates);
    sorted.sort(comparator);

    List<RankedArchiveCandidate> ranked = new ArrayList<>(sorted.size());
    Double previousScore = null;
    int previousRank = 0;
    for (int index = 0; index < sorted.size(); index += 1) {
      ArchiveAggregateCandidate candidate = sorted.get(index);
      double currentScore = resolveLeaderboardScore(candidate, metric);
      int rank =
          previousScore != null && Double.compare(previousScore, currentScore) == 0
              ? previousRank
              : index + 1;
      ranked.add(new RankedArchiveCandidate(candidate, rank, currentScore));
      previousScore = currentScore;
      previousRank = rank;
    }
    return ranked;
  }

  private Map<String, Integer> resolvePreviousEloRankBySpecimenId(
      Set<String> specimenIds, LocalDate tradingDay) {
    if (specimenIds.isEmpty()) {
      return Map.of();
    }

    LocalDate previousTradingDay = tradingDay.minusDays(1);
    List<EloDailySnapshotJpaEntity> snapshots =
        eloDailySnapshotJpaRepository.findAllBySpecimenIdInAndDateBetween(
            specimenIds, previousTradingDay, previousTradingDay);
    if (snapshots.isEmpty()) {
      return Map.of();
    }

    snapshots.sort(
        Comparator.comparingInt(EloDailySnapshotJpaEntity::getEloClose)
            .reversed()
            .thenComparing(EloDailySnapshotJpaEntity::getSpecimenId));

    Map<String, Integer> rankBySpecimenId = new HashMap<>();
    Integer previousScore = null;
    int previousRank = 0;
    for (int index = 0; index < snapshots.size(); index += 1) {
      EloDailySnapshotJpaEntity snapshot = snapshots.get(index);
      int currentScore = snapshot.getEloClose();
      int rank =
          previousScore != null && previousScore == currentScore ? previousRank : index + 1;
      rankBySpecimenId.put(snapshot.getSpecimenId(), rank);
      previousScore = currentScore;
      previousRank = rank;
    }
    return rankBySpecimenId;
  }

  private ArchiveLeaderboardMetric resolveArchiveLeaderboardMetric(String metric) {
    if (!StringUtils.hasText(metric)) {
      return ArchiveLeaderboardMetric.ELO;
    }
    try {
      return ArchiveLeaderboardMetric.valueOf(metric.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw SpecimenExceptions.validation(SpecimenConstants.Message.INVALID_METRIC);
    }
  }

  private double resolveLeaderboardScore(
      ArchiveAggregateCandidate candidate, ArchiveLeaderboardMetric metric) {
    return metric == ArchiveLeaderboardMetric.ELO
        ? candidate.metrics().elo()
        : candidate.metrics().hype();
  }

  private ArchiveSort resolveArchiveSort(String sort) {
    if (!StringUtils.hasText(sort)) {
      return ArchiveSort.HOT;
    }
    try {
      return ArchiveSort.valueOf(sort.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw SpecimenExceptions.validation(SpecimenConstants.Message.INVALID_SORT);
    }
  }

  private void sortArchive(List<ArchiveItemResult> items, ArchiveSort sort) {
    Comparator<ArchiveItemResult> comparator;
    if (sort == ArchiveSort.NEW) {
      comparator = Comparator.comparing(
          (ArchiveItemResult value) -> value.githubMeta() == null ? null : value.githubMeta().pushedAt(),
          Comparator.nullsLast(Comparator.reverseOrder()));
    } else if (sort == ArchiveSort.INSANE) {
      comparator = Comparator.comparing(
          (ArchiveItemResult value) -> value.metrics() == null ? 0.0D : value.metrics().hype(),
          Comparator.reverseOrder());
    } else {
      comparator = Comparator.comparing(
              (ArchiveItemResult value) -> value.metrics() == null ? 0.0D : value.metrics().hype(),
              Comparator.reverseOrder())
          .thenComparing(
              (ArchiveItemResult value) -> value.metrics() == null ? 0L : value.metrics().votes(),
              Comparator.reverseOrder());
    }
    items.sort(comparator.thenComparing(ArchiveItemResult::specimenId));
  }

  private boolean hasUserVotedForSpecimenInTradingDay(
      String userId, String specimenId, Instant now) {
    if (!StringUtils.hasText(userId) || !StringUtils.hasText(specimenId)) {
      return false;
    }

    LocalDate tradingDay = ArenaTradingDayResolver.currentTradingDay(now);
    Instant fromInclusive = ArenaTradingDayResolver.tradingDayStart(tradingDay);
    Instant toExclusive = ArenaTradingDayResolver.nextTradingDayStart(tradingDay);
    return battleVoteJpaRepository.countUserVotesForSpecimenBetween(
            userId.trim(), specimenId.trim(), fromInclusive, toExclusive)
        > 0L;
  }

  private int resolveArchiveLimit(Integer limit) {
    int resolved =
        limit == null || limit <= 0 ? specimenContractProperties.getWatchlistDefaultLimit() : limit;
    return Math.min(resolved, specimenContractProperties.getWatchlistMaxLimit());
  }

  private int resolvePageIndex(String cursor) {
    if (!StringUtils.hasText(cursor)) {
      return 0;
    }
    try {
      int pageIndex = Integer.parseInt(cursor);
      if (pageIndex < 0) {
        throw new NumberFormatException("negative");
      }
      return pageIndex;
    } catch (NumberFormatException ex) {
      throw SpecimenExceptions.validation(SpecimenConstants.Message.INVALID_CURSOR);
    }
  }

  private String normalizeLocale(String locale) {
    if (!StringUtils.hasText(locale)) {
      return DEFAULT_LOCALE;
    }
    String normalized = locale.trim().toLowerCase(Locale.ROOT);
    return normalized.startsWith("en") ? "en" : DEFAULT_LOCALE;
  }

  private Double toDouble(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    try {
      return Double.parseDouble(String.valueOf(value));
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private Long toLong(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private enum ArchiveSort {
    HOT,
    NEW,
    INSANE
  }

  private enum ArchiveLeaderboardMetric {
    ELO,
    HYPE
  }

  private record ArchiveAggregateCandidate(
      String specimenId, String repoFullName, ArchiveMetricsResult metrics) {}

  private record RankedArchiveCandidate(ArchiveAggregateCandidate candidate, int rank, double score) {}
}
