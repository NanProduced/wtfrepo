package com.wtfrepo.backend.auth.application.economy;

import org.springframework.stereotype.Component;

/**
 * Property-backed bridge used before dedicated economy module is implemented.
 */
@Component
public class PropertyBackedAuthEconomyBridge implements AuthEconomyBridge {

  private final AuthEconomyProperties properties;

  public PropertyBackedAuthEconomyBridge(AuthEconomyProperties properties) {
    this.properties = properties;
  }

  @Override
  public int initialBugGrantForNewUser() {
    return properties.getInitialBugGrant();
  }
}
