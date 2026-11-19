package com.krizaka.sovkora.edge.infrastructure.support;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The URI the edge forwards a request to, built only from a path in its normal form
 * (krizaka-internal#4361, javasecurity:S7044).
 *
 * <p>The route table picks a backend by the longest prefix of the raw path. A path such as {@code
 * /api/v1/jobs/../../internal/v1/capabilities} matches {@code /api/v1/jobs} here and is then
 * normalised by the backend into {@code /internal/v1/capabilities}: the prefix the edge routed on
 * is not the path the backend serves. So the path is forwarded only when it is already in normal
 * form, and refused otherwise:
 *
 * <ul>
 *   <li>every character is an RFC 3986 path character ({@code pchar} and {@code /}), and every
 *       {@code %} starts a two-digit escape;
 *   <li>no segment is {@code .} or {@code ..}, and no segment is empty except a trailing one;
 *   <li>no escape decodes to a dot, a slash, a backslash or NUL — the separators a backend would
 *       decode back into a traversal.
 * </ul>
 *
 * <p>The query is held to the RFC 3986 query characters, so it cannot carry a fragment or a second
 * request line. A dependency-free pure function (AGENTS.md §4, ERR-127 b).
 */
public final class TargetUriResolver {

  /** RFC 3986 {@code unreserved}, {@code sub-delims}, {@code :} and {@code @}: a {@code pchar}. */
  private static final String PCHAR =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~!$&'()*+,;=:@";

  /** What a path adds to {@code pchar}: the separator. */
  private static final String PATH_CHARS = PCHAR + "/";

  /** What a query adds to {@code pchar}: {@code /} and {@code ?}. */
  private static final String QUERY_CHARS = PCHAR + "/?";

  /** The escapes a backend would decode into a separator or a dot-segment. */
  private static final Pattern ENCODED_SEPARATOR = Pattern.compile("%(?:2e|2f|5c|00)");

  private TargetUriResolver() {}

  /**
   * The target URI for a request, or empty when its path or query is not in normal form.
   *
   * @param backend the backend base URL the route table chose
   * @param path the raw request path ({@code HttpServletRequest#getRequestURI})
   * @param query the raw query string, or {@code null}
   * @return the URI to forward to, or empty when the request must be refused
   */
  public static Optional<URI> resolve(URI backend, String path, String query) {
    if (!isNormalPath(path) || (query != null && !onlyOf(query, QUERY_CHARS))) {
      return Optional.empty();
    }
    String base = backend.toString();
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    return Optional.of(URI.create(base + path + (query != null ? "?" + query : "")));
  }

  private static boolean isNormalPath(String path) {
    if (!path.startsWith("/")
        || !onlyOf(path, PATH_CHARS)
        || ENCODED_SEPARATOR.matcher(path.toLowerCase(Locale.ROOT)).find()) {
      return false;
    }
    String[] segments = path.substring(1).split("/", -1);
    for (int i = 0; i < segments.length; i++) {
      String segment = segments[i];
      boolean trailing = i == segments.length - 1;
      if (".".equals(segment) || "..".equals(segment) || (segment.isEmpty() && !trailing)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Whether every character is in the allowed set, or starts an escape of two hex digits. A scan,
   * not a regular expression: linear on any input.
   */
  private static boolean onlyOf(String value, String allowed) {
    int i = 0;
    while (i < value.length()) {
      char c = value.charAt(i);
      if (c == '%') {
        if (i + 2 >= value.length() || !isHex(value.charAt(i + 1)) || !isHex(value.charAt(i + 2))) {
          return false;
        }
        i += 3;
      } else if (allowed.indexOf(c) < 0) {
        return false;
      } else {
        i++;
      }
    }
    return true;
  }

  private static boolean isHex(char c) {
    return c < 128 && Character.digit(c, 16) >= 0;
  }
}
