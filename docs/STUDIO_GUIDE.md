---
title: 'Run a Studio'
description: 'Install a Studio, run it from the web app or the terminal, and read its result and its run record.'
category: Guides
order: 3
---
# Run a Studio

A Studio is a ready-made workflow behind one form. You fill the form, Sovkora runs the steps, and you get a result and a run record.

## From the web app

1. Open **Studios** and pick one, for example image generation.
2. Fill the form. The page shows the price estimate before you run anything.
3. Press **Run**. The run appears in **Jobs** with its progress.
4. Open the result when it is ready. Its run id, duration and cost are listed beside it.

## From the terminal

```bash
npx orazaka studio list                     # the catalogue
npx orazaka studio install <studioKey>      # add one to your workspace
npx orazaka studio run <installation> --inputs key=value
npx orazaka studio logs <runId>             # each step and its result
```

Pipes make a list: `--inputs tags=a|b|c`. For text with commas, use `--inputs-file inputs.json`.

## Check before you spend

`POST /api/v1/studios/{studioKey}/policy-check` answers what the [content policy](CONTENT_POLICY.md) would do with your inputs. It holds no credits and calls no provider.

## Follow a run

Use `GET /api/v1/studios/runs/{runId}/events` to follow one run, or
`GET /api/v1/studios/runs/events` to follow the caller's in-flight runs on one SSE connection. Each
`run` event carries the current run snapshot; the terminal `done` event carries the final snapshot.
`Last-Event-ID` resumes a per-run stream from its durable, monotonically increasing event number.
Idle streams send a 15-second heartbeat; the open streams per actor are a database setting.

For server-to-server completion notifications, configure an endpoint in **Settings → Webhooks** or
use `POST /api/v1/studios/webhooks`. The endpoint URL must use HTTPS and resolve to public addresses.
The secret is shown only when created or rotated. Verify the Standard Webhooks signature over the
exact raw request body using `webhook-id`, `webhook-timestamp`, and `webhook-signature` (HMAC-SHA256,
`v1,<base64>`); during rotation, accept either active signature. Treat delivery as at-least-once and
deduplicate on the stable `webhook-id`.

Failed deliveries are retried with exponentially increasing delays (defaults: 30 seconds, doubling
to a one-hour cap, up to eight attempts), then retained as `DEAD`; successful and failed attempts are
visible from `GET /api/v1/studios/webhooks/{id}/deliveries`. Retry count, initial delay, delay cap,
timeout, and endpoint limits are database-backed runtime settings.

## Add your own

A pack is a folder with a `pack.yaml` and its blueprints. The [packs reference](_generated/PACKS.md) lists every manifest field and shows how to write, validate and install one.

## Next

- [Concepts](CONCEPTS.md): where a run fits in the engine.
- [Packs and Studios reference](_generated/PACKS.md): the full catalogue.
- [Jobs and schedules](_generated/JOBS.md): runs without a person waiting.
