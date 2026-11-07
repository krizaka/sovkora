package com.krizaka.sovkora.tools.domain.model.research;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The references an answer states in its prose — URLs, {@code owner/repo} repository names and
 * statute references (« art. 1904 », « RLRQ c. P-40.1 », « L.R.C. 1985 ») — so the verifier can
 * ask, for each one, whether a verified claim cites it (ADR-087 §3). An invented repository or URL
 * in prose is exactly the failure of krizaka/krizaka-com#365.
 */
public final class ReferenceScanner {

  private static final Pattern URL = Pattern.compile("https?://[^\\s)\\]>\"'«»]+");
  private static final Pattern REPOSITORY =
      Pattern.compile(
          "(?<![\\w./-])([A-Za-z0-9][A-Za-z0-9-]{0,38})/([A-Za-z0-9._-]{1,100})(?![\\w/])");
  private static final Pattern STATUTE =
      Pattern.compile(
          "(?i)\\b(?:art(?:icle)?s?\\.?\\s*+\\d++[\\w.-]*+|RLRQ\\s*+,?+\\s*+c(?:h)?\\.?\\s*+[A-Z]-?+[\\d.]++|L\\.?R\\.?[CQ]\\.?\\s*+\\d{4}|R\\.?S\\.?C\\.?\\s*+\\d{4})");

  private ReferenceScanner() {}

  /**
   * The references stated in a text.
   *
   * @param text an answer's prose
   * @return URLs, repositories and statute references, in order of appearance
   */
  public static Set<String> references(String text) {
    Set<String> found = new LinkedHashSet<>();
    if (text == null) {
      return found;
    }
    Matcher urls = URL.matcher(text);
    while (urls.find()) {
      found.add(trimPunctuation(urls.group()));
    }
    String withoutUrls = URL.matcher(text).replaceAll(" ");
    Matcher repos = REPOSITORY.matcher(withoutUrls);
    while (repos.find()) {
      // Trimmed first: « 10/23. » at the end of a sentence is a date, not a repository.
      String candidate = trimPunctuation(repos.group());
      // a date (10/23) or a fraction is not a repository
      if (!candidate.matches("\\d+/\\d+")) {
        found.add(candidate);
      }
    }
    Matcher statutes = STATUTE.matcher(withoutUrls);
    while (statutes.find()) {
      found.add(trimPunctuation(statutes.group()).replaceAll("\\s+", " "));
    }
    return found;
  }

  /**
   * The identity of a reference, for comparing two spellings of it: a statute by its provision
   * ({@code art.}, {@code arts.} and {@code article} are one word; the whole number counts, so «
   * art. 1 » is not « art. 12 »), a repository without case, a URL exactly as written (a path is
   * case-sensitive, and a shared prefix is another address).
   *
   * @param reference a reference as {@link #references} returns it
   * @return the key two equivalent spellings share
   */
  public static String key(String reference) {
    String ref = reference.strip();
    if (ref.startsWith("http://") || ref.startsWith("https://")) {
      return ref;
    }
    if (STATUTE.matcher(ref).matches()) {
      return "statute:"
          + ref.toLowerCase(java.util.Locale.ROOT)
              .replaceFirst("^art(?:icle)?s?", "art")
              .replaceAll("[\\s,]+", "")
              .replaceAll("(?<=[a-z])\\.", "");
    }
    return ref.toLowerCase(java.util.Locale.ROOT);
  }

  /**
   * The identities of every reference a text states.
   *
   * @param text a text
   * @return the keys, in order of appearance
   */
  public static Set<String> keys(String text) {
    Set<String> keys = new LinkedHashSet<>();
    for (String reference : references(text)) {
      keys.add(key(reference));
    }
    return keys;
  }

  private static String trimPunctuation(String s) {
    int end = s.length();
    while (end > 0 && ".,;:!?".indexOf(s.charAt(end - 1)) >= 0) {
      end--;
    }
    return s.substring(0, end);
  }
}
