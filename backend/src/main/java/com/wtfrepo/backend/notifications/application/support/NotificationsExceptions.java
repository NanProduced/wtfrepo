package com.wtfrepo.backend.notifications.application.support;

import com.wtfrepo.backend.shared.web.ApiException;
import com.wtfrepo.backend.shared.web.ErrorCode;
import org.springframework.http.HttpStatus;

/** Factory methods for notification-specific API exceptions. */
public final class NotificationsExceptions {

  private NotificationsExceptions() {}

  public static ApiException unauthorized(String message) {
    return new ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, message);
  }

  public static ApiException validation(String message) {
    return new ApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, message);
  }

  public static ApiException notFound(String message) {
    return new ApiException(ErrorCode.NOTIFY_NOT_FOUND, HttpStatus.NOT_FOUND, message);
  }

  public static ApiException forbidden(String message) {
    return new ApiException(ErrorCode.NOTIFY_FORBIDDEN, HttpStatus.FORBIDDEN, message);
  }

  public static ApiException invalidStatus(String message) {
    return new ApiException(ErrorCode.NOTIFY_INVALID_STATUS, HttpStatus.BAD_REQUEST, message);
  }

  public static ApiException invalidType(String message) {
    return new ApiException(ErrorCode.NOTIFY_INVALID_TYPE, HttpStatus.BAD_REQUEST, message);
  }

  public static ApiException invalidCursor(String message) {
    return new ApiException(ErrorCode.INVALID_CURSOR, HttpStatus.BAD_REQUEST, message);
  }

  public static ApiException invalidChannel(String message) {
    return new ApiException(ErrorCode.NOTIFY_INVALID_CHANNEL, HttpStatus.BAD_REQUEST, message);
  }
}
