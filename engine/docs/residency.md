# Where the engine runs and what it keeps (data residency)

Read on **2026-10-30** from the infrastructure as deployed (`krizaka-infra` `stacks/dev/sovkora`, the DigitalOcean account) and from the
code of this repository and `sovkora-packs`. It describes the **dev deployment** (`dev.sovkora.com`, the app `sovkora-dev`); no
production deployment exists yet, so nothing here is a claim about production. Verikora's `docs/privacy/transfers.md` links here
(krizaka/yomizaka#102, ADR-0014 slice O7). A change to the infrastructure changes this page in the same pull request.

## Where it runs

| Part | What | Region | Source |
| :--- | :--- | :--- | :--- |
| Edge, services, validation worker | App Platform app `sovkora-dev`: `core` (one image: the JVM services **and** the document-validation worker), `web`, `gpu-relay` | **Toronto (`tor`), Canada** | app spec · `stacks/dev/sovkora/main.tf` (`region = "tor1"`) |
| Broker | RabbitMQ droplet `sovkora-broker-dev`, private network only (port 5671 from the VPC) | **Toronto (`tor1`), Canada** | `stacks/dev/sovkora/main.tf` · droplet list |
| Database | Managed PostgreSQL 18 cluster `krizaka-dev-pg` (runs, jobs, audit) | **Toronto (`tor1`), Canada** | database list |
| Database backups | Daily backups of that cluster, made by DigitalOcean | **Not set by us.** Taken with the cluster in `tor1`; DigitalOcean's own statement of the backup location is not verified here | database backups list |
| Job files (outputs, archived `input.json` / input file, `result.json`) | Bunny Storage zone `sovkora-media-dev`, endpoint `ny.storage.bunnycdn.com` | **New York, United States: outside Canada** | app spec (`MEDIA_STORAGE_*`) · `docs/DEPLOY_DEV.md` |
| GPU batch (image and video renders) | A RunPod pod, rented for the length of a batch | Any datacentre RunPod offers, not fixed | `scripts/gpu-dev.sh runpod batch` |

A document check (`document-validation`) never uses the GPU batch: its worker is pure Python in `core` (no model, no clock for the
deterministic half). The only model call is the pack's optional judgement step, and **which provider answers it is data** (the dev
catalogue's routing), not a property of this page: if a commercial provider (OpenAI, Anthropic, Gemini) is routed there, the
text of the rules to judge and the quoted passages leave Canada for that provider. This page does not state which one is set.

## What is kept of a SENSITIVE run

`document-validation` declares `regulatoryClass: SENSITIVE`. The engine then applies (ADR-051, ADR-064, ADR-065):

- **Bytes**: the document travels in the run input (`documentBase64`) through the broker to the worker and is read in memory; the worker
  writes nothing of it to disk. The archived input and the result also exist as files in the job prefix of the Bunny zone above
  (that is the part in New York), until the job is purged.
- **Job purge**: `job-service` deletes a SENSITIVE or REGULATED job's directory, then its row, **at terminal state**, with a tombstone that
  removes a directory written after the purge (`JobRetentionService`, `JobListener`, `ProtectedJobRetentionIT`; audit item 27).
- **Run record**: kept in `krizaka-dev-pg` for `retention.sensitive-run-days` = **30 days** (platform default,
  `infra/migrations/2026-09-06-sensitive-controls.sql`; an installation may lower it, never raise it). The ordinary window is
  `retention.run-days` = 90.
- **Input file at terminal state** (krizaka-restricted#279): a `studio_run_retention_policy` row whose mode is `PURGE_AT_TERMINAL` names the
  input fields that carry a file. When the run ends (succeeded, failed or cancelled), those fields are removed from the run's inputs and
  outputs, its steps' outputs, its events and the body of its dispatch commands in the outbox; the run id, status, counters, input
  fingerprint and findings stay, and `studio_run_purge_audit` gets one append-only `run.inputs_purged` row with the field names, never their
  content. The schema seeds `document-authenticity` → `documentBase64` (Flyway `V4__run_input_retention.sql`). A row applies to one tenant
  (the run's actor) or to every tenant (`*`), and the strictest wins: a tenant's `KEEP` never lifts a purge that applies to everyone. The
  terminal transition purges in its own transaction; `RunInputPurgeSweeper` is the safety net, every minute. A purged run cannot be resumed.
- **Backups keep what was purged.** A purge or a retention delete changes the live database only. The daily backups of `krizaka-dev-pg`
  (see the table above) still hold a run's file, as it was when the backup was taken, until that backup ages out under the provider's own
  backup retention, which this repository does not set. A restore brings the file back, and it is purged again only by the next sweep of
  the safety net. The backup retention to declare in a privacy notice is a production decision: TO VALIDATE (founder, with the provider).
- **Audit**: append-only, without the text: the refusal text is no longer stored (`studio_run_audit.detail` is gone, ADR-065 §2.2), and
  logs carry shapes, never content (`LoggedContentRules`, ADR-064 §4).
- **Prompt cache**: a turn that declares a scope guard, a safety control or a subject guard is never put in a provider's cache
  (`TurnSensitivity`, `PromptCachePolicy`).

## What is outside Canada, and what that means

1. **Job files in Bunny's New York zone.** For a Canadian-only claim, either Verikora's privacy impact assessment (EFVP, Law 25 s. 17)
   covers this transfer before production, or the zone moves to a Canadian region (the storage zone is configuration:
   `MEDIA_STORAGE_ZONE`, `MEDIA_STORAGE_ENDPOINT`).
2. **The judgement step's provider**, if a commercial one is routed (see above).
3. **GPU batches**, which a document check does not use.

Everything else in the table is in Canada.
