package com.krizaka.sovkora.assets.domain.port;

import java.util.regex.Pattern;

/**
 * The one layout of the durable store: {@code t/<tenant>/u/<user>/jobs/<jobId>/<file>}.
 *
 * <p>Stable ids only — never an e-mail, handle or name — so that deleting a user or a tenant is
 * deleting a prefix. Every segment is validated: a key is built from request-adjacent values and
 * must not be able to climb out of its owner's prefix.
 */
public final class AssetKeys {

  private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

  private AssetKeys() {}

  /**
   * The key of one job output.
   *
   * @param tenant the tenant id (a deployment with one tenant uses one fixed id)
   * @param userId the owner of the job
   * @param jobId the job
   * @param file the file name within the job's output
   * @return the object key
   * @throws IllegalArgumentException if a segment is not a plain id or file name
   */
  public static String jobFile(String tenant, String userId, String jobId, String file) {
    return jobPrefix(tenant, userId, jobId) + segment("file", file);
  }

  /**
   * The prefix holding every file of a job; ends with {@code /}.
   *
   * @param tenant the tenant id
   * @param userId the owner of the job
   * @param jobId the job
   * @return the prefix
   */
  public static String jobPrefix(String tenant, String userId, String jobId) {
    return "t/"
        + segment("tenant", tenant)
        + "/u/"
        + segment("user", userId)
        + "/jobs/"
        + segment("job", jobId)
        + "/";
  }

  /** The pseudo job every upload of a user lives under; uploads belong to no job. */
  private static final String UPLOADS = "uploads";

  /**
   * The key of one file a user uploaded.
   *
   * @param tenant the tenant id
   * @param userId the uploader
   * @param file the stored file name ({@code <assetId><extension>})
   * @return the object key
   */
  public static String uploadFile(String tenant, String userId, String file) {
    return uploadPrefix(tenant, userId) + segment("file", file);
  }

  /**
   * The prefix holding a user's uploads; ends with {@code /}.
   *
   * @param tenant the tenant id
   * @param userId the uploader
   * @return the prefix
   */
  public static String uploadPrefix(String tenant, String userId) {
    return jobPrefix(tenant, userId, UPLOADS);
  }

  /**
   * Everything a user has; ends with {@code /}.
   *
   * @param tenant the tenant id
   * @param userId the user
   * @return the prefix
   */
  public static String userPrefix(String tenant, String userId) {
    return tenantUsersPrefix(tenant) + segment("user", userId) + "/";
  }

  /**
   * The prefix whose children are the tenant's users; ends with {@code /}.
   *
   * @param tenant the tenant id
   * @return the prefix
   */
  public static String tenantUsersPrefix(String tenant) {
    return "t/" + segment("tenant", tenant) + "/u/";
  }

  private static String segment(String what, String value) {
    if (value == null || value.contains("..") || !SEGMENT.matcher(value).matches()) {
      throw new IllegalArgumentException("not a valid " + what + " segment for a storage key");
    }
    return value;
  }
}
