# Knowledge service

> `services/knowledge/sovkora-knowledge-service` of [Sovkora](../../../README.md), one repository (maven, ai layer). Depends on `build`. Licence: [FSL-1.1-ALv2](../../../LICENSE).

Knowledge & RAG retrieval service (pgvector) with asynchronous indexing.

## What it provides

Retrieval over pgvector (port `8084`): `/internal/v1/knowledge/retrieve`,
`/internal/v1/knowledge/sources/search`, and AMQP-driven RAG indexing. Own database
`orazaka_knowledge_db`: the schema is Flyway's `V1__knowledge_baseline.sql`
(`src/main/resources/db/migration`), applied at startup; `infra/initdb/40-knowledge.sql` only
creates the role, the database and the `vector` extension for the local compose.

## CI

The pipeline is the organisation's reusable [`java-ci.yml`](https://github.com/krizaka/.github/blob/main/.github/workflows/java-ci.yml): this service is verified inside the Sovkora workspace against its upstreams built from source. A change that touches only documentation skips the build and still reports green.

<!-- krizaka:workflows:start -->
## Access (ADR-091)

Every read is made for a verified principal: the `act` claim of the core's signed service token
(tenant, subject, roles, groups). A source carries `tenant_id` and `acl_principals`
(`role:…`, `group:…`, `user:…`; default the owner alone); the tenant and the ACL are part of the SQL
`WHERE` and of the vector filter, so an unreadable passage is never fetched. A call that acts for
nobody is refused (403) and audited in `knowledge_access_audit` (principal, resource id, reason — never
the content). `PUT /internal/v1/knowledge/sources/{id}/acl` lets an administrator of the tenant change
who may read a source.

## Build and test

```bash
./mvnw verify -pl services/knowledge/sovkora-knowledge-service -am      # from the repository root: this module and what it needs
./mvnw verify -pl services/knowledge/sovkora-knowledge-service -amd     # this module and everything that depends on it
```

CI runs it on every pull request that touches `services/knowledge/sovkora-knowledge-service/` (ADR-029: one repository, one CI).
