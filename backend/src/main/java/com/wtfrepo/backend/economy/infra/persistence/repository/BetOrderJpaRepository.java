package com.wtfrepo.backend.economy.infra.persistence.repository;

import com.wtfrepo.backend.economy.domain.BetOrderStatus;
import com.wtfrepo.backend.economy.infra.persistence.entity.BetOrderJpaEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BetOrderJpaRepository extends JpaRepository<BetOrderJpaEntity, String> {

  Optional<BetOrderJpaEntity> findByIdempotencyKey(String idempotencyKey);

  List<BetOrderJpaEntity> findByUserIdAndSettleDateAndStatusAndIsHouseFalseOrderByCreatedAtDesc(
      String userId, LocalDate settleDate, BetOrderStatus status);

  long countDistinctUserIdBySpecimenIdAndSettleDateAndIsHouseFalse(
      String specimenId, LocalDate settleDate);

  List<BetOrderJpaEntity> findByUserIdAndIsHouseFalseOrderByCreatedAtDescOrderIdDesc(
      String userId, Pageable pageable);

  @Query(
      """
      SELECT order
      FROM BetOrderJpaEntity order
      WHERE order.userId = :userId
        AND order.isHouse = false
        AND (
          order.createdAt < :cursorCreatedAt
          OR (order.createdAt = :cursorCreatedAt AND order.orderId < :cursorOrderId)
        )
      ORDER BY order.createdAt DESC, order.orderId DESC
      """)
  List<BetOrderJpaEntity> findHistoryPageAfterCursor(
      @Param("userId") String userId,
      @Param("cursorCreatedAt") Instant cursorCreatedAt,
      @Param("cursorOrderId") String cursorOrderId,
      Pageable pageable);
}
