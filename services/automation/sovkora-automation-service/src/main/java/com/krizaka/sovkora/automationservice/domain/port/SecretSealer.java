package com.krizaka.sovkora.automationservice.domain.port;

import java.util.UUID;

/**
 * Seals a secret for storage at rest and opens it again. A sealed value is bound to the scope it
 * was sealed for: it opens nowhere else (ADR-084 §1).
 */
public interface SecretSealer {

  /**
   * Seals a secret.
   *
   * @param scope what the secret belongs to, bound into the sealed value
   * @param plaintext the secret
   * @return the sealed form, safe to store
   */
  String seal(UUID scope, String plaintext);

  /**
   * Opens a sealed secret.
   *
   * @param scope what it belongs to
   * @param sealed the stored form
   * @return the secret
   * @throws IllegalStateException when the value does not open: another scope, a tampered value, or
   *     a master key this deployment no longer holds — never a fallback to the stored form
   */
  String open(UUID scope, String sealed);
}
