package com.wtfrepo.backend.admin.application;

import com.wtfrepo.backend.admin.application.model.AdminModels.AdminAuditLogEntry;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminPrincipal;
import com.wtfrepo.backend.admin.application.support.AdminConstants;
import com.wtfrepo.backend.admin.application.support.AdminExceptions;
import com.wtfrepo.backend.admin.application.support.AdminRequestFingerprintCalculator;
import com.wtfrepo.backend.admin.domain.AdminAuditActions;
import com.wtfrepo.backend.admin.domain.AdminAuditTargetType;
import com.wtfrepo.backend.economy.application.BettingService;
import com.wtfrepo.backend.economy.application.BettingService.ForceSettleResult;
import com.wtfrepo.backend.economy.application.BettingService.HouseConfigRecord;
import com.wtfrepo.backend.shared.idempotency.IdempotentOperationExecutor;
import com.wtfrepo.backend.shared.idempotency.IdempotencyConflictException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Admin betting workflows for force-settle and house config management. */
@Service
public class AdminBettingService {

  private static final Logger log = LoggerFactory.getLogger(AdminBettingService.class);

  private static final BigDecimal WEIGHT_SUM = BigDecimal.ONE;
  private static final BigDecimal WEIGHT_TOLERANCE = new BigDecimal("0.001");

  private final BettingService bettingService;
  private final AdminAuditLogStore adminAuditLogStore;
  private final AdminRequestFingerprintCalculator requestFingerprintCalculator;
  private final IdempotentOperationExecutor idempotentExecutor;

  public AdminBettingService(
      BettingService bettingService,
      AdminAuditLogStore adminAuditLogStore,
      AdminRequestFingerprintCalculator requestFingerprintCalculator,
      IdempotentOperationExecutor idempotentExecutor) {
    this.bettingService = bettingService;
    this.adminAuditLogStore = adminAuditLogStore;
    this.requestFingerprintCalculator = requestFingerprintCalculator;
    this.idempotentExecutor = idempotentExecutor;
  }

  public AdminForceSettleResult forceSettle(
      AdminPrincipal principal,
      String requestId,
      String idempotencyKey,
      String specimenId,
      String reason,
      boolean confirm,
      String ipAddress,
      String userAgent) {
    String resolvedKey = resolveIdempotencyKey(requestId, idempotencyKey);
    validateIdempotencyKey(resolvedKey);
    String normalizedSpecimenId = normalizeSpecimenId(specimenId);
    if (!confirm) {
      throw AdminExceptions.validation(AdminConstants.Message.INVALID_BETTING_FORCE_SETTLE_REQUEST);
    }
    String normalizedReason =
        StringUtils.hasText(reason) ? reason.trim() : "ADMIN_FORCE_SETTLE";

    String fingerprint =
        requestFingerprintCalculator.fingerprint(
            "admin_betting_force_settle|"
                + principal.userId()
                + "|"
                + normalizedSpecimenId
                + "|"
                + normalizedReason);
    try {
      return idempotentExecutor
          .execute(
              "admin:betting:force-settle",
              resolvedKey,
              fingerprint,
              () -> {
                ForceSettleResult result =
                    bettingService.forceSettleByAdmin(
                        normalizedSpecimenId, normalizedReason, Instant.now());
                AdminForceSettleResult response = AdminForceSettleResult.from(result);
                appendAuditLog(
                    principal.userId(),
                    AdminAuditActions.BETTING_FORCE_SETTLE,
                    AdminAuditTargetType.SPECIMEN,
                    normalizedSpecimenId,
                    null,
                    response,
                    new ForceSettleAuditMeta(
                        normalizedSpecimenId,
                        normalizedReason,
                        result.outcome(),
                        result.poolStatusBefore(),
                        result.poolStatusAfter(),
                        result.cancelledOrders(),
                        result.refundedOrders(),
                        result.refundedAmount()),
                    requestId,
                    ipAddress,
                    userAgent);
                log.info(
                    "admin_betting_force_settle requestId={} operatorId={} specimenId={} outcome={}",
                    requestId,
                    principal.userId(),
                    normalizedSpecimenId,
                    result.outcome());
                return response;
              })
          .response();
    } catch (IdempotencyConflictException ex) {
      throw AdminExceptions.conflict(AdminConstants.Message.IDEMPOTENCY_CONFLICT);
    }
  }

