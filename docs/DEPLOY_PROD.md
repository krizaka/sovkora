# Sovkora in prod — the Verikora pilot's engine on api.sovkora.com

**Not deployed.** This page describes what is declared as code for the pilot (krizaka-internal#2132, ADR SOV-105). The prod
gate waits for the prod team (krizaka-internal#2138); the infrastructure the spec names (the prod VPC, the cluster, the
broker Droplet) is a later slice, in OpenTofu (krizaka-infra `stacks/prod`). No tool of this repository creates anything in
prod.

## What runs

One product calls it, server to server: Verikora, with its own `oz_` key, on `https://api.sovkora.com`. No page, no HTML, no
sign-in screen; every answer of the edge carries `X-Robots-Tag: noindex, nofollow`.

| What | Where it is declared | Size | USD/month |
|---|---|---|---|
| `core`: users, billing, job, conversation, studio and the document-validation worker; not routed | `infra/deploy/sovkora-prod.app.yaml` | `apps-s-1vcpu-1gb` | 12 |
| `edge`: the only routed component, `api.sovkora.com` → core inside the app | same | `apps-s-1vcpu-0.5gb` | 5 |
| Broker: RabbitMQ on a Droplet in `tor1`, the prod VPC only, TLS 5671 only, no data volume | `infra/deploy/broker/cloud-init.yaml` + `prod.json` | `s-1vcpu-512mb-10gb` | 4 |
| Databases: one per context (engine, users, billing, studio) on the prod cluster `krizaka-prod-pg` | the spec's `databases:` (by reference) | — | (the cluster, ORG-036 step 1) |

**About +21 USD/month** (SOV-105: core and edge +17, the broker +4), list prices before taxes, model spend apart. The pilot's
model spend has its own monthly cap in the admin settings, the row of (`prod`, the product's key) that
`AI_SPEND_ENVIRONMENT=prod` selects (krizaka-internal#3368); `AI_SPEND_DAILY_BUDGET_USD=1` is the
engine's daily backstop under it.

**Deliberately absent** (the comment block at the top of the spec says why): the web app and the operator console, every
host but `api.sovkora.com`, the GPU relay and the image and video engines, the hosted video worker, every pack but
`document-validation` (`/app/packs-pilot` in the image holds a copy of that one pack), the knowledge and automation
services, a remote media store (the Bunny zone is in New York; personal data stays in Canada, SOV-105 §4), and the job
service's development chat route.

## The keys

- **A key states its environment.** A prod key is minted with the scope `env:prod` (the scope grammar of krizaka-users
  already admits it; no change there). The prod edge declares `AUTH_API_KEY_ENVIRONMENT=prod` and refuses any other key
  with `401` and the problem code `API_KEY_WRONG_ENVIRONMENT`, before any backend sees it. A key that states nothing is a
  dev key, so every key minted before #2132 is refused in prod; a dev edge refuses a prod key that leaks. An unknown or a
  second `env:` scope is refused everywhere. `env:prod` opens no route: a prod key scoped `ai:embed` is still held to it.
- **Every refusal is coded** (RFC 9457, `application/problem+json`, field `code`): `API_KEY_INVALID`,
  `API_KEY_WRONG_ENVIRONMENT`, `API_KEY_SCOPE_DENIED`, `API_KEY_RATE_LIMITED` and `API_KEY_QUOTA_EXCEEDED` (both `429`
  with `Retry-After` and `retryAfterSeconds`), `API_KEY_EXCHANGE_UNREACHABLE` (`502`). The `error` field the edge answered
  before stays, with the same text.
- **One key per consuming product, its own limits.** The rate limit is per key, from its owner's tier, a row
  (`rate_limits`, `rate_limit_tiers`): the products' service accounts are on the `service` tier
  (`infra/migrations/2026-10-20-service-rate-tier.sql`, run once on the prod `krizaka_users_db`). The daily quota is per
  key too, and its ceiling is the owner's tier's (`rate_limits.daily_requests`, returned as `dailyRequests` by the
  token exchange; `infra/migrations/2026-11-13-service-daily-requests.sql` gives the `service` tier its row); a tier with
  none falls back to `AUTH_API_KEY_DAILY_REQUESTS`.

The checks that keep this honest: `node scripts/check-prod-spec.mjs` (no plain secret, no development host, no web app, no
video worker, no GPU relay, only the edge routed, `AUTH_API_KEY_ENVIRONMENT=prod`), `scripts/test/prod-app-spec.test.mjs`,
`scripts/test/prod-broker.test.mjs`, and the edge's `ApiKeyEnvironmentTest` and `ApiKeyExchangeFilterTest`.

## Not yet proven (the five checks of #2132, from SOV-105)

1. The dispatches are persistent messages with publisher confirms.
2. Verikora gives a paid file back when a run fails.
3. `core` at 1 GiB holds the five JVMs and the validation worker (measured as for dev, `infra/deploy/core-memory-measured.json`).
4. A silent step is re-issued from the database with the same job id (today `RunSweeper` only fails the run and releases the hold).
5. The broker Droplet has no data volume, so the message (the document's text, personal data) is never the only copy of a
   job's state: declared here (`prod.json`, tested), to verify on the Droplet once it exists.

Until all five are proven, the pilot runs no paid file.

## Bringing it up, once #2138 opens

The founder runs the steps that create or pay for anything; an agent prepares them.

1. **Infrastructure** (krizaka-infra `stacks/prod`, `tofu plan` read, then applied by the founder): the prod VPC in `tor1`,
   the cluster `krizaka-prod-pg` with the four databases and their roles, the broker Droplet from `cloud-init.yaml` with
   the values of `broker/prod.json` (its own CA and server certificate, the one user `sovkora`, a firewall that admits
   5671 from the VPC range only, no SSH, no volume, no backup), and the private name `broker-prod.sovkora.com` → its
   private IP.
2. **Secrets** in the operator's vault, never in a repository: `PROD_IDENTITY_JWT_SECRET`, `PROD_CRYPTO_KEY`,
   `PROD_CRYPTO_SALT`, `PROD_MEDIA_ENCRYPTION_KEY`, `PROD_RABBITMQ_PASS`, `PROD_RABBITMQ_CA_CERT_BASE64`, the prod model
   access key `PROD_AI_INFERENCE_KEY` and the VPC id. None is shared with dev.
3. **The app**: fill the placeholders, `doctl apps spec validate`, then create `sovkora-prod` (founder), and point
   `api.sovkora.com` at it. The database passwords come from the cluster by reference.
4. **The data**: the `service` rate tier (above); the Verikora service account and its grants on the document Studios;
   its key, minted with the scope `env:prod`, stored as a SECRET of Verikora's prod app only.
5. **Proof**: a dev key gets `401 API_KEY_WRONG_ENVIRONMENT` on `api.sovkora.com`; the prod key runs one unpaid file end
   to end; then the five checks above.

## Next slices

- The prod stacks in OpenTofu (krizaka-infra `stacks/prod`): VPC, cluster, broker Droplet, DNS.
- The edge's circuit breakers and a fast-fail `503` with `Retry-After` when core or the broker is gone.
- The SOV-105 guard: a silent step is re-issued from the database with the same job id, then fails with a typed cause.
- A daily quota per product as a row: the tier row carries it, the key exchange of krizaka-users returns it, the edge applies it.
