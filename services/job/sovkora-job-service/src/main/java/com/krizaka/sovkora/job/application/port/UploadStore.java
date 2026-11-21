package com.krizaka.sovkora.job.application.port;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

/** Outbound port: where the actors' uploads live, as configured, and how one is found. */
public interface UploadStore {

  /** The absolute storage root, from configuration. */
  Path root();

  /** The file of an uploaded asset inside its owner's directory, if there is one. */
  Optional<File> find(String userId, String assetId);
}
