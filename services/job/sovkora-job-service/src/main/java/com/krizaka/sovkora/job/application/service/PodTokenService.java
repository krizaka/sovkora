package com.krizaka.sovkora.job.application.service;

import com.krizaka.sovkora.job.domain.model.IssuedPodToken;
import com.krizaka.sovkora.job.domain.model.PodKey;
import com.krizaka.sovkora.job.domain.model.PodTokenPolicy;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Mints, checks and revokes a GPU pod's own job service token (krizaka-internal#3645).
 *
 * <p>Before this, a RunPod pod carried a {@code SERVICE} token minted on the Mac with the shared
 * identity secret: every service's {@code /internal/v1/**} accepted it until it expired, and
 * nothing could end it sooner. A pod token is addressed to the job service only, carries the pod
 * role and never {@code SERVICE}, names its pod, and is accepted only while its row here is live:
 * the launcher revokes it when it terminates the pod, and its sweep revokes the token of a pod its
 * own watchdog ended.
 *
 * <p>Every write is one statement, so no transaction spans the signing (AGENTS.md §5, ERR-109).
 */
@Service
public class PodTokenService {

  private static final Logger logger = LoggerFactory.getLogger(PodTokenService.class);

  /** How long a dead row is kept after its expiry before the purge deletes it. */
  static final Duration RETENTION = Duration.ofDays(1);

  // One live token per pod, atomically: a pod that already holds one gets the new id in its row, so
  // the old token's id is no longer recorded and the old token is refused from this statement on.
  private static final String MINT =
      """
      INSERT INTO gpu_pod_tokens (jti, pod_key, issued_at, expires_at) VALUES (?, ?, ?, ?)
      ON CONFLICT (pod_key) WHERE revoked_at IS NULL
      DO UPDATE SET jti = EXCLUDED.jti, issued_at = EXCLUDED.issued_at, expires_at = EXCLUDED.expires_at
      """;

  private final JdbcTemplate jdbc;
  private final JwtEncoder encoder;
  private final PodTokenPolicy policy;
  private final Clock clock;

  /**
   * @param jdbc the job database, where the revocation rows live
   * @param encoder the pod token signer ({@code podTokenEncoder}), whose key no other service holds
   * @param policy the lifetime cap
   * @param clock the source of {@code iat}, {@code exp} and "now"
   */
  public PodTokenService(
      JdbcTemplate jdbc,
      @Qualifier("podTokenEncoder") JwtEncoder encoder,
      PodTokenPolicy policy,
      Clock clock) {
    this.jdbc = jdbc;
    this.encoder = encoder;
    this.policy = policy;
    this.clock = clock;
  }

  /**
   * Mints the token of one pod, revoking any token that pod held before.
   *
   * @param pod the pod
   * @param requested the pod's lifetime cap; capped to the policy's maximum
   * @return the token and what its row records
   */
  public IssuedPodToken mint(PodKey pod, Duration requested) {
    Instant now = clock.instant();
    Instant expiresAt = now.plus(policy.lifetime(requested));
    UUID jti = UUID.randomUUID();
    record(pod, jti, now, expiresAt);
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(pod.subject())
            .audience(List.of(PodTokenPolicy.AUDIENCE))
            .claim("roles", List.of(PodTokenPolicy.ROLE))
            .id(jti.toString())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).keyId(PodTokenPolicy.KEY_ID).build();
    String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    logger.info("minted the job service token of pod {} until {}", pod.value(), expiresAt);
    return new IssuedPodToken(token, jti, pod, expiresAt);
  }

  private void record(PodKey pod, UUID jti, Instant now, Instant expiresAt) {
    jdbc.update(MINT, jti, pod.value(), Timestamp.from(now), Timestamp.from(expiresAt));
  }

  /**
   * Whether a token is still accepted: its row exists, names this pod, is not revoked and has not
   * expired. A token this table never recorded is refused.
   *
   * @param jti the token's id
   * @param pod the pod its subject names
   * @return {@code true} while the token is live
   */
  public boolean isLive(UUID jti, PodKey pod) {
    Integer live =
        jdbc.queryForObject(
            "SELECT count(*) FROM gpu_pod_tokens WHERE jti = ? AND pod_key = ?"
                + " AND revoked_at IS NULL AND expires_at > ?",
            Integer.class,
            jti,
            pod.value(),
            Timestamp.from(clock.instant()));
    return live != null && live > 0;
  }

  /**
   * Revokes the token of a pod that ended: from now on the job service refuses it.
   *
   * @param pod the terminated pod
   * @return how many live tokens were revoked (0 or 1)
   */
  public int revoke(PodKey pod) {
    int revoked =
        jdbc.update(
            "UPDATE gpu_pod_tokens SET revoked_at = ? WHERE pod_key = ? AND revoked_at IS NULL",
            Timestamp.from(clock.instant()),
            pod.value());
    if (revoked > 0) {
      logger.info("revoked the job service token of pod {}", pod.value());
    }
    return revoked;
  }

  /**
   * Revokes the live token of every pod that no longer runs (one its watchdog or the spend guard
   * ended without the launcher).
   *
   * @param running the pods that still run; their tokens are kept
   * @return the pods whose token was revoked
   */
  public List<PodKey> sweep(Collection<PodKey> running) {
    String[] keep = running.stream().map(PodKey::value).toArray(String[]::new);
    List<PodKey> revoked =
        jdbc.query(
            connection -> {
              var statement =
                  connection.prepareStatement(
                      "UPDATE gpu_pod_tokens SET revoked_at = ? WHERE revoked_at IS NULL"
                          + " AND NOT (pod_key = ANY (?)) RETURNING pod_key");
              statement.setTimestamp(1, Timestamp.from(clock.instant()));
              statement.setArray(2, connection.createArrayOf("varchar", keep));
              return statement;
            },
            (row, n) -> new PodKey(row.getString(1)));
    revoked.forEach(pod -> logger.info("swept the job service token of ended pod {}", pod.value()));
    return revoked;
  }

  /**
   * Deletes the rows of tokens that expired more than a day ago: the table stays bounded.
   *
   * @return how many rows were deleted
   */
  public int purge() {
    return jdbc.update(
        "DELETE FROM gpu_pod_tokens WHERE expires_at < ?",
        Timestamp.from(clock.instant().minus(RETENTION)));
  }
}
