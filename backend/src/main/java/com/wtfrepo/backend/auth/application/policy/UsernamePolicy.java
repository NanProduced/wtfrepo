package com.wtfrepo.backend.auth.application.policy;

import com.wtfrepo.backend.auth.application.AuthContractProperties;
import com.wtfrepo.backend.auth.application.support.AuthConstants;
import java.util.Random;
import org.springframework.stereotype.Component;

/**
 * Encapsulates username generation and validation policy for auth module.
 */
@Component
public class UsernamePolicy {

  private final AuthContractProperties authContractProperties;
  private final Random random = new Random();

  public UsernamePolicy(AuthContractProperties authContractProperties) {
    this.authContractProperties = authContractProperties;
  }

  public boolean containsForbiddenKeyword(String username) {
    String normalized = username.toLowerCase();
    return authContractProperties.getForbiddenUsernameKeywords().stream()
        .map(String::toLowerCase)
        .anyMatch(normalized::contains);
  }

  public String nextCandidate() {
    return AuthConstants.Username.PREFIX + randomSuffix();
  }

  public int maxGenerationAttempts() {
    return AuthConstants.Username.MAX_GENERATION_ATTEMPTS;
  }

  private String randomSuffix() {
    String token = Integer.toString(Math.abs(random.nextInt()), 36);
    int expectedLength = AuthConstants.Username.RANDOM_SUFFIX_LENGTH;
    return token.length() >= expectedLength
        ? token.substring(0, expectedLength)
        : String.format("%1$-" + expectedLength + "s", token).replace(' ', '0');
  }
}
