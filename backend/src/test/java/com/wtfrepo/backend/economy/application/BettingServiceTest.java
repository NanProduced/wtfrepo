package com.wtfrepo.backend.economy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.wtfrepo.backend.arena.domain.ArenaIpoStatus;
import com.wtfrepo.backend.arena.infra.persistence.entity.EloDailySnapshotJpaEntity;
import com.wtfrepo.backend.arena.infra.persistence.entity.SpecimenRatingJpaEntity;
import com.wtfrepo.backend.arena.infra.persistence.repository.EloDailySnapshotJpaRepository;
import com.wtfrepo.backend.arena.infra.persistence.repository.SpecimenRatingJpaRepository;
import com.wtfrepo.backend.economy.application.support.BettingPolicyProperties;
import com.wtfrepo.backend.economy.domain.BetDirection;
import com.wtfrepo.backend.economy.domain.BetOrderStatus;
import com.wtfrepo.backend.economy.domain.EconomyLedgerType;
import com.wtfrepo.backend.economy.infra.persistence.entity.BetOrderJpaEntity;
import com.wtfrepo.backend.economy.infra.persistence.entity.BetPoolJpaEntity;
import com.wtfrepo.backend.economy.infra.persistence.repository.BetHouseConfigJpaRepository;
import com.wtfrepo.backend.economy.infra.persistence.repository.BetOrderJpaRepository;
import com.wtfrepo.backend.economy.infra.persistence.repository.BetPoolJpaRepository;
import com.wtfrepo.backend.shared.outbox.OutboxEventCommand;
import com.wtfrepo.backend.shared.outbox.OutboxEventStore;
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
  @Mock private BetHouseConfigJpaRepository betHouseConfigJpaRepository;
  @Mock private BetOrderJpaRepository betOrderJpaRepository;
  @Mock private SpecimenRatingJpaRepository specimenRatingJpaRepository;
  @Mock private SpecimenJpaRepository specimenJpaRepository;
  @Mock private EloDailySnapshotJpaRepository eloDailySnapshotJpaRepository;
  @Mock private EconomyWalletService economyWalletService;
  @Mock private OutboxEventStore outboxEventStore;

  private BettingService bettingService;

  @BeforeEach
  void setUp() {
    bettingService =
        new BettingService(
            betPoolJpaRepository,
            betHouseConfigJpaRepository,
            betOrderJpaRepository,
            specimenRatingJpaRepository,
            specimenJpaRepository,
            eloDailySnapshotJpaRepository,
            economyWalletService,
            outboxEventStore,
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
  void getSettlementToday_shouldGroupOrdersBySpecimen() {
    Instant now = Instant.parse("2026-02-19T10:00:00Z");
    LocalDate tradingDay = LocalDate.parse("2026-02-19");

    BetOrderJpaEntity wonOrder =
        BetOrderJpaEntity.createUserOrder(
            "usr_1",
            "sp_1",
            BetDirection.UP,
            300,
            new BigDecimal("1.4500"),
            tradingDay,
            "idem-s-1",
            "fp-s-1",
            700L);
    wonOrder.markWon(450L, 0L, now.minusSeconds(10));

    BetOrderJpaEntity lostOrder =
        BetOrderJpaEntity.createUserOrder(
            "usr_1",
            "sp_1",
            BetDirection.DOWN,
            120,
            new BigDecimal("1.9900"),
            tradingDay,
            "idem-s-2",
            "fp-s-2",
            580L);
    lostOrder.markLost(now.minusSeconds(20));

    when(
            betOrderJpaRepository
                .findByUserIdAndSettleDateAndIsHouseFalseAndStatusInOrderBySettledAtDescOrderIdDesc(
                    eq("usr_1"), eq(tradingDay), any(), any()))
        .thenReturn(List.of(wonOrder, lostOrder));
    when(specimenJpaRepository.findBySpecimenIdIn(anySet())).thenReturn(List.of());
    when(eloDailySnapshotJpaRepository.findAllBySpecimenIdInAndDateBetween(anySet(), eq(tradingDay), eq(tradingDay)))
        .thenReturn(List.of(EloDailySnapshotJpaEntity.create("sp_1", tradingDay, 1512, 1530, 18, 8, 1)));

    BettingService.SettlementTodayView view =
        bettingService.getSettlementToday("usr_1", null, 20, now);

    assertThat(view.date()).isEqualTo(tradingDay);
    assertThat(view.settlements()).hasSize(1);
    BettingService.SettlementItem item = view.settlements().get(0);
    assertThat(item.specimenId()).isEqualTo("sp_1");
    assertThat(item.eloOpen()).isEqualTo(1512);
    assertThat(item.eloClose()).isEqualTo(1530);
    assertThat(item.deltaR()).isEqualTo(18);
    assertThat(item.outcome()).isEqualTo("UP");
    assertThat(item.myOrders()).hasSize(2);
    assertThat(view.hasMore()).isFalse();
    assertThat(view.nextCursor()).isNull();
  }


  @Test
  void getSettlementToday_shouldExposeCursorWhenPageHasMoreRows() {
    Instant now = Instant.parse("2026-02-19T10:00:00Z");
    LocalDate tradingDay = LocalDate.parse("2026-02-19");

    BetOrderJpaEntity firstOrder =
        BetOrderJpaEntity.createUserOrder(
            "usr_1",
            "sp_1",
            BetDirection.UP,
            300,
            new BigDecimal("1.4500"),
            tradingDay,
            "idem-settle-page-1",
            "fp-settle-page-1",
            700L);
    firstOrder.markWon(450L, 0L, now.minusSeconds(10));

    BetOrderJpaEntity secondOrder =
        BetOrderJpaEntity.createUserOrder(
            "usr_1",
            "sp_2",
            BetDirection.DOWN,
            120,
            new BigDecimal("1.9900"),
            tradingDay,
            "idem-settle-page-2",
            "fp-settle-page-2",
            580L);
    secondOrder.markLost(now.minusSeconds(20));

    when(
            betOrderJpaRepository
                .findByUserIdAndSettleDateAndIsHouseFalseAndStatusInOrderBySettledAtDescOrderIdDesc(
                    eq("usr_1"), eq(tradingDay), any(), any()))
        .thenReturn(List.of(firstOrder, secondOrder));
    when(specimenJpaRepository.findBySpecimenIdIn(anySet())).thenReturn(List.of());
    when(
            eloDailySnapshotJpaRepository.findAllBySpecimenIdInAndDateBetween(
                anySet(), eq(tradingDay), eq(tradingDay)))
        .thenReturn(List.of());

    BettingService.SettlementTodayView view = bettingService.getSettlementToday("usr_1", null, 1, now);

    assertThat(view.settlements()).hasSize(1);
    assertThat(view.settlements().get(0).specimenId()).isEqualTo("sp_1");
    assertThat(view.hasMore()).isTrue();
    assertThat(view.nextCursor()).isEqualTo(firstOrder.getOrderId());
  }

  @Test
  void getSettlementToday_shouldUseCursorForNextPage() {
    Instant now = Instant.parse("2026-02-19T10:00:00Z");
    LocalDate tradingDay = LocalDate.parse("2026-02-19");

    BetOrderJpaEntity cursorOrder =
        BetOrderJpaEntity.createUserOrder(
            "usr_1",
            "sp_2",
            BetDirection.DOWN,
            150,
            new BigDecimal("2.0100"),
            tradingDay,
            "idem-s-cursor",
            "fp-s-cursor",
            500L);
    cursorOrder.markLost(now.minusSeconds(30));

    BetOrderJpaEntity nextOrder =
        BetOrderJpaEntity.createUserOrder(
            "usr_1",
            "sp_3",
            BetDirection.FLAT,
            200,
            new BigDecimal("3.1000"),
            tradingDay,
            "idem-s-next",
            "fp-s-next",
            350L);
    nextOrder.markWon(620L, 0L, now.minusSeconds(40));

    when(betOrderJpaRepository.findById(cursorOrder.getOrderId())).thenReturn(Optional.of(cursorOrder));
    when(
            betOrderJpaRepository.findSettlementPageAfterCursor(
                eq("usr_1"),
                eq(tradingDay),
                any(),
                eq(cursorOrder.getSettledAt()),
                eq(cursorOrder.getOrderId()),
                any()))
        .thenReturn(List.of(nextOrder));
    when(specimenJpaRepository.findBySpecimenIdIn(anySet())).thenReturn(List.of());
    when(eloDailySnapshotJpaRepository.findAllBySpecimenIdInAndDateBetween(anySet(), eq(tradingDay), eq(tradingDay)))
        .thenReturn(List.of());

    BettingService.SettlementTodayView view =
        bettingService.getSettlementToday("usr_1", cursorOrder.getOrderId(), 20, now);

    assertThat(view.settlements()).hasSize(1);
    assertThat(view.settlements().get(0).specimenId()).isEqualTo("sp_3");
    assertThat(view.hasMore()).isFalse();
    assertThat(view.nextCursor()).isNull();
    verify(betOrderJpaRepository, never())
        .findByUserIdAndSettleDateAndIsHouseFalseAndStatusInOrderBySettledAtDescOrderIdDesc(
            eq("usr_1"), eq(tradingDay), any(), any());
  }

  @Test
  void getSettlementToday_shouldReturnEmptyWhenCursorNotFound() {
    Instant now = Instant.parse("2026-02-19T10:00:00Z");
    LocalDate tradingDay = LocalDate.parse("2026-02-19");
    when(betOrderJpaRepository.findById("missing-cursor")).thenReturn(Optional.empty());

    BettingService.SettlementTodayView view =
        bettingService.getSettlementToday("usr_1", "missing-cursor", 20, now);

    assertThat(view.date()).isEqualTo(tradingDay);
    assertThat(view.settlements()).isEmpty();
    assertThat(view.hasMore()).isFalse();
    assertThat(view.nextCursor()).isNull();
    verify(betOrderJpaRepository, never())
        .findByUserIdAndSettleDateAndIsHouseFalseAndStatusInOrderBySettledAtDescOrderIdDesc(
            eq("usr_1"), eq(tradingDay), any(), any());
    verify(betOrderJpaRepository, never())
        .findSettlementPageAfterCursor(eq("usr_1"), eq(tradingDay), any(), any(), any(), any());
  }

  @Test
  void getSettlementToday_shouldClampLimitToConfiguredMax() {
    Instant now = Instant.parse("2026-02-19T10:00:00Z");
    LocalDate tradingDay = LocalDate.parse("2026-02-19");
    when(
            betOrderJpaRepository
                .findByUserIdAndSettleDateAndIsHouseFalseAndStatusInOrderBySettledAtDescOrderIdDesc(
                    eq("usr_1"),
                    eq(tradingDay),
                    any(),
                    // Service fetches maxLimit + 1 rows to decide hasMore.
                    argThat(pageable -> pageable != null && pageable.getPageSize() == 101)))
        .thenReturn(List.of());

    BettingService.SettlementTodayView view =
        bettingService.getSettlementToday("usr_1", null, 999, now);

    assertThat(view.date()).isEqualTo(tradingDay);
    assertThat(view.settlements()).isEmpty();
    assertThat(view.hasMore()).isFalse();
    verify(betOrderJpaRepository)
        .findByUserIdAndSettleDateAndIsHouseFalseAndStatusInOrderBySettledAtDescOrderIdDesc(
            eq("usr_1"),
            eq(tradingDay),
            any(),
            argThat(pageable -> pageable != null && pageable.getPageSize() == 101));
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

  @Test
  void initializePoolFromIpoEvent_shouldCreatePoolWhenEligible() {
    Instant occurredAt = Instant.parse("2026-02-12T12:00:00Z");
    LocalDate tradingDay = LocalDate.ofInstant(occurredAt, ZoneOffset.UTC);
    SpecimenRatingJpaEntity ipoRating = createIpoRating("sp_ipo_1");

    when(betPoolJpaRepository.findBySpecimenIdAndDate("sp_ipo_1", tradingDay))
        .thenReturn(Optional.empty());
    when(specimenRatingJpaRepository.findById("sp_ipo_1")).thenReturn(Optional.of(ipoRating));
    when(betPoolJpaRepository.findBySpecimenIdAndDateForUpdate("sp_ipo_1", tradingDay))
        .thenReturn(Optional.empty());
    when(betPoolJpaRepository.saveAndFlush(any(BetPoolJpaEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(betOrderJpaRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

    BettingService.IpoPoolInitResult result =
        bettingService.initializePoolFromIpoEvent("sp_ipo_1", 1532, occurredAt);

    assertThat(result.created()).isTrue();
    assertThat(result.outcome()).isEqualTo("POOL_CREATED");
    assertThat(result.tradingDay()).isEqualTo(tradingDay);
    verify(betPoolJpaRepository).saveAndFlush(any(BetPoolJpaEntity.class));
  }

  @Test
  void initializePoolFromIpoEvent_shouldSkipWhenOutsideWindow() {
    Instant occurredAt = Instant.parse("2026-02-12T23:45:00Z");
    LocalDate tradingDay = LocalDate.ofInstant(occurredAt, ZoneOffset.UTC);
    SpecimenRatingJpaEntity ipoRating = createIpoRating("sp_ipo_2");

    when(betPoolJpaRepository.findBySpecimenIdAndDate("sp_ipo_2", tradingDay))
        .thenReturn(Optional.empty());
    when(specimenRatingJpaRepository.findById("sp_ipo_2")).thenReturn(Optional.of(ipoRating));

    BettingService.IpoPoolInitResult result =
        bettingService.initializePoolFromIpoEvent("sp_ipo_2", 1538, occurredAt);

    assertThat(result.created()).isFalse();
    assertThat(result.outcome()).isEqualTo("OUTSIDE_BET_WINDOW");
    assertThat(result.tradingDay()).isEqualTo(tradingDay);
    verify(betPoolJpaRepository, never()).saveAndFlush(any(BetPoolJpaEntity.class));
    verify(betOrderJpaRepository, never()).saveAll(anyList());
  }

  @Test
  void forceSettleBySpecimenDeactivated_shouldCancelPendingAndRefundUsers() {
    Instant occurredAt = Instant.parse("2026-02-18T03:00:00Z");
    LocalDate tradingDay = LocalDate.ofInstant(occurredAt, ZoneOffset.UTC);
    BetPoolJpaEntity pool =
        BetPoolJpaEntity.createOpen(
            "sp_force_1",
            tradingDay,
            800L,
            600L,
            600L,
            new BigDecimal("0.1000"),
            occurredAt.plusSeconds(600));
    BetOrderJpaEntity userOrder =
        BetOrderJpaEntity.createUserOrder(
            "usr_force_1",
            "sp_force_1",
            BetDirection.UP,
            200,
            new BigDecimal("1.7300"),
            tradingDay,
            "idem-force-user-1",
            "sp_force_1|UP|200|usr_force_1",
            900L);
    BetOrderJpaEntity houseOrder =
        BetOrderJpaEntity.createHouseOrder(
            "sp_force_1",
            BetDirection.DOWN,
            600,
            new BigDecimal("3.2000"),
            tradingDay,
            "HOUSE",
            "house:sp_force_1:2026-02-18:DOWN");

    when(betPoolJpaRepository.findBySpecimenIdAndDateForUpdate("sp_force_1", tradingDay))
        .thenReturn(Optional.of(pool));
    when(betOrderJpaRepository.findBySpecimenIdAndSettleDateAndStatusOrderByCreatedAtAscOrderIdAsc(
            "sp_force_1", tradingDay, BetOrderStatus.PENDING))
        .thenReturn(List.of(userOrder, houseOrder));
    when(economyWalletService.credit(any(), eq(EconomyLedgerType.FORCE_SETTLE_REFUND)))
        .thenReturn(1100L);

    BettingService.ForceSettleResult result =
        bettingService.forceSettleBySpecimenDeactivated(
            "evt-specimen-deactivated-1", "sp_force_1", occurredAt);

    assertThat(result.outcome()).isEqualTo("FORCE_SETTLED");
    assertThat(result.tradingDay()).isEqualTo(tradingDay);
    assertThat(result.poolStatusBefore()).isEqualTo("OPEN");
    assertThat(result.poolStatusAfter()).isEqualTo("SETTLED");
    assertThat(result.cancelledOrders()).isEqualTo(2);
    assertThat(result.refundedOrders()).isEqualTo(1);
    assertThat(result.refundedAmount()).isEqualTo(200L);
    assertThat(result.settled()).isTrue();

    assertThat(userOrder.getStatus()).isEqualTo(BetOrderStatus.CANCELLED);
    assertThat(userOrder.getPayout()).isEqualTo(200L);
    assertThat(userOrder.getSettledAt()).isEqualTo(occurredAt);
    assertThat(houseOrder.getStatus()).isEqualTo(BetOrderStatus.CANCELLED);
    assertThat(houseOrder.getPayout()).isEqualTo(600L);
    assertThat(pool.getStatus().name()).isEqualTo("SETTLED");

    verify(economyWalletService)
        .credit(
            argThat(
                command ->
                    command.userId().equals("usr_force_1")
                        && command.amount() == 200
                        && command.refType().equals("BET")
                        && command.refId().equals(userOrder.getOrderId())
                        && command
                            .idempotencyKey()
                            .equals("forcesettle_" + userOrder.getOrderId())),
            eq(EconomyLedgerType.FORCE_SETTLE_REFUND));
    verifyNoMoreInteractions(economyWalletService);
  }

  @Test
  void forceSettleBySpecimenDeactivated_shouldSkipWhenPoolAlreadySettled() {
    Instant occurredAt = Instant.parse("2026-02-18T03:10:00Z");
    LocalDate tradingDay = LocalDate.ofInstant(occurredAt, ZoneOffset.UTC);
    BetPoolJpaEntity settledPool =
        BetPoolJpaEntity.createOpen(
            "sp_force_2",
            tradingDay,
            800L,
            600L,
            600L,
            new BigDecimal("0.1000"),
            occurredAt.plusSeconds(600));
    settledPool.markSettled();

    when(betPoolJpaRepository.findBySpecimenIdAndDateForUpdate("sp_force_2", tradingDay))
        .thenReturn(Optional.of(settledPool));

    BettingService.ForceSettleResult result =
        bettingService.forceSettleBySpecimenDeactivated(
            "evt-specimen-deactivated-2", "sp_force_2", occurredAt);

    assertThat(result.outcome()).isEqualTo("POOL_ALREADY_SETTLED");
    assertThat(result.poolStatusBefore()).isEqualTo("SETTLED");
    assertThat(result.cancelledOrders()).isZero();
    assertThat(result.refundedOrders()).isZero();
    assertThat(result.refundedAmount()).isZero();
    assertThat(result.settled()).isFalse();

    verify(betOrderJpaRepository, never())
        .findBySpecimenIdAndSettleDateAndStatusOrderByCreatedAtAscOrderIdAsc(any(), any(), any());
    verifyNoInteractions(economyWalletService);
  }

  @Test
  void forceSettleBySpecimenDeactivated_shouldSettlePoolWhenNoPendingOrders() {
    Instant occurredAt = Instant.parse("2026-02-18T03:20:00Z");
    LocalDate tradingDay = LocalDate.ofInstant(occurredAt, ZoneOffset.UTC);
    BetPoolJpaEntity pool =
        BetPoolJpaEntity.createOpen(
            "sp_force_3",
            tradingDay,
            800L,
            600L,
            600L,
            new BigDecimal("0.1000"),
            occurredAt.plusSeconds(600));

    when(betPoolJpaRepository.findBySpecimenIdAndDateForUpdate("sp_force_3", tradingDay))
        .thenReturn(Optional.of(pool));
    when(betOrderJpaRepository.findBySpecimenIdAndSettleDateAndStatusOrderByCreatedAtAscOrderIdAsc(
            "sp_force_3", tradingDay, BetOrderStatus.PENDING))
        .thenReturn(List.of());

    BettingService.ForceSettleResult result =
        bettingService.forceSettleBySpecimenDeactivated(
            "evt-specimen-deactivated-3", "sp_force_3", occurredAt);

    assertThat(result.outcome()).isEqualTo("POOL_SETTLED_WITHOUT_PENDING_ORDERS");
    assertThat(result.cancelledOrders()).isZero();
    assertThat(result.refundedOrders()).isZero();
    assertThat(result.refundedAmount()).isZero();
    assertThat(result.settled()).isTrue();
    assertThat(pool.getStatus().name()).isEqualTo("SETTLED");
    verifyNoInteractions(economyWalletService);
  }

  @Test
  void forceSettleBySpecimenDeactivated_shouldReturnPoolMissingWhenNoTradingDayPool() {
    Instant occurredAt = Instant.parse("2026-02-18T03:30:00Z");
    LocalDate tradingDay = LocalDate.ofInstant(occurredAt, ZoneOffset.UTC);

    when(betPoolJpaRepository.findBySpecimenIdAndDateForUpdate("sp_force_4", tradingDay))
        .thenReturn(Optional.empty());

    BettingService.ForceSettleResult result =
        bettingService.forceSettleBySpecimenDeactivated(
            "evt-specimen-deactivated-4", "sp_force_4", occurredAt);

    assertThat(result.outcome()).isEqualTo("POOL_MISSING");
    assertThat(result.cancelledOrders()).isZero();
    assertThat(result.refundedOrders()).isZero();
    assertThat(result.refundedAmount()).isZero();
    assertThat(result.settled()).isFalse();
    verifyNoInteractions(economyWalletService);
  }

  @Test
  void forceSettleAfterRetryExhausted_shouldUseExplicitTradingDayAndRefundUsers() {
    Instant executionAt = Instant.parse("2026-02-19T00:02:00Z");
    LocalDate tradingDay = LocalDate.parse("2026-02-18");
    BetPoolJpaEntity closedPool =
        BetPoolJpaEntity.createOpen(
            "sp_retry_force_1",
            tradingDay,
            800L,
            600L,
            600L,
            new BigDecimal("0.1000"),
            executionAt.minusSeconds(120));
    closedPool.markClosedIfOpen();
    BetOrderJpaEntity userOrder =
        BetOrderJpaEntity.createUserOrder(
            "usr_retry_1",
            "sp_retry_force_1",
            BetDirection.UP,
            300,
            new BigDecimal("1.8500"),
            tradingDay,
            "idem-retry-user-1",
            "sp_retry_force_1|UP|300|usr_retry_1",
            1200L);

    when(betPoolJpaRepository.findBySpecimenIdAndDateForUpdate("sp_retry_force_1", tradingDay))
        .thenReturn(Optional.of(closedPool));
    when(betOrderJpaRepository.findBySpecimenIdAndSettleDateAndStatusOrderByCreatedAtAscOrderIdAsc(
            "sp_retry_force_1", tradingDay, BetOrderStatus.PENDING))
        .thenReturn(List.of(userOrder));
    when(economyWalletService.credit(any(), eq(EconomyLedgerType.FORCE_SETTLE_REFUND)))
        .thenReturn(1500L);

    BettingService.ForceSettleResult result =
        bettingService.forceSettleAfterRetryExhausted(
            "sp_retry_force_1", tradingDay, executionAt, "SETTLEMENT_RETRY_EXHAUSTED");

    assertThat(result.outcome()).isEqualTo("FORCE_SETTLED");
    assertThat(result.tradingDay()).isEqualTo(tradingDay);
    assertThat(result.poolStatusBefore()).isEqualTo("CLOSED");
    assertThat(result.poolStatusAfter()).isEqualTo("SETTLED");
    assertThat(result.cancelledOrders()).isEqualTo(1);
    assertThat(result.refundedOrders()).isEqualTo(1);
    assertThat(result.refundedAmount()).isEqualTo(300L);
    assertThat(result.settled()).isTrue();

    assertThat(userOrder.getStatus()).isEqualTo(BetOrderStatus.CANCELLED);
    assertThat(userOrder.getPayout()).isEqualTo(300L);
    assertThat(userOrder.getSettledAt()).isEqualTo(executionAt);

    verify(economyWalletService)
        .credit(
            argThat(
                command ->
                    command.userId().equals("usr_retry_1")
                        && command.amount() == 300
                        && command.refType().equals("BET")
                        && command.refId().equals(userOrder.getOrderId())
                        && command.idempotencyKey().equals("forcesettle_" + userOrder.getOrderId())),
            eq(EconomyLedgerType.FORCE_SETTLE_REFUND));
  }

  @Test
  void forceSettleAfterRetryExhausted_shouldRejectNullTradingDay() {
    BettingService.ForceSettleResult result =
        bettingService.forceSettleAfterRetryExhausted(
            "sp_retry_force_2",
            null,
            Instant.parse("2026-02-19T00:02:00Z"),
            "SETTLEMENT_RETRY_DEADLINE_REACHED");

    assertThat(result.outcome()).isEqualTo("INVALID_TRADING_DAY");
    assertThat(result.settled()).isFalse();
    verifyNoInteractions(economyWalletService);
  }

  @Test
  void settleClosedPool_shouldSettleAndCreditWinners() {
    Instant now = Instant.parse("2026-02-19T00:06:00Z");
    LocalDate tradingDay = LocalDate.parse("2026-02-18");
    BetPoolJpaEntity closedPool =
        BetPoolJpaEntity.createOpen(
            "sp_settlement_ready",
            tradingDay,
            800L,
            600L,
            600L,
            new BigDecimal("0.1000"),
            now.minusSeconds(300));
    closedPool.markClosedIfOpen();
    closedPool.addToPool(BetDirection.UP, 200);
    closedPool.addToPool(BetDirection.DOWN, 100);

    BetOrderJpaEntity userWinnerOrder =
        BetOrderJpaEntity.createUserOrder(
            "usr_settlement_1",
            "sp_settlement_ready",
            BetDirection.UP,
            200,
            new BigDecimal("1.7000"),
            tradingDay,
            "idem-settlement-user-win",
            "sp_settlement_ready|UP|200|usr_settlement_1",
            800L);
    BetOrderJpaEntity userLoseOrder =
        BetOrderJpaEntity.createUserOrder(
            "usr_settlement_2",
            "sp_settlement_ready",
            BetDirection.DOWN,
            100,
            new BigDecimal("2.0000"),
            tradingDay,
            "idem-settlement-user-lose",
            "sp_settlement_ready|DOWN|100|usr_settlement_2",
            900L);
    BetOrderJpaEntity houseWinnerOrder =
        BetOrderJpaEntity.createHouseOrder(
            "sp_settlement_ready",
            BetDirection.UP,
            800,
            new BigDecimal("1.7000"),
            tradingDay,
            "HOUSE",
            "house:sp_settlement_ready:2026-02-18:UP");
    BetOrderJpaEntity houseFlatOrder =
        BetOrderJpaEntity.createHouseOrder(
            "sp_settlement_ready",
            BetDirection.FLAT,
            600,
            new BigDecimal("2.1000"),
            tradingDay,
            "HOUSE",
            "house:sp_settlement_ready:2026-02-18:FLAT");
    BetOrderJpaEntity houseDownOrder =
        BetOrderJpaEntity.createHouseOrder(
            "sp_settlement_ready",
            BetDirection.DOWN,
            600,
            new BigDecimal("2.1000"),
            tradingDay,
            "HOUSE",
            "house:sp_settlement_ready:2026-02-18:DOWN");

    when(betPoolJpaRepository.findBySpecimenIdAndDateForUpdate("sp_settlement_ready", tradingDay))
        .thenReturn(Optional.of(closedPool));
    when(eloDailySnapshotJpaRepository.findBySpecimenIdAndDate("sp_settlement_ready", tradingDay))
        .thenReturn(
            Optional.of(
                EloDailySnapshotJpaEntity.create(
                    "sp_settlement_ready", tradingDay, 1500, 1510, 10, 8, 1)));
    when(specimenRatingJpaRepository.findById("sp_settlement_ready"))
        .thenReturn(Optional.of(createIpoRating("sp_settlement_ready")));
    when(betOrderJpaRepository.findBySpecimenIdAndSettleDateAndStatusOrderByCreatedAtAscOrderIdAsc(
            "sp_settlement_ready", tradingDay, BetOrderStatus.PENDING))
        .thenReturn(
            List.of(
                userWinnerOrder,
                userLoseOrder,
                houseWinnerOrder,
                houseFlatOrder,
                houseDownOrder));
    when(economyWalletService.credit(any(), any())).thenReturn(1000L);

    BettingService.ClosedPoolSettlementResult result =
        bettingService.settleClosedPool("sp_settlement_ready", tradingDay, now);

    assertThat(result.outcome()).isEqualTo("SETTLED_UP");
    assertThat(result.preconditionsPassed()).isTrue();
    assertThat(result.settled()).isTrue();
    assertThat(closedPool.getStatus().name()).isEqualTo("SETTLED");
    assertThat(userWinnerOrder.getStatus().name()).isEqualTo("WON");
    assertThat(userWinnerOrder.getPayout()).isEqualTo(414L);
    assertThat(userLoseOrder.getStatus().name()).isEqualTo("LOST");
    assertThat(userLoseOrder.getPayout()).isEqualTo(0L);
    assertThat(houseWinnerOrder.getStatus().name()).isEqualTo("WON");
    assertThat(houseWinnerOrder.getPayout()).isEqualTo(1656L);

    verify(economyWalletService)
        .credit(
            argThat(
                command ->
                    command.userId().equals("usr_settlement_1")
                        && command.amount() == 414
                        && command.refType().equals("BET")
                        && command.refId().equals(userWinnerOrder.getOrderId())
                        && command
                            .idempotencyKey()
                            .equals("betwin_" + userWinnerOrder.getOrderId())),
            eq(EconomyLedgerType.BET_WIN));
    verify(economyWalletService)
        .credit(
            argThat(
                command ->
                    command.userId().equals("HOUSE")
                        && command.amount() == 1656
                        && command.refType().equals("SYSTEM")
                        && command.refId().equals("sp_settlement_ready:2026-02-18")
                        && command
                            .idempotencyKey()
                            .equals("housewin_" + houseWinnerOrder.getOrderId())),
            eq(EconomyLedgerType.HOUSE_WIN));
    verify(economyWalletService)
        .credit(
            argThat(
                command ->
                    command.userId().equals("HOUSE")
                        && command.amount() == 230
                        && command.refType().equals("SYSTEM")
                        && command.refId().equals("sp_settlement_ready:2026-02-18")
                        && command.idempotencyKey().equals("rake_sp_settlement_ready:2026-02-18")),
            eq(EconomyLedgerType.RAKE));
    verify(outboxEventStore)
        .append(
            argThat(
                (OutboxEventCommand command) ->
                    command.eventType().equals("SettlementCompletedEvent")
                        && command.aggregateId().equals("sp_settlement_ready:2026-02-18")));
    verify(outboxEventStore, never())
        .append(
            argThat(
                (OutboxEventCommand command) ->
                    command.eventType().equals("MoonDoomEvent")));
  }

  @Test
  void settleClosedPool_shouldSettleWithoutPendingOrders() {
    Instant now = Instant.parse("2026-02-19T00:06:00Z");
    LocalDate tradingDay = LocalDate.parse("2026-02-18");
    BetPoolJpaEntity closedPool =
        BetPoolJpaEntity.createOpen(
            "sp_settlement_empty",
            tradingDay,
            800L,
            600L,
            600L,
            new BigDecimal("0.1000"),
            now.minusSeconds(300));
    closedPool.markClosedIfOpen();

    when(betPoolJpaRepository.findBySpecimenIdAndDateForUpdate("sp_settlement_empty", tradingDay))
        .thenReturn(Optional.of(closedPool));
    when(eloDailySnapshotJpaRepository.findBySpecimenIdAndDate("sp_settlement_empty", tradingDay))
        .thenReturn(
            Optional.of(
                EloDailySnapshotJpaEntity.create(
                    "sp_settlement_empty", tradingDay, 1500, 1510, 10, 8, 1)));
    when(betOrderJpaRepository.findBySpecimenIdAndSettleDateAndStatusOrderByCreatedAtAscOrderIdAsc(
            "sp_settlement_empty", tradingDay, BetOrderStatus.PENDING))
        .thenReturn(List.of());

    BettingService.ClosedPoolSettlementResult result =
        bettingService.settleClosedPool("sp_settlement_empty", tradingDay, now);

    assertThat(result.outcome()).isEqualTo("POOL_SETTLED_WITHOUT_PENDING_ORDERS");
    assertThat(result.preconditionsPassed()).isTrue();
    assertThat(result.settled()).isTrue();
    assertThat(closedPool.getStatus().name()).isEqualTo("SETTLED");
    verifyNoInteractions(economyWalletService);
    verify(outboxEventStore)
        .append(
            argThat(
                (OutboxEventCommand command) ->
                    command.eventType().equals("SettlementCompletedEvent")
                        && command.aggregateId().equals("sp_settlement_empty:2026-02-18")));
  }

  @Test
  void settleClosedPool_shouldReportSnapshotMissingWhenPreconditionFails() {
    Instant now = Instant.parse("2026-02-19T00:06:30Z");
    LocalDate tradingDay = LocalDate.parse("2026-02-18");
    BetPoolJpaEntity closedPool =
        BetPoolJpaEntity.createOpen(
            "sp_settlement_missing",
            tradingDay,
            800L,
            600L,
            600L,
            new BigDecimal("0.1000"),
            now.minusSeconds(300));
    closedPool.markClosedIfOpen();

    when(betPoolJpaRepository.findBySpecimenIdAndDateForUpdate("sp_settlement_missing", tradingDay))
        .thenReturn(Optional.of(closedPool));
    when(eloDailySnapshotJpaRepository.findBySpecimenIdAndDate("sp_settlement_missing", tradingDay))
        .thenReturn(Optional.empty());

    BettingService.ClosedPoolSettlementResult result =
        bettingService.settleClosedPool("sp_settlement_missing", tradingDay, now);

    assertThat(result.outcome()).isEqualTo("SNAPSHOT_MISSING");
    assertThat(result.preconditionsPassed()).isFalse();
    assertThat(result.settled()).isFalse();
  }

  private SpecimenRatingJpaEntity createIpoRating(String specimenId) {
    SpecimenRatingJpaEntity rating = SpecimenRatingJpaEntity.createDefault(specimenId, 1500);
    for (int i = 0; i < 10; i++) {
      rating.applyVoteDelta(0);
    }
    return rating;
  }
}
