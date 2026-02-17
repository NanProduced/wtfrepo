package com.wtfrepo.backend.arena.application;

import com.wtfrepo.backend.arena.application.economy.ArenaEconomyPort;
import com.wtfrepo.backend.arena.application.economy.ArenaPolicyPort;
import com.wtfrepo.backend.arena.application.policy.ArenaPolicySnapshot;
import com.wtfrepo.backend.arena.application.support.ArenaBattleIdVerifier;
import com.wtfrepo.backend.arena.application.support.ArenaConstants;
import com.wtfrepo.backend.arena.application.support.ArenaExceptions;
import com.wtfrepo.backend.arena.application.support.ArenaMatchProperties;
import com.wtfrepo.backend.arena.domain.ArenaMatchType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Arena duel matching service (GET /arena/duel). */
@Service
public class ArenaDuelService {

  private static final Logger log = LoggerFactory.getLogger(ArenaDuelService.class);

  private final ArenaSpecimenMatchReadModel specimenMatchReadModel;
  private final ArenaSpecimenMatchPairReadModel specimenMatchPairReadModel;
  private final ArenaSpecimenRatingStore arenaSpecimenRatingStore;
  private final ArenaBattleIdVerifier arenaBattleIdVerifier;
  private final ArenaPolicyPort arenaPolicyPort;
  private final ArenaEconomyPort arenaEconomyPort;
  private final ArenaRateLimiter arenaRateLimiter;
  private final ArenaFeaturedDuelStore arenaFeaturedDuelStore;
  private final ArenaMatchProperties arenaMatchProperties;

  public ArenaDuelService(
      ArenaSpecimenMatchReadModel specimenMatchReadModel,
      ArenaSpecimenMatchPairReadModel specimenMatchPairReadModel,
      ArenaSpecimenRatingStore arenaSpecimenRatingStore,
      ArenaBattleIdVerifier arenaBattleIdVerifier,
      ArenaPolicyPort arenaPolicyPort,
      ArenaEconomyPort arenaEconomyPort,
      ArenaRateLimiter arenaRateLimiter,
      ArenaFeaturedDuelStore arenaFeaturedDuelStore,
      ArenaMatchProperties arenaMatchProperties) {
    this.specimenMatchReadModel = specimenMatchReadModel;
    this.specimenMatchPairReadModel = specimenMatchPairReadModel;
    this.arenaSpecimenRatingStore = arenaSpecimenRatingStore;
    this.arenaBattleIdVerifier = arenaBattleIdVerifier;
    this.arenaPolicyPort = arenaPolicyPort;
    this.arenaEconomyPort = arenaEconomyPort;
    this.arenaRateLimiter = arenaRateLimiter;
    this.arenaFeaturedDuelStore = arenaFeaturedDuelStore;
    this.arenaMatchProperties = arenaMatchProperties;
  }

  @Transactional(readOnly = true)
  public DuelResult duel(String requestId, DuelQuery query) {
    if (!StringUtils.hasText(query.userId())) {
      return duelForAnonymous(requestId, normalizeClientIp(query.clientIp()));
    }
    return duelForAuthenticated(requestId, query);
  }

  private DuelResult duelForAnonymous(String requestId, String clientIp) {
    if (!arenaRateLimiter.allowAnonymousDuel(clientIp)) {
      throw ArenaExceptions.rateLimited(ArenaConstants.Message.TOO_MANY_DUEL_REQUESTS);
    }

    Optional<DuelResult> cached = arenaFeaturedDuelStore.findFeaturedDuel();
    if (cached.isPresent()) {
      log.info("arena_duel_featured_cache_hit requestId={} clientIp={}", requestId, clientIp);
      return cached.get();
    }

    // Contract requires guest traffic to consume featured duel cache instead of running full
    // personalized filtering logic. We therefore ignore client-provided excludes here and publish
    // one shared duel payload with a short cache TTL.
    DuelResult generated =
        duelWithMatchingEngine(
            requestId, new DuelQuery(Set.of(), Set.of(), null, clientIp));
    arenaFeaturedDuelStore.saveFeaturedDuel(generated);
    log.info("arena_duel_featured_cache_miss requestId={} clientIp={}", requestId, clientIp);
    return generated;
  }

  private DuelResult duelForAuthenticated(String requestId, DuelQuery query) {
    String normalizedUserId = query.userId().trim();
    String normalizedClientIp = normalizeClientIp(query.clientIp());

    if (!arenaRateLimiter.allowAuthenticatedDuelByUser(normalizedUserId)
        || !arenaRateLimiter.allowAuthenticatedDuelByIp(normalizedClientIp)) {
      throw ArenaExceptions.rateLimited(ArenaConstants.Message.TOO_MANY_DUEL_REQUESTS);
    }

    return duelWithMatchingEngine(
        requestId,
        new DuelQuery(
            query.excludeSpecimenIds(), query.excludeCombinations(), normalizedUserId, normalizedClientIp));
  }

