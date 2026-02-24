package com.wtfrepo.backend.shared.security;

import com.wtfrepo.backend.auth.domain.AuthUser;
import java.time.Instant;
import java.time.Duration;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService implements TokenService {

  private final JwtEncoder jwtEncoder;
  private final SecurityTokenProperties tokenProperties;

  public JwtTokenService(JwtEncoder jwtEncoder, SecurityTokenProperties tokenProperties) {
    this.jwtEncoder = jwtEncoder;
    this.tokenProperties = tokenProperties;
  }

  @Override
  public IssuedToken issueToken(AuthUser user) {
    Instant issuedAt = Instant.now();
    Duration ttl = tokenProperties.resolveTtl(user.roles());
    Instant expiresAt = issuedAt.plus(ttl);
    String tokenId = "tkn_" + UUID.randomUUID();

    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(tokenProperties.getIssuer())
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .subject(user.userId())
            .id(tokenId)
            .claim("username", user.username())
            .claim("roles", user.roles().stream().map(Enum::name).toList())
            .build();

    String tokenValue =
        jwtEncoder
            .encode(
                JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).build(),
                    claims))
            .getTokenValue();

    long expiresIn = ttl.toSeconds();
    return new IssuedToken(tokenValue, expiresIn, expiresAt);
  }
}
