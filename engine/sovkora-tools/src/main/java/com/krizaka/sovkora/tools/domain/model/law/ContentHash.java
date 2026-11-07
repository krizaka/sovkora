package com.krizaka.sovkora.tools.domain.model.law;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * SHA-256 of an article's text once normalised: composed characters (NFC), every run of whitespace
 * collapsed to one space, trimmed. Two texts that differ only in layout have the same hash.
 *
 * @param hex the 64 lowercase hexadecimal characters of the digest
 */
public record ContentHash(String hex) {

  private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00A0]+");

  public ContentHash {
    if (hex == null || !hex.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("a SHA-256 digest in lowercase hex is expected");
    }
  }

  /**
   * Hashes a text after {@link #normalise(String)}.
   *
   * @param text the article text, any layout
   * @return the digest of its normalised form
   */
  public static ContentHash of(String text) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(normalise(text).getBytes(StandardCharsets.UTF_8));
      return new ContentHash(HexFormat.of().formatHex(digest));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is mandatory on every JVM", e);
    }
  }

  /**
   * The form that is stored and hashed: NFC, whitespace collapsed, trimmed.
   *
   * @param text any text, or null (read as empty)
   * @return the normalised text
   */
  public static String normalise(String text) {
    String composed = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFC);
    return WHITESPACE.matcher(composed).replaceAll(" ").trim();
  }
}
