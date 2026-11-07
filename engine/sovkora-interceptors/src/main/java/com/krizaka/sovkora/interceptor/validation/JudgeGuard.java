package com.krizaka.sovkora.interceptor.validation;

import com.krizaka.sovkora.core.domain.model.ValidationStepType;
import com.krizaka.sovkora.core.domain.model.control.JudgeUnavailableException;
import java.time.Clock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Every call to the semantic judge (Tier C / Tier D) goes through here (sovkora-engine#125): a
 * circuit breaker and a timeout, both read from {@link ClosedLoopValidationProperties}. Anything
 * but a usable answer is a {@link JudgeUnavailableException}, never a pass and never a neutral
 * score.
 */
final class JudgeGuard {

  private static final ExecutorService JUDGE_CALLS = Executors.newVirtualThreadPerTaskExecutor();

  private final JudgeCircuitBreaker breaker;
  private final long timeoutMillis;

  JudgeGuard(ClosedLoopValidationProperties properties, Clock clock) {
    this.breaker =
        new JudgeCircuitBreaker(
            properties.judgeBreakerThreshold(), properties.judgeBreakerOpenFor(), clock);
    this.timeoutMillis = properties.judgeTimeout().toMillis();
  }

  /**
   * Runs one judge pass.
   *
   * @param tier the judge tier asking
   * @param call the pass (one or several model calls)
   * @return what the pass returned
   * @throws JudgeUnavailableException on a timeout, a failure, an empty result or an open breaker
   */
  <T> T call(ValidationStepType tier, Supplier<T> call) {
    if (!breaker.tryAcquire()) {
      throw new JudgeUnavailableException(tier, "circuit breaker open", null);
    }
    CompletableFuture<T> pending = CompletableFuture.supplyAsync(call, JUDGE_CALLS);
    try {
      T result = pending.get(timeoutMillis, TimeUnit.MILLISECONDS);
      if (result == null) {
        throw new JudgeUnavailableException(tier, "the judge returned nothing", null);
      }
      breaker.onSuccess();
      return result;
    } catch (TimeoutException e) {
      throw failed(pending, tier, "timeout after " + timeoutMillis + " ms", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw failed(pending, tier, "interrupted", e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof JudgeUnavailableException already) {
        breaker.onFailure();
        throw already;
      }
      throw failed(pending, tier, String.valueOf(e.getCause()), e.getCause());
    } catch (JudgeUnavailableException empty) {
      breaker.onFailure();
      throw empty;
    }
  }

  private JudgeUnavailableException failed(
      CompletableFuture<?> pending, ValidationStepType tier, String cause, Throwable origin) {
    pending.cancel(true);
    breaker.onFailure();
    return new JudgeUnavailableException(tier, cause, origin);
  }
}
