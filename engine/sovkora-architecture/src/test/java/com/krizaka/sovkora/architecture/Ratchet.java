package com.krizaka.sovkora.architecture;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The idea of {@code sg-ratchet} and {@code lint-ratchet}, for ArchUnit rules: the classes that
 * violate a rule today are the baseline; a violation that is not in it fails the build, a baseline
 * entry that was fixed never does (the debt only goes down). A new class cannot hide behind a fixed
 * one: the baseline holds names, and its count is derived from them.
 */
final class Ratchet {

  private Ratchet() {}

  /**
   * The violations the baseline does not know.
   *
   * @param baseline the recorded violating classes of the rule
   * @param current the violating classes now
   * @return the new violations, sorted
   */
  static Set<String> increase(Set<String> baseline, Set<String> current) {
    Set<String> added = new TreeSet<>(current);
    added.removeAll(baseline);
    return added;
  }

  /**
   * The baseline after an update: every rule at its current violations, never above what was
   * recorded (a rule seen for the first time is recorded as it is).
   *
   * @param baseline the recorded violating classes per rule
   * @param current the violating classes now per rule
   * @return the new baseline
   * @throws IllegalStateException when a rule has a violation the baseline does not know
   */
  static Map<String, Set<String>> lowered(
      Map<String, Set<String>> baseline, Map<String, Set<String>> current) {
    Map<String, Set<String>> next = new TreeMap<>();
    current.forEach(
        (rule, classes) -> {
          Set<String> known = baseline.get(rule);
          if (known != null && !increase(known, classes).isEmpty()) {
            throw new IllegalStateException(
                "The ratchet never goes up: "
                    + rule
                    + " has new violations "
                    + increase(known, classes));
          }
          next.put(rule, new TreeSet<>(classes));
        });
    return next;
  }
}
