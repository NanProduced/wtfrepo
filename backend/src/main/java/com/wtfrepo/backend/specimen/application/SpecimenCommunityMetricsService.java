package com.wtfrepo.backend.specimen.application;

import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenCommunityMetricsJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenCommunityMetricsJpaRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Applies comment-driven community metrics updates for specimen read models. */
@Service
public class SpecimenCommunityMetricsService {

  private static final Logger log =
      LoggerFactory.getLogger(SpecimenCommunityMetricsService.class);

  private final SpecimenCommunityMetricsJpaRepository repository;

  public SpecimenCommunityMetricsService(SpecimenCommunityMetricsJpaRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public void onCommentCountChanged(
      String eventId, String specimenId, long delta, Instant occurredAt) {
    if (!StringUtils.hasText(specimenId)) {
      log.warn("specimen_comment_count_skip eventId={} reason=missing_specimen_id", eventId);
      return;
    }

    SpecimenCommunityMetricsJpaEntity metrics = loadForUpdateOrCreate(specimenId.trim());
    metrics.applyCommentCountDelta(delta);
    repository.save(metrics);
  }

  @Transactional
  public void onTopRoastUpdated(
      String eventId,
      String specimenId,
      String commentId,
      int resonanceCount,
      boolean chiefConclusion,
      Instant occurredAt) {
    if (!StringUtils.hasText(specimenId) || !StringUtils.hasText(commentId)) {
      log.warn("specimen_top_roast_skip eventId={} reason=missing_fields", eventId);
      return;
    }

    SpecimenCommunityMetricsJpaEntity metrics = loadForUpdateOrCreate(specimenId.trim());
    String normalizedCommentId = commentId.trim();

    if (chiefConclusion) {
      metrics.updateTopRoast(normalizedCommentId, resonanceCount, true);
    } else if (normalizedCommentId.equals(metrics.getTopRoastCommentId())) {
      metrics.clearTopRoast();
    } else {
      return;
    }

    repository.save(metrics);
  }

  private SpecimenCommunityMetricsJpaEntity loadForUpdateOrCreate(String specimenId) {
    SpecimenCommunityMetricsJpaEntity existing =
        repository.findBySpecimenIdForUpdate(specimenId).orElse(null);
    if (existing != null) {
      return existing;
    }

    SpecimenCommunityMetricsJpaEntity created =
        SpecimenCommunityMetricsJpaEntity.createDefault(specimenId);
    try {
      return repository.saveAndFlush(created);
    } catch (DataIntegrityViolationException ex) {
      return repository.findBySpecimenIdForUpdate(specimenId).orElse(created);
    }
  }
}
