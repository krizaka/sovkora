package com.krizaka.sovkora.core.application.checks;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.core.domain.model.checks.*;
import com.krizaka.sovkora.core.domain.ports.outbound.CheckKeyStore;
import com.krizaka.sovkora.core.domain.ports.outbound.CheckMeter;
import com.krizaka.sovkora.core.infrastructure.checks.HttpCheckConnector;
import com.krizaka.sovkora.core.infrastructure.checks.RecordedProviderHttp;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The service over provider rows and the real HTTP adapters on recorded answers. */
class ExternalCheckRoutingTest {

  private static final UUID RUN = UUID.fromString("00000000-0000-0000-0000-000000000073");

  private final List<CheckAudit> audit = new ArrayList<>();
  private final List<String> meter = new ArrayList<>();
  private final RecordedProviderHttp soquijHttp = RecordedProviderHttp.recorded();
  private final RecordedProviderHttp certnHttp = RecordedProviderHttp.recorded();
  private final Map<String, String> platformKeys = Map.of("soquij", "ks", "certn", "kc");
  private Map<String, String> userKeys = Map.of();
  private List<CheckProviderRow> rows =
      List.of(
          new CheckProviderRow(
              "soquij",
              "soquij",
              Set.of(CheckPurpose.COURT_RECORDS),
              Set.of("QC"),
              1,
              true,
              "https://soquij.test",
              250_000),
          new CheckProviderRow(
              "certn",
              "certn",
              EnumSet.of(CheckPurpose.COURT_RECORDS),
              Set.of(),
              2,
              true,
              "https://certn.test",
              900_000));

  private ExternalCheckService service() {
    CheckRouter router =
        new CheckRouter(
            () -> rows,
            Map.of(
                "soquij", HttpCheckConnector.soquij(soquijHttp, Duration.ofSeconds(1)),
                "certn", HttpCheckConnector.certn(certnHttp, Duration.ofSeconds(1))));
    CheckKeyStore keys =
        new CheckKeyStore() {
          @Override
          public Optional<String> userKey(String userId, String provider) {
            return Optional.ofNullable(userKeys.get(provider));
          }

          @Override
          public Optional<String> platformKey(String provider) {
            return Optional.ofNullable(platformKeys.get(provider));
          }
        };
    CheckMeter checkMeter =
        (runId, actorId, provider, purpose) -> {
          meter.add("hold " + provider);
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
    return new ExternalCheckService(
        router,
        (subjectId, purpose) -> true,
        keys,
        audit::add,
        checkMeter,
        () -> Optional.of("a-pepper-sealed-in-the-settings-32b".getBytes(StandardCharsets.UTF_8)),
        Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC));
  }

  private static CheckCall call(String lastName, String region) {
    return new CheckCall("user-1", RUN, new CheckSubject("s-1", lastName, "Marie", null), region);
  }

  @Test
  @DisplayName("a Québec subject is searched at SOQUIJ, a platform call costing the row's price")
  void quebecGoesToSoquij() {
    CheckResult result = service().verifyCourtRecords(call("Tremblay", "QC"));

    assertThat(result.outcome()).isEqualTo(CheckOutcome.MATCH);
    assertThat(soquijHttp.sent).hasSize(1);
    assertThat(certnHttp.sent).isEmpty();
    assertThat(soquijHttp.sent.getFirst().url()).startsWith("https://soquij.test");
    assertThat(audit.getFirst().provider()).isEqualTo("soquij");
    assertThat(audit.getFirst().providerCostMicros()).isEqualTo(250_000L);
    assertThat(audit.getFirst().runId()).isEqualTo(RUN);
    assertThat(meter).containsExactly("hold soquij", "settle");
  }

  @Test
  @DisplayName("another region falls back to Certn")
  void otherRegionGoesToCertn() {
    service().verifyCourtRecords(call("Gagnon", "ON"));

    assertThat(certnHttp.sent).hasSize(1);
    assertThat(soquijHttp.sent).isEmpty();
    assertThat(audit.getFirst().provider()).isEqualTo("certn");
  }

