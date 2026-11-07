package com.krizaka.sovkora.core.domain.ports.outbound;

import java.util.Optional;

/**
 * The provider keys for external checks, read from the existing encrypted settings (sealed with the
 * platform keyring). The implementation never reads an environment variable.
 */
public interface CheckKeyStore {

  /**
   * The key the actor stored for this provider.
   *
   * @param userId the actor
   * @param provider the provider's name
   * @return the key in clear, if the actor has one
   */
  Optional<String> userKey(String userId, String provider);

  /**
   * The platform key for this provider, from the admin settings.
   *
   * @param provider the provider's name
   * @return the key in clear, if the platform has one
   */
  Optional<String> platformKey(String provider);
}
