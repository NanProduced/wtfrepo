package com.wtfrepo.backend.achievements.infra.persistence.adapter;

import com.wtfrepo.backend.achievements.application.ArenaVoteLookupPort;
import com.wtfrepo.backend.arena.infra.persistence.repository.BattleVoteJpaRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** JPA-backed lookup adapter for resolving voter user id from arena vote order id. */
@Component
public class JpaArenaVoteLookupAdapter implements ArenaVoteLookupPort {

  private final BattleVoteJpaRepository battleVoteRepository;

  public JpaArenaVoteLookupAdapter(BattleVoteJpaRepository battleVoteRepository) {
    this.battleVoteRepository = battleVoteRepository;
  }

  @Override
  public Optional<String> findVoterUserIdByOrderId(String orderId) {
    if (!StringUtils.hasText(orderId)) {
      return Optional.empty();
    }
    return battleVoteRepository
        .findByIdempotencyKey(orderId.trim())
        .map(item -> StringUtils.hasText(item.getVoterId()) ? item.getVoterId().trim() : null);
  }
}
