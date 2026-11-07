package com.krizaka.sovkora.automationservice.infrastructure.adapter.studio;

import com.krizaka.sovkora.automationservice.application.service.RunDispatchService;
import com.krizaka.sovkora.automationservice.domain.port.RunDispatcher;
import jakarta.annotation.PreDestroy;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Starts a recorded run on a virtual thread, so that a webhook is answered as soon as its run is
 * recorded and never waits on the engine. A start that fails here is not lost: the run is still
 * queued, and the next sweep starts it.
 */
@Component
class AsyncRunDispatcher implements RunDispatcher {

  private static final Logger logger = LoggerFactory.getLogger(AsyncRunDispatcher.class);

  private final RunDispatchService dispatch;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  AsyncRunDispatcher(RunDispatchService dispatch) {
    this.dispatch = Objects.requireNonNull(dispatch, "RunDispatchService cannot be null");
  }

  @Override
  public void submit(UUID runId) {
    executor.execute(
        () -> {
          try {
            dispatch.dispatch(runId);
          } catch (RuntimeException e) {
            logger.warn("run {} could not be started now: {}", runId, e.getClass().getSimpleName());
          }
        });
  }

  @PreDestroy
  void shutdown() {
    executor.shutdown();
  }
}
