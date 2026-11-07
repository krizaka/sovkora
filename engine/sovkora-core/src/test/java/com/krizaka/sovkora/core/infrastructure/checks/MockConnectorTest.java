package com.krizaka.sovkora.core.infrastructure.checks;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.core.domain.model.checks.*;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MockConnectorTest {

  private static CheckRequest request(String lastName) {
    return new CheckRequest(
        new CheckSubject("subject-1", lastName, "Marie", null),
        new CheckKey(KeySource.PLATFORM, "k"));
  }

  @Test
  @DisplayName("the recorded fixtures answer a match, a no match, a provider error and a timeout")
  void recordedFixturesCoverEveryOutcome() {
    MockConnector mock = new MockConnector();

    assertThat(mock.verifyCourtRecords(request("Tremblay")).outcome())
        .isEqualTo(CheckOutcome.MATCH);
    assertThat(mock.verifyIdentity(request("Gagnon")).outcome()).isEqualTo(CheckOutcome.NO_MATCH);
    assertThat(mock.verifyCredit(request("Roy")).outcome()).isEqualTo(CheckOutcome.PROVIDER_ERROR);
    assertThat(mock.verifyCredit(request("Bouchard")).outcome()).isEqualTo(CheckOutcome.TIMEOUT);
  }

  @Test
  @DisplayName("a match carries an opaque fixture id, never the subject's name; a no match none")
  void matchCarriesAnOpaqueFixtureId() {
    MockConnector mock = new MockConnector();

    String ref = mock.verifyCourtRecords(request("Tremblay")).providerRef();

    assertThat(ref).isEqualTo("mock-fixture-01").doesNotContainIgnoringCase("tremblay");
    assertThat(mock.verifyCourtRecords(request("Gagnon")).providerRef()).isNull();
  }

  @Test
  @DisplayName(
      "court records name a closed category: a TAL non-payment file, another TAL file, criminal, civil")
  void courtRecordsNameACategory() {
    MockConnector mock = new MockConnector();

    assertThat(mock.verifyCourtRecords(request("Tremblay")).category())
        .isEqualTo(CheckCategory.TAL_NON_PAYMENT);
    assertThat(mock.verifyCourtRecords(request("Côté")).category())
        .isEqualTo(CheckCategory.TAL_OTHER);
    assertThat(mock.verifyCourtRecords(request("Morin")).category())
        .isEqualTo(CheckCategory.CRIMINAL);
    assertThat(mock.verifyCourtRecords(request("Lavoie")).category())
        .isEqualTo(CheckCategory.CIVIL);
    assertThat(mock.verifyCourtRecords(request("Gagnon")).category()).isEqualTo(CheckCategory.NONE);
  }

  @Test
  @DisplayName("a credit or identity match has no court category")
  void otherPurposesHaveNoCategory() {
    MockConnector mock = new MockConnector();

    assertThat(mock.verifyCredit(request("Tremblay")).category()).isEqualTo(CheckCategory.NONE);
    assertThat(mock.verifyIdentity(request("Tremblay")).category()).isEqualTo(CheckCategory.NONE);
  }

  @Test
  @DisplayName("an unknown name is no match, never a match")
  void unknownNameIsNoMatch() {
    assertThat(new MockConnector().verifyCourtRecords(request("Inconnu")).outcome())
        .isEqualTo(CheckOutcome.NO_MATCH);
  }

  @Test
  @DisplayName("the fixtures given to the connector are the ones it answers")
  void givenFixturesAreUsed() {
    MockConnector mock = new MockConnector(Map.of("Roy", CheckOutcome.MATCH));

    assertThat(mock.verifyCourtRecords(request("Roy")).outcome()).isEqualTo(CheckOutcome.MATCH);
    assertThat(mock.provider()).isEqualTo("mock");
  }
}
