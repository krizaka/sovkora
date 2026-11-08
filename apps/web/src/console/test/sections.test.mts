// Dev preview `admin.ux-review` (orazaka-web-client#125 family): the console's navigation shape is a
// tested contract — the operator's jobs map to about five places, one concept each, every screen reachable once.
import { test } from "node:test";
import assert from "node:assert/strict";
import { CONSOLE_GROUPS, activeGroup, consoleRoutes } from "../core/config/sections.ts";

test("five places at most: what is happening, what is running, who and what they hold, what is offered, what was done", () => {
  assert.ok(CONSOLE_GROUPS.length <= 5, `${CONSOLE_GROUPS.length} top-level sections`);
  assert.deepEqual(
    CONSOLE_GROUPS.map((g) => g.id),
    ["supervision", "operations", "accounts", "catalogue", "audit"],
  );
});

test("every screen of the console is reachable exactly once", () => {
  const routes = consoleRoutes();
  assert.deepEqual(
    [...routes].sort((a, b) => a.localeCompare(b)),
    ["/", "/audit", "/billing", "/engine", "/law", "/models", "/operations", "/studios", "/tasks", "/users"],
  );
  assert.equal(new Set(routes).size, routes.length);
});

test("a menu of two never mixes concepts: operations hold the running platform and its tasks, accounts people and billing, the catalogue what the engine offers (models, studios, the rules of the law)", () => {
  const children = (id: string) => CONSOLE_GROUPS.find((g) => g.id === id)?.children.map((c) => c.id);
  assert.deepEqual(children("operations"), ["operations", "tasks", "engine"]);
  assert.deepEqual(children("accounts"), ["users", "billing"]);
  assert.deepEqual(children("catalogue"), ["models", "studios", "law"]);
  for (const g of CONSOLE_GROUPS)
    assert.ok(g.children.length === 0 || g.children.length >= 2, "a group of one is a link");
});

test("the section lit is the one the page belongs to, the home only on `/` itself", () => {
  assert.equal(activeGroup("/")?.id, "supervision");
  assert.equal(activeGroup("/users")?.id, "accounts");
  assert.equal(activeGroup("/billing/plans")?.id, "accounts");
  assert.equal(activeGroup("/console/studios")?.id, "catalogue");
  assert.equal(activeGroup("/models")?.id, "catalogue");
  assert.equal(activeGroup("/law")?.id, "catalogue");
  assert.equal(activeGroup("/console/law")?.id, "catalogue");
  assert.equal(activeGroup("/audit")?.id, "audit");
  assert.equal(activeGroup("/login"), null);
});
