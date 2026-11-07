package com.krizaka.sovkora.core.infrastructure.checks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.core.domain.model.checks.*;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HttpCheckConnectorsTest {

  private static final String ENDPOINT = "https://provider.test/v1/";
  private final RecordedProviderHttp http = RecordedProviderHttp.recorded();
  private final HttpCheckConnector soquij = HttpCheckConnector.soquij(http, Duration.ofSeconds(1));

  private static CheckRequest request(String lastName, String endpoint, String key) {
    return new CheckRequest(
        new CheckSubject("s-1", lastName, "Marie", LocalDate.of(1984, 3, 7)),
        new CheckKey(KeySource.PLATFORM, key),
        endpoint);
  }

  @Test
  @DisplayName("a match names its closed category and the provider's opaque reference")
  void match() {
    CheckResult result = soquij.verifyCourtRecords(request("Tremblay", ENDPOINT, "k-secret"));

    assertThat(result.outcome()).isEqualTo(CheckOutcome.MATCH);
    assertThat(result.category()).isEqualTo(CheckCategory.TAL_NON_PAYMENT);
    assertThat(result.providerRef()).isEqualTo("case-tal-01");
  }

  @Test
  @DisplayName("no match, a provider error, a timeout and an unreadable answer are each an outcome")
  void otherOutcomes() {
    assertThat(soquij.verifyCourtRecords(request("Gagnon", ENDPOINT, "k")).outcome())
        .isEqualTo(CheckOutcome.NO_MATCH);
    assertThat(soquij.verifyCourtRecords(request("Roy", ENDPOINT, "k")).outcome())
        .isEqualTo(CheckOutcome.PROVIDER_ERROR);
    assertThat(soquij.verifyCourtRecords(request("Bouchard", ENDPOINT, "k")).outcome())
        .isEqualTo(CheckOutcome.TIMEOUT);
    assertThat(soquij.verifyCourtRecords(request("Inconnu", ENDPOINT, "k")).outcome())
        .isEqualTo(CheckOutcome.PROVIDER_ERROR);
  }

  @Test
  @DisplayName("an accepted search is PENDING with the provider's reference, polled by it")
  void pendingThenPolled() {
    CheckResult result = soquij.verifyCourtRecords(request("Pelletier", ENDPOINT, "k"));

    assertThat(result.outcome()).isEqualTo(CheckOutcome.PENDING);
    assertThat(result.providerRef()).isEqualTo("case-wait-05");

    soquij.poll(request("Gagnon", ENDPOINT, "k"), CheckPurpose.COURT_RECORDS, "case-wait-05");
    assertThat(http.sent.getLast().url())
        .isEqualTo("https://provider.test/v1/plumitifs/case-wait-05");
  }

  @Test
  @DisplayName("without an HTTPS endpoint it answers not configured, calling nothing")
  void failsClosed() {
    assertThatThrownBy(() -> soquij.verifyCourtRecords(request("Tremblay", null, "k")))
        .isInstanceOf(ProviderNotConfiguredException.class);
    assertThatThrownBy(
            () -> soquij.verifyCourtRecords(request("Tremblay", "http://provider.test", "k")))
        .isInstanceOf(ProviderNotConfiguredException.class);
    assertThat(http.sent).isEmpty();
  }

  @Test
  @DisplayName("the key travels in the header only, and the error message never carries it")
  void keyStaysInTheHeader() {
    soquij.verifyCourtRecords(request("Tremblay", ENDPOINT, "k-secret"));

    RecordedProviderHttp.Sent sent = http.sent.getFirst();
    assertThat(sent.headers().get("Authorization")).isEqualTo("Bearer k-secret");
    assertThat(sent.body()).doesNotContain("k-secret");
    assertThatThrownBy(() -> soquij.verifyCourtRecords(request("Tremblay", null, "k-secret")))
        .hasMessageNotContaining("k-secret");
  }

  @Test
  @DisplayName("each adapter serves only its purposes: Trustii credit and ID, Certn court and ID")
  void purposes() {
    HttpCheckConnector trustii = HttpCheckConnector.trustii(http, Duration.ofSeconds(1));
    HttpCheckConnector certn = HttpCheckConnector.certn(http, Duration.ofSeconds(1));

    assertThat(trustii.verifyCredit(request("Gagnon", ENDPOINT, "k")).outcome())
        .isEqualTo(CheckOutcome.NO_MATCH);
    assertThat(http.sent.getLast().url()).isEqualTo("https://provider.test/v1/credit");
    assertThatThrownBy(() -> trustii.verifyCourtRecords(request("Gagnon", ENDPOINT, "k")))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(certn.verifyCourtRecords(request("Morin", ENDPOINT, "k")).category())
        .isEqualTo(CheckCategory.CRIMINAL);
    assertThat(http.sent.getLast().headers().get("Authorization")).isEqualTo("Api-Key k");
    assertThatThrownBy(() -> certn.verifyCredit(request("Gagnon", ENDPOINT, "k")))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
