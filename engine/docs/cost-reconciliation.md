# Cost reconciliation: tokens observed vs usage billed

ADR-094 slice 3 (ai-engine#171). The cost dashboard of sovkora#323 starts from this query. Money stays on
our metering (`TokenUsage`, `ChatTokenMeter`, ADR-033, the `usage_event` fact table of the billing service); the
`gen_ai` meter is the **operations** side and never the source of an invoice. The reconciliation tells the two apart:
a day and a model where they disagree is a missing hook or a billing gap.

## What the engine emits

| Meter | Built by | Tags (all low-cardinality) |
|---|---|---|
| `gen_ai.client.operation` (timer) | every chat model (Spring AI), embeddings and speech (the engine, same keys) | `gen_ai.operation.name` (`chat`, `embedding`, `text_to_speech`), `gen_ai.system`, `gen_ai.request.model`, `orazaka.feature` |
| `gen_ai.client.token.usage` (counter) | chat and embedding operations | the above plus `gen_ai.token.type` (`input`, `output`, `total`) |
| `spring.ai.tool` (timer) | each tool round | `orazaka.feature` |

- `orazaka.feature` is the feature key of the turn (`none` outside a turn: an embedding, a speech). Never the tenant or
  the user id.
- No prompt, answer, input text or tool argument is a tag or an attribute: the content properties stay `false`
  (ADR-064), pinned by `ModelObservationsTest`, `EmbeddingAndSpeechObservationsTest` and `FeatureTagObservationsTest`.
- Cached input tokens (`TokenUsage#cachedReadTokens` / `cacheWriteTokens`, filled from Spring AI's `Usage`) are informational: they are already inside the billed input total, so the billed side does not change. Spring AI also records them on the observation as `gen_ai.usage.cache_read.input_tokens` / `cache_creation.input_tokens` (trace attributes, not counter tags); read them next to the `usage_event` to see how much of a day's input was served from the provider's cache.
- The cache is a **policy per feature key** (`orazaka_feature_cache_policy`: `NONE`, `SYSTEM_ONLY`, `SYSTEM_AND_TOOLS`, default row `*`, edited by an administrator, read on every call). A SENSITIVE turn (one that declares a scope, safety or subject guard, `TurnSensitivity`) is never sent with cache options. `TokenUsage#cachedReadKilotokens()` / `cacheWriteKilotokens()` give the cached share in the unit chat is priced in (`KILOTOKEN`); the billed quantity stays the total, a separate cached-read price needs a billing unit in the pricebook (krizaka-billing), not yet metered.
- Text to speech has no token: it is billed per character, so it appears in `gen_ai.client.operation` only.

## The query

Observed side, per day and model, from the metrics store (the counter exported with Prometheus' `_total` suffix; the tags above become labels):

```promql
sum by (gen_ai_request_model) (
  increase(gen_ai_client_token_usage_total{gen_ai_token_type=~"input|output"}[1d])
)
```

Billed side, per day and model, from the billing database:

```sql
SELECT date_trunc('day', occurred_at) AS day,
       model_name,
       sum(quantity)                  AS billed_tokens
FROM usage_event
WHERE unit = 'token' -- list yours first: SELECT DISTINCT capability, unit FROM usage_event
  AND occurred_at >= now() - interval '7 days'
GROUP BY 1, 2
ORDER BY 1, 2;
```

Read the two series side by side per `(day, model)`. A gap is expected only where the engine did not bill: a platform
call that is free by design. Anything else is a bug to fix on the metering, not to explain away.
