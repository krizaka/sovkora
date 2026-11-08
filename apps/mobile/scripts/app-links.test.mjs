// node --test scripts — app.json carries what the claimed paths need: the iOS associated domain and an Android
// autoVerify intent filter covering every path of app-links.json (the file the web app serves its AASA / assetlinks from).
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { checkAppJson, isNotFoundOnly, normalizeRoute } from "./check-app-links.mjs";

const read = (f) => JSON.parse(readFileSync(new URL(`../${f}`, import.meta.url), "utf8"));

test("app.json passes the app-links rule (associated domain, autoVerify intent filter, every claimed path)", () => {
  assert.deepEqual(checkAppJson(read("app.json"), read("app-links.json")), []);
});

test("the rule notices a path no intent filter covers", () => {
  const links = { ...read("app-links.json"), paths: [...read("app-links.json").paths, "/elsewhere/*"] };
  assert.equal(checkAppJson(read("app.json"), links).length, 1);
});

test("dynamic segments of a route become a star, whatever their brackets", () => {
  assert.equal(normalizeRoute("/watch/[id]"), "/watch/*");
  assert.equal(normalizeRoute("/docs/[...slug]"), "/docs/*");
  assert.equal(normalizeRoute("/docs/[[...slug]]/x/[id]"), "/docs/*/x/*");
  assert.equal(normalizeRoute("/a/[]"), "/a/[]");
  assert.equal(normalizeRoute("[id]"), "*");
});

test("a page that only answers 404 is told apart from one that renders", () => {
  const only = 'import { notFound } from "next/navigation";\n\nexport default function Page() {\n  notFound();\n}\n';
  assert.equal(isNotFoundOnly(only), true);
  assert.equal(isNotFoundOnly(only.replace("function Page", "async function Rest")), true);
  assert.equal(isNotFoundOnly("export default function Page() {\n  return null;\n}"), false);
});
