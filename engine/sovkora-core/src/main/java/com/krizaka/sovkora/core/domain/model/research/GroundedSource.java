package com.krizaka.sovkora.core.domain.model.research;

import java.util.Objects;

/**
 * A source a grounded answer read.
 *
 * @param url its URL
 * @param title its title
 * @param sourceKey the research source it was read through
 */
public record GroundedSource(String url, String title, String sourceKey) {

  /** Validates the source. */
  public GroundedSource {
    Objects.requireNonNull(url, "url must not be null");
    title = title == null ? "" : title;
    sourceKey = sourceKey == null ? "" : sourceKey;
  }
}
