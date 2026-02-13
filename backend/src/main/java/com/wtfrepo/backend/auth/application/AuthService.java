package com.wtfrepo.backend.auth.application;

import com.wtfrepo.backend.auth.api.AuthExchangeRequest;
import com.wtfrepo.backend.auth.application.economy.AuthEconomyBridge;
import com.wtfrepo.backend.auth.application.policy.UsernamePolicy;
import com.wtfrepo.backend.auth.application.support.AuthConstants;
import com.wtfrepo.backend.auth.application.support.AuthExceptions;
import com.wtfrepo.backend.auth.application.support.AuthRequestFingerprintCalculator;
import com.wtfrepo.backend.auth.domain.AuthUser;
import com.wtfrepo.backend.auth.domain.OAuthProvider;
import com.wtfrepo.backend.shared.security.IssuedToken;
import com.wtfrepo.backend.shared.security.TokenService;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final AuthUserStore authUserStore;
  private final TokenService tokenService;
  private final OAuthStateStore oAuthStateStore;
  private final AuthExchangeIdempotencyStore authExchangeIdempotencyStore;
  private final AuthRateLimiter authRateLimiter;
  private final OAuthIdentityVerifier oAuthIdentityVerifier;
  private final UsernamePolicy usernamePolicy;
  private final AuthRequestFingerprintCalculator requestFingerprintCalculator;
  private final AuthEconomyBridge authEconomyBridge;

  public AuthService(
      AuthUserStore authUserStore,
      TokenService tokenService,
      OAuthStateStore oAuthStateStore,
      AuthExchangeIdempotencyStore authExchangeIdempotencyStore,
      AuthRateLimiter authRateLimiter,
      OAuthIdentityVerifier oAuthIdentityVerifier,
      UsernamePolicy usernamePolicy,
      AuthRequestFingerprintCalculator requestFingerprintCalculator,
      AuthEconomyBridge authEconomyBridge) {
    this.authUserStore = authUserStore;
    this.tokenService = tokenService;
    this.oAuthStateStore = oAuthStateStore;
    this.authExchangeIdempotencyStore = authExchangeIdempotencyStore;
    this.authRateLimiter = authRateLimiter;
    this.oAuthIdentityVerifier = oAuthIdentityVerifier;
    this.usernamePolicy = usernamePolicy;
    this.requestFingerprintCalculator = requestFingerprintCalculator;
    this.authEconomyBridge = authEconomyBridge;
  }

  /**
   * Exchanges BFF identity proof for backend access token.
   *
   * <p>Key responsibilities: verify proof, enforce rate-limit, apply idempotency-key replay,
   * consume oauthState once, load/create user, and issue backend token.
   */
  @Transactional
  public ExchangeResult exchange(
      AuthExchangeRequest request, String requestId, String idempotencyKey) {
    String providerSubject =
        oAuthIdentityVerifier.verifyAndResolveSubject(request.provider(), request.identityProof());
    String rateLimitKey = request.provider().name() + ":" + providerSubject;
    if (!authRateLimiter.allowExchange(rateLimitKey)) {
      throw AuthExceptions.tooManyRequests(AuthConstants.Message.TOO_MANY_EXCHANGE_REQUESTS);
    }

    String requestFingerprint = requestFingerprintCalculator.fingerprint(request);
    Optional<AuthExchangeIdempotencyStore.StoredExchangeResult> existing =
        authExchangeIdempotencyStore.find(idempotencyKey);
    if (existing.isPresent()) {
      if (!existing.get().requestFingerprint().equals(requestFingerprint)) {
        throw AuthExceptions.conflict(AuthConstants.Message.IDEMPOTENCY_CONFLICT);
      }
      log.debug(
          "auth_exchange_idempotent_hit requestId={} idempotencyKey={} userId={}",
          requestId,
          idempotencyKey,
          existing.get().result().authUser().userId());
      return existing.get().result();
    }

    validateOAuthState(request.oauthState());

    AuthUserRecord userRecord =
        authUserStore
            .findByProviderIdentity(request.provider(), providerSubject)
            .orElseGet(() -> createUser(request.provider(), providerSubject));

    IssuedToken issuedToken = tokenService.issueToken(userRecord.toAuthUser());

    log.info(
        "auth_exchange_success requestId={} idempotencyKey={} userId={} provider={} isNewUser={}",
        requestId,
        idempotencyKey,
        userRecord.userId(),
        userRecord.provider(),
        userRecord.isNewUser());

    ExchangeResult result = new ExchangeResult(
        issuedToken,
        userRecord.toAuthUser(),
        userRecord.isNewUser(),
        userRecord.isNewUser() ? authEconomyBridge.initialBugGrantForNewUser() : 0);

    authExchangeIdempotencyStore.save(idempotencyKey, requestFingerprint, result);
    return result;
  }

  /** Returns authenticated user profile from backend token subject. */
  @Transactional(readOnly = true)
  public AuthUser me(Jwt jwt) {
    String userId = jwt.getSubject();
    return authUserStore
        .findByUserId(userId)
        .map(AuthUserRecord::toAuthUser)
        .orElseThrow(() -> AuthExceptions.notFound(AuthConstants.Message.USER_NOT_FOUND));
  }

  /**
   * Applies one-time username rename policy.
   *
   * <p>Includes rate-limit, forbidden keyword validation, rename quota check and uniqueness check.
   */
  @Transactional
  public AuthUser rename(String userId, String newUsername, String requestId) {
    if (!authRateLimiter.allowRename(userId)) {
      throw AuthExceptions.tooManyRequests(AuthConstants.Message.TOO_MANY_RENAME_REQUESTS);
    }

    validateUsername(newUsername);

    Optional<AuthUserRecord> recordOpt = authUserStore.findByUserId(userId);
    AuthUserRecord record =
        recordOpt.orElseThrow(
            () -> AuthExceptions.notFound(AuthConstants.Message.USER_NOT_FOUND));

    if (record.usernameChanged()) {
      throw AuthExceptions.conflict(AuthConstants.Message.RENAME_QUOTA_EXHAUSTED);
    }

    if (authUserStore.usernameExists(newUsername)) {
      throw AuthExceptions.conflict(AuthConstants.Message.USERNAME_ALREADY_TAKEN);
    }

    AuthUserRecord updated;
    try {
      updated = authUserStore.updateUsername(userId, newUsername);
    } catch (UsernameAlreadyTakenException ex) {
      throw AuthExceptions.conflict(AuthConstants.Message.USERNAME_ALREADY_TAKEN);
    }

    if (updated == null) {
      throw AuthExceptions.notFound(AuthConstants.Message.USER_NOT_FOUND);
    }
    log.info("auth_rename_success requestId={} userId={} username={}", requestId, userId, newUsername);
    return updated.toAuthUser();
  }

  private void validateOAuthState(String oauthState) {
    boolean consumed = oAuthStateStore.consumeOnce(oauthState);
    if (!consumed) {
      throw AuthExceptions.conflict(AuthConstants.Message.INVALID_OR_CONSUMED_OAUTH_STATE);
    }
  }

  private void validateUsername(String newUsername) {
    if (usernamePolicy.containsForbiddenKeyword(newUsername)) {
      throw AuthExceptions.validation(AuthConstants.Message.USERNAME_CONTAINS_FORBIDDEN_KEYWORDS);
    }
  }

  private AuthUserRecord createUser(
      OAuthProvider provider, String providerSubject) {
    String username = generateUniqueUsername();
    // TODO(M03-economy): align bootstrap grant with economy source-of-truth policy publishing.
    return authUserStore.saveNewUser(
        provider, providerSubject, username, authEconomyBridge.initialBugGrantForNewUser());
  }

  /**
   * Generates a unique default username with bounded retries.
   */
  private String generateUniqueUsername() {
    for (int i = 0; i < usernamePolicy.maxGenerationAttempts(); i++) {
      String candidate = usernamePolicy.nextCandidate();
      if (!authUserStore.usernameExists(candidate)) {
        return candidate;
      }
    }
    throw AuthExceptions.internal(AuthConstants.Message.FAILED_TO_ALLOCATE_USERNAME);
  }

  public record ExchangeResult(
      IssuedToken issuedToken,
      AuthUser authUser,
      boolean isNewUser,
      int initialBugGrant) {}
}
