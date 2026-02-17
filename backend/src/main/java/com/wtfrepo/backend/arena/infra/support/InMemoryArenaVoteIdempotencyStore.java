package com.wtfrepo.backend.arena.infra.support;

import com.wtfrepo.backend.arena.application.ArenaVoteIdempotencyStore;
import com.wtfrepo.backend.arena.application.ArenaVoteService;
import com.wtfrepo.backend.arena.infra.persistence.repository.BattleVoteJpaRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * In-memory vote idempotency store kept for unit tests.
 *
 * <p>Production path should use {@code JpaArenaVoteIdempotencyStore}.
 */
@Component
@ConditionalOnMissingBean(BattleVoteJpaRepository.class)
public class InMemoryArenaVoteIdempotencyStore implements ArenaVoteIdempotencyStore {

  private final Map<String, StoredVoteResult> store = new ConcurrentHashMap<>();
  private final Map<String, String> battleAndVoterToIdempotency = new ConcurrentHashMap<>();

  @Override
  public Optional<StoredVoteResult> findByIdempotencyKey(String idempotencyKey) {
    return Optional.ofNullable(store.get(idempotencyKey));
  }

  @Override
  public Optional<StoredVoteResult> findByBattleAndVoter(String battleId, String voterId) {
    String existingKey = battleAndVoterToIdempotency.get(battleAndVoterKey(battleId, voterId));
    if (existingKey == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(store.get(existingKey));
  }

  @Override
  public void save(PersistVoteCommand command) {
    store.put(
        command.idempotencyKey(), new StoredVoteResult(command.requestFingerprint(), command.result()));
    battleAndVoterToIdempotency.put(
        battleAndVoterKey(command.battleId(), command.voterId()), command.idempotencyKey());
  }

  @Override
  public void updateVoteResult(String idempotencyKey, ArenaVoteService.VoteResult result) {
    StoredVoteResult existing = store.get(idempotencyKey);
    if (existing == null) {
      return;
    }
    store.put(idempotencyKey, new StoredVoteResult(existing.requestFingerprint(), result));
  }

  private String battleAndVoterKey(String battleId, String voterId) {
    return battleId + "::" + voterId;
  }
}
