package com.krizaka.sovkora.adapter.springai.spend;

import static org.assertj.core.api.Assertions.*;

import com.krizaka.sovkora.adapter.springai.spend.SpendGuard.Kind;
import com.krizaka.sovkora.core.domain.model.ModelEndpoint;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class SpendGuardTest {

  private static final ModelEndpoint PAID =
      new ModelEndpoint("do-inference", "https://inference.do-ai.run", "k", "wan");
  private static final ModelEndpoint OLLAMA =
      new ModelEndpoint("ollama", "http://localhost:11434", null, "qwen");

  private final AtomicReference<Instant> now =
      new AtomicReference<>(Instant.parse("2026-10-15T10:00:00Z"));
  private final Clock clock =
      new Clock() {
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
          return now.get();
        }
      };

  private SpendGuard guard(double budget) {
    return new SpendGuard(budget, 0.01, 0.0002, 0.60, clock);
  }

  @Test
  void paidCallsAreAllowedUntilTheBudgetIsSpentThenRefusedWith429() {
    SpendGuard guard = guard(1.0);
    guard.authorize(Kind.VIDEO, PAID); // 0.60
    assertThatThrownBy(() -> guard.authorize(Kind.VIDEO, PAID))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS))
        .isInstanceOf(SpendBudgetExceededException.class);
    // a cheaper paid call still fits
    assertThatCode(() -> guard.authorize(Kind.CHAT, PAID)).doesNotThrowAnyException();
  }

  @Test
  void onceTrippedEveryPaidKindIsRefusedButLocalModelsStayAvailable() {
    SpendGuard guard = guard(0.005);
    assertThatThrownBy(() -> guard.authorize(Kind.CHAT, PAID))
        .isInstanceOf(SpendBudgetExceededException.class);
    for (int i = 0; i < 100; i++) {
      guard.authorize(Kind.CHAT, OLLAMA);
    }
    assertThat(guard.spentTodayUsd()).isZero();
  }

  @Test
  void theBudgetResetsAtMidnightUtc() {
    SpendGuard guard = guard(0.01);
    guard.authorize(Kind.CHAT, PAID);
    assertThatThrownBy(() -> guard.authorize(Kind.CHAT, PAID))
        .isInstanceOf(SpendBudgetExceededException.class);
    now.set(Instant.parse("2026-10-16T00:00:01Z"));
    assertThatCode(() -> guard.authorize(Kind.CHAT, PAID)).doesNotThrowAnyException();
  }

  @Test
  void aNegativeBudgetSwitchesTheGuardOff() {
    SpendGuard guard = guard(-1);
    for (int i = 0; i < 50; i++) {
      guard.authorize(Kind.VIDEO, PAID);
    }
    assertThat(SpendGuard.unlimited()).isNotNull();
  }

  @Test
  void localityFollowsTheProviderNameAndThePrivateAddress() {
    assertThat(SpendGuard.isLocal(OLLAMA)).isTrue();
    assertThat(SpendGuard.isLocal(new ModelEndpoint("x", "http://10.0.0.5:8085", null, "m")))
        .isTrue();
    assertThat(SpendGuard.isLocal(new ModelEndpoint("x", "http://172.20.1.2:8085", null, "m")))
        .isTrue();
    assertThat(
            SpendGuard.isLocal(new ModelEndpoint("openai", "https://api.openai.com/v1", "k", "m")))
        .isFalse();
    assertThat(SpendGuard.isLocal(PAID)).isFalse();
  }
}
