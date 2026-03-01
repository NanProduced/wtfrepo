package com.wtfrepo.backend.shared.outbox;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository for outbox rows. */
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, String> {

  boolean existsByEventKey(String eventKey);

  Optional<OutboxEventJpaEntity> findByEventKey(String eventKey);

  long countByStatus(OutboxEventStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select event
      from OutboxEventJpaEntity event
      where event.status = :status
      order by event.createdAt asc
      """)
  List<OutboxEventJpaEntity> findByStatusForUpdate(
      @Param("status") OutboxEventStatus status, Pageable pageable);

  @Query(
      """
      select event
      from OutboxEventJpaEntity event
      where event.eventType in :eventTypes
        and (
          :cursorCreatedAt is null
          or event.createdAt < :cursorCreatedAt
          or (event.createdAt = :cursorCreatedAt and event.eventId < :cursorEventId)
        )
      order by event.createdAt desc, event.eventId desc
      """)
  List<OutboxEventJpaEntity> findTickerRecentCandidates(
      @Param("eventTypes") List<String> eventTypes,
      @Param("cursorCreatedAt") java.time.Instant cursorCreatedAt,
      @Param("cursorEventId") String cursorEventId,
      Pageable pageable);
}
