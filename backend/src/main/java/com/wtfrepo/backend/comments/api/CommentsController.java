package com.wtfrepo.backend.comments.api;

import com.wtfrepo.backend.comments.application.CommentService;
import com.wtfrepo.backend.comments.application.support.CommentsConstants;
import com.wtfrepo.backend.comments.application.support.CommentsExceptions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Collection;
import java.util.stream.Stream;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public Comments API for M05 P1 list and publish flows. */
@RestController
@Validated
@RequestMapping("/api/v1")
public class CommentsController {

  private final CommentService commentService;

  public CommentsController(CommentService commentService) {
    this.commentService = commentService;
  }

  @GetMapping("/comments")
  public ResponseEntity<CommentListResponse> list(
      @RequestParam @NotBlank String specimenId,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) @Min(1) @Max(50) Integer limit) {
    CommentService.ListResult result =
        commentService.list(new CommentService.ListQuery(specimenId, sort, cursor, limit));
    return ResponseEntity.ok(CommentListResponse.from(result));
  }

  @GetMapping("/me/comments")
  public ResponseEntity<MyCommentsResponse> myComments(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) @Min(1) @Max(50) Integer limit,
      @RequestParam(required = false) String status) {
    String userId = requireUserId(jwt);
    CommentService.MyCommentsResult result =
        commentService.myComments(userId, new CommentService.MyCommentsQuery(cursor, limit, status));
    return ResponseEntity.ok(MyCommentsResponse.from(result));
  }

  @PostMapping("/comments")
  public ResponseEntity<CommentPublishResponse> publish(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CommentPublishRequest request) {
    String userId = requireUserId(jwt);
    CommentService.PublishResult result =
        commentService.publish(
            userId,
            new CommentService.PublishCommand(
                request.specimenId(),
                request.contentMd(),
                request.replyToCommentId(),
                request.clientRequestId()));
    return ResponseEntity.ok(CommentPublishResponse.from(result));
  }

  @PostMapping("/comments/{commentId}/resonance")
  public ResponseEntity<CommentResonanceResponse> resonate(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String commentId,
      @Valid @RequestBody CommentResonanceRequest request) {
    String userId = requireUserId(jwt);
    CommentService.ResonanceResult result =
        commentService.resonate(
            userId, new CommentService.ResonanceCommand(commentId, request.clientRequestId()));
    return ResponseEntity.ok(CommentResonanceResponse.from(result));
  }

  @GetMapping("/comments/{commentId}/context")
  public ResponseEntity<CommentContextResponse> context(@PathVariable String commentId) {
    CommentService.ContextResult result = commentService.context(commentId);
    return ResponseEntity.ok(CommentContextResponse.from(result));
  }

  @DeleteMapping("/comments/{commentId}")
  public ResponseEntity<CommentDeleteResponse> delete(
      @AuthenticationPrincipal Jwt jwt, @PathVariable String commentId) {
    String userId = requireUserId(jwt);
    CommentService.DeleteResult result =
        commentService.delete(userId, new CommentService.DeleteCommand(commentId, hasAdminRole(jwt)));
    return ResponseEntity.ok(CommentDeleteResponse.from(result));
  }

  @GetMapping("/specimens/{specimenId}/comments/top-roast")
  public ResponseEntity<CommentTopRoastResponse> topRoast(@PathVariable String specimenId) {
    CommentService.TopRoastResult result = commentService.topRoast(specimenId);
    return ResponseEntity.ok(CommentTopRoastResponse.from(result));
  }

  @PostMapping("/comments/{commentId}/report")
  public ResponseEntity<CommentReportResponse> report(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String commentId,
      @Valid @RequestBody CommentReportRequest request) {
    String userId = requireUserId(jwt);
    CommentService.ReportResult result =
        commentService.report(
            userId,
            new CommentService.ReportCommand(
                commentId, request.reasonCode(), request.message(), request.clientRequestId()));
    return ResponseEntity.ok(CommentReportResponse.from(result));
  }

  private String requireUserId(Jwt jwt) {
    if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
      throw CommentsExceptions.unauthorized(CommentsConstants.Message.AUTH_REQUIRED);
    }
    return jwt.getSubject();
  }

  private boolean hasAdminRole(Jwt jwt) {
    if (jwt == null) {
      return false;
    }

    Object rolesClaim = jwt.getClaim(CommentsConstants.Claim.ROLES);
    if (rolesClaim instanceof Collection<?> roles) {
      return roles.stream().map(String::valueOf).anyMatch(this::isAdminRole);
    }
    if (rolesClaim instanceof String roles) {
      return Stream.of(roles.split(",")).map(String::trim).anyMatch(this::isAdminRole);
    }
    return false;
  }

  private boolean isAdminRole(String role) {
    return CommentsConstants.Role.ADMIN.equalsIgnoreCase(role)
        || CommentsConstants.Role.MANAGER.equalsIgnoreCase(role);
  }
}
