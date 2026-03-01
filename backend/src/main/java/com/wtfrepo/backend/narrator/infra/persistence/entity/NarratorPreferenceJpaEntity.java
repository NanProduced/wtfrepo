package com.wtfrepo.backend.narrator.infra.persistence.entity;

import com.wtfrepo.backend.narrator.domain.NarratorMode;
import com.wtfrepo.backend.narrator.domain.NarratorTone;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

/** User narrator/ticker preference snapshot for M09 behavior control. */
@Getter
@Entity
@Table(name = "narrator_preference")
public class NarratorPreferenceJpaEntity {

  @Id
  @Column(name = "user_id", nullable = false, length = 64)
  private String userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "mode", nullable = false, length = 16)
  private NarratorMode mode;

  @Column(name = "mode_is_explicit", nullable = false)
  private boolean modeExplicit;

  @Enumerated(EnumType.STRING)
  @Column(name = "tone_preference", nullable = false, length = 16)
  private NarratorTone tonePreference;

  @Column(name = "tone_is_explicit", nullable = false)
  private boolean toneExplicit;

  @Column(name = "eye_follow_enabled", nullable = false)
  private boolean eyeFollowEnabled;

  @Column(name = "ticker_enabled", nullable = false)
  private boolean tickerEnabled;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected NarratorPreferenceJpaEntity() {}

  public static NarratorPreferenceJpaEntity createDefault(String userId) {
    Instant now = Instant.now();
    NarratorPreferenceJpaEntity entity = new NarratorPreferenceJpaEntity();
    entity.userId = userId;
    entity.mode = NarratorMode.FULL;
    entity.modeExplicit = false;
    entity.tonePreference = NarratorTone.SAFE;
    entity.toneExplicit = false;
    entity.eyeFollowEnabled = true;
    entity.tickerEnabled = true;
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  public boolean applyMode(NarratorMode nextMode, boolean explicit) {
    if (nextMode == null) {
      return false;
    }
    boolean changed = mode != nextMode || modeExplicit != explicit;
    if (changed) {
      mode = nextMode;
      modeExplicit = explicit;
      touch();
    }
    return changed;
  }

  public boolean applyTone(NarratorTone nextTone, boolean explicit) {
    if (nextTone == null) {
      return false;
    }
    boolean changed = tonePreference != nextTone || toneExplicit != explicit;
    if (changed) {
      tonePreference = nextTone;
      toneExplicit = explicit;
      touch();
    }
    return changed;
  }

  public boolean applyEyeFollowEnabled(Boolean enabled) {
    if (enabled == null) {
      return false;
    }
    boolean changed = eyeFollowEnabled != enabled;
    if (changed) {
      eyeFollowEnabled = enabled;
      touch();
    }
    return changed;
  }

  public boolean applyTickerEnabled(Boolean enabled) {
    if (enabled == null) {
      return false;
    }
    boolean changed = tickerEnabled != enabled;
    if (changed) {
      tickerEnabled = enabled;
      touch();
    }
    return changed;
  }

  private void touch() {
    updatedAt = Instant.now();
  }
}
