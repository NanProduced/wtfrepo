package com.wtfrepo.backend.arena.application;

import com.wtfrepo.backend.arena.application.economy.ArenaEconomyPort;
import com.wtfrepo.backend.arena.application.economy.ArenaPolicyPort;
import com.wtfrepo.backend.arena.application.policy.ArenaPolicySnapshot;
import com.wtfrepo.backend.arena.application.profile.ArenaMatchProfilePort;
import com.wtfrepo.backend.arena.application.support.ArenaBattleIdVerifier;
import com.wtfrepo.backend.arena.application.support.ArenaConstants;
import com.wtfrepo.backend.arena.application.support.ArenaExceptions;
import com.wtfrepo.backend.arena.domain.ArenaEloCalculator;
import com.wtfrepo.backend.arena.domain.ArenaVoteWinner;
import com.wtfrepo.backend.shared.outbox.OutboxEventCommand;
import com.wtfrepo.backend.shared.outbox.OutboxEventStore;
import com.wtfrepo.backend.shared.policy.ArenaRuntimePolicyPort;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Arena vote S0 minimal loop service. */
@Service
public class ArenaVoteService {

  private static final Logger log = LoggerFactory.getLogger(ArenaVoteService.class);

  private final ArenaVoteIdempotencyStore idempotencyStore;
  private final ArenaBattleIdVerifier battleIdVerifier;
  private final ArenaSpecimenRatingStore arenaSpecimenRatingStore;
  private final ArenaPolicyPort arenaPolicyPort;
  private final ArenaEconomyPort arenaEconomyPort;
  private final ArenaRuntimePolicyPort arenaRuntimePolicyPort;
  private final OutboxEventStore outboxEventStore;
  private final ArenaMatchProfilePort arenaMatchProfilePort;

  public ArenaVoteService(
      ArenaVoteIdempotencyStore idempotencyStore,
      ArenaBattleIdVerifier battleIdVerifier,
      ArenaSpecimenRatingStore arenaSpecimenRatingStore,
      ArenaPolicyPort arenaPolicyPort,
      ArenaEconomyPort arenaEconomyPort,
      ArenaRuntimePolicyPort arenaRuntimePolicyPort,
      OutboxEventStore outboxEventStore,
      ArenaMatchProfilePort arenaMatchProfilePort) {
    this.idempotencyStore = idempotencyStore;
    this.battleIdVerifier = battleIdVerifier;
    this.arenaSpecimenRatingStore = arenaSpecimenRatingStore;
    this.arenaPolicyPort = arenaPolicyPort;
    this.arenaEconomyPort = arenaEconomyPort;
    this.arenaRuntimePolicyPort = arenaRuntimePolicyPort;
    this.outboxEventStore = outboxEventStore;
    this.arenaMatchProfilePort = arenaMatchProfilePort;
  }

  @Transactional
  public VoteResult vote(String requestId, String userId, String idempotencyKey, VoteCommand command) {
    if (arenaRuntimePolicyPort.currentArenaRuntimePolicy().settlementInProgress()) {
      throw ArenaExceptions.settlementInProgress(ArenaConstants.Message.SETTLEMENT_IN_PROGRESS);
    }

    ArenaVoteWinner winner = ArenaVoteWinner.parse(command.winner());
    if (winner == null) {
      throw ArenaExceptions.invalidWinner(ArenaConstants.Message.VOTE_INVALID_WINNER);
    }

    String requestFingerprint = command.battleId() + "|" + winner.name() + "|" + userId;
    Optional<ArenaVoteIdempotencyStore.StoredVoteResult> existing =
        idempotencyStore.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) {
      if (!existing.get().requestFingerprint().equals(requestFingerprint)) {
        throw ArenaExceptions.voteDuplicate(ArenaConstants.Message.VOTE_DUPLICATE);
      }
      return existing.get().result();
    }

