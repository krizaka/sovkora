---
title: Interceptor Registry
description: Cross-cutting pipeline interceptors and their DB-driven order.
category: Core
order: 5
generated: true
---

# Interceptor Registry

| Order | Interceptor | Concern | Enabled |
|:--|:---|:---|:--|
| core 1 | `SafetyInterceptor` | validation | 🔒 non-bypassable |
| core 2 | `ScopeGuardInterceptor` | governance | 🔒 non-bypassable |
| core 3 | `UserContextResolver` | context | 🔒 non-bypassable |
| core 4 | `SystemContextInjector` | context | 🔒 non-bypassable |
| core 5 | `RagInterceptor` | enrichment | 🔒 non-bypassable |
| 4 | `EntitlementInterceptor` | governance | ✅ |
| 5 | `McpInterceptor` | enrichment | ✅ |
| 6 | `BrandContextInterceptor` | enrichment | ✅ |
| 7 | `MemoryInterceptor` | enrichment | ✅ |
| 8 | `RefinerInterceptor` | reformulation | ✅ |
| 8 | `SemanticCacheInterceptor` | cache | — |
| 9 | `RouterInterceptor` | reformulation | ✅ |
| 9 | `ToolInterceptor` | tooling | ✅ |
| 11 | `UserContextInterceptor` | context | ✅ |
| 12 | `CitationVerifierInterceptor` | validation | — |
| — | `ClosedLoopValidationInterceptor` | validation | — |
| — | `CostShieldInterceptor` | validation | — |
| — | `DynamicMemoryCondenser` | enrichment | — |
| — | `HybridRagResolver` | enrichment | — |
| — | `LanguageAlignmentInterceptor` | translation | — |
| — | `SemanticRouterInterceptor` | reformulation | — |
