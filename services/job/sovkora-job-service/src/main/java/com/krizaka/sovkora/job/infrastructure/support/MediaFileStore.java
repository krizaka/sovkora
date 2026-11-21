package com.krizaka.sovkora.job.infrastructure.support;

import com.krizaka.sovkora.engine.assets.application.service.EncryptedAssetService;
import com.krizaka.sovkora.engine.assets.domain.model.StorageRoot;
import com.krizaka.sovkora.engine.assets.domain.port.AssetKeys;
import com.krizaka.sovkora.engine.assets.domain.port.DurableAssetStore;
import com.krizaka.sovkora.engine.assets.infrastructure.config.MediaStorageProperties;
import com.krizaka.sovkora.job.application.port.MediaStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Saves generated media bytes to the user's job output directory.
 *
 * <p>Replaces the former static {@code JobMediaHelper.saveMediaToFile(...)}: file-writing
 * orchestration is an injectable component rather than a static utility [ERR-127].
 */
@Component
public class MediaFileStore implements MediaStore {

  private final EncryptedAssetService encryptedAssetService;

  private final DurableAssetStore durableAssetStore;
  private final MediaStorageProperties storage;

  /**
   * @param encryptedAssetService seals what this store writes; there is no plaintext branch
   * @param durableAssetStore the second copy that survives the container (none on a local-disk
   *     deployment)
   * @param storage the tenant segment of the durable key
   */
  public MediaFileStore(
      EncryptedAssetService encryptedAssetService,
      DurableAssetStore durableAssetStore,
      MediaStorageProperties storage) {
    this.encryptedAssetService =
        Objects.requireNonNull(encryptedAssetService, "EncryptedAssetService required");
    this.durableAssetStore =
        Objects.requireNonNull(durableAssetStore, "DurableAssetStore required");
    this.storage = Objects.requireNonNull(storage, "MediaStorageProperties required");
  }

  private static final Logger logger = LoggerFactory.getLogger(MediaFileStore.class);

  /**
   * Reads one stored output back, decrypted, or empty when there is none.
   *
   * @param uploadDir the base upload directory
   * @param userId the owner's id
   * @param jobId the job that produced it
   * @param filename the output's name
   * @return the plaintext bytes
   */
  @Override
  public java.util.Optional<byte[]> read(
      String uploadDir, String userId, String jobId, String filename) {
    try {
      Path root = StorageRoot.resolve(uploadDir).toAbsolutePath().normalize();
      Path filePath =
          root.resolve(userId).resolve(jobId).resolve("output").resolve(filename).normalize();
      if (!filePath.startsWith(root)) {
        return java.util.Optional.empty();
      }
      // A redeploy wipes the disk, not the durable store: bring the sealed envelope back first.
      if (!Files.isRegularFile(filePath) && !restore(userId, jobId, filename, filePath)) {
        return java.util.Optional.empty();
      }
      return java.util.Optional.of(encryptedAssetService.readAllBytes(filePath, false));
    } catch (IOException e) {
      logger.error("Failed to read a stored output", e);
      return java.util.Optional.empty();
    }
  }

  private boolean restore(String userId, String jobId, String filename, Path filePath)
      throws IOException {
    if (!durableAssetStore.enabled()) {
      return false;
    }
    try {
      return durableAssetStore.fetch(
          AssetKeys.jobFile(storage.tenant(), userId, jobId, filename), filePath);
    } catch (IllegalArgumentException e) {
      logger.debug(
          "Could not restore asset for job {} / {}: {}", jobId, filename, e.getMessage(), e);
      return false;
    }
  }

  /**
   * Saves media bytes to {@code <uploadDir>/<userId>/<jobId>/output/<filename>} and returns the
   * public URL path, or an empty string if the data is null/empty or the write fails.
   *
   * @param uploadDir the base upload directory
   * @param userId the user ID
   * @param jobId the job ID
   * @param data the raw media bytes
   * @param filename the output filename (e.g. "video.mp4", "image.png")
   * @return the relative URL path, or empty string on no-op/failure
   */
  @Override
  public String save(String uploadDir, String userId, String jobId, byte[] data, String filename) {
    if (data == null || data.length == 0) {
      return "";
    }
    try {
      // Closed format first (krizaka-internal#4361): each id and the file name must be one plain
      // segment, the same rule as the durable key, so `..`, a separator or a line break is refused
      // before anything touches the disk.
      String durableKey = AssetKeys.jobFile(storage.tenant(), userId, jobId, filename);
      Path root = StorageRoot.resolve(uploadDir).toAbsolutePath().normalize();
      Path dir = root.resolve(userId).resolve(jobId).resolve("output").normalize();
      Path filePath = dir.resolve(filename).normalize();
      // Containment of the directory itself, not only of the file: `createDirectories` is a write
      // too, and a file that lands back inside the root says nothing about the directories made on
      // the way there.
      if (!dir.startsWith(root) || !filePath.startsWith(dir)) {
        logger.error("Refused to write outside the upload root");
        return "";
      }

      Files.createDirectories(dir);
      // Encrypted at rest (ADR-054). The envelope is written atomically into place, so a process
      // killed mid-write leaves no half-sealed file for the next reader to fail on.
      encryptedAssetService.write(filePath, data);
      if (durableAssetStore.enabled()) {
        // The sealed envelope, not the plaintext. A copy that could not be made is a failed save:
        // an output reported stored here and gone at the next deploy is the bug this prevents.
        durableAssetStore.put(durableKey, filePath);
      }

      logger.info("Saved generated media file to: {}", filePath);
      // The owner is deliberately absent from the URL. It is read from the job record when the
      // asset is served, so the link cannot carry — or be edited to carry — an authorisation claim.
      return "/api/v1/assets/" + jobId + "/" + filename;
    } catch (IOException | IllegalArgumentException e) {
      logger.error("Failed to save generated media to file", e);
      return "";
    }
  }
}
