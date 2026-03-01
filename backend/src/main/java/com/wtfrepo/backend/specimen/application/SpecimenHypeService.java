package com.wtfrepo.backend.specimen.application;

import com.wtfrepo.backend.specimen.application.support.SpecimenConstants;
import com.wtfrepo.backend.specimen.application.support.SpecimenExceptions;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenJpaRepository;
import java.time.Instant;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Placeholder hype participation write path with outbox emission for cross-module consumers. */
@Service
public class SpecimenHypeService {

  private final SpecimenJpaRepository specimenJpaRepository;
  private final SpecimenOutboxEventPublisher specimenOutboxEventPublisher;

  public SpecimenHypeService(
      SpecimenJpaRepository specimenJpaRepository,
      SpecimenOutboxEventPublisher specimenOutboxEventPublisher) {
    this.specimenJpaRepository = specimenJpaRepository;
    this.specimenOutboxEventPublisher = specimenOutboxEventPublisher;
  }

  @Transactional
  public HypeParticipationResult participate(
      String userId,
      String specimenId,
      String dimension,
      Instant clientTs,
      String idempotencyKey) {
    requireSpecimen(specimenId);
    String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
    String normalizedDimension =
        StringUtils.hasText(dimension) ? dimension.trim().toUpperCase(Locale.ROOT) : "UNKNOWN";
    Instant participatedAt = Instant.now();

    // Placeholder score values remain unchanged until arena-side hype algorithm is wired.
    double hypeScore = 0.0D;
    double scoreDelta = 0.0D;
    double appliedWeight = 0.0D;

    specimenOutboxEventPublisher.publishHypeParticipated(
        userId,
        specimenId,
        normalizedDimension,
        normalizedIdempotencyKey,
        participatedAt,
        clientTs,
        hypeScore,
        scoreDelta,
        appliedWeight);

    return new HypeParticipationResult(hypeScore, scoreDelta, appliedWeight);
  }

  private void requireSpecimen(String specimenId) {
    if (!specimenJpaRepository.existsById(specimenId)) {
      throw SpecimenExceptions.notFound(SpecimenConstants.Message.SPECIMEN_NOT_FOUND);
    }
  }

  private String normalizeIdempotencyKey(String idempotencyKey) {
    if (!StringUtils.hasText(idempotencyKey)) {
      throw SpecimenExceptions.validation(SpecimenConstants.Message.INVALID_IDEMPOTENCY_KEY);
    }
    return idempotencyKey.trim();
  }

  public record HypeParticipationResult(double hypeScore, double scoreDelta, double appliedWeight) {}
}
