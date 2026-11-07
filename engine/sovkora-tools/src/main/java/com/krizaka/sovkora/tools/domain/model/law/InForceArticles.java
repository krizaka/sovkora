package com.krizaka.sovkora.tools.domain.model.law;

import java.util.Optional;

/**
 * The corpus as {@code vet} reads it: the version of an article in force on the derivation's day,
 * that day being fixed by whoever supplies this view.
 */
@FunctionalInterface
public interface InForceArticles {

  /**
   * The version in force.
   *
   * @param instrument the instrument code
   * @param ref the article reference
   * @param language {@code fr} or {@code en}
   * @return the version, or empty when the corpus holds none for the day ({@code
   *     LAW_VERSION_UNKNOWN})
   */
  Optional<StatuteVersion> inForce(String instrument, String ref, String language);
}
