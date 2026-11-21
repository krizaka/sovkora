package com.krizaka.sovkora.engine.assets.domain.model;

import java.nio.file.Path;

/**
 * The storage root, taken from configuration and from nothing else.
 *
 * <p>The configured value is required: a blank or missing one fails at startup with a message that
 * names it. A relative value is anchored on the process working directory, which is a fact of the
 * deployment, never on a file found by walking up the tree.
 */
public final class StorageRoot {

  private StorageRoot() {}

  /** The absolute, normalized root; an {@link IllegalStateException} when the value is blank. */
  public static Path resolve(String configured) {
    if (configured == null || configured.isBlank()) {
      throw new IllegalStateException(
          "The storage root is not configured: set the upload directory (UPLOAD_DIR)");
    }
    return Path.of(configured).toAbsolutePath().normalize();
  }

  /** The same root, as a string. */
  public static String resolveToString(String configured) {
    return resolve(configured).toString();
  }
}
