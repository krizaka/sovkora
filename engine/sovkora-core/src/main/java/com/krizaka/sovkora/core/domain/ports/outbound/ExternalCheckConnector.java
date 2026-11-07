package com.krizaka.sovkora.core.domain.ports.outbound;

import com.krizaka.sovkora.core.domain.model.checks.CheckPurpose;
import com.krizaka.sovkora.core.domain.model.checks.CheckRequest;
import com.krizaka.sovkora.core.domain.model.checks.CheckResult;

/**
 * An external provider of checks: court records, credit, identity (sovkora-engine#73). A connector
 * only calls its provider with the key it is given. Consent, the choice of key and the audit are
 * the service's, never the connector's.
 *
 * <p>The keys are read from the encrypted settings by the key store, never from an environment
 * variable.
 */
public interface ExternalCheckConnector {

  /**
   * The provider's name, as the audit and the admin settings name it.
   *
   * @return e.g. {@code soquij}, {@code trustii}, {@code certn}, {@code mock}
   */
  String provider();

  /**
   * Searches the court records (purpose {@code COURT_RECORDS}).
   *
   * @param request the subject and the resolved key
   * @return the answer; never {@code REFUSED}
   */
  CheckResult verifyCourtRecords(CheckRequest request);

  /**
   * Reads a credit report (purpose {@code CREDIT}).
   *
   * @param request the subject and the resolved key
   * @return the answer; never {@code REFUSED}
   */
  CheckResult verifyCredit(CheckRequest request);

  /**
   * Verifies the identity (purpose {@code IDENTITY}).
   *
   * @param request the subject and the resolved key
   * @return the answer; never {@code REFUSED}
   */
  CheckResult verifyIdentity(CheckRequest request);

  /**
   * Reads the answer of a search the provider accepted earlier ({@code PENDING}). A connector with
   * no deferred answers keeps this default, which the service audits as a provider error.
   *
   * @param request the subject and the resolved key
   * @param purpose what the search was for
   * @param providerRef the reference the provider gave on submission
   * @return the answer, {@code PENDING} again while the provider works; never {@code REFUSED}
   */
  default CheckResult poll(CheckRequest request, CheckPurpose purpose, String providerRef) {
    throw new UnsupportedOperationException("this connector has no deferred answers");
  }
}
