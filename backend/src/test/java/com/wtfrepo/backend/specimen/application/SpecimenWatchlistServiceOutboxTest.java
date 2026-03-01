package com.wtfrepo.backend.specimen.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wtfrepo.backend.shared.outbox.OutboxEventCommand;
import com.wtfrepo.backend.shared.outbox.OutboxEventStore;
import com.wtfrepo.backend.specimen.application.SpecimenOutboxEventPublisher.WatchlistAddedEventPayload;
import com.wtfrepo.backend.specimen.application.model.SpecimenModels.AddWatchlistResult;
import com.wtfrepo.backend.specimen.infra.persistence.entity.UserWatchlistItemJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenArenaMetricsJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenJpaRepository;
import com.wtfrepo.backend.specimen.infra.persistence.repository.UserWatchlistItemJpaRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpecimenWatchlistServiceOutboxTest {

  @Mock
  private SpecimenJpaRepository specimenJpaRepository;

  @Mock
  private SpecimenArenaMetricsJpaRepository specimenArenaMetricsJpaRepository;

  @Mock
  private UserWatchlistItemJpaRepository userWatchlistItemJpaRepository;

  @Mock
  private OutboxEventStore outboxEventStore;

  private SpecimenWatchlistService service;

  @BeforeEach
  void setUp() {
    SpecimenContractProperties properties = new SpecimenContractProperties();
    SpecimenOutboxEventPublisher publisher = new SpecimenOutboxEventPublisher(outboxEventStore);
    service =
        new SpecimenWatchlistService(
            specimenJpaRepository,
            specimenArenaMetricsJpaRepository,
            userWatchlistItemJpaRepository,
            properties,
            publisher);
  }

  @Test
  void addWatchlistShouldAppendWatchlistAddedEventOnFirstInsert() {
    String userId = "u_1";
    String specimenId = "sp_1";

    when(specimenJpaRepository.existsById(specimenId)).thenReturn(true);
    when(userWatchlistItemJpaRepository.existsByUserIdAndSpecimenId(userId, specimenId))
        .thenReturn(false);
    when(userWatchlistItemJpaRepository.countByUserId(userId)).thenReturn(0L);
    when(userWatchlistItemJpaRepository.save(any(UserWatchlistItemJpaEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    AddWatchlistResult result = service.addWatchlist(userId, specimenId, "DETAIL");

    assertThat(result.added()).isTrue();
    assertThat(result.itemId()).isNotBlank();

    ArgumentCaptor<OutboxEventCommand> captor = ArgumentCaptor.forClass(OutboxEventCommand.class);
    verify(outboxEventStore).append(captor.capture());
    OutboxEventCommand event = captor.getValue();
    assertThat(event.eventType()).isEqualTo("WatchlistAddedEvent");
    assertThat(event.aggregateType()).isEqualTo("SPECIMEN");
    assertThat(event.aggregateId()).isEqualTo(specimenId);

    assertThat(event.payload()).isInstanceOf(WatchlistAddedEventPayload.class);
    WatchlistAddedEventPayload payload = (WatchlistAddedEventPayload) event.payload();
    assertThat(payload.userId()).isEqualTo(userId);
    assertThat(payload.specimenId()).isEqualTo(specimenId);
    assertThat(payload.watchlistItemId()).isEqualTo(result.itemId());
    assertThat(payload.source()).isEqualTo("DETAIL");
    assertThat(payload.addedAt()).isNotNull();
  }

  @Test
  void addWatchlistShouldNotAppendOutboxWhenAlreadyExists() {
    String userId = "u_2";
    String specimenId = "sp_2";
    UserWatchlistItemJpaEntity existing =
        UserWatchlistItemJpaEntity.create(userId, specimenId, "DETAIL");

    when(specimenJpaRepository.existsById(specimenId)).thenReturn(true);
    when(userWatchlistItemJpaRepository.existsByUserIdAndSpecimenId(userId, specimenId))
        .thenReturn(true);
    when(userWatchlistItemJpaRepository.findByUserId(userId)).thenReturn(List.of(existing));

    AddWatchlistResult result = service.addWatchlist(userId, specimenId, "DETAIL");

    assertThat(result.added()).isTrue();
    assertThat(result.itemId()).isEqualTo(existing.getItemId());
    verify(outboxEventStore, never()).append(any());
  }
}
