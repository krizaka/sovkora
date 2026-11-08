// ADR-085: the operator's queue is derived from the events the services already emit — never a store of its own.
// A task is open while the failure stands: a refused or failed action with no later success on the same target, a failed job.
import { test } from "node:test";
import assert from "node:assert/strict";
import { openTasks } from "../features/tasks/utils/tasks.ts";
import type { AuditRow } from "../features/accounts/utils/audit.ts";

const NOW = Date.parse("2026-10-21T12:00:00Z");
const row = (over: Partial<AuditRow>): AuditRow => ({
  key: "identity:1",
  source: "identity",
  at: "2026-10-21T10:00:00Z",
  actor: "ada",
  action: "admin.user_suspended",
  targetId: "u1",
  detail: "",
  requestId: "r-1",
  outcome: "ok",
  ...over,
});
const job = (over: object) => ({
  id: "j1",
  userId: "u",
  featureKey: "orazaka.core.media.image",
  status: "FAILED",
  payload: {},
  result: { error: "engine did not answer" },
  createdAt: "2026-10-21T11:00:00Z",
  updatedAt: "2026-10-21T11:00:30Z",
  ...over,
});

test("a refused or failed action is a task; an ok one is not", () => {
  const tasks = openTasks(
    [row({ key: "identity:2", outcome: "refused", detail: "SELF" }), row({ key: "identity:3" })],
    [],
    NOW,
  );
  assert.deepEqual(
    tasks.map((t) => [t.kind, t.key]),
    [["action", "identity:2"]],
  );
  assert.equal(tasks[0].title, "admin.user_suspended");
  assert.equal(tasks[0].why, "refused: SELF");
});

test("a later success on the same action and target closes it", () => {
  const rows = [
    row({
      key: "billing:1",
      source: "billing",
      action: "PUT /plans/{id}",
      targetId: "pro",
      outcome: "failed",
      at: "2026-10-21T09:00:00Z",
    }),
    row({
      key: "billing:2",
      source: "billing",
      action: "PUT /plans/{id}",
      targetId: "pro",
      outcome: "ok",
      at: "2026-10-21T09:30:00Z",
    }),
  ];
  assert.deepEqual(openTasks(rows, [], NOW), []);
});

test("a failed job of the last day is a task with its message; older ones and the others are not", () => {
  const tasks = openTasks(
    [],
    [job({}), job({ id: "j2", updatedAt: "2026-10-19T11:00:00Z" }), job({ id: "j3", status: "COMPLETED" })],
    NOW,
  );
  assert.deepEqual(
    tasks.map((t) => [t.kind, t.key]),
    [["job", "job:j1"]],
  );
  assert.equal(tasks[0].why, "engine did not answer");
});

test("newest first, actions and jobs together; the window is 7 days for actions", () => {
  const tasks = openTasks(
    [
      row({ key: "identity:9", outcome: "failed", at: "2026-10-21T11:30:00Z" }),
      row({ key: "identity:8", outcome: "failed", at: "2026-10-10T11:30:00Z" }),
    ],
    [job({})],
    NOW,
  );
  assert.deepEqual(
    tasks.map((t) => t.key),
    ["identity:9", "job:j1"],
  );
});
