package com.wtfrepo.backend.achievements.application;

import java.util.Optional;

/** Read-only lookup port for resolving arena vote ownership from vote order idempotency key. */
public interface ArenaVoteLookupPort {

  Optional<String> findVoterUserIdByOrderId(String orderId);
}