  private DuelResult duelWithMatchingEngine(String requestId, DuelQuery query) {
    List<ResolvedSpecimen> resolvedSpecimens = resolveSpecimensWithRatings();
    if (resolvedSpecimens.size() < 2) {
      throw ArenaExceptions.arenaPoolEmpty(ArenaConstants.Message.ARENA_POOL_EMPTY);
    }

    List<MatchCandidate> allCandidates = resolveCandidates(resolvedSpecimens);
    if (allCandidates.isEmpty()) {
      throw ArenaExceptions.arenaNoMatch(ArenaConstants.Message.ARENA_NO_MATCH);
    }

    Set<String> excludeSpecimenIds = normalizeSpecimenIds(query.excludeSpecimenIds());
    Set<String> excludeCombinations = normalizeCombinations(query.excludeCombinations());

    List<MatchCandidate> filteredCandidates =
        allCandidates.stream()
            .filter(candidate -> !isExcluded(candidate, excludeSpecimenIds, excludeCombinations))
            .toList();

    boolean shouldResetExcludeSet = filteredCandidates.size() < arenaMatchProperties.getResetExcludeThreshold();
    if (shouldResetExcludeSet) {
      filteredCandidates = allCandidates;
    }

    if (filteredCandidates.isEmpty()) {
      throw ArenaExceptions.arenaNoMatch(ArenaConstants.Message.ARENA_NO_MATCH);
    }

    MatchCandidate selected = selectWeighted(filteredCandidates);
    String battleId =
        arenaBattleIdVerifier.issue(
            selected.left().candidate().specimenId(),
            selected.right().candidate().specimenId(),
            selected.matchType());

    ArenaPolicySnapshot policySnapshot = arenaPolicyPort.currentPolicySnapshot();
    DuelWallet wallet =
        StringUtils.hasText(query.userId())
            ? new DuelWallet(arenaEconomyPort.currentBalance(query.userId()), policySnapshot.bugCost())
            : null;

    DuelResult result =
        new DuelResult(
            battleId,
            toDuelSpecimen(selected.left()),
            toDuelSpecimen(selected.right()),
            new DuelMatchMeta(
                selected.matchType().name(),
                selected.matchProfileVersion(),
                selected.left().isInIpoProtection() || selected.right().isInIpoProtection()),
            shouldResetExcludeSet,
            wallet);

    log.info(
        "arena_duel_success requestId={} battleId={} matchType={} shouldResetExcludeSet={} voterAuthenticated={}",
        requestId,
        battleId,
        selected.matchType(),
        shouldResetExcludeSet,
        StringUtils.hasText(query.userId()));
    return result;
  }

  private String normalizeClientIp(String rawClientIp) {
    if (!StringUtils.hasText(rawClientIp)) {
      return "unknown";
    }
    return rawClientIp.trim();
  }

  private List<ResolvedSpecimen> resolveSpecimensWithRatings() {
    return specimenMatchReadModel.listActiveCandidates().stream()
        .map(this::toResolvedSpecimen)
        .filter(java.util.Objects::nonNull)
        .toList();
  }

  private ResolvedSpecimen toResolvedSpecimen(ArenaSpecimenMatchReadModel.SpecimenMatchCandidate candidate) {
    return arenaSpecimenRatingStore
        .find(candidate.specimenId())
        .map(rating -> new ResolvedSpecimen(candidate, rating))
        .orElse(null);
  }

  private List<MatchCandidate> resolveCandidates(List<ResolvedSpecimen> resolvedSpecimens) {
    if (arenaMatchProperties.isPairFirstEnabled()) {
      List<MatchCandidate> precomputedCandidates = buildPrecomputedPairCandidates(resolvedSpecimens);
      if (!precomputedCandidates.isEmpty()) {
        return precomputedCandidates;
      }
      if (!arenaMatchProperties.isRuntimePairFallbackEnabled()) {
        log.warn(
            "arena_duel_pair_source_exhausted pairFirstEnabled=true runtimeFallbackEnabled=false reason=no_precomputed_rows");
        return List.of();
      }
      log.warn(
          "arena_duel_pair_source_fallback reason=no_precomputed_rows runtimeFallbackEnabled=true");
    }
    return buildRuntimeCandidates(resolvedSpecimens);
  }

