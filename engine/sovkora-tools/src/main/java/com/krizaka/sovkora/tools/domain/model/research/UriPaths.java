package com.krizaka.sovkora.tools.domain.model.research;

import java.net.URI;
import java.util.Locale;

/**
 * The path a policy compares: the raw path and query with every percent-escape of an
 * <i>unreserved</i> character decoded (RFC 3986 §6.2.2.2: {@code %70} is {@code p}) and every other
 * escape in upper case, so {@code /%70rivate/} and {@code /private/} are one path for the allow and
 * deny patterns and for robots.txt (RFC 9309 §2.2.2) — and reserved characters ({@code %2F}) keep
 * their meaning.
 */
public final class UriPaths {

  private UriPaths() {}

  /**
   * The canonical path and query of a URI.
   *
   * @param uri the URI
   * @return the path ({@code /} when empty), with {@code ?query} when there is one
   */
  public static String canonical(URI uri) {
    String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
    String query = uri.getRawQuery();
    return canonical(query == null ? path : path + "?" + query);
  }

  /**
   * The canonical form of a raw path.
   *
   * @param raw a raw path (and query)
   * @return it with unreserved escapes decoded and the others upper-cased
   */
  public static String canonical(String raw) {
    StringBuilder out = new StringBuilder(raw.length());
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c == '%' && i + 2 < raw.length()) {
        String hex = raw.substring(i + 1, Math.min(i + 3, raw.length()));
        if (hex.length() == 2 && hex.chars().allMatch(h -> Character.digit(h, 16) >= 0)) {
          char decoded = (char) Integer.parseInt(hex, 16);
          if (isUnreserved(decoded)) {
            out.append(decoded);
          } else {
            out.append('%').append(hex.toUpperCase(Locale.ROOT));
          }
          i += 2;
          continue;
        }
      }
      out.append(c);
    }
    return out.toString();
  }

  private static boolean isUnreserved(char c) {
    return (c >= 'A' && c <= 'Z')
        || (c >= 'a' && c <= 'z')
        || (c >= '0' && c <= '9')
        || c == '-'
        || c == '.'
        || c == '_'
        || c == '~';
  }
}
