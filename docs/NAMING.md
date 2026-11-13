# Naming — the retired code names (krizaka-internal#2684)

The product is **Sovkora**. Its former code names (the first one, `orazaka`, and the product names that shared the repository
before it was cut: `yomizaka`, `akinaza`, `kachizaka`) are leaving the code wave by wave. The guard
`scripts/check-code-names.mjs` (CI job « Repository tooling, layout and docs ») counts them over `git ls-files`, paths and
contents, case-insensitive, lockfiles excluded:

- `scripts/code-names.ratchet.json` holds the count per top-level folder. A count may only fall; a rise fails the build.
- `scripts/code-names.allow.json` holds what is kept on purpose. Each entry has a `reason` and the `row` below that justifies it.
  An entry that matches nothing, or whose row is not in the table, fails.
- After a rename lowers a count: `node scripts/check-code-names.mjs --update`, commit the ratchet. `--update` never raises a count.

At the end (wave W14) the ratchet file is deleted: zero tolerance, outside the rows below.

## What is kept, and why

| Id | Kept identifier | Why it stays | Ends when |
|:---|:---|:---|:---|
| K1 | Applied `infra/migrations/*.sql` | An applied migration is never edited. | Never (history). |
| K2 | Flyway `V1__*` baselines | The checksum is recorded in every database that ran them; the rename is a forward `V2__names.sql`. | Never (history). |
| K3 | npm tombstones `@krizaka/orazaka-cli`, `orazaka`, `@krizaka/orazaka-shared`, `@krizaka/orazaka-design-system`, `@krizaka/orazaka-*-client` (deprecated, pointing at the sovkora packages) | Published versions cannot be removed. | Never. |
| K4 | krizaka-java billing V1/V2 migrations; DigitalOcean ingress `orazaka-dev-averr.ondigitalocean.app`; Firebase project id `orazaka`; past evidence URLs and old issue links | Other repositories' applied history, or an external id that cannot be renamed. | W13 decides the Firebase id; the rest stays. |
| K5 | The guard's own files (`scripts/check-code-names.mjs`, `scripts/test/code-names.test.mjs`) and this page | They name what they forbid. | Never. |
| K6 | `com.krizaka.orazaka` (iOS/Android identifier) | Replaced by `com.krizaka.sovkora` in W13 (founder decision 2026-11-12); not kept after it. | W13. |

`orazaka.com` is left to lapse (founder, 2026-11-12): the table keeps no row for it, and nothing we ship may resolve it
before it expires (W12).

## How to rename (expand → switch → contract)

1. **Expand**: accept both names (a Spring key read under both, a header, a queue regex, a view under the old table name). Ship it.
2. **Switch**: move every producer and every consumer to the new name. Ship it, run the journeys.
3. **Contract**: remove the old name and the compatibility read. Then lower the ratchet (`--update`).

A name that is stored (a table, a queue, a capability id in a database row) goes through a forward Flyway migration, never an
edit of an applied one. A rename that touches a security boundary (the platform context namespace) has a test that a
caller-supplied key under either name is dropped.
