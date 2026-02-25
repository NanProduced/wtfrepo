package com.wtfrepo.backend.admin.application;

import com.wtfrepo.backend.admin.application.model.AdminModels.AdminAuditLogEntry;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminPrincipal;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminSafetyTicketRecord;
import com.wtfrepo.backend.admin.application.support.AdminConstants;
import com.wtfrepo.backend.admin.application.support.AdminExceptions;
import com.wtfrepo.backend.admin.application.support.AdminRequestFingerprintCalculator;
import com.wtfrepo.backend.admin.domain.AdminAuditActions;
import com.wtfrepo.backend.admin.domain.AdminAuditTargetType;
import com.wtfrepo.backend.admin.domain.AdminSafetyTicketStatus;
import com.wtfrepo.backend.comments.application.CommentService;
import com.wtfrepo.backend.comments.application.model.CommentAdminModels;
import com.wtfrepo.backend.shared.idempotency.IdempotentOperationExecutor;
import com.wtfrepo.backend.shared.idempotency.IdempotencyConflictException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Admin orchestration for comment moderation actions. */
@Service
public class AdminCommentService {

  private static final Logger log = LoggerFactory.getLogger(AdminCommentService.class);

  private final CommentService commentService;
  private final AdminAuditLogStore adminAuditLogStore;
  private final AdminSafetyTicketStore adminSafetyTicketStore;
  private final AdminRequestFingerprintCalculator requestFingerprintCalculator;
  private final IdempotentOperationExecutor idempotentExecutor;

  public AdminCommentService(
      CommentService commentService,
      AdminAuditLogStore adminAuditLogStore,
      AdminSafetyTicketStore adminSafetyTicketStore,
      AdminRequestFingerprintCalculator requestFingerprintCalculator,
      IdempotentOperationExecutor idempotentExecutor) {
    this.commentService = commentService;
    this.adminAuditLogStore = adminAuditLogStore;
    this.adminSafetyTicketStore = adminSafetyTicketStore;
    this.requestFingerprintCalculator = requestFingerprintCalculator;
    this.idempotentExecutor = idempotentExecutor;
  }

  public CommentAdminModels.ModerationQueuePage listModerationQueue(
      AdminPrincipal principal, Integer page, Integer pageSize) {
    return commentService.listModerationQueue(page, pageSize);
  }

  public CommentAdminModels.ModerationActionResult blockComment(
      AdminPrincipal principal,
      String requestId,
      String idempotencyKey,
      String commentId,
      String ipAddress,
      String userAgent) {
    String resolvedKey = resolveIdempotencyKey(requestId, idempotencyKey);
    validateIdempotencyKey(resolvedKey);
    String fingerprint =
        requestFingerprintCalculator.fingerprint(
            "comment_block|" + principal.userId() + "|" + commentId);

    try {
      return idempotentExecutor
          .execute(
              "admin:comment:block",
              resolvedKey,
              fingerprint,
              () -> {
                CommentAdminModels.ModerationActionResult result =
                    commentService.blockComment(principal.userId(), commentId, resolvedKey);
                appendAuditLog(
                    principal.userId(),
                    AdminAuditActions.COMMENT_BLOCK,
                    AdminAuditTargetType.COMMENT,
                    commentId,
                    result.before(),
                    result.after(),
                    new CommentModerationAuditMeta(
                        result.before() != null ? result.before().status() : null,
                        result.after() != null ? result.after().status() : null,
                        result.refundAmount()),
                    requestId,
                    ipAddress,
                    userAgent);
                maybeResolveSafetyTicket(
                    commentId,
                    principal.userId(),
                    "comment_blocked");
                log.info(
                    "admin_comment_block requestId={} operatorId={} commentId={}",
                    requestId,
                    principal.userId(),
                    commentId);
                return result;
              })
          .response();
    } catch (IdempotencyConflictException ex) {
      throw AdminExceptions.conflict(AdminConstants.Message.IDEMPOTENCY_CONFLICT);
    }
  }

