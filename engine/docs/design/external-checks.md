# External checks: the `ExternalCheckConnector` port (sovkora-engine#73)

- **Status:** Proposed, for CTO validation before any code beyond the port and the mock
- **Date:** 2026-10-23
- **Issue:** [#73](https://github.com/krizaka/sovkora-engine/issues/73) · decision: yomizaka#132
- **Not in scope:** any live provider call. No real adapter until a pilot customer's go and the provider accounts exist.

## 1. The port

```java
public interface ExternalCheckConnector {
  String provider();                                  // "soquij" | "trustii" | "certn" | "mock"
  CheckResult verifyCourtRecords(CheckRequest request);  // purpose COURT_RECORDS
  CheckResult verifyCredit(CheckRequest request);        // purpose CREDIT
  CheckResult verifyIdentity(CheckRequest request);      // purpose IDENTITY
}
```

Package: `sovkora-core`, `domain.ports.outbound` (the port) and `domain.model.checks` (the records). The domain stays free of framework.

Records:

| Record | Fields | Notes |
|:---|:---|:---|
| `CheckRequest` | `CheckSubject subject`, `CheckKey key`, `UUID runId` | what the connector sees; the key is already resolved |
| `CheckSubject` | `subjectId` (opaque platform id), `lastName`, `firstName`, `birthDate` (nullable) | `toString` redacts the names and the date |
| `CheckKey` | `KeySource source` (`USER` \| `PLATFORM`), `value` | `toString` redacts the value |
| `CheckResult` | `CheckOutcome outcome` (`MATCH` \| `NO_MATCH` \| `PROVIDER_ERROR` \| `TIMEOUT` \| `REFUSED`), `Optional<CheckRefusal>` (`NO_CONSENT` \| `NO_KEY`, present iff `REFUSED`), `providerRef` (the provider's own case id, or null) | no record content at this layer: details belong to a later slice, under retention rules |
| `CheckPurpose` | `COURT_RECORDS`, `CREDIT`, `IDENTITY` | consent and pricebook are keyed on it |

The result never carries names, dates or the body of a court record. Matches are returned as an outcome, not as data.

## 2. Consent per purpose (Loi 25)

- Consent is a record per `(subjectId, purpose)` with a **statement version**, a grant time and an optional withdrawal time. It is the same shape as the pack consent (`pack.schema.json`, `consent`): consent is to a statement, and a new statement version stops the old consent.
- **Checked at every call**, by the service, before the connector is reached. No consent, or a withdrawn one: `REFUSED / NO_CONSENT`, no provider call, no pricebook hold, one audit line.
- Consent is never inferred from the purpose of the run or from a checkbox elsewhere in the flow.
- Credit (`CREDIT`) and identity (`IDENTITY`) each need their own consent; a court-records consent does not cover a credit report. The wording is for the company's counsel (**à confirmer avec le conseiller juridique**).

## 3. Key over platform key

Resolution, per call, in this order:

1. the **user's own key** for this provider, when the actor has stored one: `USER`;
2. the **platform key** for this provider, from the admin settings: `PLATFORM`;
3. neither: `REFUSED / NO_KEY`, no provider call.

- Keys live in the **existing encrypted settings** (sealed with the platform keyring, as the MCP tokens are, ADR-054 / #58). **Never** an environment variable. The port is `CheckKeyStore`; its implementation reads the sealed settings, and the source of keys is recorded in the audit.
- Provider rows (endpoint, enabled, price) are admin data in the database, not code.
- A user key is billed by the provider to the user. The platform still meters its orchestration fee (see §4).

## 4. Pricebook `CALL` metering

- Every executed call (match, no match, provider error, timeout) takes a **`CALL`-unit** hold on the pricebook row for `(capability=EXTERNAL_CHECK, purpose)`, settled on the outcome. This is the orchestration fee of BILLING_ARCHITECTURE §6.5 and §7, the same unit as the image and vision analysis.
- A `REFUSED` call takes **no** hold. A provider error or a timeout is settled at **0** (proposed: not charged to the customer, to be confirmed).
- The provider's own invoice (per search, per report) is a platform cost recorded on the audit line when the platform key was used; it is never billed to the user through the `CALL` row.
- **ADR-088 (the budgets) is not in the workspace I could read** (the ADRs stop at ADR-085 in `docs/adr/`). The budget rule of this design is therefore a placeholder until ADR-088 is confirmed.

## 5. Audit

One line per call, refused or not, in an `external_check_audit` table (the same discipline as `research_fetch`, ADR-064 / ADR-087):

`at`, `actor_id`, `run_id`, `provider`, `purpose`, `key_source`, `outcome`, `refusal`, `subject_ref` (a keyed HMAC-SHA-256 of the opaque platform id, its pepper read from the sealed settings through `CheckSubjectPepper`; no pepper of at least 32 bytes, no check — #83), `duration_ms`, `provider_ref`.

It never holds a name, a birth date, a court record or a credit figure. A timeout and a provider error are audited as such, with no provider message copied in.

## 6. Mapping of the providers

Dates are the date of reading (2026-10-23). Where a point is not public, it is marked.

**SOQUIJ, Service Web Plumitifs** (`verifyCourtRecords`, Québec, TAL and civil and penal plumitifs)
- Public description: SOQUIJ's mini guide on Plumitifs, criminal dockets (edition of 2019-04-11), <https://soquij.qc.ca/documents/file/documentation_azimut/mini_guide_plumitifs_dossiers_criminels2019-04-11.pdf>. Read through a search result: the PDF returned 404 when fetched on 2026-10-23, so the content is not re-verified. It describes a search by last name and first name, with the birth date optional, a charge for **each** search even for the same name, and no certificate of good conduct (RCMP).
- **API: not public.** The web service's terms, its authentication and any programmatic access: **à confirmer à l'ouverture du compte**.

**Trustii** (`verifyCredit`, `verifyIdentity`)
- The public site (<https://trustii.io>, read 2026-10-23) describes a machine-learning platform and competitions. It names no credit product, no identity product and no API documentation.
- Which credit bureau sits behind it (Equifax or TransUnion), the auth method, the price and the consent it requires: **à confirmer à l'ouverture du compte**.

**Certn** (`verifyCourtRecords` and `verifyIdentity` outside Québec; no credit in this mapping, **à confirmer**)
- The old API documentation (<https://docs.certn.co/api>, read 2026-10-23) states that all its endpoints are **deprecated as of 2026-04-13** and will be unavailable on **2026-08-05**, and points to the CertnCentric API.
- CertnCentric reference (<https://centric-api-docs.certn.co/>): the page returned its title only when fetched; a search result describes an `Authorization` header with an API key, a sandbox, and 60 requests per minute. The authentication is **to be confirmed**: a third-party directory describes OAuth 2.0 client credentials, which contradicts it. Trust Certn's own page, and confirm in writing: **à confirmer à l'ouverture du compte**.
- The webhook for status updates is described by a partner integration; its signing is **à confirmer**.

Routing (proposed, for CTO): a Québec applicant's court records go to SOQUIJ; the others to Certn; credit goes to Trustii only.

## 7. Testing without the network

- `MockConnector` answers from recorded fixtures keyed on the subject: a match per court category (Tremblay `TAL_NON_PAYMENT`, Côté `TAL_OTHER`, Morin `CRIMINAL`, Lavoie `CIVIL`), a no-match (Gagnon), a provider error (Roy) and a timeout (Bouchard). A match answers the fixture's opaque id (`mock-fixture-01`) as `providerRef`, never anything built from the subject (#83). It is the connector of the demo and of CI.
- **The connector contract** (`ExternalCheckConnectorContract`, test sources of `sovkora-core`): every adapter's test extends it and gives the subjects that reach each of its answers. It asserts that a connector never answers `REFUSED`, names a category only on a match, and that no field of the `CheckResult` nor of the `CheckAudit` the service writes holds the subject's names, date of birth (ISO, basic, dd/MM/yyyy, MM/dd/yyyy) or address (#83).

## 7. The checks as engine capabilities (#84)

- `ExternalCheckService` implements the inbound port `ExternalCheckCapability`: a blueprint step names `orazaka.core.checks.courtrecords`, `orazaka.core.checks.credit` or `orazaka.core.checks.identity` (`CheckPurpose.featureKey()`), and reads a `CheckStepResult`: the result and `consumption.calls`.
- Order of one call: subject reference (HMAC) → consent → key → **one `CALL` held** (`CheckMeter`, ADR-087 unit; the ADR-033 hold until ADR-088 lands) → provider → audit → **settle** on `MATCH` / `NO_MATCH` (`calls: 1`), **release** on `PROVIDER_ERROR` / `TIMEOUT` (`calls: 0`). A refusal takes no hold (`calls: 0`); a budget that cannot hold the call is `REFUSED / BUDGET_EXHAUSTED`, before the provider.
- `CheckResult.category` is closed: `TAL_NON_PAYMENT`, `TAL_OTHER`, `CRIMINAL`, `CIVIL`, `NONE`. A category other than `NONE` comes only with a match; credit and identity always answer `NONE`. Never free text, never the record's content.
- The executor that runs these keys in a run (sovkora-job-service) is wired by the host; until then nothing is registered and no check runs.
- **The meter** (krizaka/sovkora-packs#138) is `CreditHoldCheckMeter` (`sovkora-tools`, `CallMeteringAutoConfiguration`, present only where a billing `CreditAuthorizationClient` is): one `EXTERNAL_CHECK` hold whose `model_name` is the connector's provider (pricebook row `(EXTERNAL_CHECK, mock, CALL)`, krizaka-billing#57), correlated on the run (else the actor), with no job id and the pricebook's estimate. `settle` is `settleMeasured` with `calls: 1` under the key `external-check:<holdId>`; `release` passes the service's reason. A hold closes once. Insufficient credits, a grant that is not granted (released first) or no billable actor are `BudgetExhaustedException`, so `REFUSED / BUDGET_EXHAUSTED` before the provider; a billing outage propagates and no call is made. With billing off the grant reserves nothing and nothing is settled; a shadow grant settles and releases its real hold.
- **The pepper** is `SealedCheckSubjectPepperAdapter` (`sovkora-persistence-bridge`): the setting `checks.subject-pepper` of `orazaka_runtime_config`, value `v1.<keyId>.<wrapped key>.<nonce>.<ciphertext>` (`SecretSealer`, ADR-054, sealed for the scope `UUID.nameUUIDFromBytes("sovkora:platform:checks.subject-pepper")`), opening to the base64url text of at least 32 random bytes, `value_type` `sealed`. The seed leaves it **absent**: no row, no check. The operator writes it with `CheckSubjectPepperTool seal <keyfile>` (`sovkora-assets`), which prints the upsert and never the pepper. Absent, blank, in clear, unopenable (another scope, another keyring), no keyring in the host, or shorter than 32 bytes: empty, and the service refuses. Read at each check: a new pepper takes effect at once, and changes the references written after it.
- The code PR proves: refusal without consent (no call, one audit line); user key over platform key; platform key when no user key; refusal without any key; a provider error (an exception) and a timeout (an outcome) each audited with no leak; the four fixtures.

## 7.1 Adapters, routing and deferred answers (#73, slice 2)

- **Provider rows are admin data**: settings `checks.providers` (names) and `checks.provider.<name>.{handler-key,purposes,regions,priority,enabled,endpoint,price-micros}` (`SettingsCheckProviderCatalogAdapter`); the platform key is sealed at `checks.provider.<name>.key` for the scope of that provider (`SealedCheckKeyStoreAdapter`, ADR-054). A row is off until `enabled=true`.
- **`CheckRouter`** picks, per call, the enabled row that serves the purpose and the subject's region (`CheckCall.region`, e.g. `QC`; an empty `regions` serves any), lowest `priority` first, whose `handler-key` names an adapter (`soquij`, `trustii`, `certn`, `mock`). No ordering by class, no test on a name. Québec court records: SOQUIJ (priority 1, `QC`); the rest: Certn (priority 2); credit: Trustii.
- **Fail closed**: no row, a row without an HTTPS endpoint, or an adapter without its key is `REFUSED / NOT_CONFIGURED` (the adapter throws `ProviderNotConfiguredException`; the hold is released). Without a key `NO_KEY`, as before.
- **Deferred answers**: a provider that accepts a search answers `PENDING` with its reference; it is charged once at submission (one `CALL`), the run id stays on the audit line. `ExternalCheckService.collect(provider, purpose, call, providerRef)` polls the provider that accepted it (consent and key checked again, audited, no new hold). A webhook receiver belongs to the host and calls the same door once the providers' signing is confirmed.
- **Price** is the row's `price-micros`; the audit line carries it as `providerCostMicros` only when the platform key paid and the search was charged.
- **The wire format is provisional** (`HttpCheckConnector`): the providers publish no API we can rely on (section 6), so the tests run on recorded answers of the engine's neutral shape; the mapping onto each real API is the first task when the account is opened. No live call.
- A user's own key has no store yet (`SealedCheckKeyStoreAdapter.userKey` answers empty); the service already prefers it when a store provides one.

## 8. Open points for the CTO

1. Settled provider charge on error and timeout (proposed: 0).
2. The consent statement and its versions for each purpose (counsel).
3. ADR-088 (budgets): confirm the number and the rule.
4. Whether a user key still carries the platform's `CALL` fee (proposed: yes).
