package com.wtfrepo.backend.specimen.application;

import com.wtfrepo.backend.specimen.application.support.SpecimenExceptions;
import com.wtfrepo.backend.specimen.application.support.SpecimenConstants;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Minimal match-config publish skeleton for emitting M04 -> M01 rebuild triggers.
 *
 * <p>Current implementation only emits outbox events and does not persist match-profile tables yet.
 */
@Service
public class SpecimenMatchConfigPublishService {

  private static final Set<String> SUPPORTED_CONFIG_TYPES =
      Set.of("MATCH_PROFILE", "SPECIES_ADJACENCY");

  private final SpecimenOutboxEventPublisher specimenOutboxEventPublisher;

  public SpecimenMatchConfigPublishService(SpecimenOutboxEventPublisher specimenOutboxEventPublisher) {
    this.specimenOutboxEventPublisher = specimenOutboxEventPublisher;
  }

  @Transactional
  public void publishMatchProfileChanged(String idempotencyKey, String profileVersion) {
    publish("MATCH_PROFILE", idempotencyKey, profileVersion);
  }

  @Transactional
  public void publishSpeciesAdjacencyChanged(String idempotencyKey, String version) {
    publish("SPECIES_ADJACENCY", idempotencyKey, version);
  }

  @Transactional
  public void publish(String configType, String idempotencyKey, String version) {
    String normalizedType = normalizeConfigType(configType);
    if (!SUPPORTED_CONFIG_TYPES.contains(normalizedType)) {
      throw SpecimenExceptions.validation("invalid_match_config_type");
    }
    if (!StringUtils.hasText(idempotencyKey) || idempotencyKey.length() > 128) {
      throw SpecimenExceptions.validation(SpecimenConstants.Message.INVALID_IDEMPOTENCY_KEY);
    }
    if (!StringUtils.hasText(version)) {
      throw SpecimenExceptions.validation("invalid_match_config_version");
    }

    specimenOutboxEventPublisher.publishMatchConfigChanged(
        normalizedType, version.trim(), idempotencyKey.trim());
  }

  private String normalizeConfigType(String configType) {
    if (!StringUtils.hasText(configType)) {
      return "";
    }
    return configType.trim().toUpperCase(Locale.ROOT);
  }
}
