package com.wtfrepo.backend.arena.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wtfrepo.backend.arena.application.support.ArenaContractProperties;
import com.wtfrepo.backend.arena.domain.ArenaVoteWinner;
import com.wtfrepo.backend.arena.infra.persistence.entity.SpecimenRatingJpaEntity;
import com.wtfrepo.backend.arena.infra.persistence.repository.BattleVoteJpaRepository;
import com.wtfrepo.backend.arena.infra.persistence.repository.EloDailySnapshotJpaRepository;
import com.wtfrepo.backend.arena.infra.persistence.repository.SpecimenRatingJpaRepository;
import com.wtfrepo.backend.shared.outbox.OutboxEventCommand;
import com.wtfrepo.backend.shared.outbox.OutboxEventStore;
import com.wtfrepo.backend.shared.policy.ArenaRuntimePolicy;
import com.wtfrepo.backend.shared.policy.ArenaRuntimePolicyPort;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArenaDailySnapshotServiceTest {

  @Mock
  private ArenaRuntimePolicyPort arenaRuntimePolicyPort;

  @Mock
  private ArenaSpecimenMatchReadModel specimenMatchReadModel;

  @Mock
  private ArenaSpecimenRatingStore arenaSpecimenRatingStore;

  @Mock
  private SpecimenRatingJpaRepository specimenRatingJpaRepository;

  @Mock
  private BattleVoteJpaRepository battleVoteJpaRepository;

  @Mock
  private EloDailySnapshotJpaRepository eloDailySnapshotJpaRepository;

  @Mock
  private ArenaRatingMetricsMaintenanceService arenaRatingMetricsMaintenanceService;

  @Mock
  private OutboxEventStore outboxEventStore;

  private ArenaDailySnapshotService service;

  @BeforeEach
  void setUp() {
    service =
        new ArenaDailySnapshotService(
            new ArenaContractProperties(),
            arenaRuntimePolicyPort,
            specimenMatchReadModel,
            arenaSpecimenRatingStore,
            specimenRatingJpaRepository,
            battleVoteJpaRepository,
            eloDailySnapshotJpaRepository,
            arenaRatingMetricsMaintenanceService,
            outboxEventStore);
  }

  @Test
  void shouldAppendDailySnapshotEventAfterSettlement() {
    LocalDate tradingDay = LocalDate.of(2026, 2, 15);
    String specimenId = "spm_settle_1";

    when(arenaRuntimePolicyPort.currentArenaRuntimePolicy())
        .thenReturn(new ArenaRuntimePolicy(Duration.ofMinutes(10), false, 1500));
    when(specimenMatchReadModel.listActiveCandidates())
        .thenReturn(
            List.of(
                new ArenaSpecimenMatchReadModel.SpecimenMatchCandidate(
                    specimenId,
                    "owner/repo",
                    "repo",
                    "https://example.com/thumbnail.png",
                    "species_a",
                    List.of("diag_x"))));

    SpecimenRatingJpaEntity rating = SpecimenRatingJpaEntity.createFromLegacyMetrics(specimenId, 1510, 12);
    when(specimenRatingJpaRepository.findAllBySpecimenIdInForUpdate(any()))
        .thenReturn(List.of(rating), List.of(rating));

    when(battleVoteJpaRepository.countVotesForSpecimenBetween(eq(specimenId), any(), any())).thenReturn(3L);
    when(
            battleVoteJpaRepository.countVotesForSpecimenAndWinnerBetween(
                eq(specimenId), eq(ArenaVoteWinner.BOTH_BAD), any(), any()))
        .thenReturn(1L);
    when(eloDailySnapshotJpaRepository.findBySpecimenIdAndDate(specimenId, tradingDay))
        .thenReturn(Optional.empty());

    ArenaDailySnapshotService.SnapshotRunResult result = service.runSettlementForDate(tradingDay);

    assertThat(result.processedSpecimens()).isEqualTo(1);
    assertThat(result.globalCorrection()).isEqualTo(-10);
    assertThat(result.skippedSpecimens()).isEqualTo(0);

    ArgumentCaptor<OutboxEventCommand> eventCaptor = ArgumentCaptor.forClass(OutboxEventCommand.class);
    verify(outboxEventStore).append(eventCaptor.capture());
    OutboxEventCommand event = eventCaptor.getValue();
    assertThat(event.aggregateType()).isEqualTo("ARENA_SPECIMEN");
    assertThat(event.aggregateId()).isEqualTo(specimenId);
    assertThat(event.eventType()).isEqualTo("DailySnapshotCreatedEvent");
    assertThat(event.eventKey()).isEqualTo("arena:daily-snapshot:" + specimenId + ":" + tradingDay);

    verify(arenaRatingMetricsMaintenanceService)
        .refreshForLockedRatings(any(), eq(tradingDay), any(Instant.class));
  }
}
