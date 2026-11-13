-- ============================================================================
-- Monthly provider-spend cap per environment and product key (krizaka-internal#3368)
-- ----------------------------------------------------------------------------
-- The price table (what a provider charges per model), the cap rows (amount, action, the cheaper
-- model), the spend accumulated per scope and calendar month, the alert thresholds and what already
-- fired, and the change trail of the caps. Amounts here are dev defaults: the founder sets the real
-- ones through /api/v1/admin/spend-caps, and a cap can be changed, never removed.
-- ============================================================================

CREATE TABLE provider_price (
    model_key                      VARCHAR(255) PRIMARY KEY,
    capability                     VARCHAR(50)  NOT NULL,
    input_usd_per_million_tokens   NUMERIC(14,6) NOT NULL CHECK (input_usd_per_million_tokens >= 0),
    output_usd_per_million_tokens  NUMERIC(14,6) NOT NULL CHECK (output_usd_per_million_tokens >= 0)
);

CREATE TABLE provider_spend_cap (
    id                 UUID PRIMARY KEY,
    environment        VARCHAR(50)   NOT NULL,
    product_key        VARCHAR(100)  NOT NULL,
    monthly_cap_usd    NUMERIC(14,4) NOT NULL CHECK (monthly_cap_usd > 0),
    action             VARCHAR(20)   NOT NULL CHECK (action IN ('DOWNGRADE', 'REFUSE')),
    downgrade_model_key VARCHAR(255) REFERENCES provider_price(model_key),
    CONSTRAINT uq_provider_spend_cap_scope UNIQUE (environment, product_key),
    CONSTRAINT ck_provider_spend_cap_downgrade
        CHECK (action = 'REFUSE' OR downgrade_model_key IS NOT NULL)
);

CREATE TABLE provider_spend (
    id          UUID PRIMARY KEY,
    environment VARCHAR(50)   NOT NULL,
    product_key VARCHAR(100)  NOT NULL,
    month       DATE          NOT NULL CHECK (EXTRACT(DAY FROM month) = 1),
    spent_usd   NUMERIC(16,6) NOT NULL DEFAULT 0 CHECK (spent_usd >= 0),
    CONSTRAINT uq_provider_spend_scope_month UNIQUE (environment, product_key, month)
);

CREATE TABLE provider_spend_threshold (
    percent INT PRIMARY KEY CHECK (percent > 0)
);

CREATE TABLE provider_spend_alert (
    id          UUID PRIMARY KEY,
    environment VARCHAR(50)  NOT NULL,
    product_key VARCHAR(100) NOT NULL,
    month       DATE         NOT NULL,
    percent     INT          NOT NULL,
    fired_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_provider_spend_alert UNIQUE (environment, product_key, month, percent)
);

CREATE TABLE provider_spend_cap_change (
    id          UUID PRIMARY KEY,
    environment VARCHAR(50)  NOT NULL,
    product_key VARCHAR(100) NOT NULL,
    old_cap_usd NUMERIC(14,4),
    new_cap_usd NUMERIC(14,4) NOT NULL,
    old_action  VARCHAR(20),
    new_action  VARCHAR(20)  NOT NULL,
    changed_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO provider_spend_threshold (percent) VALUES (50), (80), (100);

-- Local models cost nothing; a hosted model's row is added with its published price. `do-inference`
-- is the dev default provider: priced at the higher of DigitalOcean's Llama 3.3 70B list prices
-- (0.65 USD per million tokens in and out, docs.digitalocean.com/products/gradient-ai-platform/
-- details/pricing, read 2026-11-12), so the spend is over- rather than under-counted until the
-- model behind the key is priced exactly.
INSERT INTO provider_price (model_key, capability, input_usd_per_million_tokens, output_usd_per_million_tokens) VALUES
('ollama', 'CHAT', 0, 0),
('do-inference', 'CHAT', 0.65, 0.65);

-- Dev defaults, deliberately small so the alert can be seen to fire; the founder sets the amounts.
INSERT INTO provider_spend_cap (id, environment, product_key, monthly_cap_usd, action, downgrade_model_key) VALUES
('c0000001-0000-0000-0000-000000000001', 'local', 'unattributed', 50, 'REFUSE', NULL),
('c0000001-0000-0000-0000-000000000002', 'local', 'verikora',     50, 'REFUSE', NULL),
('c0000001-0000-0000-0000-000000000003', 'local', 'folioviv',     50, 'REFUSE', NULL),
('c0000001-0000-0000-0000-000000000004', 'local', 'sovkora',      50, 'REFUSE', NULL),
('c0000001-0000-0000-0000-000000000005', 'dev',   'unattributed', 20, 'REFUSE', NULL),
('c0000001-0000-0000-0000-000000000006', 'dev',   'verikora',     20, 'REFUSE', NULL),
('c0000001-0000-0000-0000-000000000007', 'dev',   'folioviv',     20, 'REFUSE', NULL),
('c0000001-0000-0000-0000-000000000008', 'dev',   'sovkora',      20, 'REFUSE', NULL);
