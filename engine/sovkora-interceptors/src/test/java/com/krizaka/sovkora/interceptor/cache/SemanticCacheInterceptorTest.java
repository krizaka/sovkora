package com.krizaka.sovkora.interceptor.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.application.pipeline.PipelineShortCircuitException;
import com.krizaka.sovkora.core.application.pipeline.ResponseScreen;
import com.krizaka.sovkora.core.domain.model.PromptContext;
import com.krizaka.sovkora.interceptor.validation.SafetyInterceptor;
import com.krizaka.sovkora.jobs.domain.model.JobCommand;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The six conditions a semantic cache owes before it may serve anyone (Oussama, 2026-10-21). */
class SemanticCacheInterceptorTest {

  /** Words → vectors: prompts built from the same words are identical in embedding space. */
  private static final class StubEmbedder implements TextEmbedder {
    final AtomicInteger calls = new AtomicInteger();

    @Override
    public float[] embed(String text) {
      calls.incrementAndGet();
      float[] v = new float[8];
      for (String w : text.toLowerCase().replaceAll("[^\\p{L}\\d' ]", " ").split("\\s+")) {
        // Numbers and negations barely move an embedding (the failure a cache must survive): a word
        // that carries one is invisible to this stub, the others count by their first letter.
        if (!w.isEmpty() && TurnSignature.of(w).isEmpty()) {
          v[Math.abs(w.charAt(0)) % 8] += 1;
        }
      }
      return v;
    }
  }

  private static final class MutableClock extends Clock {
    Instant now = Instant.parse("2026-10-22T00:00:00Z");

    void advance(Duration d) {
      now = now.plus(d);
    }

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  private final StubEmbedder embedder = new StubEmbedder();
  private final MutableClock clock = new MutableClock();
  private final InMemorySemanticCacheStore store = new InMemorySemanticCacheStore(100);
  private final SemanticCacheInterceptor cache =
      new SemanticCacheInterceptor(store, embedder, clock, 0.94);

  private static double cosine(float[] a, float[] b) {
    double dot = 0;
    double na = 0;
    double nb = 0;
    for (int i = 0; i < a.length; i++) {
      dot += a[i] * b[i];
      na += a[i] * a[i];
      nb += b[i] * b[i];
    }
    return dot / (Math.sqrt(na) * Math.sqrt(nb));
  }

  private static PromptContext turn(
      String text, String tenant, String mode, Map<String, Object> more) {
    Map<String, Object> meta = new HashMap<>(more);
    // The platform's keys for who the turn is for (ADR-091): one ordinary user unless a test says.
    meta.putIfAbsent("userId", "u-1");
    meta.putIfAbsent("roles", List.of("ROLE_USER"));
    if (tenant != null) {
      meta.put("orazaka.tenantId", tenant);
    }
    if (mode != null) {
      meta.put(SemanticCacheInterceptor.MODE_KEY, mode);
    }
    return new PromptContext(text, meta);
  }

  private static PromptContext turn(String text, String tenant, String mode) {
    return turn(text, tenant, mode, Map.of());
  }

  private void remember(PromptContext context, String answer) {
    cache.responseServed(context, answer);
  }

  private static boolean hit(PromptContext context) {
    return context.cachedResponse().isPresent();
  }

  // ---------------------------------------------------------------------------------------------
  // 0. never across actors (ADR-091): an answer was written from what its actor could read
  @Nested
  @DisplayName("0. never across actors")
  class Actors {
    private PromptContext as(String userId, List<String> roles) {
      return turn(
          "what is the ceo salary", "acme", "semantic", Map.of("userId", userId, "roles", roles));
    }

    @Test
    void anAnswerWrittenForAnHrUserIsNotServedToAnEmployee() {
      remember(as("hr-1", List.of("ROLE_USER", "ROLE_HR")), "480000 CAD");

      assertThat(hit(cache.beforeExecution(as("emp-1", List.of("ROLE_USER"))))).isFalse();
      assertThat(hit(cache.beforeExecution(as("hr-1", List.of("ROLE_USER"))))).isFalse();
      assertThat(hit(cache.beforeExecution(as("hr-1", List.of("ROLE_USER", "ROLE_HR"))))).isTrue();
    }

