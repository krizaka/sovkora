package com.krizaka.sovkora.tools.infrastructure.adapter.mcp;

import com.krizaka.sovkora.core.domain.ports.outbound.ToolRegistry;
import com.krizaka.sovkora.tools.domain.model.law.LookupStatuteRequest;
import jakarta.annotation.PostConstruct;

/**
 * Offers {@link LookupStatuteTool} to the models through the one {@link ToolRegistry}. The tool is
 * registered only when {@code orazaka.law.lookup.enabled=true}: a registered tool is attached to
 * every non-streaming request, and the corpus answers nothing while {@code law.corpus.enabled} is
 * false.
 */
public final class LookupStatuteToolRegistration {

  private final ToolRegistry registry;
  private final LookupStatuteTool tool;

  /**
   * Creates the registration.
   *
   * @param registry the tool registry
   * @param tool the tool
   */
  public LookupStatuteToolRegistration(ToolRegistry registry, LookupStatuteTool tool) {
    this.registry = registry;
    this.tool = tool;
  }

  /** Registers the tool once the bean is built. */
  @PostConstruct
  void register() {
    registry.registerTool(
        LookupStatuteTool.NAME,
        LookupStatuteTool.DESCRIPTION,
        LookupStatuteRequest.class,
        tool,
        java.util.Set.of("ROLE_USER"));
  }
}
