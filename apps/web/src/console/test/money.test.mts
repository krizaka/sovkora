// Money in the console: Canadian dollars by default, the currency always named (Node's test runner strips the types).
import { test } from "node:test";
import assert from "node:assert/strict";
import { BILLING_CURRENCY, money } from "../features/billing/utils/money.ts";

const plain = (text: string) => text.replace(/[  ]/g, " ");

test("bills in CAD unless the deployment says otherwise", () => {
  assert.equal(BILLING_CURRENCY, "CAD");
});

test("writes Canadian dollars the French way, the currency named", () => {
  assert.equal(plain(money(2900)), "29,00 $CA");
  assert.equal(plain(money(7900)), "79,00 $CA");
  assert.equal(plain(money(0)), "0,00 $CA");
});

test("a plan's own currency wins", () => {
  assert.equal(plain(money(1900, "EUR")), "19,00 €");
  assert.equal(plain(money(1900, null)), "19,00 $CA");
});