    ArenaBattleIdVerifier.VerifiedBattle battle = battleIdVerifier.verify(command.battleId());
    Optional<ArenaVoteIdempotencyStore.StoredVoteResult> existingBattleVote =
        idempotencyStore.findByBattleAndVoter(command.battleId(), userId);
    if (existingBattleVote.isPresent()) {
      throw ArenaExceptions.voteDuplicate(ArenaConstants.Message.VOTE_DUPLICATE);
    }

    // Lock both ratings in deterministic order to avoid deadlocks when concurrent votes touch the
    // same pair in reversed order.
    LockedRatingPair lockedRatings =
        lockRatingsForBattle(battle.leftSpecimenId(), battle.rightSpecimenId());
    ArenaSpecimenRating leftRating = lockedRatings.left();
    ArenaSpecimenRating rightRating = lockedRatings.right();
    ArenaPolicySnapshot policySnapshot = arenaPolicyPort.currentPolicySnapshot();

    ArenaEloCalculator.Result eloResult =
        ArenaEloCalculator.compute(
            new ArenaEloCalculator.Input(
                winner,
                leftRating.eloScore(),
                rightRating.eloScore(),
                leftRating.matchesPlayed(),
                rightRating.matchesPlayed()));

    VoteResult provisionalResult =
        new VoteResult(
            command.battleId(),
            winner.name(),
            battle.leftSpecimenId(),
            battle.rightSpecimenId(),
            eloResult.leftDelta(),
            eloResult.rightDelta(),
            eloResult.leftEloAfter(),
            eloResult.rightEloAfter(),
            eloResult.leftPhaseAfter().name(),
            eloResult.rightPhaseAfter().name(),
            policySnapshot.bugCost(),
            0L,
            policySnapshot);

    String matchProfileVersion = normalizeProfileVersion(arenaMatchProfilePort.currentProfile().profileVersion());

    Optional<VoteResult> replayed =
        persistVoteFact(
            new ArenaVoteIdempotencyStore.PersistVoteCommand(
                idempotencyKey,
                requestFingerprint,
                command.battleId(),
                userId,
                battle.leftSpecimenId(),
                battle.rightSpecimenId(),
                winner,
                policySnapshot.bugCost(),
                leftRating.eloScore(),
                eloResult.leftDelta(),
                rightRating.eloScore(),
                eloResult.rightDelta(),
                eloResult.leftKFactor(),
                eloResult.rightKFactor(),
                // Match metadata is sourced from signed battleId payload and snapshotted for audit.
                battle.matchType(),
                matchProfileVersion,
                policySnapshot,
                provisionalResult));
    if (replayed.isPresent()) {
      return replayed.get();
    }

    long walletBalanceAfter =
        arenaEconomyPort.deductBug(
            userId,
            policySnapshot.bugCost(),
            ArenaConstants.Economy.REF_TYPE_BATTLE,
            command.battleId(),
            idempotencyKey,
            policySnapshot);
    VoteResult result = withWalletBalanceAfter(provisionalResult, walletBalanceAfter);
    idempotencyStore.updateVoteResult(idempotencyKey, result);

    ArenaSpecimenRating leftAfter =
        arenaSpecimenRatingStore.applyVoteDelta(battle.leftSpecimenId(), eloResult.leftDelta());
    ArenaSpecimenRating rightAfter =
        arenaSpecimenRatingStore.applyVoteDelta(battle.rightSpecimenId(), eloResult.rightDelta());

    if (leftAfter.eloScore() != result.leftEloAfter() || rightAfter.eloScore() != result.rightEloAfter()) {
      log.warn(
          "arena_vote_elo_drift_detected requestId={} battleId={} expectedLeft={} actualLeft={} expectedRight={} actualRight={}",
          requestId,
          command.battleId(),
          result.leftEloAfter(),
          leftAfter.eloScore(),
          result.rightEloAfter(),
          rightAfter.eloScore());
    }

