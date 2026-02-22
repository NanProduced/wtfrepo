package com.wtfrepo.backend.arena.application.profile;

import com.wtfrepo.backend.specimen.application.SpecimenContractProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Default adapter that reads Arena match profile metadata from specimen contract configuration.
 *
 * <p>M04 specimen module owns published match profile metadata. Arena consumes the published
 * projection through this adapter to avoid keeping a duplicated source in M01.
 */
@Component
@ConditionalOnBean(SpecimenContractProperties.class)
@ConditionalOnMissingBean(ArenaMatchProfilePort.class)
public class SpecimenContractBackedArenaMatchProfilePort implements ArenaMatchProfilePort {

  private static final String UNKNOWN_PROFILE_VERSION = "unknown";
  private static final String DEFAULT_SPECIES_DIMENSION_KEY = "species";
  private static final String DEFAULT_DIAGNOSIS_DIMENSION_KEY = "diagnosis";

  private final SpecimenContractProperties specimenContractProperties;

  public SpecimenContractBackedArenaMatchProfilePort(
      SpecimenContractProperties specimenContractProperties) {
    this.specimenContractProperties = specimenContractProperties;
  }

  @Override
  public ArenaMatchProfileSnapshot currentProfile() {
    return new ArenaMatchProfileSnapshot(
        normalizeProfileVersion(specimenContractProperties.getPublishedMatchProfileVersion()),
        normalizeDimensionKey(
            specimenContractProperties.getMatchSpeciesDimensionKey(),
            DEFAULT_SPECIES_DIMENSION_KEY),
        normalizeDimensionKey(
            specimenContractProperties.getMatchDiagnosisDimensionKey(),
            DEFAULT_DIAGNOSIS_DIMENSION_KEY));
  }

  private String normalizeProfileVersion(String profileVersion) {
    if (!StringUtils.hasText(profileVersion)) {
      return UNKNOWN_PROFILE_VERSION;
    }
    return profileVersion.trim();
  }

  private String normalizeDimensionKey(String dimensionKey, String fallback) {
    if (!StringUtils.hasText(dimensionKey)) {
      return fallback;
    }
    return dimensionKey.trim();
  }
}

