package com.krizaka.sovkora.engine.interceptors.governance;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the provider calls of the jobs running in this process have cost so far, by job.
 *
 * <p>{@link ProviderSpendListener} is the one author of a call's cost; this is how a job that asked
 * for it gets the sum back without pricing anything itself. A job is opened by the host that runs
 * it, keyed by the conversation id its context carries (the job id), and closed once it has ended:
 * only an open job accumulates, so a chat turn that nobody collects leaves nothing behind.
 */
public class JobSpendTally {

  private final Map<String, BigDecimal> open = new ConcurrentHashMap<>();

  /**
   * Starts collecting the cost of a job.
   *
   * @param jobId the job, which is the conversation id of its context
   */
  public void open(String jobId) {
    open.put(jobId, BigDecimal.ZERO);
  }

  /**
   * Adds the cost of one priced call to the job it belongs to; a call of no open job adds nothing.
   *
   * @param jobId the conversation id of the call's context
   * @param usd the call's cost
   */
  void add(String jobId, BigDecimal usd) {
    open.computeIfPresent(jobId, (id, spent) -> spent.add(usd));
  }

  /**
   * What an open job has cost so far.
   *
   * @param jobId the job
   * @return the cost of its priced calls, zero when it made none or is not open
   */
  public BigDecimal spent(String jobId) {
    return open.getOrDefault(jobId, BigDecimal.ZERO);
  }

  /**
   * Stops collecting and returns what the job cost.
   *
   * @param jobId the job
   * @return the cost of its priced calls, zero when it made none or was never opened
   */
  public BigDecimal close(String jobId) {
    BigDecimal spent = open.remove(jobId);
    return spent == null ? BigDecimal.ZERO : spent;
  }
}
