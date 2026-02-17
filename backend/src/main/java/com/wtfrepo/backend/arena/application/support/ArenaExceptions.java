package com.wtfrepo.backend.arena.application.support;

import com.wtfrepo.backend.shared.web.ApiException;
import com.wtfrepo.backend.shared.web.ErrorCode;
import org.springframework.http.HttpStatus;

/** Factory methods for arena-specific API exceptions. */
public final class ArenaExceptions {

  private ArenaExceptions() {}

  public static ApiException unauthorized(String message) {
    return new ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, message);
  }

  public static ApiException forbidden(String message) {
    return new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, message);
  }

  public static ApiException invalidWinner(String message) {
    return new ApiException(ErrorCode.VOTE_INVALID_WINNER, HttpStatus.BAD_REQUEST, message);
  }

  public static ApiException insufficientBug(String message) {
    return new ApiException(ErrorCode.INSUFFICIENT_BUG, HttpStatus.PAYMENT_REQUIRED, message);
  }

  public static ApiException battleNotFound(String message) {
    return new ApiException(ErrorCode.BATTLE_NOT_FOUND, HttpStatus.NOT_FOUND, message);
  }

  public static ApiException voteDuplicate(String message) {
    return new ApiException(ErrorCode.VOTE_DUPLICATE, HttpStatus.CONFLICT, message);
  }

  public static ApiException battleExpired(String message) {
    return new ApiException(ErrorCode.BATTLE_EXPIRED, HttpStatus.GONE, message);
  }

  public static ApiException settlementInProgress(String message) {
    return new ApiException(ErrorCode.SETTLEMENT_IN_PROGRESS, HttpStatus.SERVICE_UNAVAILABLE, message);
  }

  public static ApiException arenaNoMatch(String message) {
    return new ApiException(ErrorCode.ARENA_NO_MATCH, HttpStatus.NOT_FOUND, message);
  }

  public static ApiException arenaPoolEmpty(String message) {
    return new ApiException(ErrorCode.ARENA_POOL_EMPTY, HttpStatus.SERVICE_UNAVAILABLE, message);
  }

  public static ApiException rateLimited(String message) {
    return new ApiException(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS, message);
  }
}
