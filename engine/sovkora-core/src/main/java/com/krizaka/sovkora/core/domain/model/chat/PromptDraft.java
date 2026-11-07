package com.krizaka.sovkora.core.domain.model.chat;

import java.util.List;

/**
 * A turn's messages and options before the interceptors and the tools: the engine's own view of
 * what the prompt-assembly port compiled from the request (#121).
 *
 * @param messages the history and the current prompt
 * @param options the options resolved for the provider, or {@code null}
 */
public record PromptDraft(List<EngineMessage> messages, EngineChatOptions options) {}
