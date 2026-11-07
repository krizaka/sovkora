package com.krizaka.sovkora.tools.domain.model.research;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Whether a host and path fall inside a source's allow list and outside its deny list.
 *
 * <p>A pattern is {@code host} or {@code host/path}. The host is matched without case, exactly or
 * with a leading {@code *.} for its subdomains; in the path, {@code *} matches within one segment
 * and {@code **} across segments. A host with no path allows nothing below it but the root: {@code
 * example.org/**} is how a whole site is allowed. An empty allow list allows nothing.
 */
public final class SourcePatterns {

  private SourcePatterns() {}

  /**
   * Whether the source may read this host and path.
   *
   * @param source the source
   * @param host the host, as in the URL
   * @param path the path, starting with {@code /}
   * @return true when an allow pattern matches and no deny pattern does
   */
  public static boolean allows(ResearchSource source, String host, String path) {
    return matchesAny(source.allowPatterns(), host, path)
        && !matchesAny(source.denyPatterns(), host, path);
  }

  private static boolean matchesAny(List<String> patterns, String host, String path) {
    return patterns.stream().anyMatch(pattern -> matches(pattern, host, path));
  }

  static boolean matches(String pattern, String host, String path) {
    String p = pattern.trim();
    int slash = p.indexOf('/');
    String hostPattern = (slash < 0 ? p : p.substring(0, slash)).toLowerCase(Locale.ROOT);
    String pathPattern = slash < 0 ? "/" : p.substring(slash);
    String h = host.toLowerCase(Locale.ROOT);
    boolean hostOk =
        hostPattern.startsWith("*.")
            ? h.endsWith(hostPattern.substring(1)) && h.length() > hostPattern.length() - 1
            : h.equals(hostPattern);
    // Both sides in canonical form: %70rivate is private for the deny list too.
    return hostOk
        && glob(UriPaths.canonical(pathPattern))
            .matcher(UriPaths.canonical(path.isEmpty() ? "/" : path))
            .matches();
  }

  private static Pattern glob(String pathPattern) {
    StringBuilder regex = new StringBuilder();
    for (int i = 0; i < pathPattern.length(); i++) {
      char c = pathPattern.charAt(i);
      if (c == '*') {
        boolean across = i + 1 < pathPattern.length() && pathPattern.charAt(i + 1) == '*';
        regex.append(across ? ".*" : "[^/]*");
        if (across) {
          i++;
        }
      } else {
        regex.append(Pattern.quote(String.valueOf(c)));
      }
    }
    return Pattern.compile(regex.toString());
  }
}
