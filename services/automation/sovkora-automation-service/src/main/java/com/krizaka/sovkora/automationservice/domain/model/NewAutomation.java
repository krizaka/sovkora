package com.krizaka.sovkora.automationservice.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * What a caller asks for when creating an automation. It validates itself: the owner of a field
 * rejects a bad value (ERR-106).
 *
 * @param name the label, 1 to 120 characters
 * @param studioKey the Studio to run, a lowercase key
 * @param inputs the fixed answers to the Studio's form, or null for none
 * @param enabled whether it may start runs, true when null
 */
public record NewAutomation(
    String name, String studioKey, Map<String, Object> inputs, Boolean enabled) {

  /** The longest label. */
  public static final int NAME_MAX = 120;

  private static final Pattern STUDIO_KEY = Pattern.compile("[a-z0-9][a-z0-9._-]{0,118}");

  /** Compact canonical constructor enforcing the record's invariants (ERR-106). */
  public NewAutomation {
    name = requireName(name);
    studioKey = requireStudioKey(studioKey);
    inputs = inputs == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
    enabled = enabled == null || enabled;
  }

  /**
   * Checks a label.
   *
   * @param name the candidate
   * @return the trimmed label
   * @throws IllegalArgumentException when it is blank or too long
   */
  public static String requireName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("an automation needs a name");
    }
    String trimmed = name.strip();
    if (trimmed.length() > NAME_MAX) {
      throw new IllegalArgumentException("the name is at most " + NAME_MAX + " characters");
    }
    return trimmed;
  }

  private static String requireStudioKey(String studioKey) {
    if (studioKey == null || !STUDIO_KEY.matcher(studioKey).matches()) {
      throw new IllegalArgumentException(
          "studioKey is the key of a Studio: lowercase letters, digits, '.', '-' or '_'");
    }
    return studioKey;
  }
}
