package com.krizaka.sovkora.edge.infrastructure.config.filter;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * The environment an {@code oz_} key belongs to, and the one this edge serves
 * (krizaka-internal#2132).
 *
 * <p><b>Declared, not inferred</b> (AGENTS.md §12): a key states its environment with one scope,
 * {@code env:<name>}, set when the key is minted; the edge states its own with {@code
 * AUTH_API_KEY_ENVIRONMENT}. The edge admits a key only when the two match. A key that states
 * nothing is a <b>dev</b> key — every key minted before #2132 is one — so a prod edge refuses it,
 * and a dev edge refuses a prod key that leaks. An unknown or a second {@code env:} scope is
 * refused everywhere (fail closed). Only the edge reads this scope: it opens no route ({@link
 * ApiKeyScopes} skips it).
 */
enum ApiKeyEnvironment {
  DEV,
  PROD;

  private static final String SCOPE_PREFIX = "env:";

  /** The environment named {@code declared} ({@code dev}, {@code prod}); unknown fails at start. */
  static ApiKeyEnvironment of(String declared) {
    String name = declared.strip().toUpperCase(Locale.ROOT);
    for (ApiKeyEnvironment environment : values()) {
      if (environment.name().equals(name)) {
        return environment;
      }
    }
    throw new IllegalArgumentException(
        "AUTH_API_KEY_ENVIRONMENT must be dev or prod, not '" + declared + "'");
  }

  /** {@code true} for the scope that states a key's environment, which is not a route scope. */
  static boolean isEnvironmentScope(String scope) {
    return scope.startsWith(SCOPE_PREFIX);
  }

  /** {@code true} when a key with these scopes belongs to this environment. */
  boolean admits(Collection<String> scopes) {
    List<String> declared =
        scopes == null
            ? List.of()
            : scopes.stream().filter(ApiKeyEnvironment::isEnvironmentScope).toList();
    if (declared.isEmpty()) {
      return this == DEV;
    }
    return declared.size() == 1 && declared.getFirst().equals(scopeName());
  }

  private String scopeName() {
    return SCOPE_PREFIX + name().toLowerCase(Locale.ROOT);
  }
}
