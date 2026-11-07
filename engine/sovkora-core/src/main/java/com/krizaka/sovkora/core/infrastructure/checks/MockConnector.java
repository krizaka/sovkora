package com.krizaka.sovkora.core.infrastructure.checks;

import com.krizaka.sovkora.core.domain.model.checks.CheckCategory;
import com.krizaka.sovkora.core.domain.model.checks.CheckOutcome;
import com.krizaka.sovkora.core.domain.model.checks.CheckPurpose;
import com.krizaka.sovkora.core.domain.model.checks.CheckRequest;
import com.krizaka.sovkora.core.domain.model.checks.CheckResult;
import com.krizaka.sovkora.core.domain.ports.outbound.ExternalCheckConnector;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A connector that answers from recorded fixtures, with no network: the demo's and the CI's
 * connector (sovkora-engine#73). The fixture is chosen by the subject's last name, so a test reads
 * as a sentence: the subject {@code Tremblay} has a TAL non-payment file.
 *
 * <p>A match answers the fixture's opaque id ({@code mock-fixture-01}) as its provider reference,
 * never anything built from the subject (sovkora-engine#83). Court records answer the fixture's
 * {@link CheckCategory}; credit and identity have none. An unknown last name answers {@code
 * NO_MATCH}.
 */
public final class MockConnector implements ExternalCheckConnector {

  /** The provider name of the mock. */
  private static final String PROVIDER = "mock";

  /**
   * One recorded answer.
   *
   * @param outcome what the provider answers
   * @param category the court-records category of a match, {@link CheckCategory#NONE} otherwise
   * @param ref the opaque fixture id answered on a match
   */
  public record Fixture(CheckOutcome outcome, CheckCategory category, String ref) {

    /** Validates the fixture. */
    public Fixture {
      Objects.requireNonNull(outcome, "outcome must not be null");
      Objects.requireNonNull(category, "category must not be null");
      Objects.requireNonNull(ref, "ref must not be null");
    }
  }

  /** The recorded answers of the demo: one per outcome and per court category. */
  private static final Map<String, Fixture> RECORDED =
      Map.of(
          "Tremblay",
          new Fixture(CheckOutcome.MATCH, CheckCategory.TAL_NON_PAYMENT, "mock-fixture-01"),
          "Côté",
          new Fixture(CheckOutcome.MATCH, CheckCategory.TAL_OTHER, "mock-fixture-02"),
          "Morin",
          new Fixture(CheckOutcome.MATCH, CheckCategory.CRIMINAL, "mock-fixture-03"),
          "Lavoie",
          new Fixture(CheckOutcome.MATCH, CheckCategory.CIVIL, "mock-fixture-04"),
          "Gagnon",
          new Fixture(CheckOutcome.NO_MATCH, CheckCategory.NONE, "mock-fixture-05"),
          "Roy",
          new Fixture(CheckOutcome.PROVIDER_ERROR, CheckCategory.NONE, "mock-fixture-06"),
          "Bouchard",
          new Fixture(CheckOutcome.TIMEOUT, CheckCategory.NONE, "mock-fixture-07"));

  private final Map<String, Fixture> fixtures;

  /** The connector over the recorded demo answers. */
  public MockConnector() {
    this.fixtures = RECORDED;
  }

  /**
   * The connector over the given outcomes, a match being in no court category.
   *
   * @param answers the outcome for each last name
   */
  public MockConnector(Map<String, CheckOutcome> answers) {
    Objects.requireNonNull(answers, "answers must not be null");
    Map<String, Fixture> given = new HashMap<>();
    int n = 0;
    for (Map.Entry<String, CheckOutcome> answer : answers.entrySet()) {
      n++;
      given.put(
          answer.getKey(), new Fixture(answer.getValue(), CheckCategory.NONE, "mock-given-" + n));
    }
    this.fixtures = Map.copyOf(given);
  }

  @Override
  public String provider() {
    return PROVIDER;
  }

  @Override
  public CheckResult verifyCourtRecords(CheckRequest request) {
    return answer(request, CheckPurpose.COURT_RECORDS);
  }

  @Override
  public CheckResult verifyCredit(CheckRequest request) {
    return answer(request, CheckPurpose.CREDIT);
  }

  @Override
  public CheckResult verifyIdentity(CheckRequest request) {
    return answer(request, CheckPurpose.IDENTITY);
  }

  private CheckResult answer(CheckRequest request, CheckPurpose purpose) {
    Fixture fixture = fixtures.get(request.subject().lastName());
    if (fixture == null) {
      return CheckResult.answered(CheckOutcome.NO_MATCH, null);
    }
    if (fixture.outcome() != CheckOutcome.MATCH) {
      return CheckResult.answered(fixture.outcome(), null);
    }
    CheckCategory category =
        purpose == CheckPurpose.COURT_RECORDS ? fixture.category() : CheckCategory.NONE;
    return CheckResult.answered(CheckOutcome.MATCH, category, fixture.ref());
  }
}