    appendVoteCompletedOutboxEvent(idempotencyKey, result);
    appendEloUpdatedOutboxEvent(
        idempotencyKey,
        battle.leftSpecimenId(),
        leftRating.eloScore(),
        leftAfter.eloScore(),
        eloResult.leftKFactor());
    appendEloUpdatedOutboxEvent(
        idempotencyKey,
        battle.rightSpecimenId(),
        rightRating.eloScore(),
        rightAfter.eloScore(),
        eloResult.rightKFactor());
    appendIpoCompletedOutboxEventIfNeeded(
        idempotencyKey, battle.leftSpecimenId(), leftRating.matchesPlayed(), leftAfter);
    appendIpoCompletedOutboxEventIfNeeded(
        idempotencyKey, battle.rightSpecimenId(), rightRating.matchesPlayed(), rightAfter);

    log.info(
        "arena_vote_success requestId={} userId={} battleId={} winner={} cost={} policyVersion={}",
        requestId,
        userId,
        command.battleId(),
        winner,
        policySnapshot.bugCost(),
        policySnapshot.policyVersion());
    return result;
  }

  private VoteResult withWalletBalanceAfter(VoteResult result, long walletBalanceAfter) {
    return new VoteResult(
        result.battleId(),
        result.winner(),
        result.leftSpecimenId(),
        result.rightSpecimenId(),
        result.leftDelta(),
        result.rightDelta(),
        result.leftEloAfter(),
        result.rightEloAfter(),
        result.leftPhase(),
        result.rightPhase(),
        result.bugCost(),
        walletBalanceAfter,
        result.policySnapshot());
  }

  private LockedRatingPair lockRatingsForBattle(String leftSpecimenId, String rightSpecimenId) {
    if (leftSpecimenId.compareTo(rightSpecimenId) <= 0) {
      ArenaSpecimenRating left =
          arenaSpecimenRatingStore
              .findForUpdate(leftSpecimenId)
              .orElseThrow(() -> ArenaExceptions.battleNotFound(ArenaConstants.Message.BATTLE_NOT_FOUND));
      ArenaSpecimenRating right =
          arenaSpecimenRatingStore
              .findForUpdate(rightSpecimenId)
              .orElseThrow(() -> ArenaExceptions.battleNotFound(ArenaConstants.Message.BATTLE_NOT_FOUND));
      return new LockedRatingPair(left, right);
    }

    ArenaSpecimenRating right =
        arenaSpecimenRatingStore
            .findForUpdate(rightSpecimenId)
            .orElseThrow(() -> ArenaExceptions.battleNotFound(ArenaConstants.Message.BATTLE_NOT_FOUND));
    ArenaSpecimenRating left =
        arenaSpecimenRatingStore
            .findForUpdate(leftSpecimenId)
            .orElseThrow(() -> ArenaExceptions.battleNotFound(ArenaConstants.Message.BATTLE_NOT_FOUND));
    return new LockedRatingPair(left, right);
  }

  private void appendVoteCompletedOutboxEvent(String idempotencyKey, VoteResult result) {
    String eventType = "VoteCompletedEvent";
    String eventKey = "arena:vote-completed:" + idempotencyKey;
    VoteCompletedEventPayload payload =
        new VoteCompletedEventPayload(
            result.battleId(),
            idempotencyKey,
            result.winner(),
            result.leftSpecimenId(),
            result.rightSpecimenId(),
            result.leftDelta(),
            result.rightDelta());
    outboxEventStore.append(
        new OutboxEventCommand(
            "ARENA_BATTLE",
            result.battleId(),
            eventType,
            eventKey,
            payload,
            Instant.now()));
  }

  /** Emits specimen-level Elo update event for read-model/cache refresh consumers. */
  private void appendEloUpdatedOutboxEvent(
      String idempotencyKey, String specimenId, int eloBefore, int eloAfter, int kFactor) {
    String eventType = "EloUpdatedEvent";
    String eventKey = "arena:elo-updated:" + idempotencyKey + ":" + specimenId;
    EloUpdatedEventPayload payload =
        new EloUpdatedEventPayload(specimenId, eloBefore, eloAfter, kFactor);
    outboxEventStore.append(
        new OutboxEventCommand(
            "ARENA_SPECIMEN", specimenId, eventType, eventKey, payload, Instant.now()));
  }

  /**
   * Emits IPO completion event exactly once when a specimen crosses from calibration to IPO in the
   * current vote transaction.
   */
  private void appendIpoCompletedOutboxEventIfNeeded(
      String idempotencyKey,
      String specimenId,
      long matchesBefore,
      ArenaSpecimenRating ratingAfter) {
    if (matchesBefore >= 10 || ratingAfter.matchesPlayed() < 10) {
      return;
    }

    String eventType = "IpoCompletedEvent";
    String eventKey = "arena:ipo-completed:" + idempotencyKey + ":" + specimenId;
    IpoCompletedEventPayload payload =
        new IpoCompletedEventPayload(
            specimenId,
            ratingAfter.eloScore(),
            Math.toIntExact(Math.min(Integer.MAX_VALUE, ratingAfter.matchesPlayed())));
    outboxEventStore.append(
        new OutboxEventCommand(
            "ARENA_SPECIMEN", specimenId, eventType, eventKey, payload, Instant.now()));
  }

  private Optional<VoteResult> persistVoteFact(ArenaVoteIdempotencyStore.PersistVoteCommand command) {
    try {
      idempotencyStore.save(command);
      return Optional.empty();
    } catch (DataIntegrityViolationException ex) {
      // Concurrent same-key retries are returned as replay; all other unique collisions are
      // considered duplicate voting attempts.
      Optional<ArenaVoteIdempotencyStore.StoredVoteResult> existingByIdempotency =
          idempotencyStore.findByIdempotencyKey(command.idempotencyKey());
      if (existingByIdempotency.isPresent()) {
        if (!existingByIdempotency.get().requestFingerprint().equals(command.requestFingerprint())) {
          throw ArenaExceptions.voteDuplicate(ArenaConstants.Message.VOTE_DUPLICATE);
        }
        return Optional.of(existingByIdempotency.get().result());
      }

      Optional<ArenaVoteIdempotencyStore.StoredVoteResult> existingByBattleAndVoter =
          idempotencyStore.findByBattleAndVoter(command.battleId(), command.voterId());
      if (existingByBattleAndVoter.isPresent()) {
        throw ArenaExceptions.voteDuplicate(ArenaConstants.Message.VOTE_DUPLICATE);
      }
      throw ex;
    }
  }

  private String normalizeProfileVersion(String profileVersion) {
    if (!org.springframework.util.StringUtils.hasText(profileVersion)) {
      return "unknown";
    }
    return profileVersion.trim();
  }

  public record VoteCommand(String battleId, String winner) {}

  public record VoteResult(
      String battleId,
      String winner,
      String leftSpecimenId,
      String rightSpecimenId,
      int leftDelta,
      int rightDelta,
      int leftEloAfter,
      int rightEloAfter,
      String leftPhase,
      String rightPhase,
      int bugCost,
      long walletBalanceAfter,
      ArenaPolicySnapshot policySnapshot) {}

  private record VoteCompletedEventPayload(
      String battleId,
      String orderId,
      String winner,
      String leftSpecimenId,
      String rightSpecimenId,
      int leftDelta,
      int rightDelta) {}

  private record EloUpdatedEventPayload(
      String specimenId, int eloBefore, int eloAfter, int kFactor) {}

  private record IpoCompletedEventPayload(
      String specimenId, int calibratedScore, int matchesPlayed) {}

  private record LockedRatingPair(ArenaSpecimenRating left, ArenaSpecimenRating right) {}
}
