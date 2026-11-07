package com.krizaka.sovkora.core.domain.ports.outbound;

import com.krizaka.sovkora.core.domain.model.chat.EngineTool;
import com.krizaka.sovkora.core.domain.model.security.AccessDeniedException;
import com.krizaka.sovkora.core.domain.model.security.Actor;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Registry interface for local Java methods to be used as tools by the AI models. Pure Java: the
 * adapter turns each registration into the model library's tool (ADR-092 §4 rule 1).
 *
 * <p><b>A tool declares who may use it, and nobody gets the registry's tools without being
 * named</b> (ADR-091): there is no listing without an {@link Actor}, and the tools it returns are
 * the ones that actor may use, bound to that actor. The roles come from the tool's configuration
 * row when an administrator set one, else from what the tool declared when it registered; a tool
 * that declared nothing is for administrators only.
 */
public interface ToolRegistry {

  /** What a tool that declared nothing requires: least privilege. */
  Set<String> LEAST_PRIVILEGE = Set.of("ROLE_ADMIN");

  /**
   * Registers a local Java method as a tool and declares the roles any of which may use it.
   *
   * @param name Name of the function.
   * @param description Description of what the function does.
   * @param inputType Input class.
   * @param function The actual implementation.
   * @param requiredRoles The roles, any of which may use the tool; never empty.
   * @param <I> Input type.
   * @param <O> Output type.
   */
  <I, O> void registerTool(
      String name,
      String description,
      Class<I> inputType,
      Function<I, O> function,
      Set<String> requiredRoles);

  /**
   * Registers a tool that declares no permission: it is for administrators only until one is set.
   *
   * @param name Name of the function.
   * @param description Description of what the function does.
   * @param inputType Input class.
   * @param function The actual implementation.
   * @param <I> Input type.
   * @param <O> Output type.
   */
  default <I, O> void registerTool(
      String name, String description, Class<I> inputType, Function<I, O> function) {
    registerTool(name, description, inputType, function, LEAST_PRIVILEGE);
  }

  /**
   * The tools the actor may use, bound to the actor (what they run, they run as the actor).
   *
   * @param actor who the tools are for; {@code null} gets none
   * @return the permitted tools, possibly empty
   */
  List<EngineTool> getRegisteredTools(Actor actor);

  /**
   * One tool, if the actor may use it.
   *
   * @param actor who asks; {@code null} is refused
   * @param name the tool's name
   * @return the tool, bound to the actor
   * @throws IllegalArgumentException when no such tool is registered
   * @throws AccessDeniedException when there is no actor or the actor holds none of the roles
   */
  EngineTool authorize(Actor actor, String name);
}
