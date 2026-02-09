package com.wtfrepo.backend.auth.infra.persistence.adapter;

import com.wtfrepo.backend.auth.application.AuthContractProperties;
import com.wtfrepo.backend.auth.application.AuthExchangeIdempotencyStore;
import com.wtfrepo.backend.auth.application.AuthService;
import com.wtfrepo.backend.auth.domain.AuthUser;
import com.wtfrepo.backend.auth.domain.UserRole;
import com.wtfrepo.backend.auth.infra.persistence.entity.AuthExchangeIdempotencyJpaEntity;
import com.wtfrepo.backend.auth.infra.persistence.repository.AuthExchangeIdempotencyJpaRepository;
import com.wtfrepo.backend.shared.security.IssuedToken;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Primary
public class JpaAuthExchangeIdempotencyStore implements AuthExchangeIdempotencyStore {

  private final AuthExchangeIdempotencyJpaRepository repository;
  private final AuthContractProperties authContractProperties;

  public JpaAuthExchangeIdempotencyStore(
      AuthExchangeIdempotencyJpaRepository repository,
      AuthContractProperties authContractProperties) {
    this.repository = repository;
    this.authContractProperties = authContractProperties;
  }

  @Override
  @Transactional
  public Optional<StoredExchangeResult> find(String requestId) {
    // Best-effort cleanup keeps idempotency table bounded by configured TTL.
    repository.deleteByCreatedAtBefore(
        Instant.now().minus(authContractProperties.getExchangeIdempotencyTtl()));

    return repository.findById(requestId).map(this::toExchangeResult);
  }

  @Override
  @Transactional
  public void save(String requestId, String requestFingerprint, AuthService.ExchangeResult result) {
    AuthExchangeIdempotencyJpaEntity entity =
        AuthExchangeIdempotencyJpaEntity.of(
            requestId,
            requestFingerprint,
            result.issuedToken().accessToken(),
            result.issuedToken().expiresIn(),
            result.issuedToken().expiresAt(),
            result.authUser().userId(),
            result.authUser().username(),
            result.authUser().usernameChanged(),
            result.authUser().bugBalance(),
            result.isNewUser(),
            result.initialBugGrant());
    repository.save(entity);
  }

  private StoredExchangeResult toExchangeResult(AuthExchangeIdempotencyJpaEntity entity) {
    IssuedToken issuedToken =
        new IssuedToken(entity.getAccessToken(), entity.getTokenExpiresIn(), entity.getTokenExpiresAt());
    AuthUser authUser =
        new AuthUser(
            entity.getUserId(),
            entity.getUsername(),
            entity.isUsernameChanged(),
            Set.of(UserRole.USER),
            entity.getBugBalance());
    return new StoredExchangeResult(
        entity.getRequestFingerprint(),
        new AuthService.ExchangeResult(
            issuedToken,
            authUser,
            entity.isNewUser(),
            entity.getInitialBugGrant()));
  }
}
