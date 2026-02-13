package com.wtfrepo.backend.arena.application;

import com.wtfrepo.backend.arena.application.economy.ArenaEconomyPort;
import com.wtfrepo.backend.arena.application.economy.ArenaPolicyPort;
import com.wtfrepo.backend.arena.application.policy.ArenaPolicySnapshot;
import com.wtfrepo.backend.arena.application.support.ArenaBattleIdVerifier;
import com.wtfrepo.backend.arena.application.support.ArenaConstants;
import com.wtfrepo.backend.arena.application.support.ArenaExceptions;
import com.wtfrepo.backend.arena.application.support.ArenaMatchProperties;
import com.wtfrepo.backend.arena.domain.ArenaEloCalculator;
import com.wtfrepo.backend.arena.domain.ArenaMatchType;
import com.wtfrepo.backend.arena.domain.ArenaVoteWinner;
import com.wtfrepo.backend.shared.policy.ArenaRuntimePolicyPort;
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
  private final ArenaMatchProperties arenaMatchProperties;

  public ArenaVoteService(
      ArenaVoteIdempotencyStore idempotencyStore,
      ArenaBattleIdVerifier battleIdVerifier,
      ArenaSpecimenRatingStore arenaSpecimenRatingStore,
      ArenaPolicyPort arenaPolicyPort,
      ArenaEconomyPort arenaEconomyPort,
      ArenaRuntimePolicyPort arenaRuntimePolicyPort,
      ArenaMatchProperties arenaMatchProperties) {
    this.idempotencyStore = idempotencyStore;
    this.battleIdVerifier = battleIdVerifier;
    this.arenaSpecimenRatingStore = arenaSpecimenRatingStore;
    this.arenaPolicyPort = arenaPolicyPort;
    this.arenaEconomyPort = arenaEconomyPort;
    this.arenaRuntimePolicyPort = arenaRuntimePolicyPort;
    this.arenaMatchProperties = arenaMatchProperties;
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
                // Vote endpoint currently persists only duel-generated battle ids.
                // Match type/profile are persisted as snapshots to keep historical reproducibility.
                ArenaMatchType.CROSS,
                arenaMatchProperties.getProfileVersion(),
                policySnapshot,
                provisionalResult));
    if (replayed.isPresent()) {
      return replayed.get();
    }

    long walletBalanceAfter =
        arenaEconomyPort.deductBug(
            userId,
            policySnapshot.bugCost(),
            "ARENA_VOTE",
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

  private record LockedRatingPair(ArenaSpecimenRating left, ArenaSpecimenRating right) {}
}
