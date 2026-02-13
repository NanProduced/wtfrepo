package com.wtfrepo.backend.arena.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wtfrepo.backend.arena.application.economy.ArenaEconomyPort;
import com.wtfrepo.backend.arena.application.economy.ArenaEconomyProperties;
import com.wtfrepo.backend.arena.application.economy.PropertyBackedArenaPolicyPort;
import com.wtfrepo.backend.arena.application.policy.ArenaPolicySnapshot;
import com.wtfrepo.backend.arena.application.support.ArenaBattleIdVerifier;
import com.wtfrepo.backend.arena.application.support.ArenaConstants;
import com.wtfrepo.backend.arena.application.support.ArenaContractProperties;
import com.wtfrepo.backend.arena.application.support.ArenaMatchProperties;
import com.wtfrepo.backend.arena.infra.support.InMemoryArenaVoteIdempotencyStore;
import com.wtfrepo.backend.shared.web.ApiException;
import com.wtfrepo.backend.shared.web.ErrorCode;
import com.wtfrepo.backend.specimen.application.SpecimenContractProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ArenaDuelServiceTest {

  private static final String SECRET = "arena-duel-test-secret-0123456789";

  private ArenaDuelService arenaDuelService;
  private InMemoryRatingStore ratingStore;
  private InMemorySpecimenMatchReadModel readModel;
  private RecordingEconomyPort economyPort;
  private ArenaBattleIdVerifier battleIdVerifier;
  private ArenaMatchProperties arenaMatchProperties;
  private AlwaysAllowRateLimiter rateLimiter;
  private PropertyBackedArenaPolicyPort policyPort;

  @BeforeEach
  void setUp() {
    ArenaContractProperties contractProperties = new ArenaContractProperties();
    contractProperties.setBattleIdSecret(SECRET);

    ArenaEconomyProperties economyProperties = new ArenaEconomyProperties();
    economyProperties.setVoteBugCost(100);
    economyProperties.setMinBet(100);
    economyProperties.setPolicyVersion("arena-policy-test-v1");
    economyProperties.setPolicySource("property");

    SpecimenContractProperties specimenContractProperties = new SpecimenContractProperties();
    specimenContractProperties.setDefaultElo(1500);
    policyPort =
        new PropertyBackedArenaPolicyPort(
            economyProperties, contractProperties, specimenContractProperties);
    battleIdVerifier = new ArenaBattleIdVerifier(contractProperties, policyPort);

    arenaMatchProperties = new ArenaMatchProperties();
    arenaMatchProperties.setResetExcludeThreshold(2);

    readModel = new InMemorySpecimenMatchReadModel();
    readModel.seed(
        new ArenaSpecimenMatchReadModel.SpecimenMatchCandidate(
            "spm_1",
            "owner/repo-one",
            "repo one",
            "https://example.com/a.png",
            "species_a",
            List.of("diag_x", "diag_y")));
    readModel.seed(
        new ArenaSpecimenMatchReadModel.SpecimenMatchCandidate(
            "spm_2",
            "owner/repo-two",
            "repo two",
            "https://example.com/b.png",
            "species_a",
            List.of("diag_x")));
    readModel.seed(
        new ArenaSpecimenMatchReadModel.SpecimenMatchCandidate(
            "spm_3",
            "owner/repo-three",
            "repo three",
            "https://example.com/c.png",
            "species_b",
            List.of("diag_z")));
    readModel.seed(
        new ArenaSpecimenMatchReadModel.SpecimenMatchCandidate(
            "spm_4",
            "owner/repo-four",
            "repo four",
            "https://example.com/d.png",
            "species_c",
            List.of("diag_q")));

    ratingStore = new InMemoryRatingStore();
    ratingStore.seed("spm_1", 1500, 0);
    ratingStore.seed("spm_2", 1490, 12);
    ratingStore.seed("spm_3", 1510, 20);
    ratingStore.seed("spm_4", 1480, 35);

    economyPort = new RecordingEconomyPort();
    economyPort.balance = 2500L;
    rateLimiter = new AlwaysAllowRateLimiter();

    arenaDuelService =
        new ArenaDuelService(
            readModel,
            ratingStore,
            battleIdVerifier,
            policyPort,
            economyPort,
            rateLimiter,
            new InMemoryFeaturedDuelStore(),
            arenaMatchProperties);
  }

  @Test
  void shouldReturnDuelAndWalletWhenAuthenticated() {
    ArenaDuelService.DuelResult result =
        arenaDuelService.duel(
            "req-1", new ArenaDuelService.DuelQuery(Set.of(), Set.of(), "usr_1", "127.0.0.1"));

    assertThat(result.battleId()).startsWith(ArenaConstants.Battle.ID_PREFIX);
    assertThat(result.wallet()).isNotNull();
    assertThat(result.wallet().balance()).isEqualTo(2500L);
    assertThat(result.wallet().voteCost()).isEqualTo(100);
    assertThat(result.left().specimenId()).isNotEqualTo(result.right().specimenId());
    assertThat(result.shouldResetExcludeSet()).isFalse();
  }

  @Test
  void shouldResetExcludeSetWhenCandidatesNearlyExhausted() {
    arenaMatchProperties.setResetExcludeThreshold(4);

    ArenaDuelService.DuelResult result =
        arenaDuelService.duel(
            "req-2",
            new ArenaDuelService.DuelQuery(
                Set.of("spm_1", "spm_2", "spm_3"),
                Set.of("spm_1:spm_4", "spm_2:spm_4"),
                "usr_2",
                "127.0.0.1"));

    assertThat(result.shouldResetExcludeSet()).isTrue();
    assertThat(result.wallet()).isNotNull();
  }

  @Test
  void shouldReuseFeaturedDuelForAnonymousRequests() {
    ArenaDuelService.DuelResult first =
        arenaDuelService.duel(
            "req-guest-1", new ArenaDuelService.DuelQuery(Set.of(), Set.of(), null, "8.8.8.8"));
    ArenaDuelService.DuelResult second =
        arenaDuelService.duel(
            "req-guest-2", new ArenaDuelService.DuelQuery(Set.of(), Set.of(), null, "8.8.8.8"));

    assertThat(first.battleId()).isEqualTo(second.battleId());
    assertThat(first.wallet()).isNull();
    assertThat(second.wallet()).isNull();
  }

  @Test
  void shouldRateLimitAnonymousDuelRequests() {
    ArenaDuelService duelService =
        new ArenaDuelService(
            readModel,
            ratingStore,
            battleIdVerifier,
            policyPort,
            economyPort,
            new BlockAnonymousRateLimiter(),
            new InMemoryFeaturedDuelStore(),
            arenaMatchProperties);

    assertThatThrownBy(
            () ->
                duelService.duel(
                    "req-rate-limit",
                    new ArenaDuelService.DuelQuery(Set.of(), Set.of(), null, "9.9.9.9")))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).getErrorCode())
        .isEqualTo(ErrorCode.RATE_LIMITED);
  }

  @Test
  void shouldGenerateBattleIdVerifiableByVoteService() {
    ArenaDuelService.DuelResult duelResult =
        arenaDuelService.duel(
            "req-3", new ArenaDuelService.DuelQuery(Set.of(), Set.of(), "usr_vote", "127.0.0.1"));

    ArenaVoteService voteService =
        new ArenaVoteService(
            new InMemoryArenaVoteIdempotencyStore(),
            battleIdVerifier,
            ratingStore,
            policyPort,
            economyPort,
            policyPort,
            arenaMatchProperties);

    ArenaVoteService.VoteResult voteResult =
        voteService.vote(
            "req-3",
            "usr_vote",
            duelResult.battleId() + "_usr_vote",
            new ArenaVoteService.VoteCommand(duelResult.battleId(), "LEFT"));

    assertThat(voteResult.battleId()).isEqualTo(duelResult.battleId());
  }

  @Test
  void shouldThrowArenaPoolEmptyWhenInsufficientCandidates() {
    InMemorySpecimenMatchReadModel oneCandidateReadModel = new InMemorySpecimenMatchReadModel();
    oneCandidateReadModel.seed(
        new ArenaSpecimenMatchReadModel.SpecimenMatchCandidate(
            "spm_only",
            "owner/repo-only",
            "repo only",
            null,
            "species_only",
            List.of("diag_only")));

    InMemoryRatingStore oneRatingStore = new InMemoryRatingStore();
    oneRatingStore.seed("spm_only", 1500, 0);

    ArenaDuelService duelService =
        new ArenaDuelService(
            oneCandidateReadModel,
            oneRatingStore,
            battleIdVerifier,
            policyPort,
            economyPort,
            rateLimiter,
            new InMemoryFeaturedDuelStore(),
            new ArenaMatchProperties());

    assertThatThrownBy(
            () ->
                duelService.duel(
                    "req-4",
                    new ArenaDuelService.DuelQuery(Set.of(), Set.of(), "usr_pool", "127.0.0.1")))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).getErrorCode())
        .isEqualTo(ErrorCode.ARENA_POOL_EMPTY);
  }

  private static final class InMemoryFeaturedDuelStore implements ArenaFeaturedDuelStore {

    private ArenaDuelService.DuelResult cached;

    @Override
    public java.util.Optional<ArenaDuelService.DuelResult> findFeaturedDuel() {
      return java.util.Optional.ofNullable(cached);
    }

    @Override
    public void saveFeaturedDuel(ArenaDuelService.DuelResult duelResult) {
      cached = duelResult;
    }
  }

  private static class AlwaysAllowRateLimiter implements ArenaRateLimiter {

    @Override
    public boolean allowAnonymousDuel(String clientIp) {
      return true;
    }

    @Override
    public boolean allowAuthenticatedDuelByUser(String userId) {
      return true;
    }

    @Override
    public boolean allowAuthenticatedDuelByIp(String clientIp) {
      return true;
    }
  }

  private static final class BlockAnonymousRateLimiter extends AlwaysAllowRateLimiter {

    @Override
    public boolean allowAnonymousDuel(String clientIp) {
      return false;
    }
  }

  private static final class InMemorySpecimenMatchReadModel implements ArenaSpecimenMatchReadModel {

    private final List<SpecimenMatchCandidate> candidates = new java.util.ArrayList<>();

    void seed(SpecimenMatchCandidate candidate) {
      candidates.add(candidate);
    }

    @Override
    public List<SpecimenMatchCandidate> listActiveCandidates() {
      return List.copyOf(candidates);
    }
  }

  private static final class RecordingEconomyPort implements ArenaEconomyPort {

    private long balance;

    @Override
    public long deductBug(
        String userId,
        int amount,
        String refType,
        String refId,
        String idempotencyKey,
        ArenaPolicySnapshot policySnapshot) {
      balance -= amount;
      return balance;
    }

    @Override
    public long currentBalance(String userId) {
      return balance;
    }
  }

  private static final class InMemoryRatingStore implements ArenaSpecimenRatingStore {

    private final Map<String, ArenaSpecimenRating> store = new HashMap<>();

    void seed(String specimenId, int elo, long matchesPlayed) {
      store.put(specimenId, new ArenaSpecimenRating(specimenId, elo, matchesPlayed));
    }

    @Override
    public java.util.Optional<ArenaSpecimenRating> find(String specimenId) {
      return java.util.Optional.ofNullable(store.get(specimenId));
    }

    @Override
    public ArenaSpecimenRating applyVoteDelta(String specimenId, int eloDelta) {
      ArenaSpecimenRating rating = store.get(specimenId);
      if (rating == null) {
        throw new IllegalStateException("missing rating");
      }
      ArenaSpecimenRating updated =
          new ArenaSpecimenRating(
              specimenId, rating.eloScore() + eloDelta, rating.matchesPlayed() + 1);
      store.put(specimenId, updated);
      return updated;
    }
  }
}
