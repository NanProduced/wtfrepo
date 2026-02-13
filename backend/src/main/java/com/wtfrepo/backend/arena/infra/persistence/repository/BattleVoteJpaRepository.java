package com.wtfrepo.backend.arena.infra.persistence.repository;

import com.wtfrepo.backend.arena.infra.persistence.entity.BattleVoteJpaEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BattleVoteJpaRepository extends JpaRepository<BattleVoteJpaEntity, String> {

  Optional<BattleVoteJpaEntity> findByIdempotencyKey(String idempotencyKey);

  Optional<BattleVoteJpaEntity> findByBattleIdAndVoterId(String battleId, String voterId);
}
