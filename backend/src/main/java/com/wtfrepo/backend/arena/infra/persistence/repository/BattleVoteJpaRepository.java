package com.wtfrepo.backend.arena.infra.persistence.repository;

import com.wtfrepo.backend.arena.domain.ArenaVoteWinner;
import com.wtfrepo.backend.arena.infra.persistence.entity.BattleVoteJpaEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BattleVoteJpaRepository extends JpaRepository<BattleVoteJpaEntity, String> {

  interface SpecimenAppearanceCountView {

    String getSpecimenId();

    long getAppearanceCount();
  }

  Optional<BattleVoteJpaEntity> findByIdempotencyKey(String idempotencyKey);

  Optional<BattleVoteJpaEntity> findByBattleIdAndVoterId(String battleId, String voterId);

  long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
      Instant fromInclusive, Instant toExclusive);

  @Query(
      """
      select count(vote)
      from BattleVoteJpaEntity vote
      where vote.createdAt >= :fromInclusive
        and vote.createdAt < :toExclusive
        and (vote.leftSpecimenId = :specimenId or vote.rightSpecimenId = :specimenId)
      """)
  long countVotesForSpecimenBetween(
      @Param("specimenId") String specimenId,
      @Param("fromInclusive") Instant fromInclusive,
      @Param("toExclusive") Instant toExclusive);

  @Query(
      """
      select count(vote)
      from BattleVoteJpaEntity vote
      where vote.createdAt >= :fromInclusive
        and vote.createdAt < :toExclusive
        and (vote.leftSpecimenId = :specimenId or vote.rightSpecimenId = :specimenId)
        and vote.winner = :winner
      """)
  long countVotesForSpecimenAndWinnerBetween(
      @Param("specimenId") String specimenId,
      @Param("winner") ArenaVoteWinner winner,
      @Param("fromInclusive") Instant fromInclusive,
      @Param("toExclusive") Instant toExclusive);

  @Query(
      """
      select count(vote)
      from BattleVoteJpaEntity vote
      where vote.voterId = :voterId
        and vote.createdAt >= :fromInclusive
        and vote.createdAt < :toExclusive
        and (vote.leftSpecimenId = :specimenId or vote.rightSpecimenId = :specimenId)
      """)
  long countUserVotesForSpecimenBetween(
      @Param("voterId") String voterId,
      @Param("specimenId") String specimenId,
      @Param("fromInclusive") Instant fromInclusive,
      @Param("toExclusive") Instant toExclusive);

  @Query(
      """
      select vote.leftSpecimenId as specimenId,
             count(vote) as appearanceCount
      from BattleVoteJpaEntity vote
      where vote.createdAt >= :fromInclusive
        and vote.createdAt < :toExclusive
        and vote.leftSpecimenId in :specimenIds
      group by vote.leftSpecimenId
      """)
  List<SpecimenAppearanceCountView> countLeftAppearancesForSpecimensBetween(
      @Param("specimenIds") Collection<String> specimenIds,
      @Param("fromInclusive") Instant fromInclusive,
      @Param("toExclusive") Instant toExclusive);

  @Query(
      """
      select vote.rightSpecimenId as specimenId,
             count(vote) as appearanceCount
      from BattleVoteJpaEntity vote
      where vote.createdAt >= :fromInclusive
        and vote.createdAt < :toExclusive
        and vote.rightSpecimenId in :specimenIds
      group by vote.rightSpecimenId
      """)
  List<SpecimenAppearanceCountView> countRightAppearancesForSpecimensBetween(
      @Param("specimenIds") Collection<String> specimenIds,
      @Param("fromInclusive") Instant fromInclusive,
      @Param("toExclusive") Instant toExclusive);
}
