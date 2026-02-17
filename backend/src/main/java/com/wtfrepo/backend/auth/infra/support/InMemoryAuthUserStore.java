package com.wtfrepo.backend.auth.infra.support;

import com.wtfrepo.backend.auth.application.AuthUserRecord;
import com.wtfrepo.backend.auth.application.AuthUserStore;
import com.wtfrepo.backend.auth.application.UsernameAlreadyTakenException;
import com.wtfrepo.backend.auth.domain.OAuthProvider;
import com.wtfrepo.backend.auth.infra.persistence.repository.AuthUserJpaRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * In-memory auth user store fallback for non-JPA contexts (e.g. lightweight tests).
 */
@Component
@ConditionalOnMissingBean(AuthUserJpaRepository.class)
public class InMemoryAuthUserStore implements AuthUserStore {

  private final Map<String, AuthUserRecord> usersById = new ConcurrentHashMap<>();
  private final Map<String, String> userIdByIdentity = new ConcurrentHashMap<>();
  private final Map<String, String> userIdByUsername = new ConcurrentHashMap<>();
  private final AtomicLong sequence = new AtomicLong(1L);

  @Override
  public Optional<AuthUserRecord> findByProviderIdentity(OAuthProvider provider, String providerSubject) {
    String userId = userIdByIdentity.get(identityKey(provider, providerSubject));
    if (userId == null) {
      return Optional.empty();
    }
    AuthUserRecord stored = usersById.get(userId);
    if (stored == null) {
      return Optional.empty();
    }
    return Optional.of(
        new AuthUserRecord(
            stored.userId(),
            provider,
            providerSubject,
            stored.username(),
            stored.usernameChanged(),
            stored.bugBalance(),
            false));
  }

  @Override
  public Optional<AuthUserRecord> findByUserId(String userId) {
    return Optional.ofNullable(usersById.get(userId));
  }

  @Override
  public boolean usernameExists(String username) {
    return userIdByUsername.containsKey(username);
  }

  @Override
  public synchronized AuthUserRecord saveNewUser(
      OAuthProvider provider,
      String providerSubject,
      String username,
      long initialBugBalance) {
    String identityKey = identityKey(provider, providerSubject);
    String existingUserId = userIdByIdentity.get(identityKey);
    if (existingUserId != null) {
      AuthUserRecord existing = usersById.get(existingUserId);
      if (existing == null) {
        throw new IllegalStateException("Missing user for identity key=" + identityKey);
      }
      return new AuthUserRecord(
          existing.userId(),
          provider,
          providerSubject,
          existing.username(),
          existing.usernameChanged(),
          existing.bugBalance(),
          false);
    }

    String usernameOwner = userIdByUsername.get(username);
    if (usernameOwner != null) {
      throw new UsernameAlreadyTakenException("Username already exists");
    }

    String userId = "u_mem_" + sequence.getAndIncrement();
    AuthUserRecord persisted =
        new AuthUserRecord(
            userId,
            provider,
            providerSubject,
            username,
            false,
            initialBugBalance,
            false);
    usersById.put(userId, persisted);
    userIdByIdentity.put(identityKey, userId);
    userIdByUsername.put(username, userId);

    return new AuthUserRecord(
        userId, provider, providerSubject, username, false, initialBugBalance, true);
  }

  @Override
  public synchronized AuthUserRecord updateUsername(String userId, String newUsername) {
    AuthUserRecord existing = usersById.get(userId);
    if (existing == null) {
      return null;
    }

    String usernameOwner = userIdByUsername.get(newUsername);
    if (usernameOwner != null && !usernameOwner.equals(userId)) {
      throw new UsernameAlreadyTakenException("Username already exists");
    }

    userIdByUsername.remove(existing.username());
    userIdByUsername.put(newUsername, userId);

    AuthUserRecord updated =
        new AuthUserRecord(
            existing.userId(),
            existing.provider(),
            existing.providerSubject(),
            newUsername,
            true,
            existing.bugBalance(),
            false);
    usersById.put(userId, updated);
    return updated;
  }

  private String identityKey(OAuthProvider provider, String providerSubject) {
    return provider.name() + "::" + providerSubject;
  }
}
