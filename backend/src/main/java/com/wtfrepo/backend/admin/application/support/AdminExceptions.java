package com.wtfrepo.backend.admin.application.support;

import com.wtfrepo.backend.shared.web.ApiException;
import com.wtfrepo.backend.shared.web.ErrorCode;
import org.springframework.http.HttpStatus;

/** Factory methods for admin-specific API exceptions. */
public final class AdminExceptions {

  private AdminExceptions() {}

  public static ApiException validation(String message) {
    return new ApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, message);
  }

  public static ApiException unauthorized(String message) {
    return new ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, message);
  }

  public static ApiException forbidden(String message) {
    return new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, message);
  }

  public static ApiException notFound(String message) {
    return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, message);
  }

  public static ApiException conflict(String message) {
    return new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, message);
  }
}
