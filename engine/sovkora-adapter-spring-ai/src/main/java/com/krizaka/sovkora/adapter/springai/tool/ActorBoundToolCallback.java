package com.krizaka.sovkora.adapter.springai.tool;

import com.krizaka.sovkora.core.application.security.ActorScope;
import com.krizaka.sovkora.core.domain.model.security.Actor;
import java.util.Objects;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * A tool callback that runs for one actor (ADR-091): while it runs, {@link ActorScope#current()} is
 * that actor, so a tool that reads the knowledge asks for what this actor may read.
 */
public final class ActorBoundToolCallback implements ToolCallback {

  private final Actor actor;
  private final ToolCallback delegate;

  /**
   * @param actor who the tool runs for
   * @param delegate the tool
   */
  public ActorBoundToolCallback(Actor actor, ToolCallback delegate) {
    this.actor = Objects.requireNonNull(actor, "actor");
    this.delegate = Objects.requireNonNull(delegate, "delegate");
  }

  @Override
  public ToolDefinition getToolDefinition() {
    return delegate.getToolDefinition();
  }

  @Override
  public String call(String toolInput) {
    return ActorScope.callAs(actor, () -> delegate.call(toolInput));
  }

  @Override
  public String call(String toolInput, ToolContext toolContext) {
    return ActorScope.callAs(actor, () -> delegate.call(toolInput, toolContext));
  }
}
