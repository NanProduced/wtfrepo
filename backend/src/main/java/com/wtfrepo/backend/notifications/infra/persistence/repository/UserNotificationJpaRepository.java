package com.wtfrepo.backend.notifications.infra.persistence.repository;

import com.wtfrepo.backend.notifications.domain.NotificationStatus;
import com.wtfrepo.backend.notifications.domain.NotificationType;
import com.wtfrepo.backend.notifications.infra.persistence.entity.UserNotificationJpaEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserNotificationJpaRepository extends JpaRepository<UserNotificationJpaEntity, Long> {

  Optional<UserNotificationJpaEntity> findByNotificationUid(String notificationUid);

  boolean existsByReceiverUserIdAndTypeAndDedupeKey(
      String receiverUserId, NotificationType type, String dedupeKey);

  @Query(
      """
      select n
        from UserNotificationJpaEntity n
       where n.receiverUserId = :receiverUserId
         and n.status in :statuses
         and (:type is null or n.type = :type)
         and (
             :cursorCreatedAt is null
             or n.createdAt < :cursorCreatedAt
             or (n.createdAt = :cursorCreatedAt and n.id < :cursorId)
         )
       order by n.createdAt desc, n.id desc
      """)
  List<UserNotificationJpaEntity> findPageAfterCursor(
      @Param("receiverUserId") String receiverUserId,
      @Param("statuses") Collection<NotificationStatus> statuses,
      @Param("type") NotificationType type,
      @Param("cursorCreatedAt") Instant cursorCreatedAt,
      @Param("cursorId") Long cursorId,
      Pageable pageable);

  List<UserNotificationJpaEntity> findByReceiverUserIdAndStatusInOrderByCreatedAtDesc(
      String receiverUserId, Collection<NotificationStatus> statuses, Pageable pageable);

  List<UserNotificationJpaEntity> findByReceiverUserIdAndStatusAndTypeOrderByCreatedAtDesc(
      String receiverUserId, NotificationStatus status, NotificationType type, Pageable pageable);

  List<UserNotificationJpaEntity> findByReceiverUserIdAndTypeOrderByCreatedAtDesc(
      String receiverUserId, NotificationType type, Pageable pageable);

  long countByReceiverUserIdAndStatus(String receiverUserId, NotificationStatus status);

  @Modifying
  @Query(
      """
      update UserNotificationJpaEntity n
         set n.status = :status,
             n.readAt = :readAt,
             n.updatedAt = :updatedAt
       where n.receiverUserId = :receiverUserId
         and n.status = :currentStatus
      """)
  int markAllRead(
      @Param("receiverUserId") String receiverUserId,
      @Param("currentStatus") NotificationStatus currentStatus,
      @Param("status") NotificationStatus status,
      @Param("readAt") Instant readAt,
      @Param("updatedAt") Instant updatedAt);

  @Modifying
  @Query("delete from UserNotificationJpaEntity n where n.createdAt < :cutoff")
  int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
