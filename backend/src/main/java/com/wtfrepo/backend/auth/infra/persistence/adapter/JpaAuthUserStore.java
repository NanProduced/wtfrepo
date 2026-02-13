package com.wtfrepo.backend.auth.infra.persistence.adapter;

import com.wtfrepo.backend.auth.application.AuthUserRecord;
import com.wtfrepo.backend.auth.application.AuthUserStore;
import com.wtfrepo.backend.auth.application.UsernameAlreadyTakenException;
import com.wtfrepo.backend.auth.domain.OAuthProvider;
import com.wtfrepo.backend.auth.infra.persistence.entity.AuthIdentityJpaEntity;
import com.wtfrepo.backend.auth.infra.persistence.entity.AuthUserJpaEntity;
import com.wtfrepo.backend.auth.infra.persistence.repository.AuthIdentityJpaRepository;
import com.wtfrepo.backend.auth.infra.persistence.repository.AuthUserJpaRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaAuthUserStore implements AuthUserStore {

  private final AuthUserJpaRepository authUserJpaRepository;
  private final AuthIdentityJpaRepository authIdentityJpaRepository;

  public JpaAuthUserStore(
      AuthUserJpaRepository authUserJpaRepository,
      AuthIdentityJpaRepository authIdentityJpaRepository) {
    this.authUserJpaRepository = authUserJpaRepository;
    this.authIdentityJpaRepository = authIdentityJpaRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<AuthUserRecord> findByProviderIdentity(
      OAuthProvider provider, String providerSubject) {
    return authIdentityJpaRepository
        .findByProviderAndProviderSubject(provider, providerSubject)
        .flatMap(identity -> authUserJpaRepository.findById(identity.getUserId()))
        .map(user -> toRecord(user, provider, providerSubject, false));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<AuthUserRecord> findByUserId(String userId) {
    return authUserJpaRepository.findById(userId).map(user -> toRecord(user, null, null, false));
  }

  @Override
  @Transactional(readOnly = true)
  public boolean usernameExists(String username) {
    return authUserJpaRepository.existsByUsername(username);
  }

  @Override
  @Transactional
  public AuthUserRecord saveNewUser(
      OAuthProvider provider,
      String providerSubject,
      String username,
      long initialBugBalance) {
    // Ensure first-login side effect is idempotent on (provider, providerSubject).
    Optional<AuthIdentityJpaEntity> existingIdentity =
        authIdentityJpaRepository.findByProviderAndProviderSubject(provider, providerSubject);
    if (existingIdentity.isPresent()) {
      AuthIdentityJpaEntity identity = existingIdentity.get();
      AuthUserJpaEntity existingUser = authUserJpaRepository.findById(identity.getUserId()).orElseThrow();
      return toRecord(existingUser, provider, providerSubject, false);
    }

    String userId = "u_" + UUID.randomUUID();
    AuthUserJpaEntity user = AuthUserJpaEntity.create(userId, username, initialBugBalance);
    AuthIdentityJpaEntity identity = AuthIdentityJpaEntity.create(userId, provider, providerSubject);

    try {
      authUserJpaRepository.save(user);
      authIdentityJpaRepository.save(identity);
    } catch (DataIntegrityViolationException ex) {
      // Handle concurrent create by reading winner row and returning deterministic record.
      return authIdentityJpaRepository
          .findByProviderAndProviderSubject(provider, providerSubject)
          .flatMap(existing -> authUserJpaRepository.findById(existing.getUserId()))
          .map(existingUser -> toRecord(existingUser, provider, providerSubject, false))
          .orElseThrow(() -> ex);
    }
    return toRecord(user, provider, providerSubject, true);
  }

  @Override
  @Transactional
  public AuthUserRecord updateUsername(String userId, String newUsername) {
    Optional<AuthUserJpaEntity> existingOpt = authUserJpaRepository.findById(userId);
    if (existingOpt.isEmpty()) {
      return null;
    }

    AuthUserJpaEntity existing = existingOpt.get();
    existing.rename(newUsername);
    AuthUserJpaEntity saved;
    try {
      saved = authUserJpaRepository.save(existing);
    } catch (DataIntegrityViolationException ex) {
      // Keep service-level conflict handling deterministic under concurrent rename requests.
      throw new UsernameAlreadyTakenException("Username already taken", ex);
    }

    Optional<AuthIdentityJpaEntity> identity = authIdentityJpaRepository.findByUserId(userId).stream().findFirst();

    OAuthProvider provider = identity.map(AuthIdentityJpaEntity::getProvider).orElse(null);
    String providerSubject = identity.map(AuthIdentityJpaEntity::getProviderSubject).orElse(null);
    return toRecord(saved, provider, providerSubject, false);
  }

  private AuthUserRecord toRecord(
      AuthUserJpaEntity user,
      OAuthProvider provider,
      String providerSubject,
      boolean isNewUser) {
    return new AuthUserRecord(
        user.getUserId(),
        provider,
        providerSubject,
        user.getUsername(),
        user.isUsernameChanged(),
        user.getBugBalance(),
        isNewUser);
  }
}
