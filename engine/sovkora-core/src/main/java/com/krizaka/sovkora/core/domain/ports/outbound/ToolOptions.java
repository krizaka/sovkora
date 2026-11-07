package com.krizaka.sovkora.core.domain.ports.outbound;

import com.krizaka.sovkora.core.domain.model.chat.EngineChatOptions;
import com.krizaka.sovkora.core.domain.model.chat.EngineTool;
import java.util.List;

/**
 * Outbound port: offers tools to the model by way of the call's options. How a provider is told
 * about its tools (Ollama, OpenAI, ...) is the adapter's business, not the interceptor's (ADR-092
 * §4 rule 1).
 */
public interface ToolOptions {

  /**
   * The options with the tools attached. Settings the provider's options carry (model, temperature,
   * completion cap) are kept; options the adapter does not know are returned as they came.
   *
   * @param options the call's options, or {@code null}
   * @param tools the tools to offer
   * @return the options offering the tools, or {@code null} when {@code options} was
   */
  EngineChatOptions withTools(EngineChatOptions options, List<EngineTool> tools);
}
