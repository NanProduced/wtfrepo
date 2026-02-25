package com.wtfrepo.backend.specimen.application;

import com.wtfrepo.backend.specimen.infra.persistence.entity.SpecimenArenaMetricsJpaEntity;
import com.wtfrepo.backend.specimen.infra.persistence.repository.SpecimenArenaMetricsJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Applies arena vote-derived metrics updates for specimen read models. */
@Service
public class SpecimenArenaMetricsEventConsumerService {

  private static final Logger log =
      LoggerFactory.getLogger(SpecimenArenaMetricsEventConsumerService.class);

  private final SpecimenArenaMetricsJpaRepository metricsRepository;
  private final SpecimenContractProperties contractProperties;

  public SpecimenArenaMetricsEventConsumerService(
      SpecimenArenaMetricsJpaRepository metricsRepository,
      SpecimenContractProperties contractProperties) {
    this.metricsRepository = metricsRepository;
    this.contractProperties = contractProperties;
  }

  @Transactional
  public void onEloUpdated(String eventId, String specimenId, int eloBefore, int eloAfter) {
    if (!StringUtils.hasText(specimenId)) {
      log.warn("specimen_elo_update_skip eventId={} reason=missing_specimen_id", eventId);
      return;
    }

    SpecimenArenaMetricsJpaEntity metrics = loadForUpdateOrCreate(specimenId.trim());
    metrics.applyEloUpdate(eloBefore, eloAfter);
    metricsRepository.save(metrics);
  }

  private SpecimenArenaMetricsJpaEntity loadForUpdateOrCreate(String specimenId) {
    SpecimenArenaMetricsJpaEntity existing =
        metricsRepository.findBySpecimenIdForUpdate(specimenId).orElse(null);
    if (existing != null) {
      return existing;
    }

    SpecimenArenaMetricsJpaEntity created =
        SpecimenArenaMetricsJpaEntity.createDefault(
            specimenId, contractProperties.getDefaultElo());
    try {
      return metricsRepository.saveAndFlush(created);
    } catch (DataIntegrityViolationException ex) {
      return metricsRepository.findBySpecimenIdForUpdate(specimenId).orElse(created);
    }
  }
}
