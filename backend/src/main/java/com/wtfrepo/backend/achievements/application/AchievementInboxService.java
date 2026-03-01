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
  private final AchievementEvaluationService evaluationService;

  public AchievementInboxService(
      AchievementInboxEventJpaRepository repository,
      AchievementEvaluationService evaluationService) {
    this.repository = repository;
    this.evaluationService = evaluationService;
  }

  @Transactional
  public void recordVoteCompleted(OutboxStreamMessage message) {
    recordInboundEvent(message);
  }

  @Transactional
  public void recordWatchlistAdded(OutboxStreamMessage message) {
    recordInboundEvent(message);
  }

  @Transactional
  public void recordHypeParticipated(OutboxStreamMessage message) {
    recordInboundEvent(message);
  }

  @Transactional
  public void recordNarratorPreferenceChanged(OutboxStreamMessage message) {
    recordInboundEvent(message);
  }

  @Transactional
  public void recordTickerItemClicked(OutboxStreamMessage message) {
    recordInboundEvent(message);
  }

  private void recordInboundEvent(OutboxStreamMessage message) {
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
      AchievementInboxEventJpaEntity saved = repository.save(entity);
      evaluationService.evaluateInboundEvent(saved);
    } catch (DataIntegrityViolationException ex) {
      if (repository.existsById(message.eventId())) {
        return;
      }
      throw ex;
    }
  }
}
