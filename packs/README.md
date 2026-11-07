# Sovkora packs

> `packs` of [Sovkora](../README.md), one repository (packs, content layer). Depends on nothing else of Sovkora. Licence: [Apache-2.0](LICENSE).

Reference packs for the Sovkora Studio marketplace (document validation, media, prospection, real-estate, wellbeing…) and the pack manifest schema.

## What it provides

Reference **packs** for the Sovkora Studio marketplace. A pack is a directory with a `pack.yaml`
manifest validated against [`pack.schema.json`](pack.schema.json): i18n, studios (semver'd blueprint
DAGs) and, optionally, its own worker.

| Pack                   | Content                                                                                                                                                                                                                                                                                              |
| :--------------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `data-analytics`       | Recommendations & insights — the recommender of ADR-078 (API scopes `reco:sync` / `reco:read`) and product analytics (ADR-081), served by the knowledge service; Data-driven UI (ADR-082), Video understanding and Creator suggestions Studios                                                       |
| `document-validation`  | Document Verification — Compliance Check (rules, sources, passages) and Document Authenticity (a PDF's signals of tampering), with its worker                                                                                                                                                        |
| `echo-toolkit`         | The Tier-W reference used by the platform tests — `DRAFT`, never on the marketplace shelf                                                                                                                                                                                                            |
| `orazaka-media`        | Media generation studios                                                                                                                                                                                                                                                                             |
| `outbound-prospection` | Prospection workflows                                                                                                                                                                                                                                                                                |
| `realestate-studio`    | Real-estate reels                                                                                                                                                                                                                                                                                    |
| `tenant-screening-qc`  | Tenant screening, Québec — a gated saga (documents, then court records, then credit) whose cut-offs are manager rules and whose credits are debited per stage; mocks only, `DRAFT`                                                                                                                   |
| `trade-showcase`       | Trade showcase studio                                                                                                                                                                                                                                                                                |
| `wellbeing`            | Wellbeing — guided journaling, session preparation, mood tracking (`REGULATED`; never "therapy": what a person brings to a professional, not a care); `wellbeing/listening/` holds the listening moment's data — presences, voices, the ear — and the risk fixtures the crisis guard is tested with) |

```bash
orazaka pack validate ./orazaka-packs/<bundle> --offline
orazaka pack install --all ./orazaka-packs
```

See [CONTRIBUTING.md](CONTRIBUTING.md) to write a pack.

## Golden cases

Each Studio outside document-validation ships **3–5 fictional inputs**, illustrative JSON outputs,
and executable output assertions in `<pack>/tests/golden/<studio>.json`. Never use customer
documents, messages, photos, or recordings. Media references use synthetic test assets.

```bash
npm ci
npm run test:golden
```

CI runs the shared harness on every PR (including prompt/model changes): it validates inputs
against the actual blueprint's JSON Schema, checks output assertions, and fails for uncovered
Studios. `golden-ratchet.json` lists the two document-validation Studio exceptions: their existing
deterministic document/ruleset goldens still run through `document-validation/worker/test_golden.py`.
An exception must be removed when Studio cases land; CI compares the list with the PR base and
rejects additions or renamed exceptions. New packs are discovered automatically, including DRAFT packs.

These offline checks validate fixture contracts, **not model quality**. The live-stack runner
uses the same cases and assertions against the Studio API; see [CONTRIBUTING.md](CONTRIBUTING.md).
The standalone CI does not provision inference or a Studio stack. The trusted `golden-stack`
CI lane runs actual cases on an operator-provisioned runner after pushes to `main` when enabled,
or via workflow dispatch; it never runs untrusted PR code.

## Build and test

```bash
cd packs && npm ci && npm test   # the pack schema and the goldens
```
Each pack worker's Python suites run in CI (`.github/workflows/ci.yml`, job `packs-workers`).

CI runs it on every pull request that touches `packs/` (ADR-029: one repository, one CI).
