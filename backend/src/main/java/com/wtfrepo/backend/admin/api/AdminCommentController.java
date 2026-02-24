package com.wtfrepo.backend.admin.api;

import com.wtfrepo.backend.admin.application.AdminCommentService;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminPrincipal;
import com.wtfrepo.backend.admin.application.support.AdminConstants;
import com.wtfrepo.backend.comments.application.model.CommentAdminModels;
import com.wtfrepo.backend.shared.web.RequestIdConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/admin/comments")
@Tag(name = "Admin: Comments", description = "Admin moderation endpoints for comments.")
public class AdminCommentController {

  private final AdminCommentService adminCommentService;

  public AdminCommentController(AdminCommentService adminCommentService) {
    this.adminCommentService = adminCommentService;
  }

  @GetMapping("/moderation-queue")
  @Operation(summary = "List moderation queue")
  public ResponseEntity<AdminModerationQueueResponse> listModerationQueue(
      @Parameter(
              in = ParameterIn.HEADER,
              name = RequestIdConstants.HEADER_NAME,
              description = "Request correlation id.",
              required = true)
          @RequestHeader(RequestIdConstants.HEADER_NAME)
          String requestId,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @RequestParam(name = "page", required = false) Integer page,
      @RequestParam(name = "pageSize", required = false) Integer pageSize) {
    AdminPrincipal principal = AdminApiSupport.requireAdminPrincipal(jwt);
    CommentAdminModels.ModerationQueuePage queuePage =
        adminCommentService.listModerationQueue(principal, page, pageSize);
    return ResponseEntity.ok(AdminModerationQueueResponse.from(queuePage));
  }

