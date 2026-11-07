package com.krizaka.sovkora.core.domain.ports.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.core.application.checks.CheckRouter;
import com.krizaka.sovkora.core.application.checks.ExternalCheckService;
import com.krizaka.sovkora.core.domain.model.checks.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The contract every {@link ExternalCheckConnector} inherits (sovkora-engine#83): a real adapter
 * extends this class, gives its connector and the subjects that reach each of its answers, and is
 * held to the same rules as the mock.
 *
 * <ul>
 *   <li>a connector never answers {@code REFUSED} (refusing is the service's job);
 *   <li>a category other than {@code NONE} comes only with a match;
 *   <li>no field of the {@link CheckResult}, nor of the {@link CheckAudit} the service writes for
 *       it, holds the subject's last name, first name, date of birth (in any common format) or
 *       address.
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class ExternalCheckConnectorContract {

  private static final UUID RUN = UUID.fromString("00000000-0000-0000-0000-000000000083");

  /**
   * The connector under test, with no network.
   *
   * @return the connector
   */
  protected abstract ExternalCheckConnector connector();

  /**
   * Subjects that reach every answer the connector can give (a match per category, a no match, a
   * provider error, a timeout). Each carries a name, a date of birth and an address.
   *
   * @return the subjects
   */
  protected abstract List<CheckSubject> subjects();

  /**
   * The purposes the connector serves; the contract asks only those.
   *
   * @return the purposes (all by default)
   */
  protected java.util.Set<CheckPurpose> purposes() {
    return java.util.EnumSet.allOf(CheckPurpose.class);
  }

  private static CheckRouter routerOf(ExternalCheckConnector connector) {
    CheckProviderRow row =
        new CheckProviderRow(
            connector.provider(),
            connector.provider(),
            java.util.EnumSet.allOf(CheckPurpose.class),
            java.util.Set.of(),
            0,
            true,
            "https://provider.test",
            0);
    return new CheckRouter(() -> List.of(row), java.util.Map.of(connector.provider(), connector));
  }

  /** A hold that charges nothing: the contract is about data, not money. */
  private static final CheckMeter.Hold FREE =
      new CheckMeter.Hold() {
        @Override
        public void settle() {
          // nothing to charge in the contract
        }

        @Override
        public void release(String reason) {
          // nothing was held in the contract
        }
      };

  /** One subject and one purpose. */
  protected record Case(CheckSubject subject, CheckPurpose purpose) {
    @Override
    public String toString() {
      return purpose + " / " + subject.subjectId();
    }
  }

  Stream<Case> cases() {
    return subjects().stream().flatMap(s -> purposes().stream().map(p -> new Case(s, p)));
  }

  private static CheckResult ask(ExternalCheckConnector connector, Case c) {
    CheckRequest request =
        new CheckRequest(
            c.subject(), new CheckKey(KeySource.PLATFORM, "k"), "https://provider.test");
    return switch (c.purpose()) {
      case COURT_RECORDS -> connector.verifyCourtRecords(request);
      case CREDIT -> connector.verifyCredit(request);
      case IDENTITY -> connector.verifyIdentity(request);
    };
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  @DisplayName("a connector answers, never refuses, and names a category only on a match")
  void answersWithAClosedCategory(Case c) {
    CheckResult result = ask(connector(), c);

    assertThat(result).isNotNull();
    assertThat(result.outcome()).isNotEqualTo(CheckOutcome.REFUSED);
    assertThat(result.category()).isNotNull();
    if (result.outcome() != CheckOutcome.MATCH) {
      assertThat(result.category()).isEqualTo(CheckCategory.NONE);
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  @DisplayName("no field of the result holds the subject's name, date of birth or address")
  void resultHoldsNoPersonalData(Case c) {
    CheckResult result = ask(connector(), c);

    assertNoPersonalData(c.subject(), fields(result));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  @DisplayName("no field of the audit line holds the subject's name, date of birth or address")
  void auditHoldsNoPersonalData(Case c) {
    List<CheckAudit> audit = new ArrayList<>();
    ExternalCheckService service =
        new ExternalCheckService(
            routerOf(connector()),
            (subjectId, purpose) -> true,
            new CheckKeyStore() {
              @Override
              public Optional<String> userKey(String userId, String provider) {
                return Optional.empty();
              }

              @Override
              public Optional<String> platformKey(String provider) {
                return Optional.of("k");
              }
            },
            audit::add,
            (runId, actorId, provider, purpose) -> FREE,
            () ->
                Optional.of(
                    "contract-pepper-of-at-least-32-bytes".getBytes(StandardCharsets.UTF_8)),
            Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneOffset.UTC));
    BiFunction<ExternalCheckService, CheckCall, CheckResult> verify =
        switch (c.purpose()) {
          case COURT_RECORDS -> ExternalCheckService::verifyCourtRecords;
          case CREDIT -> ExternalCheckService::verifyCredit;
          case IDENTITY -> ExternalCheckService::verifyIdentity;
        };

    CheckResult result = verify.apply(service, new CheckCall("user-1", RUN, c.subject()));

    assertNoPersonalData(c.subject(), fields(result));
    assertThat(audit).hasSize(1);
    CheckAudit line = audit.getFirst();
    assertNoPersonalData(
        c.subject(),
        Stream.of(
                line.actorId(),
                line.provider(),
                line.subjectRef(),
                line.providerRef(),
                String.valueOf(line.purpose()),
                String.valueOf(line.outcome()),
                String.valueOf(line.refusal()),
                String.valueOf(line.keySource()),
                line.toString())
            .toList());
  }

  private static List<String> fields(CheckResult result) {
    return Stream.of(
            result.providerRef(),
            String.valueOf(result.outcome()),
            String.valueOf(result.refusal()),
            String.valueOf(result.category()),
            result.toString())
        .toList();
  }

  /** Every way the subject could leak: names, the date in common formats, the address. */
  private static void assertNoPersonalData(CheckSubject subject, List<String> values) {
    List<String> forbidden = new ArrayList<>();
    forbidden.add(subject.lastName());
    forbidden.add(subject.firstName());
    LocalDate born = subject.birthDate();
    if (born != null) {
      forbidden.add(born.toString());
      forbidden.add(born.format(DateTimeFormatter.BASIC_ISO_DATE));
      forbidden.add(born.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
      forbidden.add(born.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")));
    }
    if (subject.address() != null) {
      forbidden.add(subject.address());
    }
    for (String value : values) {
      if (value == null) {
        continue;
      }
      String lower = value.toLowerCase(Locale.ROOT);
      for (String word : forbidden) {
        assertThat(lower).doesNotContain(word.toLowerCase(Locale.ROOT));
      }
    }
  }
}