    @Test
    void aTurnWithNoVerifiedActorIsNeverCached() {
      var anonymous =
          new PromptContext(
              "what is the ceo salary",
              Map.of("orazaka.tenantId", "acme", SemanticCacheInterceptor.MODE_KEY, "semantic"));
      remember(anonymous, "anything");

      assertThat(hit(cache.beforeExecution(anonymous))).isFalse();
      assertThat(embedder.calls.get()).isZero();
    }
  }

  // ---------------------------------------------------------------------------------------------
  // 1. never cross tenants
  @Nested
  @DisplayName("1. never across tenants")
  class Tenants {
    @Test
    void anAnswerOfOneTenantIsNeverServedToAnother_exact() {
      remember(turn("what is my plan", "acme", "exact"), "Acme answer");
      assertThat(hit(cache.beforeExecution(turn("what is my plan", "globex", "exact")))).isFalse();
      assertThat(hit(cache.beforeExecution(turn("what is my plan", "acme", "exact")))).isTrue();
    }

    @Test
    void anAnswerOfOneTenantIsNeverServedToAnother_semantic() {
      remember(turn("what is my plan", "acme", "semantic"), "Acme answer");
      assertThat(hit(cache.beforeExecution(turn("what is my plan", "globex", "semantic"))))
          .isFalse();
      assertThat(hit(cache.beforeExecution(turn("what is my plan", "acme", "semantic")))).isTrue();
    }

    @Test
    void noTenantDeclaredMeansNoCacheAtAll() {
      remember(turn("what is my plan", null, "semantic"), "anonymous answer");
      assertThat(hit(cache.beforeExecution(turn("what is my plan", null, "semantic")))).isFalse();
      assertThat(embedder.calls.get()).isZero();
    }

    @Test
    void aChangedScopeGuardIsAnotherPartition() {
      remember(
          turn("what is my plan", "acme", "exact", Map.of(JobCommand.SCOPE_REFUSAL_KEY, "v1")),
          "old");
      var after =
          turn("what is my plan", "acme", "exact", Map.of(JobCommand.SCOPE_REFUSAL_KEY, "v2"));
      assertThat(hit(cache.beforeExecution(after))).isFalse();
    }

    @Test
    void aDifferentModelTierIsAnotherPartition() {
      remember(
          turn(
              "what is my plan",
              "acme",
              "exact",
              Map.of(SemanticCacheInterceptor.MODEL_TIER_KEY, "small")),
          "small");
      var large =
          turn(
              "what is my plan",
              "acme",
              "exact",
              Map.of(SemanticCacheInterceptor.MODEL_TIER_KEY, "large"));
      assertThat(hit(cache.beforeExecution(large))).isFalse();
    }
  }

  // ---------------------------------------------------------------------------------------------
  // 2. a cached answer still passes the output Safety interceptor
  @Nested
  @DisplayName("2. a cached answer still passes the output Safety interceptor")
  class OutputSafety {
    private final Map<String, Object> crisis =
        Map.of(
            JobCommand.SAFETY_CRISIS_TERMS_KEY,
            "en finir",
            JobCommand.SAFETY_RESPONSE_KEY,
            "reviewed text");

    @Test
    void aHitIsScreenedBySafetyBeforeItIsServed() {
      var seeded = turn("hello there", "acme", "exact", Map.of());
      remember(seeded, "Pour en finir avec cela, voici...");

      var answered = cache.beforeExecution(turn("hello there", "acme", "exact", crisis));
      // (the crisis declaration is part of the scope hash, so give both the same declaration)
      remember(turn("hello there", "acme", "exact", crisis), "Pour en finir avec cela, voici...");
      answered = cache.beforeExecution(turn("hello there", "acme", "exact", crisis));

      assertThat(hit(answered)).isTrue();
      var served = answered.cachedResponse().orElseThrow().text();
      var screens = List.<PromptContextInterceptor>of(new SafetyInterceptor(), cache);
      var ctx = answered;
      assertThatThrownBy(() -> ResponseScreen.apply(screens, ctx, served))
          .isInstanceOf(PipelineShortCircuitException.class)
          .hasMessage("reviewed text");
    }

