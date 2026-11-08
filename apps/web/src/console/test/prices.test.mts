// The price catalogue in the console (krizaka/orazaka#417): the billing service's wire shapes become the views of
// @krizaka/ui's PriceCatalogueEditor. Amounts stay integer cents; a scheduled change never starts in the past.
import { test } from "node:test";
import assert from "node:assert/strict";
import { effectiveInstant, toHistory, toPricesView, type AdminPrice } from "../features/billing/utils/prices.ts";

const NOW = new Date("2026-10-28T15:00:00Z");

const v = (over: Partial<AdminPrice>): AdminPrice => ({
  versionId: "v1",
  key: "orazaka.business",
  unit: "year",
  amountCents: 290000,
  currency: "CAD",
  taxes: "plus-taxes",
  availability: "preparing",
  limits: {},
  effectiveFrom: "2026-10-01T00:00:00Z",
  ...over,
});

test("an entry splits its key into product and plan, and keeps the cents as they came", () => {
  const view = toPricesView([v({})], { "orazaka.business": [v({})] }, NOW);
  assert.deepEqual(view.entries[0], {
    key: "orazaka.business",
    product: "orazaka",
    plan: "business",
    currency: "CAD",
    versionId: "v1",
    effectiveFrom: "2026-10-01T00:00:00Z",
    amountCents: 290000,
    unit: "year",
    availability: "preparing",
    limits: {},
    upcoming: [],
  });
});

test("a version that starts later is upcoming, not current; one that already started is not", () => {
  const history = [v({}), v({ versionId: "v2", amountCents: 310000, effectiveFrom: "2026-12-01T00:00:00Z" })];
  const { entries } = toPricesView([v({})], { "orazaka.business": history }, NOW);
  assert.deepEqual(
    entries[0]!.upcoming.map((u) => [u.versionId, u.amountCents]),
    [["v2", 310000]],
  );
});

test("a quote stays a quote: a null amount is never turned into zero", () => {
  const { entries } = toPricesView([v({ amountCents: null })], {}, NOW);
  assert.equal(entries[0]!.amountCents, null);
});

test("the enumerations the fields offer are the service's wire values", () => {
  const view = toPricesView([], {}, NOW);
  assert.deepEqual(view.units, ["month", "year", "once", "document", "order", "pack", "hour"]);
  assert.deepEqual(view.availabilities, ["public", "internal", "founder-limited", "preparing"]);
});

test("the history reads oldest first as before -> after, the first having no before", () => {
  const second = v({ versionId: "v2", amountCents: 310000, effectiveFrom: "2026-12-01T00:00:00Z" });
  const lines = toHistory([second, v({})]);
  assert.equal(lines.length, 2);
  assert.equal(lines[0]!.before, null);
  assert.equal(lines[0]!.after.amountCents, 290000);
  assert.equal(lines[1]!.before?.amountCents, 290000);
  assert.equal(lines[1]!.after.amountCents, 310000);
  assert.equal(lines[1]!.effectiveFrom, "2026-12-01T00:00:00Z");
});

test("a change from a later day starts at the start of that day (UTC)", () => {
  assert.equal(effectiveInstant("2026-11-01", "2026-10-28", NOW), "2026-11-01T00:00:00.000Z");
});

test("a change from today starts two minutes from now, never in the past", () => {
  assert.equal(effectiveInstant("2026-10-28", "2026-10-28", NOW), "2026-10-28T15:02:00.000Z");
});

test("a day before today is refused", () => {
  assert.throws(() => effectiveInstant("2026-10-27", "2026-10-28", NOW), /past/);
});
