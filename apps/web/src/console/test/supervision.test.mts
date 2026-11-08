// The supervision's figures, tested on their own (Node's test runner strips the types; no bundler, no DOM).
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  byCapability,
  consumers,
  jobsPerDay,
  latencyByCapability,
  usageTotals,
} from "../features/supervision/utils/supervision.ts";

const usage = [
  {
    capability: "IMAGE",
    modelName: null,
    events: 24,
    creditsCharged: 2400,
    refusals: 0,
    enforcedRefusals: 0,
    gpuSeconds: 1586,
  },
  {
    capability: "CHAT",
    modelName: "a",
    events: 20,
    creditsCharged: 60,
    refusals: 4,
    enforcedRefusals: 0,
    gpuSeconds: null,
  },
  {
    capability: "CHAT",
    modelName: "b",
    events: 9,
    creditsCharged: 45,
    refusals: 1,
    enforcedRefusals: 1,
    gpuSeconds: null,
  },
];

test("totals: the refusal rate is a share of the attempts, not of the requests served", () => {
  assert.deepEqual(usageTotals(usage), { requests: 53, refusals: 5, credits: 2505, refusalRate: 5 / 58 });
  assert.equal(usageTotals([]).refusalRate, 0);
});

test("capabilities: models merged, written for a person, most credits first", () => {
  assert.deepEqual(byCapability(usage), [
    { capability: "Image", events: 24, refusals: 0, credits: 2400 },
    { capability: "Chat", events: 29, refusals: 5, credits: 105 },
  ]);
});

test("consumers: an actor is written short", () => {
  assert.deepEqual(consumers([{ actorId: "1c10ee07-1b3a", events: 51, creditsCharged: 3710 }]), [
    { actor: "1c10ee07…", credits: 3710, events: 51 },
  ]);
});

const job = (over: object) => ({
  id: "j",
  userId: "u",
  featureKey: "orazaka.core.media.image",
  status: "COMPLETED",
  payload: {},
  result: {},
  createdAt: "2026-10-11T10:00:00Z",
  updatedAt: "2026-10-11T10:01:00Z",
  ...over,
});

test("jobs per day: every day of the window, by outcome", () => {
  const rows = jobsPerDay(
    [job({}), job({ status: "FAILED" }), job({ status: "PROCESSING", createdAt: "2026-10-01T10:00:00Z" })],
    3,
    new Date("2026-10-11T12:00:00Z"),
  );
  assert.equal(rows.length, 3);
  assert.deepEqual(rows.at(-1), { day: rows.at(-1)!.day, completed: 1, failed: 1, running: 0 });
});

test("latency: the median of the completed jobs, per capability, slowest first", () => {
  assert.deepEqual(
    latencyByCapability([
      job({}),
      job({ updatedAt: "2026-10-11T10:03:00Z" }),
      job({ featureKey: "x.speech", updatedAt: "2026-10-11T10:00:02Z" }),
      job({ status: "FAILED" }),
    ]),
    [
      { capability: "image", seconds: 120, samples: 2 },
      { capability: "speech", seconds: 2, samples: 1 },
    ],
  );
});

test("jobs per day: the days are UTC days, so the server and the browser draw the same labels in any time zone (React #418 on /)", () => {
  // 02:30 UTC on the 11th is still the 10th in Montréal: a local day key made the server (UTC) and the browser disagree.
  const now = new Date("2026-10-11T02:30:00Z");
  const rows = jobsPerDay([], 3, now);
  assert.deepEqual(
    rows.map((r) => r.day),
    ["2026-10-09", "2026-10-10", "2026-10-11"],
  );
  const late = jobsPerDay([job({ createdAt: "2026-10-11T02:10:00Z" })], 1, now);
  assert.equal(late[0].completed, 1);
});
