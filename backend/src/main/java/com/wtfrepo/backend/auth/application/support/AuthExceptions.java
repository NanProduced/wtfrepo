package com.wtfrepo.backend.auth.application.support;

import com.wtfrepo.backend.shared.web.ApiException;
import com.wtfrepo.backend.shared.web.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Factory methods for auth-specific API exceptions.
 */
public final class AuthExceptions {

  private AuthExceptions() {}

  public static ApiException unauthorized(String message) {
    return new ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, message);
  }

  public static ApiException conflict(String message) {
    return new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, message);
  }

  public static ApiException forbidden(String message) {
    return new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, message);
  }

  public static ApiException validation(String message) {
    return new ApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, message);
  }

  public static ApiException tooManyRequests(String message) {
    return new ApiException(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS, message);
  }

  public static ApiException notFound(String message) {
    return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, message);
  }

  public static ApiException internal(String message) {
    return new ApiException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, message);
  }
}