    @Test
    void aCachedAnswerIsNotKeptAgainWhenItWasServedFromTheCache() {
      var cachedTurn = turn("hello there", "acme", "exact");
      remember(cachedTurn, "first");
      var hitCtx = cache.beforeExecution(cachedTurn);
      cache.responseServed(hitCtx, "first");
      assertThat(hit(cache.beforeExecution(cachedTurn))).isTrue();
    }
  }

  // ---------------------------------------------------------------------------------------------
  // 3. the exact tier is checked before the semantic tier
  @Test
  @DisplayName("3. the exact tier is checked first: an exact hit never asks the embedder")
  void exactBeforeSemantic() {
    var t = turn("how do I reset my password", "acme", "semantic");
    remember(t, "Use the reset link.");
    int callsAfterStore = embedder.calls.get();

    var answered = cache.beforeExecution(t);

    assertThat(answered.cachedResponse().orElseThrow().tier()).isEqualTo("EXACT");
    assertThat(embedder.calls.get()).isEqualTo(callsAfterStore);
  }

  @Test
  void aNearWordingIsAHitOnTheSemanticTier() {
    remember(turn("how do I reset my password", "acme", "semantic"), "Use the reset link.");
    var near = cache.beforeExecution(turn("how do I reset my password?", "acme", "semantic"));
    assertThat(near.cachedResponse().orElseThrow().tier()).isIn("EXACT", "SEMANTIC");
    var other =
        cache.beforeExecution(turn("how do I reset my password please", "acme", "semantic"));
    assertThat(hit(other)).isTrue();
    assertThat(other.cachedResponse().orElseThrow().tier()).isEqualTo("SEMANTIC");
  }

  // ---------------------------------------------------------------------------------------------
  // 4. opt-in, off by default, off for what must not be cached
  @Nested
  @DisplayName("4. opt-in per pack or route, off by default and for what must not be cached")
  class OptIn {
    @Test
    void offByDefault_nothingIsLookedUpNorKept() {
      var t = turn("what is my plan", "acme", null);
      remember(t, "answer");
      assertThat(hit(cache.beforeExecution(t))).isFalse();
      assertThat(embedder.calls.get()).isZero();
      assertThat(
              store.nearest(
                  new CachePartition("acme", "x", "default"), new float[8], 5, clock.instant()))
          .isEmpty();
    }

    @Test
    void anUnknownModeIsOff() {
      var t = turn("what is my plan", "acme", "aggressive");
      remember(t, "answer");
      assertThat(hit(cache.beforeExecution(t))).isFalse();
    }

    @Test
    void aVolatileTurnIsNeverCachedEvenWhenOptedIn() {
      // tools, RAG over changing data, user-specific context, numbers that matter (invoices, taxes,
      // leases)
      var t =
          turn(
              "what do I owe",
              "acme",
              "semantic",
              Map.of(SemanticCacheInterceptor.VOLATILE_KEY, "true"));
      remember(t, "you owe 120");
      assertThat(hit(cache.beforeExecution(t))).isFalse();
    }

    @Test
    void aTurnThatCarriesMediaIsNeverCached() {
      var withImage =
          new PromptContext(
              "look at this",
              Map.of(
                  "orazaka.tenantId",
                  "acme",
                  SemanticCacheInterceptor.MODE_KEY,
                  "semantic",
                  "userId",
                  "u-1",
                  "roles",
                  List.of("ROLE_USER")),
              Map.of(),
              "look at this",
              null,
              null,
              1);
      remember(withImage, "it is a lease");
      assertThat(hit(cache.beforeExecution(withImage))).isFalse();
    }

    @Test
    void theThresholdCanNeverBeLoweredBelowZeroPointNinetyFour() {
      var lax = new SemanticCacheInterceptor(store, embedder, clock, 0.5);
      remember(turn("alpha beta gamma", "acme", "semantic"), "A");
      // a different bag of words: cosine well under 0.94
      assertThat(hit(lax.beforeExecution(turn("zeta eta theta", "acme", "semantic")))).isFalse();
    }
  }

