package com.wtfrepo.backend.narrator.application.model;

import java.time.Instant;
import java.util.List;

/** Shared application models for narrator/ticker module. */
public final class NarratorModels {

  private NarratorModels() {}

  public record PreferenceView(
      String mode,
      boolean modeIsExplicit,
      String tonePreference,
      boolean eyeFollowEnabled,
      boolean tickerEnabled,
      boolean reducedMotionApplied,
      Instant updatedAt) {}

  public record PatchPreferenceCommand(
      String mode, String tonePreference, Boolean eyeFollowEnabled, Boolean tickerEnabled) {}

  public record MergeOnLoginCommand(
      String localMode,
      String localTonePreference,
      Boolean localEyeFollowEnabled,
      Boolean localTickerEnabled) {}

  public record MergeOnLoginResult(
      String mode,
      boolean modeIsExplicit,
      String tonePreference,
      boolean eyeFollowEnabled,
      boolean tickerEnabled,
      boolean reducedMotionApplied,
      String mergedFrom) {}

  public record TickerRecentItem(
      String eventId,
      String eventType,
      String text,
      String priority,
      String actionUrl,
      Instant occurredAt,
      Instant expiresAt) {}

  public record TickerRecentPage(List<TickerRecentItem> items, String nextCursor, boolean hasMore) {}

  public record TickerItemClickCommand(String eventId, String eventType, String actionUrl) {}
}