  public CommentAdminModels.ModerationActionResult unblockComment(
      AdminPrincipal principal,
      String requestId,
      String idempotencyKey,
      String commentId,
      String ipAddress,
      String userAgent) {
    String resolvedKey = resolveIdempotencyKey(requestId, idempotencyKey);
    validateIdempotencyKey(resolvedKey);
    String fingerprint =
        requestFingerprintCalculator.fingerprint(
            "comment_unblock|" + principal.userId() + "|" + commentId);

    try {
      return idempotentExecutor
          .execute(
              "admin:comment:unblock",
              resolvedKey,
              fingerprint,
              () -> {
                CommentAdminModels.ModerationActionResult result =
                    commentService.unblockComment(principal.userId(), commentId);
                appendAuditLog(
                    principal.userId(),
                    AdminAuditActions.COMMENT_UNBLOCK,
                    AdminAuditTargetType.COMMENT,
                    commentId,
                    result.before(),
                    result.after(),
                    new CommentModerationAuditMeta(
                        result.before() != null ? result.before().status() : null,
                        result.after() != null ? result.after().status() : null,
                        result.refundAmount()),
                    requestId,
                    ipAddress,
                    userAgent);
                maybeResolveSafetyTicket(
                    commentId,
                    principal.userId(),
                    "comment_unblocked");
                log.info(
                    "admin_comment_unblock requestId={} operatorId={} commentId={}",
                    requestId,
                    principal.userId(),
                    commentId);
                return result;
              })
          .response();
    } catch (IdempotencyConflictException ex) {
      throw AdminExceptions.conflict(AdminConstants.Message.IDEMPOTENCY_CONFLICT);
    }
  }

  public CommentAdminModels.ModerationActionResult approveComment(
      AdminPrincipal principal,
      String requestId,
      String idempotencyKey,
      String commentId,
      String ipAddress,
      String userAgent) {
    String resolvedKey = resolveIdempotencyKey(requestId, idempotencyKey);
    validateIdempotencyKey(resolvedKey);
    String fingerprint =
        requestFingerprintCalculator.fingerprint(
            "comment_approve|" + principal.userId() + "|" + commentId);

    try {
      return idempotentExecutor
          .execute(
              "admin:comment:approve",
              resolvedKey,
              fingerprint,
              () -> {
                CommentAdminModels.ModerationActionResult result =
                    commentService.approveComment(principal.userId(), commentId);
                appendAuditLog(
                    principal.userId(),
                    AdminAuditActions.COMMENT_APPROVE,
                    AdminAuditTargetType.COMMENT,
                    commentId,
                    result.before(),
                    result.after(),
                    new CommentModerationAuditMeta(
                        result.before() != null ? result.before().status() : null,
                        result.after() != null ? result.after().status() : null,
                        result.refundAmount()),
                    requestId,
                    ipAddress,
                    userAgent);
                maybeResolveSafetyTicket(
                    commentId,
                    principal.userId(),
                    "comment_approved");
                log.info(
                    "admin_comment_approve requestId={} operatorId={} commentId={}",
                    requestId,
                    principal.userId(),
                    commentId);
                return result;
              })
          .response();
    } catch (IdempotencyConflictException ex) {
      throw AdminExceptions.conflict(AdminConstants.Message.IDEMPOTENCY_CONFLICT);
    }
  }

  public CommentAdminModels.ModerationActionResult deleteComment(
      AdminPrincipal principal,
      String requestId,
      String idempotencyKey,
      String commentId,
      String ipAddress,
      String userAgent) {
    String resolvedKey = resolveIdempotencyKey(requestId, idempotencyKey);
    validateIdempotencyKey(resolvedKey);
    String fingerprint =
        requestFingerprintCalculator.fingerprint(
            "comment_delete|" + principal.userId() + "|" + commentId);

    try {
      return idempotentExecutor
          .execute(
              "admin:comment:delete",
              resolvedKey,
              fingerprint,
              () -> {
                CommentAdminModels.ModerationActionResult result =
                    commentService.deleteByAdmin(principal.userId(), commentId);
                appendAuditLog(
                    principal.userId(),
                    AdminAuditActions.COMMENT_DELETE,
                    AdminAuditTargetType.COMMENT,
                    commentId,
                    result.before(),
                    result.after(),
                    new CommentModerationAuditMeta(
                        result.before() != null ? result.before().status() : null,
                        result.after() != null ? result.after().status() : null,
                        result.refundAmount()),
                    requestId,
                    ipAddress,
                    userAgent);
                maybeResolveSafetyTicket(
                    commentId,
                    principal.userId(),
                    "comment_deleted");
                log.info(
                    "admin_comment_delete requestId={} operatorId={} commentId={}",
                    requestId,
                    principal.userId(),
                    commentId);
                return result;
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

  private void maybeResolveSafetyTicket(
      String commentId, String operatorId, String resolution) {
    Optional<AdminSafetyTicketRecord> ticketOpt =
        adminSafetyTicketStore.findLatestActiveByTarget("COMMENT", commentId);
    if (ticketOpt.isEmpty()) {
      return;
    }
    AdminSafetyTicketRecord ticket = ticketOpt.get();
    adminSafetyTicketStore.updateStatus(
        ticket.id(),
        AdminSafetyTicketStatus.ACTIONED,
        resolution,
        operatorId,
        Instant.now(),
        Instant.now());
  }

  private record CommentModerationAuditMeta(
      String oldStatus, String newStatus, Integer refundAmount) {}
}
