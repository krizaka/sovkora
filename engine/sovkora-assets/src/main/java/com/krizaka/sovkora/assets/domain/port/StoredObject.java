package com.krizaka.sovkora.assets.domain.port;

import java.time.Instant;

/**
 * One entry of {@link DurableAssetStore#list}: a file with the instant it last changed, or a
 * directory (its key ends with {@code /}).
 *
 * @param key the full object key
 * @param lastModified when it last changed; the store's own clock
 */
public record StoredObject(String key, Instant lastModified) {

  /** Whether this entry is a directory rather than a file. */
  public boolean directory() {
    return key.endsWith("/");
  }
}
