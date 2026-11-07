package com.krizaka.sovkora.interceptor.enrichment;

import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.domain.model.chat.EngineChatOptions;
import com.krizaka.sovkora.core.domain.model.chat.EngineMessage;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import com.krizaka.sovkora.core.domain.model.security.Actor;
import com.krizaka.sovkora.core.domain.ports.outbound.KnowledgeService;
import com.krizaka.sovkora.core.infrastructure.config.CoreProperties;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * RAG (Retrieval-Augmented Generation) interceptor — enriches the prompt with relevant knowledge
 * base context retrieved via {@link KnowledgeService}.
 *
 * <p>Conditional on {@code orazaka.core.rag.enabled} being {@code true}.
 *
 * @see KnowledgeService#retrieveContext(Actor, String, int)
 */
class RagInterceptor implements PromptContextInterceptor {
  private final CoreProperties properties;
  private final KnowledgeService knowledgeService;

  RagInterceptor(CoreProperties properties, KnowledgeService knowledgeService) {
    this.properties = Objects.requireNonNull(properties, "CoreProperties must not be null");
    this.knowledgeService =
        Objects.requireNonNull(knowledgeService, "KnowledgeService must not be null");
  }

  @Override
  public EngineChatOptions preProcess(
      InternalChatRequest request,
      String promptText,
      List<EngineMessage> messages,
      EngineChatOptions options) {
    Optional.ofNullable(properties.rag())
        .filter(CoreProperties.RagConfig::enabled)
        .ifPresent(
            rag ->
                // Retrieval is made for the turn's actor and for nobody else (ADR-091): no verified
                // identity, no retrieval — the knowledge service filters by what the actor may
                // read.
                Actor.from(request.context())
                    .ifPresent(
                        actor ->
                            // A document is content someone wrote, not an instruction: an uploaded
                            // file can carry text aimed at the model, so it goes in as data
                            // (sovkora-engine#57).
                            ExternalContent.insert(
                                messages,
                                "knowledge sources",
                                knowledgeService.retrieveContext(actor, promptText, rag.topK()))));
    return options;
  }
}
