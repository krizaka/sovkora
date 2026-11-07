package com.krizaka.sovkora.core.domain.model;

/**
 * Enumeration of AI media categories used across the system to classify model operations, route
 * requests, and filter catalogs.
 *
 * <p>These constants replace scattered string literals ("chat", "image", "audio", "video",
 * "speech", "theme") throughout the monorepo, providing compile-time safety and a single source of
 * truth.
 *
 * @see com.krizaka.sovkora.core.domain.ports.outbound.ModelCatalogProvider
 */
public enum MediaCategory {

  /** Text-based chat/conversational AI generation. */
  CHAT("chat"),

  /** Image generation (DALL-E, Stable Diffusion, etc.). */
  IMAGE("image"),

  /** Audio processing and generation. */
  AUDIO("audio"),

  /** Video generation (Stable Video Diffusion, etc.). */
  VIDEO("video"),

  /** Text-to-speech synthesis. */
  SPEECH("speech"),

  /** Text embeddings (semantic search, recommendations). */
  EMBEDDING("embedding"),

  /** UI theme accent configuration (dynamic catalog). */
  THEME("theme");

  private final String value;

  MediaCategory(String value) {
    this.value = value;
  }

  /** Returns the lowercase string representation used in database and API payloads. */
  public String value() {
    return value;
  }

  @Override
  public String toString() {
    return value;
  }
}
