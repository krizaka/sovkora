// The sign-in flag of the public admin site: closed unless the deployment says otherwise.
import { test } from "node:test";
import assert from "node:assert/strict";
import { authClosed, isBackendPath, parseFlag, readAuthFlags } from "../core/config/auth-flags.ts";

test("a flag is off unless it is explicitly on", () => {
  for (const v of [undefined, "", "false", "0", "no", "off", "maybe"]) assert.equal(parseFlag(v), false);
  for (const v of ["true", "TRUE", " 1 ", "yes", "On"]) assert.equal(parseFlag(v), true);
});

test("the site is closed by default, with no environment at all", () => {
  assert.deepEqual(readAuthFlags({}), { login: false, signup: false });
  assert.equal(authClosed(readAuthFlags({})), true);
});

test("AUTH_CONSOLE_LOGIN_ENABLED opens it; the public site's AUTH_LOGIN_ENABLED does not", () => {
  assert.equal(readAuthFlags({ AUTH_LOGIN_ENABLED: "true" }).login, false);
  const flags = readAuthFlags({ AUTH_CONSOLE_LOGIN_ENABLED: "true" });
  assert.equal(flags.login, true);
  assert.equal(authClosed(flags), false);
});

test("the BFF routes are recognised", () => {
  for (const p of ["/api/v1/billing/plans", "/api/auth/session", "/api/v1"]) assert.equal(isBackendPath(p), true);
  for (const p of ["/login", "/", "/api/health", "/api/v10"]) assert.equal(isBackendPath(p), false);
});