  @Test
  @DisplayName("the user's own key over the platform's, and then the provider's price is not ours")
  void userKeyOverPlatformKey() {
    userKeys = Map.of("soquij", "k-user");

    service().verifyCourtRecords(call("Tremblay", "QC"));

    assertThat(soquijHttp.sent.getFirst().headers().get("Authorization"))
        .isEqualTo("Bearer k-user");
    assertThat(audit.getFirst().keySource()).isEqualTo(KeySource.USER);
    assertThat(audit.getFirst().providerCostMicros()).isNull();
  }

  @Test
  @DisplayName("no enabled row for the purpose is NOT_CONFIGURED: nothing held, nothing called")
  void noRowIsNotConfigured() {
    rows = List.of();

    CheckResult result = service().verifyCourtRecords(call("Tremblay", "QC"));

    assertThat(result.refusal()).isEqualTo(CheckRefusal.NOT_CONFIGURED);
    assertThat(meter).isEmpty();
    assertThat(audit).hasSize(1);
    assertThat(audit.getFirst().refusal()).isEqualTo(CheckRefusal.NOT_CONFIGURED);
  }

  @Test
  @DisplayName("a row without an endpoint is NOT_CONFIGURED and the hold is released")
  void rowWithoutEndpointIsNotConfigured() {
    rows =
        List.of(
            new CheckProviderRow(
                "soquij",
                "soquij",
                Set.of(CheckPurpose.COURT_RECORDS),
                Set.of(),
                1,
                true,
                null,
                0));

    CheckResult result = service().verifyCourtRecords(call("Tremblay", "QC"));

    assertThat(result.refusal()).isEqualTo(CheckRefusal.NOT_CONFIGURED);
    assertThat(soquijHttp.sent).isEmpty();
    assertThat(meter).containsExactly("hold soquij", "release");
  }

  @Test
  @DisplayName("a timeout and a provider error release the hold and are audited with no leak")
  void errorsReleaseTheHold() {
    CheckResult slow = service().verifyCourtRecords(call("Bouchard", "QC"));
    CheckResult broken = service().verifyCourtRecords(call("Roy", "QC"));

    assertThat(slow.outcome()).isEqualTo(CheckOutcome.TIMEOUT);
    assertThat(broken.outcome()).isEqualTo(CheckOutcome.PROVIDER_ERROR);
    assertThat(meter).containsExactly("hold soquij", "release", "hold soquij", "release");
    assertThat(audit).extracting(CheckAudit::providerRef).containsOnlyNulls();
    assertThat(audit.toString()).doesNotContain("Roy", "Marie", "1984");
  }

  @Test
  @DisplayName("a PENDING search is charged once at submission, then collected by polling")
  void pendingThenCollected() {
    ExternalCheckService service = service();

    CheckResult pending = service.verifyCourtRecords(call("Pelletier", "QC"));
    assertThat(pending.outcome()).isEqualTo(CheckOutcome.PENDING);
    assertThat(pending.providerRef()).isEqualTo("case-wait-05");
    assertThat(CheckStepResult.of(pending).calls()).isEqualTo(1);
    assertThat(meter).containsExactly("hold soquij", "settle");

    CheckResult collected =
        service.collect("soquij", CheckPurpose.COURT_RECORDS, call("Gagnon", "QC"), "case-wait-05");

    assertThat(collected.outcome()).isEqualTo(CheckOutcome.NO_MATCH);
    assertThat(meter).hasSize(2);
    assertThat(soquijHttp.sent.getLast().url()).endsWith("/plumitifs/case-wait-05");
    assertThat(audit).hasSize(2);
    assertThat(audit.getLast().providerCostMicros()).isNull();
  }

  @Test
  @DisplayName("polling a provider whose row is gone is NOT_CONFIGURED")
  void collectUnknownProvider() {
    CheckResult result =
        service().collect("ghost", CheckPurpose.COURT_RECORDS, call("Gagnon", "QC"), "x");

    assertThat(result.refusal()).isEqualTo(CheckRefusal.NOT_CONFIGURED);
  }
}
