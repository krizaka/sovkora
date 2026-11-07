package com.krizaka.sovkora.core.domain.model.checks;

import java.util.Arrays;
import java.util.Optional;

/**
 * What an external check is for (sovkora-engine#73). Consent is given per purpose, and the
 * pricebook prices each one. Each purpose is an engine capability a blueprint step calls by its
 * feature key (sovkora-engine#84).
 */
public enum CheckPurpose {
  /** Court records: the TAL and the civil and penal plumitifs. */
  COURT_RECORDS("orazaka.core.checks.courtrecords"),
  /** A credit report. */
  CREDIT("orazaka.core.checks.credit"),
  /** Identity verification. */
  IDENTITY("orazaka.core.checks.identity");

  private final String featureKey;

  CheckPurpose(String featureKey) {
    this.featureKey = featureKey;
  }

  /**
   * The capability's feature key, as a blueprint step names it.
   *
   * @return e.g. {@code orazaka.core.checks.courtrecords}
   */
  public String featureKey() {
    return featureKey;
  }

  /**
   * The purpose a feature key names.
   *
   * @param featureKey the step's feature key
   * @return the purpose, or empty for any other key
   */
  public static Optional<CheckPurpose> ofFeatureKey(String featureKey) {
    return Arrays.stream(values()).filter(p -> p.featureKey.equals(featureKey)).findFirst();
  }
}
