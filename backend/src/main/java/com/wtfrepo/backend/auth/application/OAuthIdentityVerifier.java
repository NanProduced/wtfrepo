package com.wtfrepo.backend.auth.application;

import com.wtfrepo.backend.auth.domain.OAuthProvider;

/**
 * Verifies upstream/BFF identity proof and resolves stable provider subject.
 */
public interface OAuthIdentityVerifier {

  /**
   * Verifies proof authenticity + claims and returns provider-specific subject (`sub`).
   */
  String verifyAndResolveSubject(OAuthProvider provider, String identityProof);
}
