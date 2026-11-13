-- krizaka-internal#3368: a run is counted under the product its API key declares, and keeps its cost.
-- product_key: the producer's declaration (edge, from the key's product: scope); NULL when the key
-- declared none, which the spend ledger counts as `unattributed`. No FK, no CHECK: a product is a
-- cap row in another context, and a key nobody capped is refused there, not here.
-- cost_usd: what the provider calls of the run cost, summed from its steps' outcomes (the price table
-- is the engine's, the saga only adds what the executor reports). Expand only: old code ignores both.
ALTER TABLE studio_run
    ADD COLUMN product_key VARCHAR(63),
    ADD COLUMN cost_usd    NUMERIC(14, 8) NOT NULL DEFAULT 0;
