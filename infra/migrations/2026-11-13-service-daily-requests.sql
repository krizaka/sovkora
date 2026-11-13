-- krizaka-internal#3423: the edge applies each owner's daily request quota from the token exchange
-- (`rate_limits.daily_requests`, krizaka-users V9). The products' service accounts sit on the `service` tier, so
-- Verikora's `svc_verikora` gets its ceiling here; a tier with NULL falls back to AUTH_API_KEY_DAILY_REQUESTS.
-- Run on krizaka_users_db once krizaka-users carries V9. Idempotent: only fills a tier that has none, never
-- overwrites a value an operator set through PUT /api/v1/admin/rate-limits/{tier}/daily-requests.
UPDATE rate_limits SET daily_requests = 50000
WHERE tier_key = 'service' AND daily_requests IS NULL;
