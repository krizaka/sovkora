package com.krizaka.sovkora.core.application.checks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.core.domain.model.checks.*;
import com.krizaka.sovkora.core.domain.ports.outbound.CheckAuditLog;
import com.krizaka.sovkora.core.domain.ports.outbound.CheckKeyStore;
import com.krizaka.sovkora.core.domain.ports.outbound.CheckMeter;
import com.krizaka.sovkora.core.domain.ports.outbound.ConsentRegistry;
import com.krizaka.sovkora.core.domain.ports.outbound.ExternalCheckConnector;
import com.krizaka.sovkora.core.infrastructure.checks.MockConnector;
import com.krizaka.sovkora.core.infrastructure.support.CoreException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExternalCheckServiceTest {

  private static final String SUBJECT = "subject-1";
  private static final String USER = "user-1";
  private static final String PROVIDER = "soquij";
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC);

  private final Set<String> consents = new java.util.HashSet<>();
  private final Map<String, String> userKeys = new HashMap<>();
  private final Map<String, String> platformKeys = new HashMap<>();
  private final List<CheckAudit> audit = new ArrayList<>();
  private final List<CheckRequest> reached = new ArrayList<>();

  private ConsentRegistry registry;
  private CheckKeyStore keyStore;
  private CheckAuditLog log;

  @BeforeEach
  void setUp() {
    registry = (subjectId, purpose) -> consents.contains(subjectId + "|" + purpose);
    keyStore =
        new CheckKeyStore() {
          @Override
          public java.util.Optional<String> userKey(String userId, String provider) {
            return java.util.Optional.ofNullable(userKeys.get(userId + "|" + provider));
          }

          @Override
          public java.util.Optional<String> platformKey(String provider) {
            return java.util.Optional.ofNullable(platformKeys.get(provider));
          }
        };
    log = audit::add;
    consents.add(SUBJECT + "|" + CheckPurpose.COURT_RECORDS);
    platformKeys.put(PROVIDER, "platform-key");
  }

  /** A connector that records every request it receives and answers what it is told. */
  private ExternalCheckConnector connector(Function<CheckRequest, CheckResult> answer) {
    return new ExternalCheckConnector() {
      @Override
      public String provider() {
        return PROVIDER;
      }

      @Override
      public CheckResult verifyCourtRecords(CheckRequest request) {
        reached.add(request);
        return answer.apply(request);
      }

      @Override
      public CheckResult verifyCredit(CheckRequest request) {
        reached.add(request);
        return answer.apply(request);
      }

      @Override
      public CheckResult verifyIdentity(CheckRequest request) {
        reached.add(request);
        return answer.apply(request);
      }
    };
  }

  private static final byte[] PEPPER =
      "a-pepper-sealed-in-the-settings-32b".getBytes(StandardCharsets.UTF_8);

  private final List<String> meter = new ArrayList<>();
  private boolean budgetExhausted;
  private Optional<byte[]> pepper = Optional.of(PEPPER);

  /** A meter that records hold, settle and release, and can refuse a hold. */
  private final CheckMeter checkMeter =
      (runId, actorId, provider, purpose) -> {
        if (budgetExhausted) {
          throw new CheckMeter.BudgetExhaustedException("the run budget is spent");
        }
        meter.add("hold " + purpose);
        return new CheckMeter.Hold() {
          @Override
          public void settle() {
            meter.add("settle");
          }

          @Override
          public void release(String reason) {
            meter.add("release");
          }
        };
      };

  private ExternalCheckService service(ExternalCheckConnector connector) {
    return new ExternalCheckService(
        CheckRouter.fixed(connector), registry, keyStore, log, checkMeter, () -> pepper, CLOCK);
  }

  private static CheckCall call(String userId) {
    return new CheckCall(
        userId,
        UUID.fromString("00000000-0000-0000-0000-000000000042"),
        new CheckSubject(SUBJECT, "Tremblay", "Marie", null));
  }

  @Test
  @DisplayName("without consent the provider is never reached, and the refusal is audited")
  void refusesWithoutConsent() {
    consents.clear();
    ExternalCheckService service =
        service(connector(r -> CheckResult.answered(CheckOutcome.MATCH, "x")));

    CheckResult result = service.verifyCourtRecords(call(USER));

    assertThat(result.outcome()).isEqualTo(CheckOutcome.REFUSED);
    assertThat(result.refusal()).isEqualTo(CheckRefusal.NO_CONSENT);
    assertThat(reached).isEmpty();
    assertThat(audit).hasSize(1);
    assertThat(audit.getFirst().outcome()).isEqualTo(CheckOutcome.REFUSED);
    assertThat(audit.getFirst().keySource()).isNull();
  }

  @Test
  @DisplayName("consent is per purpose: court records consent does not allow a credit check")
  void consentIsPerPurpose() {
    ExternalCheckService service =
        service(connector(r -> CheckResult.answered(CheckOutcome.MATCH, "x")));

    CheckResult result = service.verifyCredit(call(USER));

    assertThat(result.refusal()).isEqualTo(CheckRefusal.NO_CONSENT);
    assertThat(reached).isEmpty();
  }

  @Test
  @DisplayName("the actor's own key is used over the platform key")
  void userKeyOverPlatformKey() {
    userKeys.put(USER + "|" + PROVIDER, "user-key");
    ExternalCheckService service =
        service(connector(r -> CheckResult.answered(CheckOutcome.MATCH, "case-7")));

    CheckResult result = service.verifyCourtRecords(call(USER));

    assertThat(reached).hasSize(1);
    assertThat(reached.getFirst().key().source()).isEqualTo(KeySource.USER);
    assertThat(reached.getFirst().key().value()).isEqualTo("user-key");
    assertThat(result.outcome()).isEqualTo(CheckOutcome.MATCH);
    assertThat(result.providerRef()).isEqualTo("case-7");
    assertThat(audit.getFirst().keySource()).isEqualTo(KeySource.USER);
  }

  @Test
  @DisplayName("without an own key the platform key is used")
  void platformKeyWhenNoUserKey() {
    ExternalCheckService service =
        service(connector(r -> CheckResult.answered(CheckOutcome.NO_MATCH, null)));

    CheckResult result = service.verifyCourtRecords(call(USER));

    assertThat(reached.getFirst().key().source()).isEqualTo(KeySource.PLATFORM);
    assertThat(result.outcome()).isEqualTo(CheckOutcome.NO_MATCH);
    assertThat(audit.getFirst().keySource()).isEqualTo(KeySource.PLATFORM);
  }

  @Test
  @DisplayName("with neither key the call is refused before the provider")
  void refusesWithoutAnyKey() {
    platformKeys.clear();
    ExternalCheckService service =
        service(connector(r -> CheckResult.answered(CheckOutcome.MATCH, "x")));

    CheckResult result = service.verifyCourtRecords(call(USER));

    assertThat(result.refusal()).isEqualTo(CheckRefusal.NO_KEY);
    assertThat(reached).isEmpty();
    assertThat(audit).hasSize(1);
  }

  @Test
  @DisplayName("a connector that throws is a provider error, and its message is not audited")
  void providerExceptionIsAnOutcomeWithoutItsMessage() {
    ExternalCheckService service =
        service(
            connector(
                r -> {
                  throw new IllegalStateException("payload: Tremblay Marie 1980");
                }));

    CheckResult result = service.verifyCourtRecords(call(USER));

    assertThat(result.outcome()).isEqualTo(CheckOutcome.PROVIDER_ERROR);
    assertThat(audit).hasSize(1);
    assertThat(audit.getFirst().outcome()).isEqualTo(CheckOutcome.PROVIDER_ERROR);
    assertThat(audit.getFirst().toString()).doesNotContain("Tremblay", "1980");
  }

  @Test
  @DisplayName("a connector that answers REFUSED is treated as a provider fault")
  void aConnectorCannotRefuseForTheService() {
    ExternalCheckService service = service(connector(r -> null));

    CheckResult result = service.verifyCourtRecords(call(USER));

    assertThat(result.outcome()).isEqualTo(CheckOutcome.PROVIDER_ERROR);
    assertThat(result.refusal()).isNull();
  }

  @Test
  @DisplayName("a timeout is answered as TIMEOUT and audited with its duration")
  void timeoutIsAudited() {
    ExternalCheckService service =
        service(connector(r -> CheckResult.answered(CheckOutcome.TIMEOUT, null)));

    CheckResult result = service.verifyCourtRecords(call(USER));

    assertThat(result.outcome()).isEqualTo(CheckOutcome.TIMEOUT);
    assertThat(audit.getFirst().outcome()).isEqualTo(CheckOutcome.TIMEOUT);
    assertThat(audit.getFirst().durationMs()).isNotNull();
  }

  @Test
  @DisplayName("the audit names the subject by a keyed HMAC-SHA-256 of its id, never a plain hash")
  void auditNamesTheSubjectByAKeyedHmac() throws Exception {
    ExternalCheckService service =
        service(connector(r -> CheckResult.answered(CheckOutcome.NO_MATCH, null)));

    service.verifyCourtRecords(call(USER));

    CheckAudit line = audit.getFirst();
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(PEPPER, "HmacSHA256"));
    String expected =
        HexFormat.of().formatHex(mac.doFinal(SUBJECT.getBytes(StandardCharsets.UTF_8)));
    String plain =
        HexFormat.of()
            .formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(SUBJECT.getBytes(StandardCharsets.UTF_8)));
    assertThat(line.subjectRef()).isEqualTo(expected).isNotEqualTo(plain).doesNotContain(SUBJECT);
    assertThat(line.runId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000042"));
    assertThat(line.at()).isEqualTo(Instant.parse("2026-10-23T12:00:00Z"));
    assertThat(line.provider()).isEqualTo(PROVIDER);
  }

  @Test
  @DisplayName(
      "another pepper gives another reference: the reference cannot be enumerated without it")
  void anotherPepperAnotherReference() {
    ExternalCheckService service =
        service(connector(r -> CheckResult.answered(CheckOutcome.NO_MATCH, null)));
    service.verifyCourtRecords(call(USER));

    pepper = Optional.of("another-pepper-sealed-elsewhere-32b".getBytes(StandardCharsets.UTF_8));
    service.verifyCourtRecords(call(USER));

    assertThat(audit.getFirst().subjectRef()).isNotEqualTo(audit.get(1).subjectRef());
  }

  @Test
  @DisplayName("without a sealed pepper (or a short one) no check runs: fail closed, nothing held")
  void noPepperFailsClosed() {
    ExternalCheckService service =
        service(connector(r -> CheckResult.answered(CheckOutcome.MATCH, "x")));

    pepper = Optional.empty();
    assertThatThrownBy(() -> service.verifyCourtRecords(call(USER)))
        .isInstanceOf(CoreException.class);
    pepper = Optional.of("short".getBytes(StandardCharsets.UTF_8));
    assertThatThrownBy(() -> service.verifyCourtRecords(call(USER)))
        .isInstanceOf(CoreException.class);

    assertThat(reached).isEmpty();
    assertThat(meter).isEmpty();
  }

  @Test
  @DisplayName("an answered call holds one CALL and settles it")
  void answeredCallIsSettled() {
    ExternalCheckService service =
        service(connector(r -> CheckResult.answered(CheckOutcome.MATCH, "x")));

    service.verifyCourtRecords(call(USER));

    assertThat(meter).containsExactly("hold COURT_RECORDS", "settle");
  }

  @Test
  @DisplayName("a provider error or a timeout releases the hold: nothing is charged")
  void failedCallIsReleased() {
    ExternalCheckService failing =
        service(connector(r -> CheckResult.answered(CheckOutcome.TIMEOUT, null)));
    failing.verifyCourtRecords(call(USER));
    ExternalCheckService throwing =
        service(
            connector(
                r -> {
                  throw new IllegalStateException("down");
                }));
    throwing.verifyCourtRecords(call(USER));

    assertThat(meter)
        .containsExactly("hold COURT_RECORDS", "release", "hold COURT_RECORDS", "release");
  }

  @Test
  @DisplayName("a refused call takes no hold")
  void refusedCallTakesNoHold() {
    consents.clear();
    service(connector(r -> CheckResult.answered(CheckOutcome.MATCH, "x")))
        .verifyCourtRecords(call(USER));

    assertThat(meter).isEmpty();
  }

  @Test
  @DisplayName("a run budget that cannot hold the call refuses it before the provider, audited")
  void budgetExhaustedRefusesBeforeTheProvider() {
    budgetExhausted = true;
    CheckResult result =
        service(connector(r -> CheckResult.answered(CheckOutcome.MATCH, "x")))
            .verifyCourtRecords(call(USER));

    assertThat(result.refusal()).isEqualTo(CheckRefusal.BUDGET_EXHAUSTED);
    assertThat(reached).isEmpty();
    assertThat(audit)
        .singleElement()
        .extracting(CheckAudit::refusal)
        .isEqualTo(CheckRefusal.BUDGET_EXHAUSTED);
  }

  @Test
  @DisplayName("a blueprint step calls a check by its feature key and reads calls: 1 when answered")
  void capabilityByFeatureKeyReportsOneCall() {
    CheckStepResult step =
        service(
                connector(
                    r ->
                        CheckResult.answered(
                            CheckOutcome.MATCH, CheckCategory.TAL_NON_PAYMENT, "x")))
            .run("orazaka.core.checks.courtrecords", call(USER));

    assertThat(step.result().outcome()).isEqualTo(CheckOutcome.MATCH);
    assertThat(step.result().category()).isEqualTo(CheckCategory.TAL_NON_PAYMENT);
    assertThat(step.consumption()).containsExactlyEntriesOf(Map.of("calls", 1));
    assertThat(reached).hasSize(1);
  }

  @Test
  @DisplayName("a refused, failed or timed-out step reports calls: 0")
  void capabilityReportsZeroCallsWhenNotAnswered() {
    CheckStepResult timeout =
        service(connector(r -> CheckResult.answered(CheckOutcome.TIMEOUT, null)))
            .run("orazaka.core.checks.courtrecords", call(USER));
    consents.clear();
    CheckStepResult refused =
        service(connector(r -> CheckResult.answered(CheckOutcome.MATCH, "x")))
            .run("orazaka.core.checks.credit", call(USER));

    assertThat(timeout.consumption()).containsEntry("calls", 0);
    assertThat(refused.consumption()).containsEntry("calls", 0);
  }

  @Test
  @DisplayName("each feature key reaches its own purpose, and an unknown key is refused")
  void featureKeysMapToPurposes() {
    consents.add(SUBJECT + "|" + CheckPurpose.CREDIT);
    consents.add(SUBJECT + "|" + CheckPurpose.IDENTITY);
    ExternalCheckService service =
        service(connector(r -> CheckResult.answered(CheckOutcome.NO_MATCH, null)));

    service.run("orazaka.core.checks.credit", call(USER));
    service.run("orazaka.core.checks.identity", call(USER));

    assertThat(audit)
        .extracting(CheckAudit::purpose)
        .containsExactly(CheckPurpose.CREDIT, CheckPurpose.IDENTITY);
    assertThat(CheckPurpose.ofFeatureKey("orazaka.core.checks.courtrecords"))
        .contains(CheckPurpose.COURT_RECORDS);
    // The capability registry's own pattern (sovkora-contracts CapabilityDeclaration): no hyphen.
    assertThat(CheckPurpose.values())
        .extracting(CheckPurpose::featureKey)
        .allMatch(key -> key.matches("^orazaka\\.[a-z0-9]+(\\.[a-z0-9]+)+$"));
    assertThatThrownBy(() -> service.run("orazaka.core.checks.unknown", call(USER)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("a category other than NONE comes only with a match: never free text")
  void categoryOnlyWithAMatch() {
    assertThatThrownBy(
            () -> CheckResult.answered(CheckOutcome.NO_MATCH, CheckCategory.CRIMINAL, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(CheckResult.answered(CheckOutcome.NO_MATCH, null).category())
        .isEqualTo(CheckCategory.NONE);
  }

  @Test
  @DisplayName("the recorded mock answers the service end to end, without network")
  void mockAnswersTheService() {
    consents.add(SUBJECT + "|" + CheckPurpose.CREDIT);
    platformKeys.put("mock", "mock-key");
    ExternalCheckService service = service(new MockConnector());

    CheckResult matched =
        service.verifyCredit(
            new CheckCall(USER, null, new CheckSubject(SUBJECT, "Tremblay", "Marie", null)));
    CheckResult unknown =
        service.verifyCredit(
            new CheckCall(USER, null, new CheckSubject(SUBJECT, "Inconnu", "Marie", null)));

    assertThat(matched.outcome()).isEqualTo(CheckOutcome.MATCH);
    assertThat(unknown.outcome()).isEqualTo(CheckOutcome.NO_MATCH);
    assertThat(audit).hasSize(2);
  }

  @Test
  @DisplayName("consent withdrawn stops the next call at once")
  void withdrawnConsentStopsTheNextCall() {
    ExternalCheckService service =
        service(connector(r -> CheckResult.answered(CheckOutcome.MATCH, "x")));
    assertThat(service.verifyCourtRecords(call(USER)).outcome()).isEqualTo(CheckOutcome.MATCH);

    consents.clear();

    assertThat(service.verifyCourtRecords(call(USER)).refusal()).isEqualTo(CheckRefusal.NO_CONSENT);
    assertThat(reached).hasSize(1);
    assertThat(audit).hasSize(2);
  }
}
