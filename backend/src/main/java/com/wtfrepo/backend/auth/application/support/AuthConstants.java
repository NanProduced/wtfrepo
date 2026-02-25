package com.wtfrepo.backend.auth.application.support;

/**
 * Centralized auth constants to reduce magic strings and duplicated literals.
 */
public final class AuthConstants {

  private AuthConstants() {}

  public static final class IdentityProof {

    public static final String PROVIDER_CLAIM = "provider";

    private IdentityProof() {}
  }

  public static final class Header {

    public static final String IDEMPOTENCY_KEY = "X-Idempotency-Key";

    private Header() {}
  }

  public static final class Username {

    public static final String PREFIX = "patient_";
    public static final int RANDOM_SUFFIX_LENGTH = 6;
    public static final int MAX_GENERATION_ATTEMPTS = 20;

    private Username() {}
  }

  public static final class RateLimit {

    public static final String EXCHANGE_KEY_PREFIX = "auth:rate:exchange:";
    public static final String RENAME_KEY_PREFIX = "auth:rate:rename:";

    private RateLimit() {}
  }

  public static final class Message {

    public static final String INVALID_IDENTITY_PROOF = "Invalid identity proof";
    public static final String UNSUPPORTED_IDENTITY_PROOF_ALGORITHM =
        "Unsupported identity proof signature algorithm";
    public static final String INVALID_IDENTITY_PROOF_SIGNATURE = "Invalid identity proof signature";
    public static final String INVALID_IDENTITY_PROOF_CLAIMS = "Invalid identity proof claims";
    public static final String INVALID_IDENTITY_PROOF_ISSUER = "Invalid identity proof issuer";
    public static final String INVALID_IDENTITY_PROOF_AUDIENCE = "Invalid identity proof audience";
    public static final String INVALID_IDENTITY_PROOF_SUBJECT = "Invalid identity proof subject";
    public static final String IDENTITY_PROOF_PROVIDER_MISMATCH = "Identity proof provider mismatch";
    public static final String INVALID_IDENTITY_PROOF_JTI = "Invalid identity proof jti";
    public static final String MISSING_IDENTITY_PROOF_TEMPORAL_CLAIMS =
        "Missing identity proof temporal claims";
    public static final String INVALID_IDENTITY_PROOF_LIFETIME = "Invalid identity proof lifetime";
    public static final String IDENTITY_PROOF_LIFETIME_EXCEEDED =
        "Identity proof lifetime exceeds maximum allowed duration";
    public static final String IDENTITY_PROOF_NOT_ACTIVE = "Identity proof is not active yet";
    public static final String IDENTITY_PROOF_EXPIRED = "Identity proof expired";
    public static final String INVALID_IDENTITY_PROOF_CLAIM_PREFIX = "Invalid identity proof claim: ";

    public static final String TOO_MANY_EXCHANGE_REQUESTS = "Too many exchange requests";
    public static final String TOO_MANY_RENAME_REQUESTS = "Too many rename requests";
    public static final String IDEMPOTENCY_CONFLICT = "Idempotency conflict for request id";
    public static final String INVALID_OR_CONSUMED_OAUTH_STATE = "Invalid or consumed oauth state";
    public static final String USER_NOT_FOUND = "User not found";
    public static final String RENAME_QUOTA_EXHAUSTED = "Rename quota exhausted";
    public static final String USERNAME_ALREADY_TAKEN = "Username already taken";
    public static final String USERNAME_CONTAINS_FORBIDDEN_KEYWORDS =
        "Username contains forbidden keywords";
    public static final String FAILED_TO_ALLOCATE_USERNAME = "Failed to allocate username";
    public static final String USER_BANNED = "User is banned";

    private Message() {}
  }
}
