package com.krizaka.sovkora.assets.infrastructure.storage;

import com.krizaka.sovkora.assets.domain.port.DurableAssetStore;
import java.nio.file.Path;

/** The local-disk-only deployment: no second copy, nothing to fetch back. The default. */
public final class NoDurableAssetStore implements DurableAssetStore {

  @Override
  public boolean enabled() {
    return false;
  }

  @Override
  public void put(String key, Path file) {
    // The disk is the only copy.
  }

  @Override
  public boolean fetch(String key, Path target) {
    return false;
  }

  @Override
  public void delete(String key) {
    // Nothing is kept elsewhere.
  }
}
