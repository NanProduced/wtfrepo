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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ArenaVoteServiceTest {

  private static final String SECRET = "arena-test-secret-0123456789";

  private ArenaVoteService arenaVoteService;
  private RecordingEconomyPort recordingEconomyPort;

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
    PropertyBackedArenaPolicyPort policyPort =
        new PropertyBackedArenaPolicyPort(
            economyProperties, contractProperties, specimenContractProperties);

    recordingEconomyPort = new RecordingEconomyPort();
    recordingEconomyPort.balance = 1000L;
    InMemoryRatingStore ratingStore = new InMemoryRatingStore();
    ratingStore.seed("spm_left", 1500, 0);
    ratingStore.seed("spm_right", 1500, 0);
    ArenaMatchProperties matchProperties = new ArenaMatchProperties();
    matchProperties.setProfileVersion("test-match-profile-v1");
    arenaVoteService =
        new ArenaVoteService(
            new InMemoryArenaVoteIdempotencyStore(),
            new ArenaBattleIdVerifier(contractProperties, policyPort),
            ratingStore,
            policyPort,
            recordingEconomyPort,
            policyPort,
            matchProperties);
  }

  @Test
  void shouldProcessVoteAndDeductBug() {
    String battleId = createBattleId("spm_left", "spm_right", Instant.now().getEpochSecond());

    ArenaVoteService.VoteResult result =
        arenaVoteService.vote(
            "req-1",
            "usr_1",
            battleId + "_usr_1",
            new ArenaVoteService.VoteCommand(battleId, "LEFT"));

    assertThat(result.winner()).isEqualTo("LEFT");
    assertThat(result.leftDelta()).isEqualTo(32);
    assertThat(result.rightDelta()).isEqualTo(-32);
    assertThat(result.policySnapshot().policyVersion()).isEqualTo("arena-policy-test-v1");
    assertThat(result.walletBalanceAfter()).isEqualTo(900L);
    assertThat(recordingEconomyPort.lastAmount).isEqualTo(100);
  }

  @Test
  void shouldReplayOnSameIdempotencyKey() {
    String battleId = createBattleId("spm_left", "spm_right", Instant.now().getEpochSecond());
    String idem = battleId + "_usr_1";

    ArenaVoteService.VoteResult first =
        arenaVoteService.vote(
            "req-1", "usr_1", idem, new ArenaVoteService.VoteCommand(battleId, "RIGHT"));
    ArenaVoteService.VoteResult second =
        arenaVoteService.vote(
            "req-2", "usr_1", idem, new ArenaVoteService.VoteCommand(battleId, "RIGHT"));

    assertThat(second).isEqualTo(first);
    assertThat(recordingEconomyPort.callCount).isEqualTo(1);
  }

  @Test
  void shouldRejectInvalidWinner() {
    String battleId = createBattleId("spm_left", "spm_right", Instant.now().getEpochSecond());

    assertThatThrownBy(
            () ->
                arenaVoteService.vote(
                    "req-1",
                    "usr_1",
                    battleId + "_usr_1",
                    new ArenaVoteService.VoteCommand(battleId, "INVALID")))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).getErrorCode())
        .isEqualTo(ErrorCode.VOTE_INVALID_WINNER);
  }

  @Test
  void shouldRejectExpiredBattleId() {
    String battleId =
        createBattleId("spm_left", "spm_right", Instant.now().minusSeconds(11 * 60).getEpochSecond());

    assertThatThrownBy(
            () ->
                arenaVoteService.vote(
                    "req-1",
                    "usr_1",
                    battleId + "_usr_1",
                    new ArenaVoteService.VoteCommand(battleId, "LEFT")))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).getErrorCode())
        .isEqualTo(ErrorCode.BATTLE_EXPIRED);
  }

  private String createBattleId(String left, String right, long issuedAtEpochSecond) {
    String payload = left + "|" + right + "|" + issuedAtEpochSecond;
    String encodedPayload =
        Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    String signature = hmacHex(encodedPayload);
    return ArenaConstants.Battle.ID_PREFIX + encodedPayload + "_" + signature;
  }

  private String hmacHex(String encodedPayload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] bytes = mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder(bytes.length * 2);
      for (byte value : bytes) {
        builder.append(String.format("%02x", value));
      }
      return builder.toString();
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  private static final class RecordingEconomyPort implements ArenaEconomyPort {

    private int callCount;
    private int lastAmount;
    private long balance;

    @Override
    public long deductBug(
        String userId,
        int amount,
        String refType,
        String refId,
        String idempotencyKey,
        ArenaPolicySnapshot policySnapshot) {
      callCount++;
      lastAmount = amount;
      balance -= amount;
      return balance;
    }

    @Override
    public long currentBalance(String userId) {
      return balance;
    }
  }

  private static final class InMemoryRatingStore implements ArenaSpecimenRatingStore {

    private final java.util.Map<String, ArenaSpecimenRating> store = new java.util.HashMap<>();

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