  private List<MatchCandidate> buildPrecomputedPairCandidates(List<ResolvedSpecimen> resolvedSpecimens) {
    List<ArenaSpecimenMatchPairReadModel.SpecimenMatchPair> precomputedPairs =
        specimenMatchPairReadModel.listActivePairs();
    if (precomputedPairs.isEmpty()) {
      return List.of();
    }

    Map<String, ResolvedSpecimen> resolvedBySpecimenId = new LinkedHashMap<>();
    for (ResolvedSpecimen resolvedSpecimen : resolvedSpecimens) {
      resolvedBySpecimenId.put(resolvedSpecimen.candidate().specimenId(), resolvedSpecimen);
    }

    List<MatchCandidate> candidates = new ArrayList<>();
    for (ArenaSpecimenMatchPairReadModel.SpecimenMatchPair pair : precomputedPairs) {
      ResolvedSpecimen left = resolvedBySpecimenId.get(pair.leftSpecimenId());
      ResolvedSpecimen right = resolvedBySpecimenId.get(pair.rightSpecimenId());
      if (left == null || right == null) {
        continue;
      }

      double cooldownWeight = Math.min(cooldownWeight(left), cooldownWeight(right));
      int matchScore = Math.max(0, pair.matchScore());
      double finalScore = Math.max(1.0D, matchScore * cooldownWeight);
      candidates.add(
          new MatchCandidate(
              left,
              right,
              pair.matchType(),
              finalScore,
              resolveMatchProfileVersion(pair.matchProfileVersion())));
    }
    return candidates;
  }

  private List<MatchCandidate> buildRuntimeCandidates(List<ResolvedSpecimen> resolvedSpecimens) {
    List<MatchCandidate> result = new ArrayList<>();
    Set<String> adjacency = normalizeAdjacentPairs(arenaMatchProperties.getAdjacentSpeciesPairs());
    for (int i = 0; i < resolvedSpecimens.size() - 1; i++) {
      for (int j = i + 1; j < resolvedSpecimens.size(); j++) {
        ResolvedSpecimen left = resolvedSpecimens.get(i);
        ResolvedSpecimen right = resolvedSpecimens.get(j);
        ArenaMatchType matchType = resolveMatchType(left.candidate().species(), right.candidate().species(), adjacency);
        int matchScore = speciesScore(matchType) + diagnosisBonus(left, right);
        double cooldownWeight = Math.min(cooldownWeight(left), cooldownWeight(right));
        double finalScore = Math.max(1.0D, matchScore * cooldownWeight);
        result.add(
            new MatchCandidate(
                left, right, matchType, finalScore, resolveMatchProfileVersion(null)));
      }
    }
    return result;
  }

  private int speciesScore(ArenaMatchType matchType) {
    return switch (matchType) {
      case SAME_SPECIES -> arenaMatchProperties.getSameSpeciesScore();
      case ADJACENT -> arenaMatchProperties.getAdjacentSpeciesScore();
      case CROSS -> arenaMatchProperties.getCrossSpeciesScore();
    };
  }

  private int diagnosisBonus(ResolvedSpecimen left, ResolvedSpecimen right) {
    Set<String> leftTags = new HashSet<>(left.candidate().diagnosisTags());
    leftTags.retainAll(right.candidate().diagnosisTags());
    int sharedCount = leftTags.size();
    int bonus = sharedCount * arenaMatchProperties.getDiagnosisBaseScore();
    return Math.min(arenaMatchProperties.getDiagnosisBonusCap(), bonus);
  }

  private double cooldownWeight(ResolvedSpecimen specimen) {
    // Contract formula: weight = 1 / (1 + recent_appearances * alpha).
    return computeCooldownWeight(
        specimen.rating().recentAppearances(), arenaMatchProperties.getCooldownAlpha());
  }

  static double computeCooldownWeight(int recentAppearances, double cooldownAlpha) {
    int safeRecentAppearances = Math.max(0, recentAppearances);
    double safeCooldownAlpha = cooldownAlpha;
    if (!Double.isFinite(safeCooldownAlpha) || safeCooldownAlpha < 0.0D) {
      safeCooldownAlpha = 0.0D;
    }
    return 1.0D / (1.0D + safeRecentAppearances * safeCooldownAlpha);
  }

  private ArenaMatchType resolveMatchType(String leftSpecies, String rightSpecies, Set<String> adjacency) {
    if (leftSpecies.equalsIgnoreCase(rightSpecies)) {
      return ArenaMatchType.SAME_SPECIES;
    }
    if (adjacency.contains(canonicalPair(leftSpecies, rightSpecies, ":"))) {
      return ArenaMatchType.ADJACENT;
    }
    return ArenaMatchType.CROSS;
  }

  private boolean isExcluded(
      MatchCandidate candidate, Set<String> excludeSpecimenIds, Set<String> excludeCombinations) {
    String leftId = candidate.left().candidate().specimenId();
    String rightId = candidate.right().candidate().specimenId();
    if (excludeSpecimenIds.contains(leftId) || excludeSpecimenIds.contains(rightId)) {
      return true;
    }
    return excludeCombinations.contains(canonicalPair(leftId, rightId, ":"));
  }

