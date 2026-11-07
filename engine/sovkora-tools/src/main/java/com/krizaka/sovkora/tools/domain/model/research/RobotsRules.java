package com.krizaka.sovkora.tools.domain.model.research;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A robots.txt file read per RFC 9309: the group of the most specific matching user agent (else
 * {@code *}), its {@code Allow} and {@code Disallow} rules, the longest match deciding and {@code
 * Allow} winning a tie; {@code *} and the end anchor {@code $} in paths.
 *
 * <p>An unavailable file (4xx) allows everything; an unreachable one (5xx, a network error) allows
 * nothing, as the RFC says (§2.3.1.3–2.3.1.4). The caller decides which constructor applies.
 */
public final class RobotsRules {

  private record Rule(boolean allow, String path, Pattern pattern) {}

  private final List<Rule> rules;
  private final boolean allowAllWhenNoRule;

  private RobotsRules(List<Rule> rules, boolean allowAllWhenNoRule) {
    this.rules = List.copyOf(rules);
    this.allowAllWhenNoRule = allowAllWhenNoRule;
  }

  /**
   * No robots.txt (a 4xx answer): everything is allowed.
   *
   * @return the rules
   */
  public static RobotsRules allowAll() {
    return new RobotsRules(List.of(), true);
  }

  /**
   * robots.txt could not be read (5xx, a network error): nothing is allowed.
   *
   * @return the rules
   */
  public static RobotsRules disallowAll() {
    return new RobotsRules(List.of(new Rule(false, "/", compile("/"))), true);
  }

  /**
   * The rules of the group that applies to a product token.
   *
   * @param robotsTxt the file
   * @param productToken our user agent's product token, e.g. {@code SovkoraResearch}
   * @return the rules
   */
  public static RobotsRules parse(String robotsTxt, String productToken) {
    Objects.requireNonNull(productToken, "productToken must not be null");
    String token = productToken.toLowerCase(Locale.ROOT);
    List<Rule> ours = new ArrayList<>();
    List<Rule> star = new ArrayList<>();
    boolean oursFound = false;
    List<String> agents = new ArrayList<>();
    List<Rule> current = new ArrayList<>();
    boolean inRules = false;
    for (String raw : (robotsTxt == null ? "" : robotsTxt).split("\\R")) {
      String line = raw.contains("#") ? raw.substring(0, raw.indexOf('#')) : raw;
      int colon = line.indexOf(':');
      if (colon < 0) {
        continue;
      }
      String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
      String value = line.substring(colon + 1).trim();
      if ("user-agent".equals(key)) {
        if (inRules) {
          // A new group starts: file the previous one.
          oursFound |= file(agents, current, token, ours, star);
          agents = new ArrayList<>();
          current = new ArrayList<>();
          inRules = false;
        }
        agents.add(value.toLowerCase(Locale.ROOT));
      } else if ("allow".equals(key) || "disallow".equals(key)) {
        inRules = true;
        if (!value.isEmpty()) {
          String rulePath = UriPaths.canonical(value);
          current.add(new Rule("allow".equals(key), rulePath, compile(rulePath)));
        }
      }
    }
    oursFound |= file(agents, current, token, ours, star);
    return new RobotsRules(oursFound ? ours : star, true);
  }

  private static boolean file(
      List<String> agents, List<Rule> rules, String token, List<Rule> ours, List<Rule> star) {
    boolean mine = agents.stream().anyMatch(token::equals);
    if (mine) {
      ours.addAll(rules);
    } else if (agents.contains("*")) {
      star.addAll(rules);
    }
    return mine;
  }

  /**
   * Whether the path may be read.
   *
   * @param path the path and query, starting with {@code /}
   * @return true when allowed
   */
  public boolean allows(String path) {
    // RFC 9309 §2.2.2: rules and paths compare with unreserved escapes decoded.
    String p = UriPaths.canonical(path == null || path.isEmpty() ? "/" : path);
    Rule best = null;
    for (Rule rule : rules) {
      if (rule.pattern().matcher(p).lookingAt()
          && (best == null
              || rule.path().length() > best.path().length()
              || (rule.path().length() == best.path().length() && rule.allow()))) {
        best = rule;
      }
    }
    return best == null ? allowAllWhenNoRule : best.allow();
  }

  private static Pattern compile(String rulePath) {
    StringBuilder regex = new StringBuilder();
    for (int i = 0; i < rulePath.length(); i++) {
      char c = rulePath.charAt(i);
      if (c == '*') {
        regex.append(".*");
      } else if (c == '$' && i == rulePath.length() - 1) {
        regex.append("$");
      } else {
        regex.append(Pattern.quote(String.valueOf(c)));
      }
    }
    return Pattern.compile(regex.toString());
  }
}
