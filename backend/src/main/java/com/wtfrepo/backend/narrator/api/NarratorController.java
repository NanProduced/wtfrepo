package com.wtfrepo.backend.narrator.api;

import com.wtfrepo.backend.narrator.application.NarratorPreferenceService;
import com.wtfrepo.backend.narrator.application.TickerInteractionService;
import com.wtfrepo.backend.narrator.application.TickerQueryService;
import com.wtfrepo.backend.narrator.application.model.NarratorModels;
import com.wtfrepo.backend.narrator.application.support.NarratorConstants;
import com.wtfrepo.backend.narrator.application.support.NarratorExceptions;
import com.wtfrepo.backend.shared.web.RequestIdConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Narrator and ticker API endpoints for M09 Phase-1 contract. */
@RestController
@Validated
@RequestMapping("/api/v1")
public class NarratorController {

  private final NarratorPreferenceService narratorPreferenceService;
  private final TickerInteractionService tickerInteractionService;
  private final TickerQueryService tickerQueryService;

  public NarratorController(
      NarratorPreferenceService narratorPreferenceService,
      TickerInteractionService tickerInteractionService,
      TickerQueryService tickerQueryService) {
    this.narratorPreferenceService = narratorPreferenceService;
    this.tickerInteractionService = tickerInteractionService;
    this.tickerQueryService = tickerQueryService;
  }

  @GetMapping("/me/narrator-preference")
  public ResponseEntity<PreferenceResponse> getPreference(
      @RequestHeader(RequestIdConstants.HEADER_NAME) String requestId,
      @AuthenticationPrincipal Jwt jwt) {
    String userId = requireUserId(jwt);
    NarratorModels.PreferenceView result = narratorPreferenceService.getPreference(userId);
    return ResponseEntity.ok(PreferenceResponse.from(result));
  }

  @PatchMapping("/me/narrator-preference")
  public ResponseEntity<PreferenceResponse> patchPreference(
      @RequestHeader(RequestIdConstants.HEADER_NAME) String requestId,
      @RequestHeader(NarratorConstants.Header.IDEMPOTENCY_KEY) @NotBlank String idempotencyKey,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody PatchPreferenceRequest request) {
    String userId = requireUserId(jwt);
    NarratorModels.PreferenceView result =
        narratorPreferenceService.patchPreference(
            userId,
            new NarratorModels.PatchPreferenceCommand(
                request.mode(),
                request.tonePreference(),
                request.eyeFollowEnabled(),
                request.tickerEnabled()),
            idempotencyKey);
    return ResponseEntity.ok(PreferenceResponse.from(result));
  }

  @PostMapping("/me/narrator-preference/merge-on-login")
  public ResponseEntity<MergeOnLoginResponse> mergeOnLogin(
      @RequestHeader(RequestIdConstants.HEADER_NAME) String requestId,
      @RequestHeader(NarratorConstants.Header.IDEMPOTENCY_KEY) @NotBlank String idempotencyKey,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody MergeOnLoginRequest request) {
    String userId = requireUserId(jwt);
    NarratorModels.MergeOnLoginResult result =
        narratorPreferenceService.mergeOnLogin(
            userId,
            new NarratorModels.MergeOnLoginCommand(
                request.localMode(),
                request.localTonePreference(),
                request.localEyeFollowEnabled(),
                request.localTickerEnabled()),
            idempotencyKey);
    return ResponseEntity.ok(MergeOnLoginResponse.from(result));
  }

  @GetMapping("/ticker/recent")
  public ResponseEntity<TickerRecentResponse> listTickerRecent(
      @RequestHeader(RequestIdConstants.HEADER_NAME) String requestId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) @Min(1) @Max(30) Integer limit) {
    NarratorModels.TickerRecentPage result = tickerQueryService.listRecent(cursor, limit);
    return ResponseEntity.ok(TickerRecentResponse.from(result));
  }

  @PostMapping("/me/ticker-item-clicks")
  public ResponseEntity<Void> recordTickerItemClick(
      @RequestHeader(RequestIdConstants.HEADER_NAME) String requestId,
      @RequestHeader(NarratorConstants.Header.IDEMPOTENCY_KEY) @NotBlank String idempotencyKey,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody TickerItemClickRequest request) {
    String userId = requireUserId(jwt);
    tickerInteractionService.recordTickerItemClick(
        userId,
        new NarratorModels.TickerItemClickCommand(
            request.eventId(), request.eventType(), request.actionUrl()),
        idempotencyKey);
    return ResponseEntity.accepted().build();
  }

  private String requireUserId(Jwt jwt) {
    if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
      throw NarratorExceptions.unauthorized(NarratorConstants.Message.AUTH_REQUIRED);
    }
    return jwt.getSubject().trim();
  }

  public record PatchPreferenceRequest(
      String mode, String tonePreference, Boolean eyeFollowEnabled, Boolean tickerEnabled) {}

  public record MergeOnLoginRequest(
      String localMode,
      String localTonePreference,
      Boolean localEyeFollowEnabled,
      Boolean localTickerEnabled) {}

  public record TickerItemClickRequest(
      @NotBlank String eventId, @NotBlank String eventType, String actionUrl) {}

  public record PreferenceResponse(
      String mode,
      boolean modeIsExplicit,
      String tonePreference,
      boolean eyeFollowEnabled,
      boolean tickerEnabled,
      boolean reducedMotionApplied,
      String updatedAt) {
    static PreferenceResponse from(NarratorModels.PreferenceView value) {
      return new PreferenceResponse(
          value.mode(),
          value.modeIsExplicit(),
          value.tonePreference(),
          value.eyeFollowEnabled(),
          value.tickerEnabled(),
          value.reducedMotionApplied(),
          value.updatedAt() == null ? null : value.updatedAt().toString());
    }
  }

  public record MergeOnLoginResponse(
      String mode,
      boolean modeIsExplicit,
      String tonePreference,
      boolean eyeFollowEnabled,
      boolean tickerEnabled,
      boolean reducedMotionApplied,
      String mergedFrom) {
    static MergeOnLoginResponse from(NarratorModels.MergeOnLoginResult value) {
      return new MergeOnLoginResponse(
          value.mode(),
          value.modeIsExplicit(),
          value.tonePreference(),
          value.eyeFollowEnabled(),
          value.tickerEnabled(),
          value.reducedMotionApplied(),
          value.mergedFrom());
    }
  }

  public record TickerRecentResponse(List<TickerItemResponse> items, String nextCursor, boolean hasMore) {
    static TickerRecentResponse from(NarratorModels.TickerRecentPage value) {
      return new TickerRecentResponse(
          value.items().stream().map(TickerItemResponse::from).toList(),
          value.nextCursor(),
          value.hasMore());
    }
  }

  public record TickerItemResponse(
      String eventId,
      String eventType,
      String text,
      String priority,
      String actionUrl,
      String occurredAt,
      String expiresAt) {
    static TickerItemResponse from(NarratorModels.TickerRecentItem item) {
      return new TickerItemResponse(
          item.eventId(),
          item.eventType(),
          item.text(),
          item.priority(),
          item.actionUrl(),
          item.occurredAt() == null ? null : item.occurredAt().toString(),
          item.expiresAt() == null ? null : item.expiresAt().toString());
    }
  }
}
