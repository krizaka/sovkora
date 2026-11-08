// The console signs its session with its own key: never the public site's, and fail closed in production.
import { test } from "node:test";
import assert from "node:assert/strict";
import { consoleAuthKey } from "../core/auth/session-key.ts";
import { SESSION_COOKIE } from "../core/auth/session-cookie.ts";

test("the key is AUTH_CONSOLE_SECRET, trimmed", () => {
  assert.equal(consoleAuthKey({ AUTH_CONSOLE_SECRET: " abc " }), "abc");
});

test("the public site's key is never used", () => {
  assert.equal(consoleAuthKey({ NEXTAUTH_SECRET: "public" }), undefined);
  const a = consoleAuthKey({ NEXTAUTH_SECRET: "public", NODE_ENV: "production" });
  assert.ok(a && a !== "public");
});

test("in production an unset key is a throw-away one: no session survives, none is shared", () => {
  assert.notEqual(consoleAuthKey({ NODE_ENV: "production" }), consoleAuthKey({ NODE_ENV: "production" }));
});

test("the console's cookie is not next-auth's default one", () => {
  assert.notEqual(SESSION_COOKIE, "next-auth.session-token");
});
