# Edge service

> `services/edge/sovkora-edge-service` of [Sovkora](../../../README.md), one repository (maven, foundation layer). Depends on `build`. Licence: [Apache-2.0](../LICENSE).

Transparent HTTP edge in front of every Sovkora service: routing, API-key → JWT exchange, CORS and tracing. Spring MVC + virtual threads.

## What it provides

The single HTTP entry point (port `8088`): routes `/api/v1/**` to the owning service, exchanges API
keys for JWTs (`ApiKeyExchangeFilter`), CORS, tracing. Spring MVC + virtual threads — not WebFlux,
not Spring Cloud Gateway. Reusable in front of any Krizaka service set.

## Build and test

```bash
./mvnw verify -pl services/edge/sovkora-edge-service -am      # from the repository root: this module and what it needs
./mvnw verify -pl services/edge/sovkora-edge-service -amd     # this module and everything that depends on it
```

CI runs it on every pull request that touches `services/edge/sovkora-edge-service/` (ADR-029: one repository, one CI).
