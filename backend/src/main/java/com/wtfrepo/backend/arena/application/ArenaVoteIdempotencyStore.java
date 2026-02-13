package com.wtfrepo.backend.arena.application;

import com.wtfrepo.backend.arena.application.policy.ArenaPolicySnapshot;
import com.wtfrepo.backend.arena.domain.ArenaMatchType;
import com.wtfrepo.backend.arena.domain.ArenaVoteWinner;
import java.util.Optional;

/** Idempotency replay store for arena vote endpoint. */
public interface ArenaVoteIdempotencyStore {

  Optional<StoredVoteResult> findByIdempotencyKey(String idempotencyKey);

  Optional<StoredVoteResult> findByBattleAndVoter(String battleId, String voterId);

  void save(PersistVoteCommand command);

  void updateVoteResult(String idempotencyKey, ArenaVoteService.VoteResult result);

  /**
   * Complete vote persistence payload used for both idempotency replay and audit trail.
   *
   * <p>Keeping this as a single immutable command avoids partial writes when new snapshot fields
   * are added in future contract revisions.
   */
  record PersistVoteCommand(
      String idempotencyKey,
      String requestFingerprint,
      String battleId,
      String voterId,
      String leftSpecimenId,
      String rightSpecimenId,
      ArenaVoteWinner winner,
      int bugCost,
      int leftEloBefore,
      int leftEloDelta,
      int rightEloBefore,
      int rightEloDelta,
      int leftKFactor,
      int rightKFactor,
      ArenaMatchType matchType,
      String matchProfileVersion,
      ArenaPolicySnapshot policySnapshot,
      ArenaVoteService.VoteResult result) {}

  record StoredVoteResult(String requestFingerprint, ArenaVoteService.VoteResult result) {}
}
