package com.krizaka.sovkora.core.domain.ports.outbound;

import java.time.Duration;
import java.util.Map;

/**
 * The one HTTP door of the provider adapters, so a test replaces the network with a recorded
 * answer.
 */
public interface CheckHttp {

  /**
   * What the provider answered.
   *
   * @param status the HTTP status
   * @param body the body, possibly empty
   */
  record Reply(int status, String body) {}

  /** The provider did not answer in time. */
  class TimeoutException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** A timeout; carries no message so nothing of the call leaks. */
    public TimeoutException() {
      super("the provider did not answer in time");
    }
  }

  /**
   * Sends one POST.
   *
   * @param url the HTTPS URL
   * @param headers the request headers
   * @param body the JSON body
   * @param timeout how long to wait
   * @return the reply
   * @throws TimeoutException when the provider is too slow
   */
  Reply post(String url, Map<String, String> headers, String body, Duration timeout);
}
