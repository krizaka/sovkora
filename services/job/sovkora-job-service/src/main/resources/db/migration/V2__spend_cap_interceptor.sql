-- The monthly provider-spend cap (krizaka-internal#3368) sits with the entitlement gate, at order 4:
-- before anything that reaches a model, so a refusal costs nothing. Same order, created later, so
-- it runs after the plan check. Its rows (caps, prices, spend) are in the governance schema.
INSERT INTO pipeline_interceptor_config (interceptor_key, display_label, execution_order, is_enabled, description) VALUES
('SpendCapInterceptor', 'Spend Cap Interceptor', 4, TRUE, 'Monthly provider-spend cap per environment and product key — refuses or downgrades at the cap; fails closed (krizaka-internal#3368)')
ON CONFLICT (interceptor_key) DO NOTHING;
