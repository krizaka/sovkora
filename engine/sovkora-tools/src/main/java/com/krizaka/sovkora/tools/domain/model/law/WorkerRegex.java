package com.krizaka.sovkora.tools.domain.model.law;

import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A rule's pattern as the pack's worker will run it. The worker is Python ({@code re}, Unicode by
 * default), so a pattern is accepted only when it compiles here with Unicode character classes,
 * uses none of the constructs one engine reads differently from the other, and cannot backtrack
 * exponentially (the linter of sovkora-packs {@code test_regex_safety.py}, same shape).
 */
final class WorkerRegex {

  /** Escapes Java reads and the worker does not, or reads otherwise. */
  private static final String NOT_PORTABLE_ESCAPES = "pPhHRXQEGzvV";

  /** A group that sets flags for the rest of the pattern, as {@code (?i)}. */
  private static final Pattern GLOBAL_FLAGS = Pattern.compile("\\(\\?[a-zA-Z]+\\)");

  private WorkerRegex() {}

  /**
   * Compiles a pattern the way the worker reads it.
   *
   * @param pattern the pattern
   * @return the compiled pattern
   * @throws PatternSyntaxException when it does not compile
   */
  static Pattern compile(String pattern) {
    return Pattern.compile(pattern, Pattern.UNICODE_CHARACTER_CLASS);
  }

  /**
   * Why a pattern cannot be run by the worker, if it cannot.
   *
   * @param pattern the pattern
   * @return the reason in words, or empty when it is usable
   */
  static Optional<String> problem(String pattern) {
    try {
      compile(pattern);
    } catch (PatternSyntaxException invalid) {
      return Optional.of("does not compile: " + invalid.getDescription());
    }
    Optional<String> construct = notPortable(pattern);
    if (construct.isPresent()) {
      return construct;
    }
    if (nestsUnboundedQuantifiers(pattern)) {
      return Optional.of("nests unbounded quantifiers, which can backtrack exponentially");
    }
    return Optional.empty();
  }

  /**
   * Whether a pattern finds something in the empty text, and so in every document.
   *
   * @param pattern a pattern {@link #problem} accepts
   * @return true when it matches the empty string
   */
  static boolean matchesEmpty(String pattern) {
    return compile(pattern).matcher("").find();
  }

  /**
   * Whether a pattern finds something in a text.
   *
   * @param pattern a pattern {@link #problem} accepts
   * @param text the text
   * @return true when found
   */
  static boolean finds(String pattern, String text) {
    return compile(pattern).matcher(text).find();
  }

  private static Optional<String> notPortable(String pattern) {
    boolean inClass = false;
    boolean afterQuantifier = false;
    for (int i = 0; i < pattern.length(); i++) {
      char c = pattern.charAt(i);
      if (c == '\\' && i + 1 < pattern.length()) {
        char escaped = pattern.charAt(++i);
        if (NOT_PORTABLE_ESCAPES.indexOf(escaped) >= 0) {
          return Optional.of("uses \\" + escaped + ", which the worker reads differently");
        }
        afterQuantifier = false;
        continue;
      }
      Optional<String> problem = charProblem(pattern, i, inClass, afterQuantifier);
      if (problem.isPresent()) {
        return problem;
      }
      if (inClass) {
        inClass = c != ']';
      } else {
        inClass = c == '[';
        afterQuantifier = c == '*' || c == '+' || c == '?' || c == '}';
      }
    }
    return Optional.empty();
  }

  private static Optional<String> charProblem(
      String pattern, int i, boolean inClass, boolean afterQuantifier) {
    char c = pattern.charAt(i);
    if (inClass) {
      boolean intersects = c == '&' && i + 1 < pattern.length() && pattern.charAt(i + 1) == '&';
      return intersects
          ? Optional.of("intersects classes with &&, which the worker reads as two characters")
          : Optional.empty();
    }
    if (c == '+' && afterQuantifier) {
      return Optional.of("uses a possessive quantifier, which the worker may not read");
    }
    return groupProblem(pattern.substring(i), i);
  }

  private static Optional<String> groupProblem(String rest, int at) {
    if (rest.startsWith("(?<") && !rest.startsWith("(?<=") && !rest.startsWith("(?<!")) {
      return Optional.of("names a group with (?<name>), which the worker does not read");
    }
    if (rest.startsWith("(?>")) {
      return Optional.of("uses an atomic group (?>, which the worker may not read");
    }
    if (at > 0 && GLOBAL_FLAGS.matcher(rest).lookingAt()) {
      return Optional.of("sets a flag after the start, which the worker refuses");
    }
    return Optional.empty();
  }

  /**
   * Whether a group whose body repeats without bound is itself repeated without bound, as {@code
   * (a+)+} or {@code (\\w*\\s?)*}: the shape the pack's linter refuses, read by a scan rather than
   * by a pattern that could itself backtrack.
   */
  static boolean nestsUnboundedQuantifiers(String pattern) {
    java.util.ArrayDeque<boolean[]> groups = new java.util.ArrayDeque<>();
    groups.push(new boolean[1]);
    int i = 0;
    while (i < pattern.length()) {
      char c = pattern.charAt(i);
      if (c == '\\') {
        i += 2;
      } else if (c == '[') {
        i = endOfClass(pattern, i);
      } else if (c == '(') {
        groups.push(new boolean[1]);
        i++;
      } else if (c == ')' && groups.size() > 1) {
        i = closeGroup(groups, pattern, i);
        if (i < 0) {
          return true;
        }
      } else {
        int after = unboundedQuantifierEnd(pattern, i);
        if (after > i) {
          groups.peek()[0] = true;
          i = after;
        } else {
          i++;
        }
      }
    }
    return false;
  }

  /**
   * Pops the group closed at {@code at}: the next index, or -1 when it repeats an unbounded body.
   */
  private static int closeGroup(java.util.ArrayDeque<boolean[]> groups, String pattern, int at) {
    boolean bodyUnbounded = groups.pop()[0];
    int after = unboundedQuantifierEnd(pattern, at + 1);
    if (bodyUnbounded && after > at + 1) {
      return -1;
    }
    groups.peek()[0] |= bodyUnbounded || after > at + 1;
    return Math.max(after, at + 1);
  }

  /** The index after an unbounded quantifier ({@code *}, {@code +}, {@code {n,}}) at {@code at}. */
  private static int unboundedQuantifierEnd(String pattern, int at) {
    if (at >= pattern.length()) {
      return at;
    }
    char c = pattern.charAt(at);
    if (c == '*' || c == '+') {
      return at + 1;
    }
    if (c == '{') {
      int close = pattern.indexOf('}', at);
      if (close > at && pattern.substring(at + 1, close).matches("\\d+,")) {
        return close + 1;
      }
    }
    return at;
  }

  /** The index after the character class that opens at {@code open}. */
  private static int endOfClass(String pattern, int open) {
    int i = open + 1;
    while (i < pattern.length() && pattern.charAt(i) != ']') {
      i += pattern.charAt(i) == '\\' ? 2 : 1;
    }
    return i + 1;
  }
}
