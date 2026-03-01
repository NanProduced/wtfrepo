package com.wtfrepo.backend.achievements.application.support;

import com.wtfrepo.backend.shared.web.ApiException;
import com.wtfrepo.backend.shared.web.ErrorCode;
import org.springframework.http.HttpStatus;

/** Factory methods for achievements API exceptions. */
public final class AchievementsExceptions {

  private AchievementsExceptions() {}

  public static ApiException unauthorized(String message) {
    return new ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, message);
  }

  public static ApiException validation(String message) {
    return new ApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, message);
  }

  public static ApiException invalidCursor(String message) {
    return new ApiException(ErrorCode.INVALID_CURSOR, HttpStatus.BAD_REQUEST, message);
  }

  public static ApiException notFound(String message) {
    return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, message);
  }
}
