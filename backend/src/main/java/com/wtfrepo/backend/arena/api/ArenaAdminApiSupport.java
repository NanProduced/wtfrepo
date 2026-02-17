package com.wtfrepo.backend.arena.api;

import com.wtfrepo.backend.arena.application.support.ArenaConstants;
import com.wtfrepo.backend.arena.application.support.ArenaExceptions;
import java.util.Collection;
import java.util.stream.Stream;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

/** Shared helper methods for arena admin API endpoints. */
final class ArenaAdminApiSupport {

  private static final String ROLES_CLAIM = "roles";
  private static final String ROLE_ADMIN = "ADMIN";
  private static final String ROLE_MANAGER = "MANAGER";

  private ArenaAdminApiSupport() {}

  static String requireAdminUserId(Jwt jwt) {
    if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
      throw ArenaExceptions.unauthorized(ArenaConstants.Message.AUTH_REQUIRED);
    }
    if (!hasAdminRole(jwt)) {
      throw ArenaExceptions.forbidden(ArenaConstants.Message.FORBIDDEN_ADMIN);
    }
    return jwt.getSubject().trim();
  }

  private static boolean hasAdminRole(Jwt jwt) {
    Object rolesClaim = jwt.getClaim(ROLES_CLAIM);
    if (rolesClaim instanceof Collection<?> roles) {
      return roles.stream().map(String::valueOf).anyMatch(ArenaAdminApiSupport::isAdminRole);
    }

    if (rolesClaim instanceof String roles) {
      return Stream.of(roles.split(","))
          .map(String::trim)
          .filter(StringUtils::hasText)
          .anyMatch(ArenaAdminApiSupport::isAdminRole);
    }
    return false;
  }

  private static boolean isAdminRole(String role) {
    return ROLE_ADMIN.equalsIgnoreCase(role) || ROLE_MANAGER.equalsIgnoreCase(role);
  }
}
