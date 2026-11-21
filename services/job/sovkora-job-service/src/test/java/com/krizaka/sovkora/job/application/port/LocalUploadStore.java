package com.krizaka.sovkora.job.application.port;

import com.krizaka.sovkora.job.infrastructure.support.AssetFileResolver;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

/** Test double of {@link UploadStore}: a plain directory. */
public record LocalUploadStore(Path root) implements UploadStore {

  @Override
  public Optional<File> find(String userId, String assetId) {
    return AssetFileResolver.resolve(root.toString(), userId, assetId);
  }
}
