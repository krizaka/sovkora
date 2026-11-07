// The console's sign-in is the family's entry template (@krizaka/ui/auth-layout), never a hand-made layout.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const form = readFileSync(new URL("../../app/console/login/LoginForm.tsx", import.meta.url), "utf8");
const page = readFileSync(new URL("../../app/console/login/page.tsx", import.meta.url), "utf8");

test("the sign-in is built on @krizaka/ui/auth-layout and wears the Sovkora brand", () => {
  assert.match(form, /from "@krizaka\/ui\/auth-layout"/);
  assert.match(form, /brand-orazaka/);
  assert.match(form, /<OrazakaLogo size=\{112\} \/>/); // the animated mark in the brand panel
});

test("no local split layout, no radar, no page-local main around the template", () => {
  assert.doesNotMatch(form + page, /login-split|login-hero|sentinel|radar/i);
  assert.doesNotMatch(page, /<main/);
});

test("the closed site keeps its disabled preview", () => {
  assert.match(form, /disabled=\{pending \|\| Boolean\(preview\)\}/);
});
