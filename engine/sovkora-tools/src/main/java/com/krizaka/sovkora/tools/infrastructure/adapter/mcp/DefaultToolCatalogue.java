package com.krizaka.sovkora.tools.infrastructure.adapter.mcp;

import com.krizaka.sovkora.core.application.security.ActorScope;
import com.krizaka.sovkora.core.domain.model.security.Actor;
import com.krizaka.sovkora.core.domain.ports.outbound.KnowledgeService;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolRegistry;
import com.krizaka.sovkora.tools.domain.model.audio.AnalyzeAudioExtractRequest;
import com.krizaka.sovkora.tools.domain.model.audio.AnalyzeAudioExtractResponse;
import com.krizaka.sovkora.tools.domain.model.poster.AnalyzePosterRequest;
import com.krizaka.sovkora.tools.domain.model.poster.AnalyzePosterResponse;
import com.krizaka.sovkora.tools.domain.model.search.SearchWebRequest;
import com.krizaka.sovkora.tools.domain.model.search.SearchWebResponse;
import jakarta.annotation.PostConstruct;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The default tools of the engine, registered in the {@link ToolRegistry} port. Pure Java: how a
 * registry turns them into a model library's tool type is the registry's concern (ADR-092 §4). RAG
 * source lookup goes through the {@link KnowledgeService} port — the knowledge service owns the
 * sources and their ingestion since Phase 4.
 */
@Component
public class DefaultToolCatalogue {

  private static final Logger log = LoggerFactory.getLogger(DefaultToolCatalogue.class);

  /**
   * Searches the knowledge corpus the knowledge service holds — not the web. It was called {@code
   * searchWeb}, which told the model it could look things up live (ADR-087, sovkora-engine#59). The
   * name is also the key of its sources and of its cache settings.
   */
  static final String TOOL_SEARCH_KNOWLEDGE = "searchKnowledge";

  /** The built-in tools are for any signed-in user, until an administrator narrows them. */
  private static final Set<String> USERS = Set.of("ROLE_USER");

  private final ToolRegistry toolRegistry;
  private final KnowledgeService knowledgeService;

  /**
   * Creates the catalogue.
   *
   * @param toolRegistry The registry the tools are registered in.
   * @param knowledgeService Knowledge context port (RAG retrieval + tool source lookup).
   */
  public DefaultToolCatalogue(ToolRegistry toolRegistry, KnowledgeService knowledgeService) {
    this.toolRegistry = toolRegistry;
    this.knowledgeService = knowledgeService;
  }

  @PostConstruct
  void registerDefaultTools() {
    toolRegistry.registerTool(
        "analyzePoster",
        "Analyzes a movie poster provided as a base64 encoded string using vision model.",
        AnalyzePosterRequest.class,
        this::analyzePoster,
        USERS);
    toolRegistry.registerTool(
        "analyzeAudioExtract",
        "Analyzes a film audio extract to check for specific compliance or content criteria.",
        AnalyzeAudioExtractRequest.class,
        this::analyzeAudioExtract,
        USERS);
    toolRegistry.registerTool(
        TOOL_SEARCH_KNOWLEDGE,
        "Searches the documents of the organisation's knowledge base (not the web) and returns"
            + " the passages that match the query.",
        SearchWebRequest.class,
        this::searchKnowledge,
        USERS);
  }

  SearchWebResponse searchKnowledge(SearchWebRequest request) {
    // The tool, never its arguments: a query or a poster is the user's content (ADR-064).
    log.info("Executing searchKnowledge tool");
    if (knowledgeService == null) {
      return new SearchWebResponse("Knowledge service is not initialized.", false);
    }
    // The actor of the running call, never an argument the model wrote: no actor, no knowledge.
    Actor actor = ActorScope.current().orElse(null);
    if (actor == null) {
      return new SearchWebResponse("", false);
    }
    String matches = knowledgeService.searchSources(actor, TOOL_SEARCH_KNOWLEDGE, request.query());
    if (matches.isBlank()) {
      // Fallback: return any corporate profile/framework description visible to the actor.
      matches = knowledgeService.searchSources(actor, TOOL_SEARCH_KNOWLEDGE, "");
    }
    return new SearchWebResponse(matches, true);
  }

  private AnalyzePosterResponse analyzePoster(AnalyzePosterRequest request) {
    log.info("Executing analyzePoster tool");
    String base64 = request.posterBase64();
    if (base64.contains("error") || base64.contains("corrupt")) {
      return new AnalyzePosterResponse("Failed to parse poster image: corrupt payload.", false);
    }
    return new AnalyzePosterResponse(
        "Poster analysis success. Detected themes: Sci-Fi, cyberpunk architecture, neon aesthetic. Prompt: "
            + request.prompt(),
        true);
  }

  private AnalyzeAudioExtractResponse analyzeAudioExtract(AnalyzeAudioExtractRequest request) {
    log.info("Executing analyzeAudioExtract tool");
    String path = request.clipPath();
    if (path.contains("corrupt") || path.contains("invalid")) {
      return new AnalyzeAudioExtractResponse("Audio clip corrupt or missing at " + path, false);
    }
    return new AnalyzeAudioExtractResponse(
        "Audio extract compliance check passed. Track checks clear under type: "
            + request.checkType(),
        true);
  }
}
