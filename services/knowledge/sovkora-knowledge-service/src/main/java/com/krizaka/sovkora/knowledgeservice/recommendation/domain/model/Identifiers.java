package com.krizaka.sovkora.knowledgeservice.recommendation.domain.model;

import java.util.regex.Pattern;

/**
 * The shape of the identifiers a product sends: opaque, short, from a safe alphabet. Pure functions
 * over strings (AGENTS.md §4, ERR-127 exception (b)) — the records that hold an identifier call
 * them from their compact constructor.
 */
public final class Identifiers {

  /** An item, a creator or an event id: a UUID, a ULID, a slug. */
  private static final Pattern ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

  /** A pseudonym: typically a hex HMAC (64 characters), or a base64url one. */
  private static final Pattern PSEUDONYM = Pattern.compile("[A-Za-z0-9_-]{16,128}");

  private Identifiers() {}

  /** The id, or an {@link IllegalArgumentException} naming the field. */
  public static String require(String value, String field) {
    if (value == null || !ID.matcher(value).matches()) {
      throw new IllegalArgumentException(field + " must be 1-64 characters of [A-Za-z0-9._:-]");
    }
    return value;
  }

  /** The pseudonym, or an {@link IllegalArgumentException}: a raw e-mail or a name never fits. */
  public static String requirePseudonym(String value) {
    if (value == null || !PSEUDONYM.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "user must be a pseudonym of 16-128 characters of [A-Za-z0-9_-]");
    }
    return value;
  }
}
