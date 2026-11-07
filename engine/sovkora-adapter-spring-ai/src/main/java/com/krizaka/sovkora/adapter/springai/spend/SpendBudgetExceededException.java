package com.krizaka.sovkora.adapter.springai.spend;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * The daily budget of paid providers is spent: the call is refused before it reaches the provider.
 * Answers {@code 429} so every client reads it as "not now" and not as a fault of the request.
 */
public class SpendBudgetExceededException extends ResponseStatusException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * @param provider the paid provider whose call was refused
   * @param budgetUsd the daily budget that is spent
   */
  public SpendBudgetExceededException(String provider, double budgetUsd) {
    super(
        HttpStatus.TOO_MANY_REQUESTS,
        "Daily spend budget of "
            + String.format(java.util.Locale.ROOT, "%.2f", budgetUsd)
            + " USD for paid providers is reached ('"
            + provider
            + "' refused); local models stay available. It resets at 00:00 UTC.");
  }
}
