package com.wtfrepo.backend.shared.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.util.StringUtils;

/** JwtDecoder decorator that rejects blacklisted token ids. */
public class BlacklistAwareJwtDecoder implements JwtDecoder {

  private final JwtDecoder delegate;
  private final TokenBlacklistStore blacklistStore;

  public BlacklistAwareJwtDecoder(JwtDecoder delegate, TokenBlacklistStore blacklistStore) {
    this.delegate = delegate;
    this.blacklistStore = blacklistStore;
  }

  @Override
  public Jwt decode(String token) throws JwtException {
    Jwt jwt = delegate.decode(token);
    String tokenId = jwt.getId();
    if (StringUtils.hasText(tokenId) && blacklistStore.isBlacklisted(tokenId)) {
      throw new JwtException("Token is blacklisted");
    }
    return jwt;
  }
}
