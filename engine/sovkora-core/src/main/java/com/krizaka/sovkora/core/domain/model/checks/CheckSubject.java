package com.krizaka.sovkora.core.domain.model.checks;

import java.time.LocalDate;
import java.util.Objects;

/**
 * The person a check is about. Names, birth date and address are personal data: {@link #toString()}
 * never prints them.
 *
 * @param subjectId the opaque platform id of the subject (consent is keyed on it)
 * @param lastName the last name searched
 * @param firstName the first name searched
 * @param birthDate the birth date, or {@code null} when not given
 * @param address the postal address searched, or {@code null} when not given
 */
public record CheckSubject(
    String subjectId, String lastName, String firstName, LocalDate birthDate, String address) {

  /**
   * A subject with no address.
   *
   * @param subjectId the opaque platform id
   * @param lastName the last name
   * @param firstName the first name
   * @param birthDate the birth date, or {@code null}
   */
  public CheckSubject(String subjectId, String lastName, String firstName, LocalDate birthDate) {
    this(subjectId, lastName, firstName, birthDate, null);
  }

  /** Validates the subject. */
  public CheckSubject {
    requireText(subjectId, "subjectId");
    requireText(lastName, "lastName");
    requireText(firstName, "firstName");
  }

  @Override
  public String toString() {
    return "CheckSubject[subjectId="
        + subjectId
        + ", names=redacted, birthDate="
        + (birthDate == null ? "none" : "redacted")
        + ", address="
        + (address == null ? "none" : "redacted")
        + "]";
  }

  static void requireText(String value, String name) {
    Objects.requireNonNull(value, name + " must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
