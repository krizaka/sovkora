package com.krizaka.sovkora.job.infrastructure.support;

import com.krizaka.sovkora.engine.assets.domain.model.StorageRoot;
import com.krizaka.sovkora.job.application.port.UploadStore;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Local-disk adapter of {@link UploadStore}, rooted at the configured upload directory. */
@Component
class FileUploadStoreAdapter implements UploadStore {

  private final Path root;

  FileUploadStoreAdapter(
      @Value("${spring.servlet.multipart.location:var/sovkora-uploads}") String uploadDir) {
    this.root = StorageRoot.resolve(uploadDir);
  }

  @Override
  public Path root() {
    return root;
  }

  @Override
  public Optional<File> find(String userId, String assetId) {
    return AssetFileResolver.resolve(root.toString(), userId, assetId);
  }
}
