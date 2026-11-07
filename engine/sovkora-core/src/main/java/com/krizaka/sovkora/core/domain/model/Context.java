package com.krizaka.sovkora.core.domain.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable context for Sovkora AI requests. Carries multi-session and multi-modal preferences.
 *
 * <p>This context is inherently thread-safe and safe for concurrent access via Virtual Threads due
 * to its immutable nature and defensive copying.
 *
 * <p><b>Two namespaces, and nothing else, in {@code preferences}</b> (ADR-064). What the platform
 * declares about a turn — a Studio's scope guard, the metering marker, the session — lives under
 * {@value #PLATFORM_NAMESPACE}. What the user wrote about themselves lives under {@value
 * #USER_PREFERENCE_NAMESPACE}, put there by {@link #userPreferences}. The pipeline merges only
 * these two into the metadata its interceptors read, beside keys of its own that carry neither
 * prefix — so a preference cannot name the actor, the conversation or the roles, however it is
 * spelled and wherever the engine writes them. A preference used to be able to: {@code userId} and
 * {@code orazaka.metering.deferred} saved through the profile endpoint moved or removed the credit
 * hold.
 *
 * @param userId The unique identifier of the user making the request.
 * @param conversationId The session identifier for conversation thread state mapping.
 * @param preferences Platform declarations and namespaced user preferences, see above.
 * @param authorities The resolved security roles tied to the user session.
 * @see Authority
 */
public record Context(
    String userId,
    String conversationId,
    Map<String, Object> preferences,
    Set<Authority> authorities) {

  /**
   * What the platform declares about a turn: producer keys the engine and its interceptors read.
   */
  public static final String PLATFORM_NAMESPACE = "orazaka.";

  /**
   * How long the content of a turn may live, declared by the host service from the actor's plan
   * (the {@code retention.none} entitlement). Absent means the engine's default; {@value
   * #RETENTION_NONE} means the turn is processed and forgotten (see {@link #retentionNone()}).
   */
  public static final String RETENTION_KEY = PLATFORM_NAMESPACE + "retention";

  /** The value of {@link #RETENTION_KEY} that keeps only counters and metadata. */
  public static final String RETENTION_NONE = "none";

  /** What a user wrote about themselves, which the platform carries and never takes as its own. */
  public static final String USER_PREFERENCE_NAMESPACE = "preference.";

  /**
   * The key under which the host service records the request-bound session, for tracing across
   * services. The platform's, so it is namespaced like every other platform key.
   */
  public static final String SESSION_ID_KEY = PLATFORM_NAMESPACE + "pipeline.sessionId";

  public Context {
    Objects.requireNonNull(userId, "userId must not be null");
    Objects.requireNonNull(conversationId, "conversationId must not be null");
    preferences =
        preferences != null
            ? Collections.unmodifiableMap(Map.copyOf(preferences))
            : Collections.emptyMap();
    authorities =
        authorities != null
            ? Collections.unmodifiableSet(Set.copyOf(authorities))
            : Collections.emptySet();
  }

  /**
   * Whether this turn runs in no-storage mode ({@code retention: none}): the engine keeps counters
   * and metadata — never the prompt, the completion or a transcript — and its logs carry no
   * content. Only the platform namespace can declare it, so a user preference cannot lower or raise
   * it (ADR-064).
   *
   * @return {@code true} when the host declared {@value #RETENTION_NONE}
   */
  public boolean retentionNone() {
    return RETENTION_NONE.equals(preferences.get(RETENTION_KEY));
  }

  /**
   * Checks if the user session has the specified authority name.
   *
   * <p>This checking operation is thread-safe and non-blocking under Virtual Thread execution
   * contexts, as it queries the immutable local set of user authorities. The input parameter is
   * normalized internally to uppercase and stripped of whitespace.
   *
   * @param authName The name of the authority role to look up.
   * @return {@code true} if the authority is present in the session context, otherwise {@code
   *     false}.
   * @see Authority
   */
  public boolean hasAuthority(String authName) {
    return authorities.stream()
        .anyMatch(auth -> auth.name().equals(authName.toUpperCase().strip()));
  }

  /**
   * Places what a user wrote under the user's own namespace, so no key of theirs can be read as one
   * the platform declared.
   *
   * <p>Every key is prefixed, none is dropped or refused: {@code language} arrives as {@code
   * preference.language}, and {@code orazaka.metering.deferred} as {@code
   * preference.orazaka.metering.deferred} — which no interceptor reads. Namespacing makes the
   * collision inexpressible instead of forbidding it one name at a time.
   *
   * @param written the user's own preferences, as stored; {@code null} is empty
   * @return the same entries under {@value #USER_PREFERENCE_NAMESPACE}
   */
  public static Map<String, Object> userPreferences(Map<String, ?> written) {
    if (written == null) {
      return Map.of();
    }
    return written.entrySet().stream()
        .filter(entry -> entry.getKey() != null && entry.getValue() != null)
        .collect(
            HashMap::new,
            (map, entry) -> map.put(USER_PREFERENCE_NAMESPACE + entry.getKey(), entry.getValue()),
            HashMap::putAll);
  }

  /**
   * Creates an anonymous context for test and internal pipeline use. Satisfies the non-null
   * invariant with safe defaults.
   *
   * @return An anonymous {@link Context} with no preferences or authorities.
   */
  public static Context anonymous() {
    return new Context("anonymous", "none", Map.of(), Set.of());
  }

  /**
   * Creates a deterministic context for unit tests. Satisfies the non-null invariant with stable
   * test identifiers and no preferences or authorities.
   *
   * @return A {@link Context} with fixed test identifiers.
   */
  public static Context forTest() {
    return new Context("test-user", "test-conversation", Map.of(), Set.of());
  }
}
