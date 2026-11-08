---
title: 'Sovkora Core Engine: Architecture & Pipeline'
description: 'Documentation for Sovkora Core Engine: Architecture & Pipeline'
category: Architecture
order: 3
---
# Sovkora Core Engine: Architecture & Pipeline

> Deep-dive into the `orazaka-core` module — the stateless, Spring AI-powered orchestration library.

---

## 1. Architectural Position

`orazaka-core` is the central orchestration library. It is strictly stateless, web-agnostic, database-agnostic, and security-context-agnostic. It wraps Spring AI (`2.0`, ADR-030) under a single entry facade: `AiClient`.

```architecture
product: orazaka
focus: orazaka-core
mode: build
```

Select a module to see what it depends on and what depends on it: every arrow points toward the core, never away from it.

---

## 2. The AiClient Facade & Request Records

All AI operations invoke the unified `AiClient`:
```java
public interface AiClient {
    ChatResponse chat(ChatRequest request);
    Flux<ChatResponse> stream(ChatRequest request);
    AudioResponse audio(AudioRequest request);
    ImageResponse image(ImageRequest request);
    VideoResponse video(VideoRequest request);
}
```

Request payloads are immutable Java `record`s enforcing compact constructor validation boundaries (`ERR-106`, `ERR-116`).

---

## 3. Dynamic Pipeline Orchestrator

The orchestrator executes a sequence of `PromptContextInterceptor` beans. It is never bypassed: `orazaka.core.orchestration.enabled=false` (`ORCHESTRATION_ENABLED`) **refuses** every engine turn (`PipelineDisabledException` → `503`) instead of calling the model without its controls (ADR-062).

### Routing Modes (`orazaka.core.orchestration.routing.mode`)
- **DETERMINISTIC**: Database-driven order configured in `pipeline_interceptor_config`.
- **AGENTIC**: LLM-driven runtime intent classification.

### Kill-Switch
If `orazaka.security.disable-ai=true`, any interceptor returning `isAiDependent() == true` raises a `SecurityException`.

### The interceptor chain

A turn goes through the core chain first — in the order of the code, never bypassable — then through the configured interceptors in their database order (`pipeline_interceptor_config`). The chain below is read from both; [Interceptor Registry](INTERCEPTORS.md) lists every row with its class.

```pipeline
product: orazaka
```

---

## 4. Engine Topology

One engine serves every modality. `AbstractEngine` is **sealed** and permits a single subclass, `Engine` (ADR-003): chat, streaming, image and speech are methods of the same class, each wired to its Spring AI model (`ChatModel`, `TextToSpeechModel`, image and video through their outbound ports), so a pattern match over engines is always exhaustive. Model choice is not a subclass: it is routing (`DeterministicRouterEngine`, `SemanticRoutingEngine`) over the model catalogue in the database.

---

## 5. Outbound Ports & Lifecycle Policies

- **Interface-Driven Boundaries**: Outbound ports (e.g. `ChatGeneratorClient`, `VideoGeneratorClient`) live as public interfaces in `domain.ports.outbound`. Implementations reside in infrastructure packs as package-private beans.
- **Resource Recovery**: Streams and SSE channels must register completion/timeout hooks (`onCompletion`, `.doFinally()`) to dispose subscriptions (`Disposable.dispose()`) and prevent memory leaks.
- **Hikari Database Connection Eviction**:
  ```yaml
  hikari:
    maximum-pool-size: 10
    minimum-idle: 2
    idle-timeout: 30000            # Evict idle connections in 30s
    max-lifetime: 60000            # Recycle before DB timeout
    connection-timeout: 5000       # Fail fast in 5s
  ```

---

## 6. Model Catalog & Chat Configuration

- **Database Model Catalog**: Image, video, and speech models reside in the `orazaka_models` table. Cached via Caffeine TTL.
- **Chat Model Resolution Cascade**:
  1. `ORAZAKA_OLLAMA_MODEL` environment variable (highest priority)
  2. `spring.ai.ollama.chat.options.model` YAML value
  3. Auto-detected default (first non-embedding model from Ollama tags catalog)

---

## Related Documentation
- [Developer Onboarding Guide](101.md)
- [Architecture Reference](_generated/ARCHITECTURE.md)
