package com.krizaka.sovkora.core.domain.model.security;

import com.krizaka.sovkora.core.domain.model.Authority;
import com.krizaka.sovkora.core.domain.model.Context;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Who a retrieval or a tool call is made for: the tenant, the user and the roles and groups the
 * host resolved from the verified session (ADR-091). It is built from the {@link Context} the host
 * service declared, never from a request field, and it is the one value the knowledge service and
 * the tool executor decide on.
 *
 * <p>The directory hands the engine one list of authorities. Three kinds are told apart by their
 * prefix: {@code TENANT_<id>} names the tenant, {@code GROUP_<name>} a group, anything else is a
 * role. Without a {@code TENANT_} authority a user is a tenant of one (their own id), so two users
 * share documents only when an administrator put them in the same tenant. More than one {@code
 * TENANT_} authority is ambiguous and yields no actor: access is closed by default.
 *
 * @param tenant the tenant, never blank and never {@code *}
 * @param subject the user's opaque id
 * @param roles the roles, e.g. {@code ROLE_HR}
 * @param groups the groups, e.g. {@code GROUP_FINANCE}
 */
public record Actor(String tenant, String subject, Set<String> roles, Set<String> groups) {

  /** The authority that names a tenant. */
  public static final String TENANT_PREFIX = "TENANT_";

  /** The authority that names a group. */
  public static final String GROUP_PREFIX = "GROUP_";

  /**
   * What a tenant, a user id, a role or a group may be made of: nothing that could split a list.
   */
  public static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.@-]{1,100}");

  private static final String ANONYMOUS = "anonymous";

  public Actor {
    requireName(tenant, "tenant");
    requireName(subject, "subject");
    if ("*".equals(tenant)) {
      throw new IllegalArgumentException("invalid tenant");
    }
    roles = checked(roles);
    groups = checked(groups);
  }

  /**
   * The actor of a turn.
   *
   * @param context the context the host service declared
   * @return the actor, or empty for an anonymous or ambiguous context
   */
  public static Optional<Actor> from(Context context) {
    if (context == null) {
      return Optional.empty();
    }
    return of(context.userId(), context.authorities().stream().map(Authority::name).toList());
  }

  /**
   * The actor of a pipeline turn, from the platform keys {@code userId} and {@code roles} the
   * pipeline sets after the user's own preferences (so a preference cannot name them).
   *
   * @param userMetadata the turn's user metadata
   * @return the actor, or empty
   */
  public static Optional<Actor> from(java.util.Map<String, Object> userMetadata) {
    if (userMetadata == null
        || !(userMetadata.get("userId") instanceof String userId)
        || !(userMetadata.get("roles") instanceof Collection<?> roles)) {
      return Optional.empty();
    }
    return of(
        userId, roles.stream().filter(String.class::isInstance).map(String.class::cast).toList());
  }

  /**
   * The actor of a user id and its authorities.
   *
   * @param userId the user's id
   * @param authorities the authorities the directory granted
   * @return the actor, or empty for an anonymous, malformed or ambiguous identity
   */
  public static Optional<Actor> of(String userId, Collection<String> authorities) {
    if (userId == null || ANONYMOUS.equals(userId) || !NAME.matcher(userId).matches()) {
      return Optional.empty();
    }
    Set<String> tenants = new LinkedHashSet<>();
    Set<String> roles = new LinkedHashSet<>();
    Set<String> groups = new LinkedHashSet<>();
    for (String authority : authorities) {
      if (authority == null || !NAME.matcher(authority).matches()) {
        continue;
      }
      if (authority.startsWith(TENANT_PREFIX)) {
        tenants.add(authority.substring(TENANT_PREFIX.length()));
      } else if (authority.startsWith(GROUP_PREFIX)) {
        groups.add(authority);
      } else {
        roles.add(authority);
      }
    }
    if (tenants.size() > 1) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          new Actor(tenants.isEmpty() ? userId : tenants.iterator().next(), userId, roles, groups));
    } catch (IllegalArgumentException malformed) {
      return Optional.empty();
    }
  }

  /** Whether the actor holds any of {@code required}. An empty requirement is held by nobody. */
  public boolean holdsAny(Set<String> required) {
    return required.stream().anyMatch(role -> roles.contains(role) || groups.contains(role));
  }

  /** Separates the parts of a scope digest; a NUL cannot appear in a tenant, a user or a role. */
  private static final String SCOPE_SEPARATOR = "\u0000";

  /**
   * Identifies what this actor may see, for a cache key: two actors share a cached answer only if
   * they are the same tenant, the same user and hold the same roles and groups. A changed role is a
   * new scope, so access that was removed is not served from a cache.
   *
   * @return a short digest
   */
  public String scope() {
    String joined =
        tenant
            + SCOPE_SEPARATOR
            + subject
            + SCOPE_SEPARATOR
            + String.join(",", new TreeSet<>(roles))
            + SCOPE_SEPARATOR
            + String.join(",", new TreeSet<>(groups));
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(joined.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest, 0, 16);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static Set<String> checked(Set<String> names) {
    Set<String> copy = new LinkedHashSet<>(names == null ? Set.of() : names);
    copy.forEach(name -> requireName(name, "name"));
    return Set.copyOf(copy);
  }

  private static void requireName(String value, String what) {
    if (value == null || !NAME.matcher(value).matches()) {
      throw new IllegalArgumentException("invalid " + what);
    }
  }
}
