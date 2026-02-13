package com.wtfrepo.backend.shared.idempotency;

/**
 * Result of idempotent operation execution.
 *
 * @param response operation response payload
 * @param replayed whether response came from historical replay
 */
public record IdempotentOperationResult<T>(T response, boolean replayed) {}

