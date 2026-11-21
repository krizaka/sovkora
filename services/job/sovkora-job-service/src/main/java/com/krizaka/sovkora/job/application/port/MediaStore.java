package com.krizaka.sovkora.job.application.port;

import java.util.Optional;

/** Outbound port: the sealed outputs of a job, under the configured storage root. */
public interface MediaStore {

  /** The plaintext of a job's output, or empty when there is none or it lies outside the root. */
  Optional<byte[]> read(String uploadDir, String userId, String jobId, String filename);

  /** Seals and stores one output; returns the URL it is served under, or empty on a no-op. */
  String save(String uploadDir, String userId, String jobId, byte[] data, String filename);
}
