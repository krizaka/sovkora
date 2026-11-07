package com.krizaka.sovkora.core.domain.ports.outbound;

import java.util.Optional;

/**
 * Outbound port to resolve decrypted API key credentials for dynamic model instantiation in core.
 */
public interface UserCredentialsProvider {

  /**
   * Resolves the decrypted API key for a user and provider.
   *
   * @param userId The unique user identifier.
   * @param providerName The AI provider name.
   * @return An Optional containing the plaintext API key.
   */
  Optional<String> getDecryptedApiKey(String userId, String providerName);
}
