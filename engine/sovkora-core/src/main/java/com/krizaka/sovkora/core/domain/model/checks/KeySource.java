package com.krizaka.sovkora.core.domain.model.checks;

/** Whose key a provider call was made with: the actor's own, or the platform's. */
public enum KeySource {
  /** The actor's own key, used when present. */
  USER,
  /** The platform key from the admin settings. */
  PLATFORM
}
