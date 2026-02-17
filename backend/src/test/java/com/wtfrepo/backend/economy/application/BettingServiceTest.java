package com.wtfrepo.backend.economy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wtfrepo.backend.arena.domain.ArenaIpoStatus;
import com.wtfrepo.backend.arena.infra.persistence.entity.SpecimenRatingJpaEntity;
import com.wtfrepo.backend.arena.infra.persistence.repository.EloDailySnapshotJpaRepository;
import com.wtfrepo.backend.arena.infra.persistence.repository.SpecimenRatingJpaRepository;
import com.wtfrepo.backend.economy.application.support.BettingPolicyProperties;
import com.wtfrepo.backend.economy.domain.BetDirection;
import com.wtfrepo.backend.economy.infra.persistence.entity.BetOrderJpaEntity;
import com.wtfrepo.backend.economy.infra.persistence.entity.BetPoolJpaEntity;
import com.wtfrepo.backend.economy.infra.persistence.repository.BetOrderJpaRepository;
import com.wtfrepo.backend.economy.infra.persistence.repository.BetPoolJpaRepository;
import com.wtfrepo.backend.shared.web.ApiException;
import com.wtfrepo.backend.shared.web.ErrorCode;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenJpaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BettingServiceTest {

  @Mock private BetPoolJpaRepository betPoolJpaRepository;
  @Mock private BetOrderJpaRepository betOrderJpaRepository;
  @Mock private SpecimenRatingJpaRepository specimenRatingJpaRepository;
  @Mock private SpecimenJpaRepository specimenJpaRepository;
  @Mock private EloDailySnapshotJpaRepository eloDailySnapshotJpaRepository;
  @Mock private EconomyWalletService economyWalletService;

  private BettingService bettingService;

  @BeforeEach
  void setUp() {
    bettingService =
        new BettingService(
            betPoolJpaRepository,
            betOrderJpaRepository,
            specimenRatingJpaRepository,
            specimenJpaRepository,
            eloDailySnapshotJpaRepository,
            economyWalletService,
            new BettingPolicyProperties());
  }

  @Test
  void placeBet_shouldRejectInvalidDirection() {
    assertThatThrownBy(
            () ->
                bettingService.placeBet(
                    "req-1",
                    "usr_1",
                    "idem-1",
                    new BettingService.PlaceBetCommand("sp_1", "INVALID", 200),
                    Instant.parse("2026-02-12T12:00:00Z")))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).getErrorCode())
        .isEqualTo(ErrorCode.BET_INVALID_DIRECTION);
  }

  @Test
  void placeBet_shouldRejectIpoLockedSpecimen() {
    SpecimenRatingJpaEntity privateBetaRating = SpecimenRatingJpaEntity.createDefault("sp_1", 1500);
    when(betOrderJpaRepository.findByIdempotencyKey("idem-2")).thenReturn(Optional.empty());
    when(specimenRatingJpaRepository.findById("sp_1")).thenReturn(Optional.of(privateBetaRating));

    assertThatThrownBy(
            () ->
                bettingService.placeBet(
                    "req-2",
                    "usr_1",
                    "idem-2",
                    new BettingService.PlaceBetCommand("sp_1", "UP", 200),
                    Instant.parse("2026-02-12T12:00:00Z")))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).getErrorCode())
        .isEqualTo(ErrorCode.BET_IPO_LOCKED);
  }

  @Test
  void placeBet_shouldReplayWhenIdempotencyMatches() {
    BetOrderJpaEntity existing =
        BetOrderJpaEntity.createUserOrder(
            "usr_1",
            "sp_1",
            BetDirection.UP,
            200,
            new BigDecimal("1.7300"),
            LocalDate.parse("2026-02-12"),
            "idem-3",
            "sp_1|UP|200|usr_1",
            900L);
    when(betOrderJpaRepository.findByIdempotencyKey("idem-3")).thenReturn(Optional.of(existing));

    BettingService.PlaceBetResult replay =
        bettingService.placeBet(
            "req-3",
            "usr_1",
            "idem-3",
            new BettingService.PlaceBetCommand("sp_1", "UP", 200),
            Instant.parse("2026-02-12T12:00:00Z"));

    assertThat(replay.orderId()).isEqualTo(existing.getOrderId());
    assertThat(replay.walletBalanceAfter()).isEqualTo(900L);
    verifyNoInteractions(specimenRatingJpaRepository);
  }

  @Test
  void placeBet_shouldRejectIdempotencyConflict() {
    BetOrderJpaEntity existing =
        BetOrderJpaEntity.createUserOrder(
            "usr_1",
            "sp_1",
            BetDirection.UP,
            200,
            new BigDecimal("1.7300"),
            LocalDate.parse("2026-02-12"),
            "idem-4",
            "sp_1|UP|200|usr_1",
            900L);
    when(betOrderJpaRepository.findByIdempotencyKey("idem-4")).thenReturn(Optional.of(existing));

    assertThatThrownBy(
            () ->
                bettingService.placeBet(
                    "req-4",
                    "usr_1",
                    "idem-4",
                    new BettingService.PlaceBetCommand("sp_1", "FLAT", 200),
                    Instant.parse("2026-02-12T12:00:00Z")))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).getErrorCode())
        .isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT);
  }

  @Test
  void getHistory_shouldReturnOrderIdCursorWhenMoreData() {
    BetOrderJpaEntity first =
        BetOrderJpaEntity.createUserOrder(
            "usr_1",
            "sp_1",
            BetDirection.UP,
            300,
            new BigDecimal("1.4500"),
            LocalDate.parse("2026-02-12"),
            "idem-h-1",
            "fp-h-1",
            700L);
    BetOrderJpaEntity second =
        BetOrderJpaEntity.createUserOrder(
            "usr_1",
            "sp_2",
            BetDirection.DOWN,
            200,
            new BigDecimal("2.0100"),
            LocalDate.parse("2026-02-12"),
            "idem-h-2",
            "fp-h-2",
            500L);
    BetOrderJpaEntity third =
        BetOrderJpaEntity.createUserOrder(
            "usr_1",
            "sp_3",
            BetDirection.FLAT,
            150,
            new BigDecimal("3.1000"),
            LocalDate.parse("2026-02-12"),
            "idem-h-3",
            "fp-h-3",
            350L);

    when(betOrderJpaRepository.findByUserIdAndIsHouseFalseOrderByCreatedAtDescOrderIdDesc(
            eq("usr_1"), any()))
        .thenReturn(List.of(first, second, third));
    when(specimenJpaRepository.findBySpecimenIdIn(anySet())).thenReturn(List.of());

    BettingService.HistoryBetsView history = bettingService.getHistory("usr_1", null, 2);

    assertThat(history.orders()).hasSize(2);
    assertThat(history.orders().get(0).orderId()).isEqualTo(first.getOrderId());
    assertThat(history.orders().get(1).orderId()).isEqualTo(second.getOrderId());
    assertThat(history.hasMore()).isTrue();
    assertThat(history.nextCursor()).isEqualTo(second.getOrderId());
  }

  @Test
  void getHistory_shouldUseCursorOrderForNextPage() {
    BetOrderJpaEntity cursorOrder =
        BetOrderJpaEntity.createUserOrder(
            "usr_1",
            "sp_1",
            BetDirection.UP,
            300,
            new BigDecimal("1.4500"),
            LocalDate.parse("2026-02-12"),
            "idem-c-1",
            "fp-c-1",
            700L);
    BetOrderJpaEntity nextOrder =
        BetOrderJpaEntity.createUserOrder(
            "usr_1",
            "sp_4",
            BetDirection.DOWN,
            120,
            new BigDecimal("1.9900"),
            LocalDate.parse("2026-02-12"),
            "idem-c-2",
            "fp-c-2",
            580L);

    when(betOrderJpaRepository.findById(cursorOrder.getOrderId())).thenReturn(Optional.of(cursorOrder));
    when(betOrderJpaRepository.findHistoryPageAfterCursor(
            eq("usr_1"), eq(cursorOrder.getCreatedAt()), eq(cursorOrder.getOrderId()), any()))
        .thenReturn(List.of(nextOrder));
    when(specimenJpaRepository.findBySpecimenIdIn(anySet())).thenReturn(List.of());

    BettingService.HistoryBetsView history =
        bettingService.getHistory("usr_1", cursorOrder.getOrderId(), 2);

    assertThat(history.orders()).hasSize(1);
    assertThat(history.orders().get(0).orderId()).isEqualTo(nextOrder.getOrderId());
    assertThat(history.hasMore()).isFalse();
    assertThat(history.nextCursor()).isNull();
    verify(betOrderJpaRepository, never())
        .findByUserIdAndIsHouseFalseOrderByCreatedAtDescOrderIdDesc(eq("usr_1"), any());
  }

  @Test
  void getBetSummary_shouldLazyCreatePoolForIpoSpecimen() {
    Instant now = Instant.parse("2026-02-12T12:00:00Z");
    LocalDate tradingDay = LocalDate.ofInstant(now, ZoneOffset.UTC);
    SpecimenRatingJpaEntity ipoRating = createIpoRating("sp_2");

    when(specimenRatingJpaRepository.findById("sp_2")).thenReturn(Optional.of(ipoRating));
    when(betPoolJpaRepository.findBySpecimenIdAndDate("sp_2", tradingDay)).thenReturn(Optional.empty());
    when(betPoolJpaRepository.findBySpecimenIdAndDateForUpdate("sp_2", tradingDay))
        .thenReturn(Optional.empty());
    when(betPoolJpaRepository.saveAndFlush(any(BetPoolJpaEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(betOrderJpaRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    when(betOrderJpaRepository.countDistinctUserIdBySpecimenIdAndSettleDateAndIsHouseFalse(
            "sp_2", tradingDay))
        .thenReturn(0L);
    when(eloDailySnapshotJpaRepository.findBySpecimenIdAndDate("sp_2", tradingDay))
        .thenReturn(Optional.empty());

    BettingService.BetSummaryView summary = bettingService.getBetSummary("sp_2", now);

    assertThat(summary.poolStatus()).isEqualTo("OPEN");
    assertThat(summary.houseUp()).isEqualTo(800L);
    assertThat(summary.houseFlat()).isEqualTo(600L);
    assertThat(summary.houseDown()).isEqualTo(600L);
    assertThat(summary.ipoStatus()).isEqualTo(ArenaIpoStatus.IPO.name());
    verify(betPoolJpaRepository).saveAndFlush(any(BetPoolJpaEntity.class));
  }

  private SpecimenRatingJpaEntity createIpoRating(String specimenId) {
    SpecimenRatingJpaEntity rating = SpecimenRatingJpaEntity.createDefault(specimenId, 1500);
    for (int i = 0; i < 10; i++) {
      rating.applyVoteDelta(0);
    }
    return rating;
  }
}
