package com.wtfrepo.backend.achievements.application;

import com.wtfrepo.backend.achievements.infra.persistence.entity.AchievementInboxEventJpaEntity;
import com.wtfrepo.backend.achievements.infra.persistence.repository.AchievementInboxEventJpaRepository;
import com.wtfrepo.backend.shared.outbox.OutboxStreamMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Inbox service for achievement-triggering outbox events. */
@Service
public class AchievementInboxService {

  private static final Logger log = LoggerFactory.getLogger(AchievementInboxService.class);

  private final AchievementInboxEventJpaRepository repository;

  public AchievementInboxService(AchievementInboxEventJpaRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public void recordVoteCompleted(OutboxStreamMessage message) {
    if (message == null || !StringUtils.hasText(message.eventId())) {
      log.warn("achievement_inbox_skip reason=missing_event_id");
      return;
    }

    AchievementInboxEventJpaEntity entity =
        AchievementInboxEventJpaEntity.of(
            message.eventId(),
            message.eventType(),
            message.eventKey(),
            message.aggregateType(),
            message.aggregateId(),
            StringUtils.hasText(message.payload()) ? message.payload() : "{}",
            message.occurredAt());

    try {
      repository.save(entity);
    } catch (DataIntegrityViolationException ex) {
      if (repository.existsById(message.eventId())) {
        return;
      }
      throw ex;
    }
  }
}
