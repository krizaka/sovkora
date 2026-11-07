package com.krizaka.sovkora.tools.domain.model.law;

import java.util.List;
import java.util.Objects;

/**
 * The golden cases of a kind against the minimum of promotion (ADR-089 §5 step 2): at least {@code
 * law.review.min-golden-cases} documents, among them one clean, one with known errors and the
 * kind's blank form.
 *
 * @param kindId the kind
 * @param required {@code law.review.min-golden-cases}
 * @param cases the cases the pack holds for the kind
 */
public record GoldenCases(String kindId, int required, List<GoldenCase> cases) {

  /** Copies the cases. */
  public GoldenCases {
    Objects.requireNonNull(kindId, "kindId");
    cases = cases == null ? List.of() : List.copyOf(cases);
  }

  /**
   * How many clean documents.
   *
   * @return the count
   */
  public long compliant() {
    return cases.stream().filter(GoldenCase::compliant).count();
  }

  /**
   * How many documents with known errors.
   *
   * @return the count
   */
  public long defective() {
    return cases.stream().filter(GoldenCase::defective).count();
  }

  /**
   * How many blank forms.
   *
   * @return the count
   */
  public long blank() {
    return cases.stream().filter(GoldenCase::blank).count();
  }

  /**
   * Whether promotion may go on.
   *
   * @return true when the count and the three intents are all there
   */
  public boolean met() {
    return cases.size() >= required && compliant() >= 1 && defective() >= 1 && blank() >= 1;
  }
}
