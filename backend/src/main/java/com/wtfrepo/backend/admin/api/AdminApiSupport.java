package com.wtfrepo.backend.admin.api;

import com.wtfrepo.backend.admin.application.model.AdminModels.AdminPrincipal;
import com.wtfrepo.backend.admin.application.support.AdminConstants;
import com.wtfrepo.backend.admin.application.support.AdminExceptions;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

/** Shared helper methods for admin API endpoints. */
final class AdminApiSupport {

  private AdminApiSupport() {}

  static AdminPrincipal requireUserPrincipal(Jwt jwt) {
    if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
      throw AdminExceptions.unauthorized(AdminConstants.Message.AUTH_REQUIRED);
    }
    List<String> roles = resolveRoles(jwt.getClaim(AdminConstants.Claim.ROLES));
    String username = jwt.getClaimAsString("username");
    return new AdminPrincipal(jwt.getSubject().trim(), username, roles);
  }

  static AdminPrincipal requireAdminPrincipal(Jwt jwt) {
    AdminPrincipal principal = requireUserPrincipal(jwt);
    if (!hasAdminRole(principal.roles())) {
      throw AdminExceptions.forbidden(AdminConstants.Message.FORBIDDEN_ADMIN);
    }
    return principal;
  }

  private static boolean hasAdminRole(List<String> roles) {
    return roles.stream().anyMatch(AdminApiSupport::isAdminRole);
  }

  private static boolean isAdminRole(String role) {
    return AdminConstants.Role.ADMIN.equalsIgnoreCase(role)
        || AdminConstants.Role.MANAGER.equalsIgnoreCase(role);
  }

  private static List<String> resolveRoles(Object rolesClaim) {
    Set<String> resolved = new LinkedHashSet<>();
    if (rolesClaim instanceof Collection<?> roles) {
      roles.forEach(role -> addRole(resolved, role));
    } else if (rolesClaim instanceof String roles) {
      Stream.of(roles.split(",")).forEach(role -> addRole(resolved, role));
    }
    return List.copyOf(resolved);
  }

  private static void addRole(Set<String> resolved, Object role) {
    if (role == null) {
      return;
    }
    String value = String.valueOf(role).trim();
    if (StringUtils.hasText(value)) {
      resolved.add(value);
    }
  }

}
