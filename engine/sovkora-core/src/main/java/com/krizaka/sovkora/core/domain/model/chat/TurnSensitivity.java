package com.krizaka.sovkora.core.domain.model.chat;

import java.util.Map;
import java.util.Set;

/**
 * Whether a turn is SENSITIVE (ADR-051, ADR-064): a pack with {@code regulatory_class: SENSITIVE}
 * switches on a scope guard, a safety control and a subject guard, which declare themselves in the
 * turn's metadata. A turn carrying any of them is treated as sensitive: its prompt is never put in
 * a provider's cache. The keys are those of {@code JobCommand}; a test in the interceptors module
 * keeps the two in step.
 */
public final class TurnSensitivity {

  /** {@code JobCommand.SCOPE_REFUSED_TERMS_KEY}. */
  private static final String SCOPE_KEY = "orazaka.scope.refused-terms";

  /** {@code JobCommand.SAFETY_CRISIS_TERMS_KEY}. */
  private static final String SAFETY_KEY = "orazaka.safety.crisis-terms";

  /** {@code JobCommand.GUARD_SUBJECT_KEY}. */
  private static final String GUARD_KEY = "orazaka.guard.subject";

  private static final Set<String> DECLARATIONS = Set.of(SCOPE_KEY, SAFETY_KEY, GUARD_KEY);

  private TurnSensitivity() {}

  /**
   * @return the metadata key of the scope guard's declaration
   */
  public static String scopeKey() {
    return SCOPE_KEY;
  }

  /**
   * @return the metadata key of the safety control's declaration
   */
  public static String safetyKey() {
    return SAFETY_KEY;
  }

  /**
   * @return the metadata key of the subject guard's declaration
   */
  public static String guardKey() {
    return GUARD_KEY;
  }

  /**
   * @param userMetadata the turn's metadata, may be {@code null}
   * @return true when the turn declares one of the sensitive controls
   */
  public static boolean of(Map<String, Object> userMetadata) {
    return userMetadata != null && DECLARATIONS.stream().anyMatch(userMetadata::containsKey);
  }
}
