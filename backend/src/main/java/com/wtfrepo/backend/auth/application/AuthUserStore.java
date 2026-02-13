package com.wtfrepo.backend.auth.application;

import com.wtfrepo.backend.auth.domain.OAuthProvider;
import java.util.Optional;

public interface AuthUserStore {

  Optional<AuthUserRecord> findByProviderIdentity(OAuthProvider provider, String providerSubject);

  Optional<AuthUserRecord> findByUserId(String userId);

  boolean usernameExists(String username);

  AuthUserRecord saveNewUser(
      OAuthProvider provider, String providerSubject, String username, long initialBugBalance);

  AuthUserRecord updateUsername(String userId, String newUsername);
}
