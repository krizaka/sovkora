package com.krizaka.sovkora.job.infrastructure.config;

import com.krizaka.sovkora.job.domain.model.PodTokenPolicy;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The {@code sovkora.jobs.pod-token} keys (krizaka-internal#3645).
 *
 * <p>The signing key is derived from {@code signing-secret} under a fixed label, so it is never the
 * session secret itself even when the deployment gives the same value: a pod token verifies nowhere
 * but here, and no session or {@code SERVICE} token verifies as a pod token.
 *
 * @param signingSecret the job service's own secret for pod tokens ({@code JOB_POD_TOKEN_SECRET}),
 *     at least 32 characters
 * @param maxLifetime the longest a pod token lives: the longest pod the launcher starts, plus its
 *     boot
 */
@ConfigurationProperties(prefix = "sovkora.jobs.pod-token")
public record PodTokenProperties(
    String signingSecret, @DefaultValue("PT12H30M") Duration maxLifetime) {

  static final int MINIMUM_SECRET_LENGTH = 32;
  private static final String LABEL = "sovkora.job.gpu-pod-token.v1";
  private static final String HMAC_SHA256 = "HmacSHA256";

  public PodTokenProperties {
    if (signingSecret == null || signingSecret.length() < MINIMUM_SECRET_LENGTH) {
      throw new IllegalArgumentException(
          "sovkora.jobs.pod-token.signing-secret must be at least 32 characters");
    }
  }

  /**
   * @return the validated lifetime policy
   */
  public PodTokenPolicy toPolicy() {
    return new PodTokenPolicy(maxLifetime);
  }

  /**
   * The HS256 key pod tokens are signed and verified with: HMAC-SHA256 of the label under the
   * secret.
   *
   * @return the key
   */
  public SecretKey signingKey() {
    try {
      Mac mac = Mac.getInstance(HMAC_SHA256);
      mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
      return new SecretKeySpec(mac.doFinal(LABEL.getBytes(StandardCharsets.UTF_8)), HMAC_SHA256);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("cannot derive the pod token key", e);
    }
  }

  /** Hides the secret from logs, actuator output and exception messages. */
  @Override
  public String toString() {
    return "PodTokenProperties[signingSecret=<redacted>, maxLifetime=" + maxLifetime + "]";
  }
}
