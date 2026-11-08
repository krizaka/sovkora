package com.krizaka.sovkora.knowledge.domain.model;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Who a read is made for: the verified principal the core sends in the {@code act} claim of its
 * signed service token (ADR-091). It is never read from a request body or a header — the claim is
 * covered by the token's signature, so a caller cannot choose it.
 *
 * <p>A principal that cannot be parsed is no principal ({@link #fromClaim} is empty) and the caller
 * is refused: access is closed by default.
 *
 * @param tenant the tenant the caller belongs to; never {@value #PLATFORM_TENANT}
 * @param subject the caller's opaque id
 * @param roles the caller's roles, e.g. {@code ROLE_HR}
 * @param groups the caller's groups, e.g. {@code GROUP_FINANCE}
 */
public record ActingPrincipal(
    String tenant, String subject, Set<String> roles, Set<String> groups) {

  private static final Logger log = LoggerFactory.getLogger(ActingPrincipal.class);

  /** The tenant of the platform's own content; it is a source's tenant, never a caller's. */
  public static final String PLATFORM_TENANT = "*";

  private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.@-]{1,100}");

  public ActingPrincipal {
    requireName(tenant, "tenant");
    requireName(subject, "subject");
    if (PLATFORM_TENANT.equals(tenant)) {
      throw new IllegalArgumentException("tenant must not be the platform tenant");
    }
    roles = validated(roles, "role");
    groups = validated(groups, "group");
  }

  /**
   * The grants this principal can match in a source's ACL.
   *
   * @return {@code role:<ROLE>}, {@code group:<GROUP>} and {@code user:<subject>} entries
   */
  public Set<String> grantees() {
    Set<String> grants = new LinkedHashSet<>();
    roles.forEach(role -> grants.add(Acl.role(role)));
    groups.forEach(group -> grants.add(Acl.group(group)));
    grants.add(Acl.user(subject));
    return Set.copyOf(grants);
  }

  /** Whether the principal holds {@code role}. */
  public boolean hasRole(String role) {
    return roles.contains(role);
  }

  /**
   * Reads the {@code act} claim of a verified token.
   *
   * @param claim the claim value: a JSON object with {@code sub}, {@code tenant}, {@code roles} and
   *     {@code groups}
   * @return the principal, or empty when the claim is absent or malformed
   */
  public static Optional<ActingPrincipal> fromClaim(Object claim) {
    if (!(claim instanceof Map<?, ?> map)) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          new ActingPrincipal(
              text(map.get("tenant")),
              text(map.get("sub")),
              names(map.get("roles")),
              names(map.get("groups"))));
    } catch (IllegalArgumentException | NullPointerException malformed) {
      log.debug("ActingPrincipal claim is malformed and cannot be resolved", malformed);
      return Optional.empty();
    }
  }

  private static String text(Object value) {
    return value instanceof String string ? string : null;
  }

  private static Set<String> names(Object value) {
    if (value == null) {
      return Set.of();
    }
    if (!(value instanceof Collection<?> collection)) {
      throw new IllegalArgumentException("a list of names is expected");
    }
    Set<String> names = new LinkedHashSet<>();
    for (Object item : collection) {
      names.add(text(item));
    }
    return names;
  }

  private static Set<String> validated(Set<String> names, String what) {
    Set<String> copy = new LinkedHashSet<>(names == null ? Set.of() : names);
    copy.forEach(name -> requireName(name, what));
    return Set.copyOf(copy);
  }

  private static void requireName(String value, String what) {
    if (Objects.isNull(value) || !NAME.matcher(value).matches()) {
      throw new IllegalArgumentException("invalid " + what);
    }
  }
}
