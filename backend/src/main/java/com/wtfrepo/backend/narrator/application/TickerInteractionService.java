package com.wtfrepo.backend.narrator.application;

import com.wtfrepo.backend.narrator.application.model.NarratorModels;
import com.wtfrepo.backend.narrator.application.support.NarratorConstants;
import com.wtfrepo.backend.narrator.application.support.NarratorExceptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Write service for user interactions on ticker items. */
@Service
public class TickerInteractionService {

  private final NarratorOutboxEventPublisher outboxEventPublisher;

  public TickerInteractionService(NarratorOutboxEventPublisher outboxEventPublisher) {
    this.outboxEventPublisher = outboxEventPublisher;
  }

  @Transactional
  public void recordTickerItemClick(
      String userId, NarratorModels.TickerItemClickCommand command, String idempotencyKey) {
    String normalizedUserId = requireText(userId);
    String normalizedIdempotencyKey = requireIdempotencyKey(idempotencyKey);
    NarratorModels.TickerItemClickCommand normalizedCommand = requireCommand(command);

    outboxEventPublisher.publishTickerItemClicked(
        normalizedUserId,
        normalizedCommand.eventId().trim(),
        normalizedCommand.eventType().trim(),
        normalizedCommand.actionUrl(),
        normalizedIdempotencyKey);
  }

  private String requireText(String value) {
    if (!StringUtils.hasText(value)) {
      throw NarratorExceptions.unauthorized(NarratorConstants.Message.AUTH_REQUIRED);
    }
    return value.trim();
  }

  private String requireIdempotencyKey(String idempotencyKey) {
    if (!StringUtils.hasText(idempotencyKey)) {
      throw NarratorExceptions.validation(NarratorConstants.Message.INVALID_IDEMPOTENCY_KEY);
    }
    return idempotencyKey.trim();
  }

  private NarratorModels.TickerItemClickCommand requireCommand(
      NarratorModels.TickerItemClickCommand command) {
    if (command == null
        || !StringUtils.hasText(command.eventId())
        || !StringUtils.hasText(command.eventType())) {
      throw NarratorExceptions.validation(NarratorConstants.Message.INVALID_PAYLOAD);
    }
    return command;
  }
}
