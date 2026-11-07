package com.krizaka.sovkora.core.domain.ports.outbound;

import java.util.Optional;

/**
 * The pepper that keys the audit's subject reference (an HMAC-SHA-256 of the subject id): a name
 * plus a birth date can be enumerated, a plain hash of an id can be too. The implementation reads
 * it from the sealed settings (the platform keyring, as the provider keys), never from an
 * environment variable or the code.
 */
public interface CheckSubjectPepper {

  /**
   * The current pepper.
   *
   * @return the pepper's bytes, or empty when none is sealed (then no check runs)
   */
  Optional<byte[]> pepper();
}
