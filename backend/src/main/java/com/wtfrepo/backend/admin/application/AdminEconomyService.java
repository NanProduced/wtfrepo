package com.wtfrepo.backend.admin.application;

import com.wtfrepo.backend.admin.application.model.AdminModels.AdminAuditLogEntry;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminPrincipal;
import com.wtfrepo.backend.admin.application.support.AdminConstants;
import com.wtfrepo.backend.admin.application.support.AdminExceptions;
import com.wtfrepo.backend.admin.application.support.AdminRequestFingerprintCalculator;
import com.wtfrepo.backend.admin.domain.AdminAuditActions;
import com.wtfrepo.backend.admin.domain.AdminAuditTargetType;
import com.wtfrepo.backend.auth.application.AuthUserStore;
import com.wtfrepo.backend.economy.application.EconomyWalletService;
import com.wtfrepo.backend.economy.application.EconomyWalletService.InsufficientBalanceException;
import com.wtfrepo.backend.economy.application.support.EconomyConstants;
import com.wtfrepo.backend.shared.idempotency.IdempotentOperationExecutor;
import com.wtfrepo.backend.shared.idempotency.IdempotencyConflictException;
import com.wtfrepo.backend.shared.web.ApiException;
import com.wtfrepo.backend.shared.web.ErrorCode;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Admin economy workflows for manual grant/revoke operations. */
@Service
public class AdminEconomyService {

  private static final Logger log = LoggerFactory.getLogger(AdminEconomyService.class);

  private final EconomyWalletService economyWalletService;
  private final AuthUserStore authUserStore;
  private final AdminAuditLogStore adminAuditLogStore;
  private final AdminRequestFingerprintCalculator requestFingerprintCalculator;
  private final IdempotentOperationExecutor idempotentExecutor;

  public AdminEconomyService(
      EconomyWalletService economyWalletService,
      AuthUserStore authUserStore,
      AdminAuditLogStore adminAuditLogStore,
      AdminRequestFingerprintCalculator requestFingerprintCalculator,
      IdempotentOperationExecutor idempotentExecutor) {
    this.economyWalletService = economyWalletService;
    this.authUserStore = authUserStore;
    this.adminAuditLogStore = adminAuditLogStore;
    this.requestFingerprintCalculator = requestFingerprintCalculator;
    this.idempotentExecutor = idempotentExecutor;
  }

  public AdminGrantBugResult grantBug(
      AdminPrincipal principal,
      String requestId,
      String idempotencyKey,
      String userId,
      int delta,
      String reason,
      String note,
      String ipAddress,
      String userAgent) {
    String resolvedKey = resolveIdempotencyKey(requestId, idempotencyKey);
    validateIdempotencyKey(resolvedKey);

    String normalizedUserId = normalizeUserId(userId);
    if (authUserStore.findByUserId(normalizedUserId).isEmpty()) {
      throw AdminExceptions.notFound(AdminConstants.Message.USER_NOT_FOUND);
    }

    if (delta == 0) {
      throw AdminExceptions.validation(AdminConstants.Message.INVALID_ECONOMY_GRANT_REQUEST);
    }
    String normalizedReason = normalizeReason(reason);
    boolean grant = delta > 0;
    if (grant && !"ADMIN_GRANT".equals(normalizedReason)) {
      throw AdminExceptions.validation(AdminConstants.Message.INVALID_ECONOMY_GRANT_REQUEST);
    }
    if (!grant && !"ADMIN_REVOKE".equals(normalizedReason)) {
      throw AdminExceptions.validation(AdminConstants.Message.INVALID_ECONOMY_GRANT_REQUEST);
    }

    String fingerprint =
        requestFingerprintCalculator.fingerprint(
            "admin_grant_bug|"
                + principal.userId()
                + "|"
                + normalizedUserId
                + "|"
                + delta
                + "|"
                + normalizedReason);
    try {
      return idempotentExecutor
          .execute(
              "admin:economy:grant",
              resolvedKey,
              fingerprint,
              () -> {
                EconomyWalletService.LedgerWriteResult ledgerResult =
                    executeGrant(
                        normalizedUserId,
                        delta,
                        normalizedReason,
                        requestId,
                        resolvedKey);
                AdminGrantBugResult result =
                    new AdminGrantBugResult(
                        ledgerResult.ledgerId(),
                        normalizedUserId,
                        delta,
                        normalizedReason,
                        ledgerResult.balanceAfter(),
                        requestId,
                        note);
                appendAuditLog(
                    principal.userId(),
                    grant ? AdminAuditActions.ECONOMY_GRANT : AdminAuditActions.ECONOMY_REVOKE,
                    AdminAuditTargetType.USER,
                    normalizedUserId,
                    null,
                    result,
                    new GrantBugAuditMeta(
                        normalizedUserId,
                        delta,
                        normalizedReason,
                        note,
                        ledgerResult.ledgerId(),
                        ledgerResult.balanceAfter()),
                    requestId,
                    ipAddress,
                    userAgent);
                log.info(
                    "admin_economy_grant requestId={} operatorId={} userId={} delta={} reason={}",
                    requestId,
                    principal.userId(),
                    normalizedUserId,
                    delta,
                    normalizedReason);
                return result;
              })
          .response();
    } catch (IdempotencyConflictException ex) {
      throw AdminExceptions.conflict(AdminConstants.Message.IDEMPOTENCY_CONFLICT);
    }
  }

  private EconomyWalletService.LedgerWriteResult executeGrant(
      String userId, int delta, String reason, String requestId, String resolvedKey) {
    String ledgerIdempotencyKey =
        reason.equals("ADMIN_GRANT") ? "grant_" + resolvedKey : "revoke_" + resolvedKey;
    try {
      if (delta > 0) {
        return economyWalletService.creditBug(
            userId,
            delta,
            "ADMIN_GRANT",
            requestId,
            EconomyConstants.RefType.ADMIN,
            ledgerIdempotencyKey);
      }
      return economyWalletService.deductBug(
          userId,
          Math.abs(delta),
          "ADMIN_REVOKE",
          requestId,
          EconomyConstants.RefType.ADMIN,
          ledgerIdempotencyKey);
    } catch (InsufficientBalanceException ex) {
      throw insufficientBug();
    }
  }

  private ApiException insufficientBug() {
    return new ApiException(
        ErrorCode.INSUFFICIENT_BUG, HttpStatus.PAYMENT_REQUIRED, AdminConstants.Message.INSUFFICIENT_BUG);
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

  private String normalizeUserId(String userId) {
    if (!StringUtils.hasText(userId)) {
      throw AdminExceptions.validation(AdminConstants.Message.INVALID_ECONOMY_GRANT_REQUEST);
    }
    return userId.trim();
  }

  private String normalizeReason(String reason) {
    if (!StringUtils.hasText(reason)) {
      throw AdminExceptions.validation(AdminConstants.Message.INVALID_ECONOMY_GRANT_REQUEST);
    }
    return reason.trim().toUpperCase(Locale.ROOT);
  }

  public record AdminGrantBugResult(
      String ledgerId,
      String userId,
      int delta,
      String reason,
      long balanceAfter,
      String refId,
      String note) {}

  private record GrantBugAuditMeta(
      String userId,
      int delta,
      String reason,
      String note,
      String ledgerId,
      long balanceAfter) {}
}
