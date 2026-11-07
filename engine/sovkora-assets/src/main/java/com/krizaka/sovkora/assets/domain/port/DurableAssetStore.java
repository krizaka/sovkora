package com.krizaka.sovkora.assets.domain.port;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Where an asset lives when the container's own disk is not enough to keep it.
 *
 * <p>The disk stays the working copy: services write and serve from it, and {@code Range} is
 * answered from it. This port is the second copy that survives the container — written once, after
 * the encrypted envelope is sealed, and fetched back onto the disk when a read finds it gone.
 * <b>Only sealed envelopes cross it</b>: the store never sees plaintext, whichever adapter backs
 * it.
 *
 * <p>Keys follow one layout, built by {@link AssetKeys}: {@code
 * t/<tenant>/u/<user>/jobs/<jobId>/<file>}. Deleting a user or a tenant is deleting a prefix.
 */
public interface DurableAssetStore {

  /** Whether a durable copy is kept at all. {@code false} is the local-disk-only deployment. */
  boolean enabled();

  /**
   * Stores the file under the key, replacing any previous copy.
   *
   * @param key the object key, see {@link AssetKeys}
   * @param file the sealed envelope on disk
   * @throws IOException if the copy could not be made — the caller must not report the asset stored
   */
  void put(String key, Path file) throws IOException;

  /**
   * Brings the object back onto the disk, atomically.
   *
   * @param key the object key
   * @param target where the file lands; its parent directories are created
   * @return {@code true} if the object existed and {@code target} now holds it, {@code false} if
   *     there is no such object
   * @throws IOException if the object exists but could not be read
   */
  boolean fetch(String key, Path target) throws IOException;

  /**
   * Deletes the object, or everything under the key when it ends with {@code /}. Absent is not an
   * error: a purge that ran twice is still a purge.
   *
   * @param key an object key, or a prefix ending in {@code /}
   * @throws IOException if the store refused
   */
  void delete(String key) throws IOException;

  /**
   * The direct children of a prefix: files (with their last change) and directories (keys ending
   * with {@code /}). Absent is empty, not an error. The default is for a store that keeps nothing.
   *
   * @param prefix a prefix ending in {@code /}
   * @return the children, in no particular order
   * @throws IOException if the store refused
   */
  default List<StoredObject> list(String prefix) throws IOException {
    return List.of();
  }
}
