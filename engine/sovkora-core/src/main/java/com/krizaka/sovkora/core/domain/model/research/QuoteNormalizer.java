package com.krizaka.sovkora.core.domain.model.research;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Brings a quote and the text it is looked for in to one form, so that typography never decides
 * whether a passage was read (ADR-087 §3, CTO review): Unicode NFC (an NFD « é » equals an NFC
 * one), case folding, French quotation marks and apostrophes to plain ones, the narrow and ordinary
 * no-break spaces (U+202F, U+00A0) and the other fixed-width spaces to a space — so the French
 * space before {@code ; : ! ?} matches — the ligatures œ and æ to {@code oe} and {@code ae}, dashes
 * to a hyphen, the ellipsis to three dots, and runs of whitespace to one space.
 */
public final class QuoteNormalizer {

  private static final Pattern SPACES =
      Pattern.compile(
          "[\\s\\u00A0\\u2007\\u2009\\u202F\\u2002-\\u2006\\u2008\\u200A\\u205F\\u3000]+");
  private static final Pattern DOUBLE_QUOTES = Pattern.compile("[«»“”„‟″]");
  private static final Pattern SINGLE_QUOTES = Pattern.compile("[‘’‚‛′`´]");
  private static final Pattern DASHES = Pattern.compile("[‐‑‒–—―−]");

  private QuoteNormalizer() {}

  /**
   * The normal form of a text.
   *
   * @param text a quote or a document's text
   * @return its normal form, empty for {@code null}
   */
  public static String normalize(String text) {
    if (text == null) {
      return "";
    }
    String s = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    s = s.replace("œ", "oe").replace("æ", "ae").replace("…", "...");
    s = DOUBLE_QUOTES.matcher(s).replaceAll("\"");
    s = SINGLE_QUOTES.matcher(s).replaceAll("'");
    s = DASHES.matcher(s).replaceAll("-");
    s = SPACES.matcher(s).replaceAll(" ");
    // « mot » becomes " mot " above; the spaces a French quotation mark carries inside it go too.
    s = s.replace("\" ", "\"").replace(" \"", "\"");
    return s.strip();
  }

  /**
   * Whether the quote appears in the text once both are normalised.
   *
   * @param quote the passage
   * @param text the document's text
   * @return true when found; an empty quote is never found
   */
  public static boolean contains(String text, String quote) {
    String q = normalize(quote);
    return !q.isEmpty() && normalize(text).contains(q);
  }
}
