package com.krizaka.sovkora.business.api;

/**
 * Execution mode of an {@link Intention}: {@code SYNC} for low-latency interactive responses
 * (including token streaming), {@code ASYNC} for heavy/deferred work dispatched to a worker.
 */
public enum ExecutionMode {
  SYNC,
  ASYNC
}
