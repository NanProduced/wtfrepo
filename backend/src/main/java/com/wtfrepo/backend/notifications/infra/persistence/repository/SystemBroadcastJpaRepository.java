package com.wtfrepo.backend.notifications.infra.persistence.repository;

import com.wtfrepo.backend.notifications.domain.BroadcastStatus;
import com.wtfrepo.backend.notifications.infra.persistence.entity.SystemBroadcastJpaEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SystemBroadcastJpaRepository extends JpaRepository<SystemBroadcastJpaEntity, Long> {

  @Query(
      """
      select b
        from SystemBroadcastJpaEntity b
       where b.status = :status
         and (
              :createdAfter is null
              or b.createdAt > :createdAfter
              or (b.createdAt = :createdAfter and b.id > :lastId)
         )
       order by b.createdAt asc, b.id asc
      """)
  List<SystemBroadcastJpaEntity> findPendingBroadcasts(
      @Param("status") BroadcastStatus status,
      @Param("createdAfter") Instant createdAfter,
      @Param("lastId") Long lastId,
      Pageable pageable);

  @Query(
      """
      select count(b)
        from SystemBroadcastJpaEntity b
       where b.status = :status
         and (
              :createdAfter is null
              or b.createdAt > :createdAfter
              or (b.createdAt = :createdAfter and b.id > :lastId)
         )
      """)
  long countPendingBroadcasts(
      @Param("status") BroadcastStatus status,
      @Param("createdAfter") Instant createdAfter,
      @Param("lastId") Long lastId);
}
