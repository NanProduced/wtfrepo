package com.wtfrepo.backend.narrator.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtfrepo.backend.narrator.application.model.NarratorModels;
import com.wtfrepo.backend.shared.outbox.OutboxEventJpaEntity;
import com.wtfrepo.backend.shared.outbox.OutboxEventJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TickerQueryServiceTest {

  @Mock
  private OutboxEventJpaRepository outboxEventRepository;

  private TickerQueryService service;

  @BeforeEach
  void setUp() {
    service = new TickerQueryService(outboxEventRepository, new ObjectMapper());
  }

  @Test
  void listRecentShouldMapTickerItemsWithCursorPagination() {
    OutboxEventJpaEntity eloEvent =
        OutboxEventJpaEntity.createPending(
            "ARENA_SPECIMEN",
            "sp_1",
            "EloUpdatedEvent",
            "event:key:1",
            "{\"specimenId\":\"sp_1\",\"eloBefore\":1000,\"eloAfter\":1088}",
            Instant.parse("2026-02-28T10:00:00Z"));
    OutboxEventJpaEntity topRoastEvent =
        OutboxEventJpaEntity.createPending(
            "SPECIMEN",
            "sp_2",
            "TopRoastUpdatedEvent",
            "event:key:2",
            "{\"specimenId\":\"sp_2\",\"commentId\":\"c_1\"}",
            Instant.parse("2026-02-28T09:00:00Z"));
    OutboxEventJpaEntity extraEvent =
        OutboxEventJpaEntity.createPending(
            "SPECIMEN",
            "sp_3",
            "SpecimenActivatedEvent",
            "event:key:3",
            "{\"specimenId\":\"sp_3\"}",
            Instant.parse("2026-02-28T08:00:00Z"));

    when(outboxEventRepository.findTickerRecentCandidates(anyList(), isNull(), isNull(), any()))
        .thenReturn(List.of(eloEvent, topRoastEvent, extraEvent));

    NarratorModels.TickerRecentPage page = service.listRecent(null, 2);

    assertThat(page.items()).hasSize(2);
    assertThat(page.hasMore()).isTrue();
    assertThat(page.nextCursor()).isEqualTo(topRoastEvent.getEventId());

    NarratorModels.TickerRecentItem first = page.items().get(0);
    assertThat(first.eventType()).isEqualTo("ticker.elo_delta_major");
    assertThat(first.priority()).isEqualTo("P2_AMBIENT");
    assertThat(first.text()).contains("+88");
    assertThat(first.actionUrl()).isEqualTo("/specimen/sp_1");

    NarratorModels.TickerRecentItem second = page.items().get(1);
    assertThat(second.eventType()).isEqualTo("ticker.top_roast_changed");
    assertThat(second.actionUrl()).isEqualTo("/specimen/sp_2#comment-c_1");
  }

  @Test
  void listRecentShouldUseCursorAnchor() {
    OutboxEventJpaEntity anchor =
        OutboxEventJpaEntity.createPending(
            "ARENA_SPECIMEN",
            "sp_anchor",
            "EloUpdatedEvent",
            "event:key:anchor",
            "{\"specimenId\":\"sp_anchor\",\"eloBefore\":1000,\"eloAfter\":1020}",
            Instant.parse("2026-02-28T10:30:00Z"));
    OutboxEventJpaEntity next =
        OutboxEventJpaEntity.createPending(
            "SPECIMEN",
            "sp_4",
            "SpecimenActivatedEvent",
            "event:key:next",
            "{\"specimenId\":\"sp_4\"}",
            Instant.parse("2026-02-28T10:20:00Z"));

    when(outboxEventRepository.findById(anchor.getEventId())).thenReturn(Optional.of(anchor));
    when(outboxEventRepository.findTickerRecentCandidates(
            anyList(), eq(anchor.getCreatedAt()), eq(anchor.getEventId()), any()))
        .thenReturn(List.of(next));

    NarratorModels.TickerRecentPage page = service.listRecent(anchor.getEventId(), 10);

    assertThat(page.items()).hasSize(1);
    assertThat(page.hasMore()).isFalse();
    assertThat(page.nextCursor()).isNull();
    verify(outboxEventRepository)
        .findTickerRecentCandidates(anyList(), eq(anchor.getCreatedAt()), eq(anchor.getEventId()), any());
  }

  @Test
  void listRecentShouldRejectInvalidCursorWhenAnchorMissing() {
    when(outboxEventRepository.findById("missing_cursor")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.listRecent("missing_cursor", 10)).hasMessage("invalid_cursor");
  }

  @Test
  void listRecentShouldRejectUnsupportedCursorEventType() {
    OutboxEventJpaEntity unsupported =
        OutboxEventJpaEntity.createPending(
            "ARENA_BATTLE",
            "battle_1",
            "VoteCompletedEvent",
            "event:key:unsupported",
            "{}",
            Instant.parse("2026-02-28T10:30:00Z"));
    when(outboxEventRepository.findById(unsupported.getEventId())).thenReturn(Optional.of(unsupported));

    assertThatThrownBy(() -> service.listRecent(unsupported.getEventId(), 10))
        .hasMessage("invalid_cursor");
  }
}
