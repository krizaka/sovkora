package com.krizaka.sovkora.core.domain.model.checks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CheckRecordsTest {

  @Test
  @DisplayName("the subject's names and birth date never reach a log line")
  void subjectToStringRedactsPersonalData() {
    CheckSubject subject =
        new CheckSubject("subject-1", "Tremblay", "Marie", LocalDate.of(1980, 2, 3));

    assertThat(subject.toString())
        .contains("subject-1")
        .doesNotContain("Tremblay", "Marie", "1980");
  }

  @Test
  @DisplayName("a blank subject id or name is refused")
  void subjectRequiresItsIdentifiers() {
    assertThatThrownBy(() -> new CheckSubject(" ", "Tremblay", "Marie", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CheckSubject("s", null, "Marie", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("a key never prints its value")
  void keyToStringRedactsTheValue() {
    CheckKey key = new CheckKey(KeySource.USER, "sk-secret-value");

    assertThat(key.toString()).contains("USER").doesNotContain("sk-secret-value");
  }

  @Test
  @DisplayName("a refusal comes with REFUSED, and REFUSED with a refusal, never otherwise")
  void resultRefusalAndOutcomeAgree() {
    assertThat(CheckResult.refused(CheckRefusal.NO_CONSENT).outcome())
        .isEqualTo(CheckOutcome.REFUSED);
    assertThatThrownBy(() -> CheckResult.answered(CheckOutcome.REFUSED, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new CheckResult(CheckOutcome.MATCH, CheckRefusal.NO_KEY, CheckCategory.NONE, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CheckResult(CheckOutcome.REFUSED, null, CheckCategory.NONE, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