  private MatchCandidate selectWeighted(List<MatchCandidate> candidates) {
    double total = candidates.stream().mapToDouble(MatchCandidate::finalScore).sum();
    if (total <= 0.0D) {
      return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    double pick = ThreadLocalRandom.current().nextDouble(total);
    double cursor = 0.0D;
    for (MatchCandidate candidate : candidates) {
      cursor += candidate.finalScore();
      if (pick <= cursor) {
        return candidate;
      }
    }
    return candidates.get(candidates.size() - 1);
  }

  private Set<String> normalizeSpecimenIds(Set<String> rawValues) {
    if (rawValues == null || rawValues.isEmpty()) {
      return Set.of();
    }
    return rawValues.stream()
        .filter(StringUtils::hasText)
        .map(String::trim)
        .collect(
            LinkedHashSet::new,
            LinkedHashSet::add,
            LinkedHashSet::addAll);
  }

  private Set<String> normalizeCombinations(Set<String> rawValues) {
    if (rawValues == null || rawValues.isEmpty()) {
      return Set.of();
    }
    Set<String> normalized = new LinkedHashSet<>();
    for (String raw : rawValues) {
      if (!StringUtils.hasText(raw) || !raw.contains(":")) {
        continue;
      }
      String[] parts = raw.split(":");
      if (parts.length != 2) {
        continue;
      }
      String left = parts[0].trim();
      String right = parts[1].trim();
      if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
        continue;
      }
      normalized.add(canonicalPair(left, right, ":"));
    }
    return normalized;
  }

  private Set<String> normalizeAdjacentPairs(List<String> rawPairs) {
    if (rawPairs == null || rawPairs.isEmpty()) {
      return Set.of();
    }
    Set<String> normalized = new LinkedHashSet<>();
    for (String raw : rawPairs) {
      if (!StringUtils.hasText(raw) || !raw.contains(":")) {
        continue;
      }
      String[] parts = raw.split(":");
      if (parts.length != 2) {
        continue;
      }
      String left = parts[0].trim();
      String right = parts[1].trim();
      if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
        continue;
      }
      normalized.add(canonicalPair(left, right, ":"));
    }
    return normalized;
  }

  private String canonicalPair(String left, String right, String separator) {
    return left.compareTo(right) <= 0 ? left + separator + right : right + separator + left;
  }

  private String resolveMatchProfileVersion(String pairProfileVersion) {
    if (StringUtils.hasText(pairProfileVersion)) {
      return pairProfileVersion.trim();
    }
    if (StringUtils.hasText(arenaMatchProperties.getProfileVersion())) {
      return arenaMatchProperties.getProfileVersion().trim();
    }
    return "unknown";
  }

  private DuelSpecimen toDuelSpecimen(ResolvedSpecimen specimen) {
    ArenaSpecimenMatchReadModel.SpecimenMatchCandidate candidate = specimen.candidate();
    return new DuelSpecimen(
        candidate.specimenId(),
        candidate.title(),
        candidate.tagline(),
        candidate.species(),
        candidate.diagnosisTags(),
        specimen.rating().eloScore(),
        specimen.rating().matchesPlayed(),
        specimen.isInIpoProtection() ? "PRIVATE_BETA" : "IPO",
        candidate.thumbnailUrl());
  }

  public record DuelQuery(
      Set<String> excludeSpecimenIds,
      Set<String> excludeCombinations,
      String userId,
      String clientIp) {}

  public record DuelResult(
      String battleId,
      DuelSpecimen left,
      DuelSpecimen right,
      DuelMatchMeta matchMeta,
      boolean shouldResetExcludeSet,
      DuelWallet wallet) {}

  public record DuelSpecimen(
      String specimenId,
      String title,
      String tagline,
      String species,
      List<String> diagnosisTags,
      int elo,
      long matchesPlayed,
      String ipoStatus,
      String thumbnailUrl) {}

  public record DuelMatchMeta(String matchType, String matchProfileVersion, boolean isIpoMatch) {}

  public record DuelWallet(long balance, int voteCost) {}

  private record ResolvedSpecimen(
      ArenaSpecimenMatchReadModel.SpecimenMatchCandidate candidate, ArenaSpecimenRating rating) {

    boolean isInIpoProtection() {
      return rating.matchesPlayed() < 10;
    }
  }

  private record MatchCandidate(
      ResolvedSpecimen left,
      ResolvedSpecimen right,
      ArenaMatchType matchType,
      double finalScore,
      String matchProfileVersion) {}
}
