// NAV-SYS (@krizaka/ui 3.10): the private console wears the environment badge and never the family.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const read = (f: string) => readFileSync(new URL(`../${f}`, import.meta.url), "utf8");

test("the bar shows the environment badge and no family switcher", () => {
  const nav = read("components/ConsoleNav.tsx");
  assert.match(nav, /<NavBar\.Badge env=\{APP_ENV\}/);
  assert.doesNotMatch(nav, /<NavBar\.Family/);
});

test("the footer is @krizaka/ui's SiteFooter in app mode: one slim line, no family columns", () => {
  const src = read("components/SiteFooter.tsx");
  assert.match(src, /@krizaka\/ui\/site-footer/);
  assert.match(src, /family=\{false\}/);
  assert.match(src, /variant="app"/);
});

test("the build names its environment, dev by default", () => {
  assert.match(
    readFileSync(new URL("../../../next.config.ts", import.meta.url), "utf8"),
    /NEXT_PUBLIC_APP_ENV[^\n]*\|\| "dev"/,
  );
});

test("the environment badge shows on a phone too: the kit hides it below 640 px, the console overrides that", () => {
  assert.match(read("components/ConsoleNav.tsx"), /<NavBar\.Badge env=\{APP_ENV\} className="inline-flex"/);
});
