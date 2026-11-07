package com.krizaka.sovkora.core.application.checks;

import com.krizaka.sovkora.core.domain.model.checks.*;
import com.krizaka.sovkora.core.domain.ports.inbound.ExternalCheckCapability;
import com.krizaka.sovkora.core.domain.ports.outbound.*;
import com.krizaka.sovkora.core.infrastructure.support.CoreException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The one door to the external checks (sovkora-engine#73). Every call, in this order:
 *
 * <ol>
 *   <li>the subject reference, a keyed HMAC-SHA-256 of the subject id: no sealed pepper, no check;
 *   <li>consent for the purpose: none, or withdrawn, refuses without touching the provider;
 *   <li>the key: the actor's own, then the platform's; neither refuses;
 *   <li>one {@code CALL} held against the run budget: a budget that cannot hold it refuses;
 *   <li>the connector, which may answer an outcome or fail: a failure is a {@code PROVIDER_ERROR},
 *       and its message is neither returned nor audited;
 *   <li>one audit line, whatever happened;
 *   <li>the hold settled when the provider answered, released (nothing charged) otherwise.
 * </ol>
 *
 * <p>The connector is never reached without consent, a key and a hold, so no route can skip any of
 * them. The same door is the engine capability a blueprint step calls (sovkora-engine#84).
 */
public class ExternalCheckService implements ExternalCheckCapability {

  /** A pepper shorter than the HMAC's block of security is refused. */
  private static final int MIN_PEPPER_BYTES = 32;

  private static final String HMAC = "HmacSHA256";

  /** The provider named in the audit when no row serves the call. */
  private static final String UNROUTED = "unrouted";

  private final CheckRouter router;
  private final ConsentRegistry consents;
  private final CheckKeyStore keys;
  private final CheckAuditLog audit;
  private final CheckMeter meter;
  private final CheckSubjectPepper pepper;
  private final Clock clock;

  /**
   * A service whose provider is chosen per call from the admin rows.
   *
   * @param router the provider routing (rows by {@code handler_key})
   * @param consents the consent register
   * @param keys the provider keys
   * @param audit where the audit lines go
   * @param meter the {@code CALL} metering against the run budget
   * @param pepper the sealed pepper of the subject reference
   * @param clock the clock (injected, never ambient)
   */
  public ExternalCheckService(
      CheckRouter router,
      ConsentRegistry consents,
      CheckKeyStore keys,
      CheckAuditLog audit,
      CheckMeter meter,
      CheckSubjectPepper pepper,
      Clock clock) {
    this.router = Objects.requireNonNull(router, "router must not be null");
    this.consents = Objects.requireNonNull(consents, "consents must not be null");
    this.keys = Objects.requireNonNull(keys, "keys must not be null");
    this.audit = Objects.requireNonNull(audit, "audit must not be null");
    this.meter = Objects.requireNonNull(meter, "meter must not be null");
    this.pepper = Objects.requireNonNull(pepper, "pepper must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /**
   * Searches the court records.
   *
   * @param call the check asked for
   * @return the result
   */
  public CheckResult verifyCourtRecords(CheckCall call) {
    return verify(CheckPurpose.COURT_RECORDS, call, ExternalCheckConnector::verifyCourtRecords);
  }

  /**
   * Reads a credit report.
   *
   * @param call the check asked for
   * @return the result
   */
  public CheckResult verifyCredit(CheckCall call) {
    return verify(CheckPurpose.CREDIT, call, ExternalCheckConnector::verifyCredit);
  }

  /**
   * Verifies the identity.
   *
   * @param call the check asked for
   * @return the result
   */
  public CheckResult verifyIdentity(CheckCall call) {
    return verify(CheckPurpose.IDENTITY, call, ExternalCheckConnector::verifyIdentity);
  }

  @Override
  public CheckStepResult run(String featureKey, CheckCall call) {
    CheckPurpose purpose =
        CheckPurpose.ofFeatureKey(featureKey)
            .orElseThrow(
                () -> new IllegalArgumentException("not an external check: " + featureKey));
    CheckResult result;
    if (purpose == CheckPurpose.COURT_RECORDS) {
      result = verifyCourtRecords(call);
    } else if (purpose == CheckPurpose.CREDIT) {
      result = verifyCredit(call);
    } else {
      result = verifyIdentity(call);
    }
    return CheckStepResult.of(result);
  }

  /**
   * Reads the deferred answer of a search that came back {@code PENDING}, by polling the provider
   * that accepted it. Consent and the key are checked again; no new hold is taken (the search was
   * charged at submission); the poll is audited.
   *
   * @param provider the provider named in the audit line of the submission
   * @param purpose what the search was for
   * @param call the actor, the run and the subject
   * @param providerRef the reference the provider gave
   * @return the answer, {@code PENDING} again while the provider works
   */
  public CheckResult collect(
      String provider, CheckPurpose purpose, CheckCall call, String providerRef) {
    String subjectId = call.subject().subjectId();
    String subjectRef = subjectRef(subjectId);
    Optional<CheckRouter.Route> route = router.routeOf(provider);
    if (route.isEmpty()) {
      return refused(purpose, call, subjectRef, provider, CheckRefusal.NOT_CONFIGURED);
    }
    Gate gate = admit(purpose, call, provider);
    if (gate.refusal() != null) {
      return refused(purpose, call, subjectRef, provider, gate.refusal());
    }
    CheckRouter.Route chosen = route.get();
    Instant start = clock.instant();
    CheckResult result;
    try {
      result =
          answer(
              (connector, request) -> connector.poll(request, purpose, providerRef),
              chosen.connector(),
              new CheckRequest(call.subject(), gate.key(), chosen.row().endpoint()));
    } catch (ProviderNotConfiguredException unconfigured) {
      return refused(purpose, call, subjectRef, provider, CheckRefusal.NOT_CONFIGURED);
    }
    record(purpose, call, chosen, gate.key(), result, subjectRef, start, false);
    return result;
  }

  private CheckResult verify(
      CheckPurpose purpose,
      CheckCall call,
      BiFunction<ExternalCheckConnector, CheckRequest, CheckResult> operation) {
    String subjectId = call.subject().subjectId();
    String subjectRef = subjectRef(subjectId);

    Optional<CheckRouter.Route> route = router.route(purpose, call.region());
    if (route.isEmpty()) {
      return refused(purpose, call, subjectRef, UNROUTED, CheckRefusal.NOT_CONFIGURED);
    }
    CheckRouter.Route chosen = route.get();
    String provider = chosen.row().provider();

    Gate gate = admit(purpose, call, provider);
    if (gate.refusal() != null) {
      return refused(purpose, call, subjectRef, provider, gate.refusal());
    }
    CheckMeter.Hold hold;
    try {
      hold = meter.hold(call.runId(), call.userId(), provider, purpose);
    } catch (CheckMeter.BudgetExhaustedException exhausted) {
      return refused(purpose, call, subjectRef, provider, CheckRefusal.BUDGET_EXHAUSTED);
    }

    Instant start = clock.instant();
    CheckResult result;
    try {
      result =
          answer(
              operation,
              chosen.connector(),
              new CheckRequest(call.subject(), gate.key(), chosen.row().endpoint()));
    } catch (ProviderNotConfiguredException unconfigured) {
      hold.release("the provider is not configured");
      return refused(purpose, call, subjectRef, provider, CheckRefusal.NOT_CONFIGURED);
    }
    record(purpose, call, chosen, gate.key(), result, subjectRef, start, true);
    if (result.isCharged()) {
      hold.settle();
    } else {
      hold.release("the provider did not answer: " + result.outcome());
    }
    return result;
  }

  private void record(
      CheckPurpose purpose,
      CheckCall call,
      CheckRouter.Route chosen,
      CheckKey key,
      CheckResult result,
      String subjectRef,
      Instant start,
      boolean billable) {
    Duration took = Duration.between(start, clock.instant());
    Long cost =
        billable && key.source() == KeySource.PLATFORM && result.isCharged()
            ? chosen.row().priceMicros()
            : null;
    audit.record(
        new CheckAudit(
            clock.instant(),
            call.userId(),
            call.runId(),
            chosen.row().provider(),
            purpose,
            key.source(),
            result.outcome(),
            null,
            subjectRef,
            took.toMillis(),
            result.providerRef(),
            cost));
  }

  private CheckResult answer(
      BiFunction<ExternalCheckConnector, CheckRequest, CheckResult> operation,
      ExternalCheckConnector connector,
      CheckRequest request) {
    try {
      CheckResult result = operation.apply(connector, request);
      if (result == null || result.outcome() == CheckOutcome.REFUSED) {
        // A connector cannot refuse for the service: that is a provider fault.
        return CheckResult.answered(CheckOutcome.PROVIDER_ERROR, null);
      }
      return result;
    } catch (ProviderNotConfiguredException unconfigured) {
      throw unconfigured;
    } catch (RuntimeException failure) {
      // The message stays out: it may carry the provider's payload or the subject.
      return CheckResult.answered(CheckOutcome.PROVIDER_ERROR, null);
    }
  }

  private CheckResult refused(
      CheckPurpose purpose,
      CheckCall call,
      String subjectRef,
      String provider,
      CheckRefusal refusal) {
    audit.record(
        new CheckAudit(
            clock.instant(),
            call.userId(),
            call.runId(),
            provider,
            purpose,
            null,
            CheckOutcome.REFUSED,
            refusal,
            subjectRef,
            null,
            null));
    return CheckResult.refused(refusal);
  }

  /** What stands before the provider: the key to call with, or why the call is refused. */
  private record Gate(CheckKey key, CheckRefusal refusal) {}

  /** Consent for the purpose, then a key: the order of every call, a poll included. */
  private Gate admit(CheckPurpose purpose, CheckCall call, String provider) {
    if (!consents.isGranted(call.subject().subjectId(), purpose)) {
      return new Gate(null, CheckRefusal.NO_CONSENT);
    }
    return resolveKey(call.userId(), provider)
        .map(key -> new Gate(key, null))
        .orElse(new Gate(null, CheckRefusal.NO_KEY));
  }

  /** The actor's own key when there is one, else the platform's; empty when neither. */
  private Optional<CheckKey> resolveKey(String userId, String provider) {
    if (userId != null) {
      Optional<String> own = keys.userKey(userId, provider);
      if (own.isPresent()) {
        return Optional.of(new CheckKey(KeySource.USER, own.get()));
      }
    }
    return keys.platformKey(provider).map(value -> new CheckKey(KeySource.PLATFORM, value));
  }

  /**
   * The audit names a subject by a keyed HMAC-SHA-256 of its id, never by the id, a name or a plain
   * hash (which an id list could enumerate). No sealed pepper, or a short one, stops the call.
   */
  private String subjectRef(String subjectId) {
    byte[] key =
        pepper
            .pepper()
            .filter(bytes -> bytes.length >= MIN_PEPPER_BYTES)
            .orElseThrow(
                () ->
                    new CoreException(
                        "no subject pepper of at least "
                            + MIN_PEPPER_BYTES
                            + " bytes is sealed: external checks stay off"));
    try {
      Mac mac = Mac.getInstance(HMAC);
      mac.init(new SecretKeySpec(key, HMAC));
      return HexFormat.of().formatHex(mac.doFinal(subjectId.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException impossible) {
      throw new UnsupportedOperationException(
          "HmacSHA256 is part of every Java runtime", impossible);
    }
  }
}