  public HouseConfigUpdateResult updateHouseConfig(
      AdminPrincipal principal,
      String requestId,
      String idempotencyKey,
      String specimenId,
      long houseBudget,
      BigDecimal weightUp,
      BigDecimal weightFlat,
      BigDecimal weightDown,
      String ipAddress,
      String userAgent) {
    String resolvedKey = resolveIdempotencyKey(requestId, idempotencyKey);
    validateIdempotencyKey(resolvedKey);
    String normalizedSpecimenId = normalizeSpecimenIdForHouseConfig(specimenId);

    if (houseBudget <= 0) {
      throw AdminExceptions.validation(AdminConstants.Message.INVALID_BETTING_HOUSE_CONFIG_REQUEST);
    }
    BigDecimal normalizedUp = normalizeWeight(weightUp);
    BigDecimal normalizedFlat = normalizeWeight(weightFlat);
    BigDecimal normalizedDown = normalizeWeight(weightDown);
    BigDecimal sum = normalizedUp.add(normalizedFlat).add(normalizedDown);
    if (sum.subtract(WEIGHT_SUM).abs().compareTo(WEIGHT_TOLERANCE) > 0) {
      throw AdminExceptions.validation(AdminConstants.Message.INVALID_BETTING_HOUSE_CONFIG_REQUEST);
    }

    String fingerprint =
        requestFingerprintCalculator.fingerprint(
            "admin_betting_house_config|"
                + principal.userId()
                + "|"
                + normalizedSpecimenId
                + "|"
                + houseBudget
                + "|"
                + normalizedUp
                + "|"
                + normalizedFlat
                + "|"
                + normalizedDown);
    try {
      return idempotentExecutor
          .execute(
              "admin:betting:house-config",
              resolvedKey,
              fingerprint,
              () -> {
                HouseConfigRecord before = bettingService.findHouseConfig(normalizedSpecimenId);
                HouseConfigRecord after =
                    bettingService.updateHouseConfig(
                        normalizedSpecimenId,
                        houseBudget,
                        normalizedUp,
                        normalizedFlat,
                        normalizedDown,
                        principal.userId());
                HouseConfigUpdateResult response = new HouseConfigUpdateResult(before, after);
                appendAuditLog(
                    principal.userId(),
                    AdminAuditActions.BETTING_HOUSE_CONFIG_UPDATE,
                    AdminAuditTargetType.SPECIMEN,
                    normalizedSpecimenId,
                    before,
                    after,
                    new HouseConfigAuditMeta(
                        normalizedSpecimenId,
                        after.houseBudget(),
                        after.weightUp(),
                        after.weightFlat(),
                        after.weightDown()),
                    requestId,
                    ipAddress,
                    userAgent);
                log.info(
                    "admin_betting_house_config requestId={} operatorId={} specimenId={}",
                    requestId,
                    principal.userId(),
                    normalizedSpecimenId);
                return response;
              })
          .response();
    } catch (IdempotencyConflictException ex) {
      throw AdminExceptions.conflict(AdminConstants.Message.IDEMPOTENCY_CONFLICT);
    }
  }

  private void appendAuditLog(
      String operatorId,
      String action,
      String targetType,
      String targetId,
      Object beforeSnapshot,
      Object afterSnapshot,
      Object metadata,
      String requestId,
      String ipAddress,
      String userAgent) {
    AdminAuditLogEntry entry =
        new AdminAuditLogEntry(
            "aal_" + UUID.randomUUID(),
            operatorId,
            action,
            targetType,
            targetId,
            beforeSnapshot,
            afterSnapshot,
            metadata,
            requestId,
            ipAddress,
            userAgent,
            Instant.now());
    adminAuditLogStore.append(entry);
  }

  private String resolveIdempotencyKey(String requestId, String idempotencyKey) {
    if (StringUtils.hasText(idempotencyKey)) {
      return idempotencyKey.trim();
    }
    return requestId;
  }

  private void validateIdempotencyKey(String idempotencyKey) {
    if (!StringUtils.hasText(idempotencyKey) || idempotencyKey.length() > 128) {
      throw AdminExceptions.validation(AdminConstants.Message.INVALID_IDEMPOTENCY_KEY);
    }
  }

  private String normalizeSpecimenId(String specimenId) {
    if (!StringUtils.hasText(specimenId)) {
      throw AdminExceptions.validation(AdminConstants.Message.INVALID_BETTING_FORCE_SETTLE_REQUEST);
    }
    return specimenId.trim();
  }

  private String normalizeSpecimenIdForHouseConfig(String specimenId) {
    if (!StringUtils.hasText(specimenId)) {
      throw AdminExceptions.validation(AdminConstants.Message.INVALID_BETTING_HOUSE_CONFIG_REQUEST);
    }
    return specimenId.trim();
  }

  private BigDecimal normalizeWeight(BigDecimal value) {
    if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
      throw AdminExceptions.validation(AdminConstants.Message.INVALID_BETTING_HOUSE_CONFIG_REQUEST);
    }
    return value.setScale(4, RoundingMode.HALF_UP);
  }

  public record AdminForceSettleResult(
      String tradingDay,
      String outcome,
      String poolStatusBefore,
      String poolStatusAfter,
      int cancelledOrders,
      int refundedOrders,
      long refundedAmount,
      boolean settled) {

    static AdminForceSettleResult from(ForceSettleResult result) {
      return new AdminForceSettleResult(
          result.tradingDay() != null ? result.tradingDay().toString() : null,
          result.outcome(),
          result.poolStatusBefore(),
          result.poolStatusAfter(),
          result.cancelledOrders(),
          result.refundedOrders(),
          result.refundedAmount(),
          result.settled());
    }
  }

  public record HouseConfigUpdateResult(
      HouseConfigRecord before,
      HouseConfigRecord after) {}

  private record ForceSettleAuditMeta(
      String specimenId,
      String reason,
      String outcome,
      String poolStatusBefore,
      String poolStatusAfter,
      int cancelledOrders,
      int refundedOrders,
      long refundedAmount) {}

  private record HouseConfigAuditMeta(
      String specimenId,
      long houseBudget,
      BigDecimal weightUp,
      BigDecimal weightFlat,
      BigDecimal weightDown) {}
}
