package com.krizaka.sovkora.knowledge.recommendation.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * An item a product makes eligible for recommendation — its public metadata only. The title and the
 * description are embedded and then <em>not stored</em> (data minimisation): only their hash, the
 * vector, the tags and the facts ranking needs are kept.
 *
 * @param itemId the product's id of the item
 * @param creatorId who published it
 * @param title its title
 * @param description its description (may be empty)
 * @param tags its tags, normalised to lower case
 * @param language a BCP-47 language tag, when known
 * @param durationSeconds its duration, when known
 * @param publishedAt when it became available
 */
public record CatalogueItem(
    String itemId,
    String creatorId,
    String title,
    String description,
    List<String> tags,
    String language,
    Integer durationSeconds,
    Instant publishedAt) {

  /** The longest text kept for embedding — beyond it, a description adds noise, not meaning. */
  private static final int MAX_DOCUMENT_CHARS = 2_000;

  private static final int MAX_TAGS = 30;

  public CatalogueItem {
    itemId = Identifiers.require(itemId, "id");
    creatorId = Identifiers.require(creatorId, "creator");
    title = title == null ? "" : title.strip();
    description = description == null ? "" : description.strip();
    if (title.isEmpty() && description.isEmpty()) {
      throw new IllegalArgumentException("an item needs a title or a description");
    }
    tags =
        tags == null
            ? List.of()
            : tags.stream()
                .filter(Objects::nonNull)
                .map(t -> t.strip().toLowerCase(java.util.Locale.ROOT))
                .filter(t -> !t.isEmpty() && t.length() <= 64)
                .distinct()
                .limit(MAX_TAGS)
                .toList();
    if (language != null && !isBcp47(language)) {
      throw new IllegalArgumentException("language must be a BCP-47 tag");
    }
    if (durationSeconds != null && durationSeconds < 0) {
      throw new IllegalArgumentException("durationSeconds must not be negative");
    }
    Objects.requireNonNull(publishedAt, "publishedAt is required");
  }

  /** A BCP-47 tag: a 2-3 letter primary subtag, then subtags of 2-8 letters or digits. */
  private static boolean isBcp47(String tag) {
    String[] parts = tag.split("-", -1);
    if (!parts[0].matches("[A-Za-z]{2,3}")) {
      return false;
    }
    for (int i = 1; i < parts.length; i++) {
      if (!parts[i].matches("[A-Za-z0-9]{2,8}")) {
        return false;
      }
    }
    return true;
  }

  /** The text that is embedded: title, tags, description — in that order of weight. */
  public String document() {
    String text =
        String.join(
                "\n",
                title,
                tags.isEmpty()
                    ? ""
                    : tags.stream().map(t -> "#" + t).collect(Collectors.joining(" ")),
                description)
            .strip();
    return text.length() > MAX_DOCUMENT_CHARS ? text.substring(0, MAX_DOCUMENT_CHARS) : text;
  }

  /** SHA-256 of {@link #document()}: an unchanged item is never embedded twice. */
  public String contentHash() {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(document().getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is part of every JVM", e);
    }
  }
}
