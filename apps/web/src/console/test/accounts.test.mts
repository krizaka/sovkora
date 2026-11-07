import { test } from "node:test";
import assert from "node:assert/strict";
import { erasureArmed, refusalMessage, stateActions } from "../features/accounts/utils/accounts.ts";

test("refusals are worded from their stable code", () => {
  assert.match(refusalMessage("SELF", 409), /own account/);
  assert.match(refusalMessage("LAST_ADMIN", 409), /last administrator/);
  assert.match(refusalMessage("CONFIRM_MISMATCH", 409), /does not match/);
  assert.match(refusalMessage(null, 403), /administrator/);
  assert.match(refusalMessage(null, 404), /no longer exists/);
});

test("the action offered undoes the account's current state", () => {
  assert.deepEqual(stateActions({ enabled: true, admin: false }), { status: "suspend", role: "grant" });
  assert.deepEqual(stateActions({ enabled: false, admin: true }), { status: "restore", role: "withdraw" });
});

test("the erasure is armed only by the account's own e-mail, case and spaces aside", () => {
  assert.equal(erasureArmed("  Ada@Example.test ", "ada@example.test"), true);
  assert.equal(erasureArmed("ada@example.te", "ada@example.test"), false);
  assert.equal(erasureArmed("", "ada@example.test"), false);
});

test("an audit line carries the request it was made in (ADR-085), and an older server's line without one still parses", async () => {
  const { AdminAuditEntrySchema } = await import("../services/platform.schemas.ts");
  const line = {
    id: 1,
    at: "2026-10-21T01:00:00Z",
    actor: "ada@example.test",
    action: "admin.user_suspended",
    targetId: "u1",
    detail: null,
  };
  assert.equal(
    AdminAuditEntrySchema.parse({ ...line, requestId: "9f1c2d3e-aaaa-bbbb-cccc-123456789abc" }).requestId,
    "9f1c2d3e-aaaa-bbbb-cccc-123456789abc",
  );
  assert.equal(AdminAuditEntrySchema.parse(line).requestId, undefined);
});

test("an audit line carries its outcome: ok, refused or failed (an older server's line is ok)", async () => {
  const { AdminAuditEntrySchema } = await import("../services/platform.schemas.ts");
  const line = {
    id: 2,
    at: "2026-10-21T01:00:00Z",
    actor: "ada@example.test",
    action: "admin.user_suspended",
    targetId: "u1",
    detail: "SELF",
  };
  assert.equal(AdminAuditEntrySchema.parse({ ...line, outcome: "refused" }).outcome, "refused");
  assert.equal(AdminAuditEntrySchema.parse(line).outcome, "ok");
});
