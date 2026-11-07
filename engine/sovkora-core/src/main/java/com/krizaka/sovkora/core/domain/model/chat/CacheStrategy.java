package com.krizaka.sovkora.core.domain.model.chat;

/**
 * What of a prompt a provider may keep in its prompt cache (ADR-094 slice 5, #173). The engine's
 * own vocabulary: the adapter maps it onto the provider library's option, so no library type
 * reaches the policy data.
 */
public enum CacheStrategy {
  /** Nothing is cached: every call is billed at the full input price. */
  NONE,
  /** The system prompt is the cached prefix. */
  SYSTEM_ONLY,
  /** The tool definitions and the system prompt are the cached prefix. */
  SYSTEM_AND_TOOLS
}
