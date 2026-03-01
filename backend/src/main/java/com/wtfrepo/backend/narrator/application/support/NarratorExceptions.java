package com.wtfrepo.backend.narrator.application.support;

import com.wtfrepo.backend.shared.web.ApiException;
import com.wtfrepo.backend.shared.web.ErrorCode;
import org.springframework.http.HttpStatus;

/** Factory methods for narrator-specific API exceptions. */
public final class NarratorExceptions {

  private NarratorExceptions() {}

  public static ApiException validation(String message) {
    return new ApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, message);
  }

  public static ApiException unauthorized(String message) {
    return new ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, message);
  }

  public static ApiException invalidCursor(String message) {
    return new ApiException(ErrorCode.INVALID_CURSOR, HttpStatus.BAD_REQUEST, message);
  }
}
