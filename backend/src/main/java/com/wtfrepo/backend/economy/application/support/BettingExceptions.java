package com.wtfrepo.backend.economy.application.support;

import com.wtfrepo.backend.shared.web.ApiException;
import com.wtfrepo.backend.shared.web.ErrorCode;
import org.springframework.http.HttpStatus;

/** Factory methods for betting-specific API exceptions. */
public final class BettingExceptions {

  private BettingExceptions() {}

  public static ApiException unauthorized(String message) {
    return new ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, message);
  }

  public static ApiException invalidDirection(String message) {
    return new ApiException(ErrorCode.BET_INVALID_DIRECTION, HttpStatus.BAD_REQUEST, message);
  }

  public static ApiException amountTooLow(String message) {
    return new ApiException(ErrorCode.BET_AMOUNT_TOO_LOW, HttpStatus.BAD_REQUEST, message);
  }

  public static ApiException ipoLocked(String message) {
    return new ApiException(ErrorCode.BET_IPO_LOCKED, HttpStatus.FORBIDDEN, message);
  }

  public static ApiException cutoffPassed(String message) {
    return new ApiException(ErrorCode.BET_CUTOFF_PASSED, HttpStatus.FORBIDDEN, message);
  }

  public static ApiException poolNotAvailable(String message) {
    return new ApiException(ErrorCode.BET_POOL_NOT_AVAILABLE, HttpStatus.FORBIDDEN, message);
  }

  public static ApiException insufficientBug(String message) {
    return new ApiException(ErrorCode.INSUFFICIENT_BUG, HttpStatus.PAYMENT_REQUIRED, message);
  }

  public static ApiException idempotencyConflict(String message) {
    return new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT, HttpStatus.CONFLICT, message);
  }

  public static ApiException specimenNotFound(String message) {
    return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, message);
  }
}