  // ---------------------------------------------------------------------------------------------
  // 5. a TTL and invalidation exist
  @Nested
  @DisplayName("5. a TTL and invalidation exist")
  class Lifetime {
    @Test
    void anAnswerExpiresAfterItsTtl() {
      var t =
          turn(
              "what is my plan",
              "acme",
              "exact",
              Map.of(SemanticCacheInterceptor.TTL_SECONDS_KEY, "60"));
      remember(t, "answer");
      clock.advance(Duration.ofSeconds(59));
      assertThat(hit(cache.beforeExecution(t))).isTrue();
      clock.advance(Duration.ofSeconds(2));
      assertThat(hit(cache.beforeExecution(t))).isFalse();
    }

    @Test
    void theDefaultTtlIsOneDay() {
      var t = turn("what is my plan", "acme", "exact");
      remember(t, "answer");
      clock.advance(Duration.ofHours(23));
      assertThat(hit(cache.beforeExecution(t))).isTrue();
      clock.advance(Duration.ofHours(2));
      assertThat(hit(cache.beforeExecution(t))).isFalse();
    }

    @Test
    void aTenantCanBeInvalidatedWithoutTouchingTheOthers() {
      remember(turn("what is my plan", "acme", "exact"), "acme");
      remember(turn("what is my plan", "globex", "exact"), "globex");

      assertThat(cache.invalidateTenant("acme")).isEqualTo(1);

      assertThat(hit(cache.beforeExecution(turn("what is my plan", "acme", "exact")))).isFalse();
      assertThat(hit(cache.beforeExecution(turn("what is my plan", "globex", "exact")))).isTrue();
    }

    @Test
    void expiredEntriesAreSwept() {
      remember(
          turn(
              "what is my plan",
              "acme",
              "exact",
              Map.of(SemanticCacheInterceptor.TTL_SECONDS_KEY, "10")),
          "a");
      clock.advance(Duration.ofSeconds(11));
      assertThat(cache.purgeExpired()).isEqualTo(1);
    }
  }

  // ---------------------------------------------------------------------------------------------
  // 6. two prompts differing only by a number or a negation do NOT hit
  @Nested
  @DisplayName("6. two prompts differing only by a number or a negation do not hit")
  class NumbersAndNegations {
    private void assertNoHit(String stored, String asked) {
      // The stub embedder sees both as (nearly) the same text, above the threshold: only the
      // signature can separate them.
      remember(turn(stored, "acme", "semantic"), "answer to: " + stored);
      assertThat(cosine(embedder.embed(stored), embedder.embed(asked)))
          .isGreaterThanOrEqualTo(0.94);
      assertThat(hit(cache.beforeExecution(turn(asked, "acme", "semantic"))))
          .as("%s  vs  %s", stored, asked)
          .isFalse();
    }

    @Test
    void aDifferentNumber() {
      assertNoHit("refund order 100 please", "refund order 1000 please");
      assertNoHit("pay 12.50 dollars now", "pay 15.50 dollars now");
      assertNoHit("book the 3rd floor room", "book the 4th floor room");
    }

    @Test
    void aSpelledOutNumber() {
      assertNoHit("send one hundred units", "send one thousand units");
      assertNoHit("envoie cent unités", "envoie mille unités");
    }

    @Test
    void aNegation() {
      assertNoHit("i can cancel my order", "i cannot cancel my order");
      assertNoHit("i can cancel my order", "i can't cancel my order");
      assertNoHit("je peux annuler ma commande", "je ne peux pas annuler ma commande");
      assertNoHit("is it allowed", "is it not allowed");
    }

    @Test
    void theSameTurnStillHitsAndTheSignatureIsStable() {
      remember(turn("refund order 100 please", "acme", "semantic"), "ok");
      assertThat(hit(cache.beforeExecution(turn("Refund order 100 please!", "acme", "semantic"))))
          .isTrue();
      assertThat(TurnSignature.of("Refund 100")).isEqualTo(TurnSignature.of("refund 100,0"));
      assertThat(TurnSignature.of("refund 100")).isNotEqualTo(TurnSignature.of("refund 1000"));
    }
  }
}
