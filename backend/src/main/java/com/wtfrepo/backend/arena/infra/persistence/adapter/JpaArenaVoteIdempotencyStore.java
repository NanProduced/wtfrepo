package com.wtfrepo.backend.arena.infra.persistence.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtfrepo.backend.arena.application.ArenaVoteIdempotencyStore;
import com.wtfrepo.backend.arena.application.ArenaVoteService;
import com.wtfrepo.backend.arena.application.policy.ArenaPolicySnapshot;
import com.wtfrepo.backend.arena.infra.persistence.entity.BattleVoteJpaEntity;
import com.wtfrepo.backend.arena.infra.persistence.repository.BattleVoteJpaRepository;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA-backed vote persistence for idempotency replay and anti-duplicate constraints.
 *
 * <p>Both idempotency replay and `(battle_id, voter_id)` conflict checks are served by the same
 * table to keep write-path consistency simple during MVP stage.
 */
@Component
@Primary
@ConditionalOnBean(BattleVoteJpaRepository.class)
public class JpaArenaVoteIdempotencyStore implements ArenaVoteIdempotencyStore {

  private final BattleVoteJpaRepository repository;
  private final ObjectMapper objectMapper;

  public JpaArenaVoteIdempotencyStore(BattleVoteJpaRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<StoredVoteResult> findByIdempotencyKey(String idempotencyKey) {
    return repository.findByIdempotencyKey(idempotencyKey).map(this::toStoredVoteResult);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<StoredVoteResult> findByBattleAndVoter(String battleId, String voterId) {
    return repository.findByBattleIdAndVoterId(battleId, voterId).map(this::toStoredVoteResult);
  }

  @Override
  @Transactional
  public void save(PersistVoteCommand command) {
    BattleVoteJpaEntity entity =
        BattleVoteJpaEntity.of(
            command.idempotencyKey(),
            command.requestFingerprint(),
            command.battleId(),
            command.leftSpecimenId(),
            command.rightSpecimenId(),
            command.winner(),
            command.voterId(),
            command.bugCost(),
            command.leftEloBefore(),
            command.leftEloDelta(),
            command.rightEloBefore(),
            command.rightEloDelta(),
            command.result().leftEloAfter(),
            command.result().rightEloAfter(),
            command.leftKFactor(),
            command.rightKFactor(),
            command.matchType(),
            command.matchProfileVersion(),
            command.result().leftPhase(),
            command.result().rightPhase(),
            command.result().walletBalanceAfter(),
            writePolicySnapshot(command.policySnapshot()));
    repository.save(entity);
  }

  @Override
  @Transactional
  public void updateVoteResult(String idempotencyKey, ArenaVoteService.VoteResult result) {
    repository
        .findByIdempotencyKey(idempotencyKey)
        .ifPresent(entity -> entity.updateWalletBalanceAfter(result.walletBalanceAfter()));
  }

  private StoredVoteResult toStoredVoteResult(BattleVoteJpaEntity entity) {
    ArenaVoteService.VoteResult result =
        new ArenaVoteService.VoteResult(
            entity.getBattleId(),
            entity.getWinner().name(),
            entity.getLeftSpecimenId(),
            entity.getRightSpecimenId(),
            entity.getLeftEloDelta(),
            entity.getRightEloDelta(),
            entity.getLeftEloAfter(),
            entity.getRightEloAfter(),
            entity.getLeftPhase(),
            entity.getRightPhase(),
            entity.getBugCost(),
            entity.getWalletBalanceAfter(),
            readPolicySnapshot(entity.getPolicySnapshot()));
    return new StoredVoteResult(entity.getRequestFingerprint(), result);
  }

  private String writePolicySnapshot(ArenaPolicySnapshot snapshot) {
    try {
      return objectMapper.writeValueAsString(snapshot);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize arena policy snapshot", ex);
    }
  }

  private ArenaPolicySnapshot readPolicySnapshot(String payload) {
    try {
      return objectMapper.readValue(payload, ArenaPolicySnapshot.class);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to deserialize arena policy snapshot", ex);
    }
  }
}
