package com.krizaka.sovkora.automationservice.domain.port;

import java.io.IOException;

/** The body of an inbound call, read only when the service decides it is worth reading. */
@FunctionalInterface
public interface BodySource {

  /**
   * Reads the whole body if it is at most {@code maxBytes}.
   *
   * @param maxBytes the largest body accepted
   * @return the body, or null when it is larger (nothing beyond {@code maxBytes + 1} is read)
   * @throws IOException when the connection fails
   */
  byte[] readAtMost(int maxBytes) throws IOException;
}
