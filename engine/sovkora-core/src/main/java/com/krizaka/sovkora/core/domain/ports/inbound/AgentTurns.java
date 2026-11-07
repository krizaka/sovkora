package com.krizaka.sovkora.core.domain.ports.inbound;

import com.krizaka.sovkora.core.domain.model.chat.AgentTurnRequest;
import com.krizaka.sovkora.core.domain.model.chat.AgentTurnResult;

/**
 * Inbound port (ADR-093 §4): one model turn of a client-run agent. The engine offers only the tools
 * the actor may use (ADR-091), routes by the turn's feature key (ADR-029) and returns the model's
 * tool calls instead of running them.
 */
public interface AgentTurns {

  /**
   * @param request the conversation, the client's tools and the actor
   * @return the text and the screened tool calls
   * @throws com.krizaka.sovkora.core.domain.model.security.AccessDeniedException when the actor may
   *     use none of the offered tools or is not allowed the turn
   */
  AgentTurnResult turn(AgentTurnRequest request);
}