  @PostMapping("/{commentId}/block")
  @Operation(summary = "Block comment")
  public ResponseEntity<AdminCommentActionResponse> blockComment(
      @Parameter(
              in = ParameterIn.HEADER,
              name = RequestIdConstants.HEADER_NAME,
              description = "Request correlation id.",
              required = true)
          @RequestHeader(RequestIdConstants.HEADER_NAME)
          String requestId,
      @Parameter(
              in = ParameterIn.HEADER,
              name = AdminConstants.Header.IDEMPOTENCY_KEY,
              description = "Idempotency key for admin write operations.",
              required = false)
          @RequestHeader(name = AdminConstants.Header.IDEMPOTENCY_KEY, required = false)
          String idempotencyKey,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "Target comment id.", required = true) @PathVariable String commentId,
      HttpServletRequest servletRequest) {
    AdminPrincipal principal = AdminApiSupport.requireAdminPrincipal(jwt);
    CommentAdminModels.ModerationActionResult result =
        adminCommentService.blockComment(
            principal,
            requestId,
            idempotencyKey,
            commentId,
            resolveClientIp(servletRequest),
            resolveUserAgent(servletRequest));
    return ResponseEntity.ok(AdminCommentActionResponse.from(result));
  }

  @PostMapping("/{commentId}/unblock")
  @Operation(summary = "Unblock comment")
  public ResponseEntity<AdminCommentActionResponse> unblockComment(
      @Parameter(
              in = ParameterIn.HEADER,
              name = RequestIdConstants.HEADER_NAME,
              description = "Request correlation id.",
              required = true)
          @RequestHeader(RequestIdConstants.HEADER_NAME)
          String requestId,
      @Parameter(
              in = ParameterIn.HEADER,
              name = AdminConstants.Header.IDEMPOTENCY_KEY,
              description = "Idempotency key for admin write operations.",
              required = false)
          @RequestHeader(name = AdminConstants.Header.IDEMPOTENCY_KEY, required = false)
          String idempotencyKey,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "Target comment id.", required = true) @PathVariable String commentId,
      HttpServletRequest servletRequest) {
    AdminPrincipal principal = AdminApiSupport.requireAdminPrincipal(jwt);
    CommentAdminModels.ModerationActionResult result =
        adminCommentService.unblockComment(
            principal,
            requestId,
            idempotencyKey,
            commentId,
            resolveClientIp(servletRequest),
            resolveUserAgent(servletRequest));
    return ResponseEntity.ok(AdminCommentActionResponse.from(result));
  }

  @PostMapping("/{commentId}/approve")
  @Operation(summary = "Approve comment")
  public ResponseEntity<AdminCommentActionResponse> approveComment(
      @Parameter(
              in = ParameterIn.HEADER,
              name = RequestIdConstants.HEADER_NAME,
              description = "Request correlation id.",
              required = true)
          @RequestHeader(RequestIdConstants.HEADER_NAME)
          String requestId,
      @Parameter(
              in = ParameterIn.HEADER,
              name = AdminConstants.Header.IDEMPOTENCY_KEY,
              description = "Idempotency key for admin write operations.",
              required = false)
          @RequestHeader(name = AdminConstants.Header.IDEMPOTENCY_KEY, required = false)
          String idempotencyKey,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "Target comment id.", required = true) @PathVariable String commentId,
      HttpServletRequest servletRequest) {
    AdminPrincipal principal = AdminApiSupport.requireAdminPrincipal(jwt);
    CommentAdminModels.ModerationActionResult result =
        adminCommentService.approveComment(
            principal,
            requestId,
            idempotencyKey,
            commentId,
            resolveClientIp(servletRequest),
            resolveUserAgent(servletRequest));
    return ResponseEntity.ok(AdminCommentActionResponse.from(result));
  }

  @PostMapping("/{commentId}/delete")
  @Operation(summary = "Delete comment")
  public ResponseEntity<AdminCommentActionResponse> deleteComment(
      @Parameter(
              in = ParameterIn.HEADER,
              name = RequestIdConstants.HEADER_NAME,
              description = "Request correlation id.",
              required = true)
          @RequestHeader(RequestIdConstants.HEADER_NAME)
          String requestId,
      @Parameter(
              in = ParameterIn.HEADER,
              name = AdminConstants.Header.IDEMPOTENCY_KEY,
              description = "Idempotency key for admin write operations.",
              required = false)
          @RequestHeader(name = AdminConstants.Header.IDEMPOTENCY_KEY, required = false)
          String idempotencyKey,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "Target comment id.", required = true) @PathVariable String commentId,
      HttpServletRequest servletRequest) {
    AdminPrincipal principal = AdminApiSupport.requireAdminPrincipal(jwt);
    CommentAdminModels.ModerationActionResult result =
        adminCommentService.deleteComment(
            principal,
            requestId,
            idempotencyKey,
            commentId,
            resolveClientIp(servletRequest),
            resolveUserAgent(servletRequest));
    return ResponseEntity.ok(AdminCommentActionResponse.from(result));
  }

  private String resolveClientIp(HttpServletRequest servletRequest) {
    if (servletRequest == null) {
      return null;
    }
    String forwardedFor = servletRequest.getHeader("X-Forwarded-For");
    if (StringUtils.hasText(forwardedFor)) {
      String firstHop =
          Arrays.stream(forwardedFor.split(","))
              .map(String::trim)
              .filter(StringUtils::hasText)
              .findFirst()
              .orElse("");
      if (StringUtils.hasText(firstHop)) {
        return firstHop;
      }
    }

    String realIp = servletRequest.getHeader("X-Real-IP");
    if (StringUtils.hasText(realIp)) {
      return realIp.trim();
    }
    return servletRequest.getRemoteAddr();
  }

  private String resolveUserAgent(HttpServletRequest servletRequest) {
    if (servletRequest == null) {
      return null;
    }
    String userAgent = servletRequest.getHeader("User-Agent");
    return StringUtils.hasText(userAgent) ? userAgent.trim() : null;
  }

  public record AdminModerationQueueItemResponse(
      String commentId,
      String specimenId,
      String authorUserId,
      String contentPreview,
      String status,
      String moderationRiskLevel,
      String moderationReasonCode,
      Instant createdAt,
      Instant updatedAt) {

    static AdminModerationQueueItemResponse from(CommentAdminModels.ModerationQueueItem item) {
      return new AdminModerationQueueItemResponse(
          item.commentId(),
          item.specimenId(),
          item.authorUserId(),
          item.contentPreview(),
          item.status(),
          item.moderationRiskLevel(),
          item.moderationReasonCode(),
          item.createdAt(),
          item.updatedAt());
    }
  }

  public record AdminModerationQueueResponse(
      List<AdminModerationQueueItemResponse> items,
      AdminPaginationResponse pagination) {

    static AdminModerationQueueResponse from(CommentAdminModels.ModerationQueuePage page) {
      List<AdminModerationQueueItemResponse> items =
          page.items().stream().map(AdminModerationQueueItemResponse::from).toList();
      return new AdminModerationQueueResponse(
          items,
          new AdminPaginationResponse(
              page.page(), page.pageSize(), page.total(), page.totalPages()));
    }
  }

  public record AdminCommentActionResponse(
      String commentId,
      String specimenId,
      String authorUserId,
      String previousStatus,
      String status,
      int refundAmount) {

    static AdminCommentActionResponse from(CommentAdminModels.ModerationActionResult result) {
      CommentAdminModels.ModerationSnapshot before = result.before();
      CommentAdminModels.ModerationSnapshot after = result.after();
      return new AdminCommentActionResponse(
          after != null ? after.commentId() : (before != null ? before.commentId() : null),
          after != null ? after.specimenId() : (before != null ? before.specimenId() : null),
          after != null ? after.authorUserId() : (before != null ? before.authorUserId() : null),
          before != null ? before.status() : null,
          after != null ? after.status() : null,
          result.refundAmount());
    }
  }

  public record AdminPaginationResponse(int page, int pageSize, int total, int totalPages) {}
}
