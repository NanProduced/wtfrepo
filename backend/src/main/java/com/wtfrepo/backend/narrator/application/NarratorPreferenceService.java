package com.wtfrepo.backend.narrator.application;

import com.wtfrepo.backend.narrator.application.model.NarratorModels;
import com.wtfrepo.backend.narrator.application.support.NarratorConstants;
import com.wtfrepo.backend.narrator.application.support.NarratorExceptions;
import com.wtfrepo.backend.narrator.domain.NarratorMode;
import com.wtfrepo.backend.narrator.domain.NarratorTone;
import com.wtfrepo.backend.narrator.infra.persistence.entity.NarratorPreferenceJpaEntity;
import com.wtfrepo.backend.narrator.infra.persistence.repository.NarratorPreferenceJpaRepository;
import java.time.Instant;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Application service for narrator preference read/write and login merge flows. */
@Service
public class NarratorPreferenceService {

  private final NarratorPreferenceJpaRepository preferenceRepository;
  private final NarratorOutboxEventPublisher outboxEventPublisher;

  public NarratorPreferenceService(
      NarratorPreferenceJpaRepository preferenceRepository,
      NarratorOutboxEventPublisher outboxEventPublisher) {
    this.preferenceRepository = preferenceRepository;
    this.outboxEventPublisher = outboxEventPublisher;
  }

  @Transactional(readOnly = true)
  public NarratorModels.PreferenceView getPreference(String userId) {
    NarratorPreferenceJpaEntity entity = findOrDefault(userId);
    return toPreferenceView(entity);
  }

  @Transactional
  public NarratorModels.PreferenceView patchPreference(
      String userId, NarratorModels.PatchPreferenceCommand command, String idempotencyKey) {
    String normalizedUserId = requireUserId(userId);
    String normalizedIdempotencyKey = requireIdempotencyKey(idempotencyKey);
    NarratorModels.PatchPreferenceCommand normalizedCommand = requirePatchCommand(command);

    NarratorPreferenceJpaEntity entity =
        preferenceRepository
            .findById(normalizedUserId)
            .orElseGet(() -> NarratorPreferenceJpaEntity.createDefault(normalizedUserId));

    NarratorMode oldMode = entity.getMode();
    NarratorTone oldTone = entity.getTonePreference();

    boolean changed = false;
    changed |= entity.applyMode(parseMode(normalizedCommand.mode()), true);
    changed |= entity.applyTone(parseTone(normalizedCommand.tonePreference()), true);
    changed |= entity.applyEyeFollowEnabled(normalizedCommand.eyeFollowEnabled());
    changed |= entity.applyTickerEnabled(normalizedCommand.tickerEnabled());

    if (!changed && preferenceRepository.existsById(normalizedUserId)) {
      return toPreferenceView(entity);
    }

    NarratorPreferenceJpaEntity saved = preferenceRepository.save(entity);
    if (changed) {
      outboxEventPublisher.publishPreferenceChanged(
          normalizedUserId,
          oldMode,
          saved.getMode(),
          saved.getTonePreference(),
          normalizedIdempotencyKey);
    }
    return toPreferenceView(saved);
  }

