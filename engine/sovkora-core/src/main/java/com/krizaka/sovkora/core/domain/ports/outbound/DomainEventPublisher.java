package com.krizaka.sovkora.core.domain.ports.outbound;

/**
 * Outbound port: announces a domain event ({@code ChatCompletedEvent}, {@code
 * JobStatusChangedEvent}, {@code PipelineStageEvent}) to whoever listens. The core publishes; the
 * host decides how events travel (sovkora-engine#121: the core is plain Java, no Spring).
 */
@FunctionalInterface
public interface DomainEventPublisher {

  /**
   * Announces an event.
   *
   * @param event the event, never {@code null}
   */
  void publish(Object event);
}
