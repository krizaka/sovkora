package com.krizaka.sovkora.persistence.api.domain.ports.inbound;

/**
 * Inbound port exposing <b>dynamic runtime config</b> (ADR-031) read from the DB ({@code
 * orazaka_runtime_config}), Spring-first and typed. Each getter falls back to the supplied default
 * when the key is absent — so removing a key reverts cleanly to the code default.
 */
public interface RuntimeConfigProvider {

  /** Resolves a boolean config value, or {@code defaultValue} if the key is absent. */
  boolean getBoolean(String key, boolean defaultValue);

  /** Resolves an int config value, or {@code defaultValue} if absent or unparseable. */
  int getInt(String key, int defaultValue);

  /** Resolves a string config value, or {@code defaultValue} if the key is absent. */
  String getString(String key, String defaultValue);
}
