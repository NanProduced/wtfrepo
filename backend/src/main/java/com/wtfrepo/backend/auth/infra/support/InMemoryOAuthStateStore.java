package com.wtfrepo.backend.auth.infra.support;

import com.wtfrepo.backend.auth.application.OAuthStateStore;
import com.wtfrepo.backend.auth.infra.persistence.repository.OAuthStateJpaRepository;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** In-memory oauth-state consume-once fallback for non-JPA contexts. */
@Component
@Primary
@ConditionalOnMissingBean(OAuthStateJpaRepository.class)
public class InMemoryOAuthStateStore implements OAuthStateStore {

  private final Set<String> consumedStates = ConcurrentHashMap.newKeySet();

  @Override
  public boolean consumeOnce(String oauthState) {
    if (oauthState == null || oauthState.isBlank()) {
      return false;
    }
    return consumedStates.add(oauthState.trim());
  }
}
