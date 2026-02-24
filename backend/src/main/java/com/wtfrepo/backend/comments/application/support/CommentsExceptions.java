package com.wtfrepo.backend.comments.application.support;

import com.wtfrepo.backend.shared.web.ApiException;
import com.wtfrepo.backend.shared.web.ErrorCode;
import org.springframework.http.HttpStatus;

/** Factory methods for comments module API errors. */
public final class CommentsExceptions {

  private CommentsExceptions() {}

  public static ApiException unauthorized(String message) {
    return new ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, message);
  }

  public static ApiException specimenNotFound(String message) {
    return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, message);
  }

  public static ApiException specimenNotActive(String message) {
    return new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, message);
  }

  public static ApiException specimenLocked(String message) {
    return new ApiException(ErrorCode.SPECIMEN_LOCKED, HttpStatus.LOCKED, message);
  }

  public static ApiException forbidden(String message) {
    return new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, message);
  }

  public static ApiException commentNotFound(String message) {
    return new ApiException(ErrorCode.COMMENT_NOT_FOUND, HttpStatus.NOT_FOUND, message);
  }

  public static ApiException alreadyDeleted(String message) {
    return new ApiException(ErrorCode.ALREADY_DELETED, HttpStatus.CONFLICT, message);
  }

  public static ApiException contextNotVisible(String message) {
    return new ApiException(ErrorCode.CONTEXT_NOT_VISIBLE, HttpStatus.GONE, message);
  }

  public static ApiException invalidContent(String message) {
    return new ApiException(ErrorCode.INVALID_CONTENT, HttpStatus.BAD_REQUEST, message);
  }

  public static ApiException contentTooLong(String message) {
    return new ApiException(ErrorCode.CONTENT_TOO_LONG, HttpStatus.BAD_REQUEST, message);
  }

  public static ApiException invalidSort(String message) {
    return new ApiException(ErrorCode.INVALID_SORT, HttpStatus.BAD_REQUEST, message);
  }

  public static ApiException invalidCursor(String message) {
    return new ApiException(ErrorCode.INVALID_CURSOR, HttpStatus.BAD_REQUEST, message);
  }

  public static ApiException invalidReason(String message) {
    return new ApiException(ErrorCode.INVALID_REASON, HttpStatus.BAD_REQUEST, message);
  }

  public static ApiException tooManyMentions(String message) {
    return new ApiException(ErrorCode.TOO_MANY_MENTIONS, HttpStatus.BAD_REQUEST, message);
  }

  public static ApiException invalidMentionUser(String message) {
    return new ApiException(ErrorCode.INVALID_MENTION_USER, HttpStatus.BAD_REQUEST, message);
  }

  public static ApiException mentionSelfNotAllowed(String message) {
    return new ApiException(ErrorCode.MENTION_SELF_NOT_ALLOWED, HttpStatus.BAD_REQUEST, message);
  }

  public static ApiException duplicateReport(String message) {
    return new ApiException(ErrorCode.DUPLICATE_REPORT, HttpStatus.CONFLICT, message);
  }

  public static ApiException idempotencyConflict(String message) {
    return new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT, HttpStatus.CONFLICT, message);
  }

  public static ApiException invalidStatus(String message) {
    return new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, message);
  }
}
