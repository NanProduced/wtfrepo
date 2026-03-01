package com.wtfrepo.backend.achievements.infra.persistence.repository;

import com.wtfrepo.backend.achievements.infra.persistence.entity.AchievementInboxEventJpaEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AchievementInboxEventJpaRepository
    extends JpaRepository<AchievementInboxEventJpaEntity, String> {

  List<AchievementInboxEventJpaEntity> findByEventTypeAndAggregateIdOrderByReceivedAtDesc(
      String eventType, String aggregateId, Pageable pageable);
}