  @Transactional
  public NarratorModels.MergeOnLoginResult mergeOnLogin(
      String userId, NarratorModels.MergeOnLoginCommand command, String idempotencyKey) {
    String normalizedUserId = requireUserId(userId);
    String normalizedIdempotencyKey = requireIdempotencyKey(idempotencyKey);
    NarratorModels.MergeOnLoginCommand normalizedCommand = requireMergeCommand(command);

    NarratorPreferenceJpaEntity entity =
        preferenceRepository
            .findById(normalizedUserId)
            .orElseGet(() -> NarratorPreferenceJpaEntity.createDefault(normalizedUserId));

    NarratorMode oldMode = entity.getMode();
    NarratorTone oldTone = entity.getTonePreference();

    NarratorMode localMode = parseMode(normalizedCommand.localMode());
    NarratorTone localTone = parseTone(normalizedCommand.localTonePreference());

    MergeSource modeSource;
    MergeSource toneSource;
    boolean changed = false;

    if (entity.isModeExplicit()) {
      modeSource = MergeSource.SERVER;
    } else if (localMode != null) {
      changed |= entity.applyMode(localMode, true);
      modeSource = MergeSource.LOCAL;
    } else {
      changed |= entity.applyMode(NarratorMode.FULL, true);
      modeSource = MergeSource.DEFAULT;
    }

    if (entity.isToneExplicit()) {
      toneSource = MergeSource.SERVER;
    } else if (localTone != null) {
      changed |= entity.applyTone(localTone, true);
      toneSource = MergeSource.LOCAL;
    } else {
      changed |= entity.applyTone(NarratorTone.SAFE, true);
      toneSource = MergeSource.DEFAULT;
    }

    changed |= entity.applyEyeFollowEnabled(normalizedCommand.localEyeFollowEnabled());
    changed |= entity.applyTickerEnabled(normalizedCommand.localTickerEnabled());

    NarratorPreferenceJpaEntity saved = changed ? preferenceRepository.save(entity) : entity;
    if (changed) {
      outboxEventPublisher.publishPreferenceChanged(
          normalizedUserId,
          oldMode,
          saved.getMode(),
          saved.getTonePreference(),
          normalizedIdempotencyKey);
    }

    return new NarratorModels.MergeOnLoginResult(
        saved.getMode().name(),
        saved.isModeExplicit(),
        saved.getTonePreference().name(),
        saved.isEyeFollowEnabled(),
        saved.isTickerEnabled(),
        false,
        mergeSource(modeSource, toneSource).name());
  }

  private String requireUserId(String userId) {
    if (!StringUtils.hasText(userId)) {
      throw NarratorExceptions.unauthorized(NarratorConstants.Message.AUTH_REQUIRED);
    }
    return userId.trim();
  }

  private String requireIdempotencyKey(String idempotencyKey) {
    if (!StringUtils.hasText(idempotencyKey)) {
      throw NarratorExceptions.validation(NarratorConstants.Message.INVALID_IDEMPOTENCY_KEY);
    }
    return idempotencyKey.trim();
  }

  private NarratorPreferenceJpaEntity findOrDefault(String userId) {
    String normalizedUserId = requireUserId(userId);
    return preferenceRepository
        .findById(normalizedUserId)
        .orElseGet(() -> NarratorPreferenceJpaEntity.createDefault(normalizedUserId));
  }

  private NarratorModels.PatchPreferenceCommand requirePatchCommand(
      NarratorModels.PatchPreferenceCommand command) {
    if (command == null) {
      throw NarratorExceptions.validation(NarratorConstants.Message.INVALID_PAYLOAD);
    }
    return command;
  }

  private NarratorModels.MergeOnLoginCommand requireMergeCommand(
      NarratorModels.MergeOnLoginCommand command) {
    if (command == null) {
      throw NarratorExceptions.validation(NarratorConstants.Message.INVALID_PAYLOAD);
    }
    return command;
  }

  private NarratorMode parseMode(String raw) {
    if (!StringUtils.hasText(raw)) {
      return null;
    }
    try {
      return NarratorMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw NarratorExceptions.validation(NarratorConstants.Message.INVALID_MODE);
    }
  }

  private NarratorTone parseTone(String raw) {
    if (!StringUtils.hasText(raw)) {
      return null;
    }
    try {
      return NarratorTone.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw NarratorExceptions.validation(NarratorConstants.Message.INVALID_TONE);
    }
  }

  private NarratorModels.PreferenceView toPreferenceView(NarratorPreferenceJpaEntity entity) {
    Instant updatedAt = entity.getUpdatedAt();
    return new NarratorModels.PreferenceView(
        entity.getMode().name(),
        entity.isModeExplicit(),
        entity.getTonePreference().name(),
        entity.isEyeFollowEnabled(),
        entity.isTickerEnabled(),
        false,
        updatedAt);
  }

  private MergeSource mergeSource(MergeSource modeSource, MergeSource toneSource) {
    if (modeSource == MergeSource.LOCAL || toneSource == MergeSource.LOCAL) {
      return MergeSource.LOCAL;
    }
    if (modeSource == MergeSource.DEFAULT || toneSource == MergeSource.DEFAULT) {
      return MergeSource.DEFAULT;
    }
    return MergeSource.SERVER;
  }

  private enum MergeSource {
    SERVER,
    LOCAL,
    DEFAULT
  }
}
