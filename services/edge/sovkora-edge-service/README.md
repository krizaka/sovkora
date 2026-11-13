# Edge service

> `services/edge/sovkora-edge-service` of [Sovkora](../../../README.md), one repository (maven, foundation layer). Depends on `build`. Licence: [Apache-2.0](../LICENSE).

Transparent HTTP edge in front of every Sovkora service: routing, API-key → JWT exchange, CORS and tracing. Spring MVC + virtual threads.

## What it provides

The single HTTP entry point (port `8088`): routes `/api/v1/**` to the owning service, exchanges API
keys for JWTs (`ApiKeyExchangeFilter`), CORS, tracing. Spring MVC + virtual threads — not WebFlux,
not Spring Cloud Gateway. Reusable in front of any Krizaka service set.

## When a backend is down or slow

Each backend of the route table has its own circuit breaker (Resilience4j). A backend whose circuit is
open, that refuses the connection, or that sends no response headers within
`EDGE_BACKEND_RESPONSE_TIMEOUT` (50 s by default) gets the caller a 503 problem at once:
`BACKEND_UNAVAILABLE` or `BACKEND_TIMEOUT`, with `Retry-After` in seconds (the circuit's remaining
open time, or `EDGE_BACKEND_RETRY_AFTER`). The backend's own text is never shown. The timeout only
covers the wait for the headers, so an SSE stream that is already flowing is never cut. The settings
and their bounds are in `sovkora.edge.backend` (`EdgeBackendProperties`); the meters are
`edge.backend.breaker.state` and `edge.backend.rejections`.

## Build and test

```bash
./mvnw verify -pl services/edge/sovkora-edge-service -am      # from the repository root: this module and what it needs
./mvnw verify -pl services/edge/sovkora-edge-service -amd     # this module and everything that depends on it
```

CI runs it on every pull request that touches `services/edge/sovkora-edge-service/` (ADR-029: one repository, one CI).
