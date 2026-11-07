package com.krizaka.sovkora.interceptor.cache;

import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.domain.model.CachedResponse;
import com.krizaka.sovkora.core.domain.model.PromptContext;
import com.krizaka.sovkora.core.domain.model.security.Actor;
import com.krizaka.sovkora.jobs.domain.model.JobCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Answers a turn from a cache, before any model — placed in Phase 2 just before the router by a
 * {@code pipeline_interceptor_config} row, never by an {@code @Order}.
 *
 * <p>Two tiers, in this order: an <b>exact</b> match (the same tenant, scope, tier and words), then
 * a <b>semantic</b> one (cosine at or above the threshold <em>and</em> the same numbers and
 * negations, see {@link TurnSignature}). A hit is a {@link CachedResponse}: no GPU, no usage, the
 * hold released.
 *
 * <p><b>Safety, in the order it is owed:</b>
 *
 * <ul>
 *   <li><b>Opt-in.</b> Off unless the host declares {@value #MODE_KEY} as {@code exact} or {@code
 *       semantic} for the pack or route. Never for a turn that carries media, nor when the host
 *       declares {@value #VOLATILE_KEY} (tools, RAG over changing data, user-specific context,
 *       numbers that matter: invoices, taxes, leases).
 *   <li><b>Never across tenants.</b> No tenant declared, no cache; the tenant is part of the
 *       partition of every read and every write.
 *   <li><b>Screened like any answer.</b> A hit is still an answer: the engine runs the output
 *       screens (Safety among them) on it before it is served. An answer is kept only after a fresh
 *       one has passed them ({@link #responseServed}).
 *   <li><b>Expires and can be invalidated.</b> Every entry carries a TTL; {@link #invalidateTenant}
 *       forgets a tenant; a changed scope guard is a new partition.
 * </ul>
 */
public final class SemanticCacheInterceptor implements PromptContextInterceptor {

  private static final Logger log = LoggerFactory.getLogger(SemanticCacheInterceptor.class);

  /** Platform key: {@code off} (default), {@code exact} or {@code semantic}. */
  public static final String MODE_KEY = "orazaka.pipeline.cache.mode";

  /** Platform key: {@code true} when the turn must never be cached whatever the mode. */
  public static final String VOLATILE_KEY = "orazaka.pipeline.cache.volatile";

  /** Platform key: seconds an answer is kept (default one day). */
  public static final String TTL_SECONDS_KEY = "orazaka.pipeline.cache.ttlSeconds";

  /** Platform key: the declared model tier (default {@code default}). */
  public static final String MODEL_TIER_KEY = "orazaka.pipeline.cache.modelTier";

  /** Platform keys naming the tenant, first found wins. */
  private static final List<String> TENANT_KEYS = List.of("orazaka.tenantId", "orazaka.studioId");

  /** The lowest cosine accepted for a semantic hit. */
  public static final double MIN_THRESHOLD = 0.94;

  private static final Duration DEFAULT_TTL = Duration.ofDays(1);
  private static final int NEAREST = 3;

  private final SemanticCacheStore store;
  private final TextEmbedder embedder;
  private final Clock clock;
  private final double threshold;

  /**
   * @param store where answers are kept
   * @param embedder the local embedding model
   * @param clock the injected clock (expiry)
   * @param threshold the cosine threshold; never below {@value #MIN_THRESHOLD}
   */
  public SemanticCacheInterceptor(
      SemanticCacheStore store, TextEmbedder embedder, Clock clock, double threshold) {
    this.store = store;
    this.embedder = embedder;
    this.clock = clock;
    this.threshold = Math.max(threshold, MIN_THRESHOLD);
  }

  @Override
  public PromptContext beforeExecution(PromptContext context) {
    Mode mode = modeOf(context);
    CachePartition partition = partitionOf(context);
    if (mode == Mode.OFF || partition == null || blank(context.rawUserQuery())) {
      return context;
    }
    Instant now = clock.instant();
    String exactKey = exactKey(partition, context.rawUserQuery());

    var exact = store.findExact(partition, exactKey, now);
    if (exact.isPresent()) {
      log.info("Cache hit (exact)");
      return context.withCachedResponse(new CachedResponse(exact.get(), "EXACT", 1.0));
    }
    if (mode != Mode.SEMANTIC) {
      return context;
    }
    String signature = TurnSignature.of(context.rawUserQuery());
    float[] embedding = embedder.embed(context.rawUserQuery());
    for (var candidate : store.nearest(partition, embedding, NEAREST, now)) {
      if (candidate.similarity() >= threshold && candidate.signature().equals(signature)) {
        log.info(
            "Cache hit (semantic, {})", String.format(Locale.ROOT, "%.3f", candidate.similarity()));
        return context.withCachedResponse(
            new CachedResponse(
                candidate.response(), "SEMANTIC", Math.min(candidate.similarity(), 1.0)));
      }
    }
    return context;
  }

  @Override
  public void responseServed(PromptContext context, String servedText) {
    Mode mode = modeOf(context);
    CachePartition partition = partitionOf(context);
    if (mode == Mode.OFF
        || partition == null
        || blank(context.rawUserQuery())
        || blank(servedText)
        || context.cachedResponse().isPresent()) {
      return;
    }
    Instant expires = clock.instant().plus(ttlOf(context));
    float[] embedding =
        mode == Mode.SEMANTIC ? embedder.embed(context.rawUserQuery()) : new float[0];
    store.put(
        partition,
        exactKey(partition, context.rawUserQuery()),
        embedding,
        TurnSignature.of(context.rawUserQuery()),
        servedText,
        expires);
  }

  /** Forgets everything a tenant has cached (a pack, a model or a prompt changed). */
  public int invalidateTenant(String tenantId) {
    return store.invalidateTenant(tenantId);
  }

  /** Forgets what has expired. */
  public int purgeExpired() {
    return store.purgeExpired(clock.instant());
  }

  private enum Mode {
    OFF,
    EXACT,
    SEMANTIC
  }

  private static Mode modeOf(PromptContext context) {
    Map<String, Object> meta = context.userMetadata();
    if (context.carriesMedia() || "true".equalsIgnoreCase(String.valueOf(meta.get(VOLATILE_KEY)))) {
      return Mode.OFF;
    }
    return switch (String.valueOf(meta.get(MODE_KEY)).toLowerCase(Locale.ROOT)) {
      case "exact" -> Mode.EXACT;
      case "semantic" -> Mode.SEMANTIC;
      default -> Mode.OFF;
    };
  }

  private static CachePartition partitionOf(PromptContext context) {
    Map<String, Object> meta = context.userMetadata();
    String tenant = null;
    for (String key : TENANT_KEYS) {
      if (meta.get(key) instanceof String s && !s.isBlank()) {
        tenant = s;
        break;
      }
    }
    // An answer was written from what its actor could read, so it is served to that actor only: the
    // actor's scope (tenant, user, roles, groups) is part of the partition. No verified actor, no
    // cache — and a removed role is a new scope, never a stale hit (ADR-091).
    Actor actor = Actor.from(meta).orElse(null);
    if (tenant == null || actor == null) {
      return null;
    }
    String scope =
        sha256(
            actor.scope()
                + "\u0000"
                + String.valueOf(meta.get(JobCommand.SCOPE_REFUSED_TERMS_KEY))
                + "\u0000"
                + meta.get(JobCommand.SCOPE_REFUSAL_KEY)
                + "\u0000"
                + meta.get(JobCommand.SAFETY_CRISIS_TERMS_KEY));
    Object tier = meta.get(MODEL_TIER_KEY);
    return new CachePartition(
        tenant, scope, tier instanceof String t && !t.isBlank() ? t : "default");
  }

  private static Duration ttlOf(PromptContext context) {
    try {
      long seconds = Long.parseLong(String.valueOf(context.userMetadata().get(TTL_SECONDS_KEY)));
      return seconds > 0 ? Duration.ofSeconds(seconds) : DEFAULT_TTL;
    } catch (NumberFormatException none) {
      return DEFAULT_TTL;
    }
  }

  private static String exactKey(CachePartition partition, String query) {
    return sha256(
        partition.tenantId()
            + "\u0000"
            + partition.scopeGuardHash()
            + "\u0000"
            + partition.modelTier()
            + "\u0000"
            + query.strip());
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
