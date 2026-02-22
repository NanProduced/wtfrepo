package com.wtfrepo.backend.economy.application.support;

import com.wtfrepo.backend.shared.web.ApiException;
import com.wtfrepo.backend.shared.web.ErrorCode;
import org.springframework.http.HttpStatus;

/** Factory methods for economy wallet/game API errors. */
public final class EconomyExceptions {

  private EconomyExceptions() {}

  public static ApiException unauthorized(String message) {
    return new ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, message);
  }

  public static ApiException invalidCursor(String message) {
    return new ApiException(ErrorCode.INVALID_CURSOR, HttpStatus.BAD_REQUEST, message);
  }

  public static ApiException invalidReason(String message) {
    return new ApiException(ErrorCode.INVALID_REASON, HttpStatus.BAD_REQUEST, message);
  }

  public static ApiException gameInvalidType(String message) {
    return new ApiException(ErrorCode.GAME_INVALID_TYPE, HttpStatus.BAD_REQUEST, message);
  }

  public static ApiException gameInvalidScore(String message) {
    return new ApiException(ErrorCode.GAME_INVALID_SCORE, HttpStatus.BAD_REQUEST, message);
  }

  public static ApiException gameDailyLimitExceeded(String message) {
    return new ApiException(
        ErrorCode.GAME_DAILY_LIMIT_EXCEEDED, HttpStatus.TOO_MANY_REQUESTS, message);
  }

  public static ApiException gameDailyBugCapReached(String message) {
    return new ApiException(
        ErrorCode.GAME_DAILY_BUG_CAP_REACHED, HttpStatus.TOO_MANY_REQUESTS, message);
  }
}

