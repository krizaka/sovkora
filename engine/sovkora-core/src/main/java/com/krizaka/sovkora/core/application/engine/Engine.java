package com.krizaka.sovkora.core.application.engine;

import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.application.pipeline.DynamicPipelineExecutor;
import com.krizaka.sovkora.core.application.pipeline.EnginePipelineBridge;
import com.krizaka.sovkora.core.application.pipeline.EngineStreamBridge;
import com.krizaka.sovkora.core.domain.ports.outbound.ChatInvocation;
import com.krizaka.sovkora.core.domain.ports.outbound.DomainEventPublisher;
import com.krizaka.sovkora.core.domain.ports.outbound.ModelCatalogProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.SpeechSynthesis;
import com.krizaka.sovkora.core.domain.ports.outbound.UserCredentialsProvider;
import com.krizaka.sovkora.core.infrastructure.config.CoreProperties;
import java.util.List;

/**
 * Concrete, final implementation of {@link AbstractEngine}.
 *
 * <p>This is the sole permitted subclass of the sealed {@link AbstractEngine} hierarchy. It
 * delegates all orchestration logic to the abstract parent and serves as the default production
 * engine.
 */
public final class Engine extends AbstractEngine {

  /**
   * Primary constructor for Engine with direct Spring AI dependency injection.
   *
   * @param chatInvocation The port that sends a prepared prompt to a model.
   * @param speech The port that turns text into speech audio.
   * @param properties Core engine configuration properties.
   * @param interceptors Ordered list of context interceptors.
   * @param orchestrationPipeline The orchestration pipeline (nullable).
   * @param eventPublisher Event publisher for domain events.
   * @param modelCatalogProvider The model catalog provider to resolve active models dynamically.
   * @param pipelineBridge The pipeline compilation bridge component.
   * @param streamBridge The reactive streaming bridge component.
   * @param credentialsProvider Outbound port for credentials lookup.
   */
  public Engine(
      ChatInvocation chatInvocation,
      SpeechSynthesis speech,
      CoreProperties properties,
      List<PromptContextInterceptor> interceptors,
      DynamicPipelineExecutor pipelineExecutor,
      DomainEventPublisher eventPublisher,
      ModelCatalogProvider modelCatalogProvider,
      EnginePipelineBridge pipelineBridge,
      EngineStreamBridge streamBridge,
      UserCredentialsProvider credentialsProvider) {
    this(
        chatInvocation,
        speech,
        properties,
        interceptors,
        pipelineExecutor,
        eventPublisher,
        new EngineInfrastructure(
            modelCatalogProvider, pipelineBridge, streamBridge, credentialsProvider));
  }

  /**
   * Constructor with the infrastructure already grouped.
   *
   * @param chatInvocation The port that sends a prepared prompt to a model.
   * @param speech The port that turns text into speech audio.
   * @param properties Core engine configuration properties.
   * @param interceptors Ordered list of context interceptors.
   * @param pipelineExecutor The orchestration pipeline (nullable).
   * @param eventPublisher Publisher for domain events.
   * @param infra The grouped infrastructure dependencies.
   */
  public Engine(
      ChatInvocation chatInvocation,
      SpeechSynthesis speech,
      CoreProperties properties,
      List<PromptContextInterceptor> interceptors,
      DynamicPipelineExecutor pipelineExecutor,
      DomainEventPublisher eventPublisher,
      EngineInfrastructure infra) {
    super(
        chatInvocation,
        speech,
        properties != null ? properties.defaultProvider() : null,
        interceptors,
        pipelineExecutor,
        eventPublisher,
        infra);
  }
}
