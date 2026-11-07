// Law → Proposals (ADR-089 §8, orazaka-web-client#275): which action a proposal offers, what a kind may promote, how a
// refusal reads, the editor's round trip — and both languages say the same things.
import { test } from "node:test";
import assert from "node:assert/strict";
import { lawCopy } from "../core/copy/law.ts";
import {
  canApprove,
  canEdit,
  canReject,
  draftOf,
  isIsoDate,
  kindsOf,
  orDash,
  promotable,
  refusalKey,
  ruleOf,
  wordOf,
} from "../features/law/utils/law.ts";
import { proposal } from "../../__tests__/helpers/law-fixtures.ts";

test("only a PROPOSED rule is approved; PROPOSED or APPROVED is rejected unless a packs pull request carries it", () => {
  assert.equal(canApprove(proposal()), true);
  assert.equal(canApprove(proposal({ status: "APPROVED" })), false);
  assert.equal(canReject(proposal({ status: "APPROVED" })), true);
  assert.equal(
    canReject(proposal({ status: "APPROVED", packsPrUrl: "https://github.com/krizaka/orazaka-packs/pull/7" })),
    false,
  );
  assert.equal(canReject(proposal({ status: "REJECTED_AUTO" })), false);
});

test("PROMOTED and SUPERSEDED revisions are history: never edited", () => {
  assert.equal(canEdit(proposal({ status: "REJECTED_AUTO" })), true);
  assert.equal(canEdit(proposal({ status: "PROMOTED" })), false);
  assert.equal(canEdit(proposal({ status: "SUPERSEDED" })), false);
});

test("a kind promotes its approved rules that no packs pull request carries yet", () => {
  const list = [
    proposal({ id: "a", status: "APPROVED" }),
    proposal({ id: "b", status: "APPROVED", packsPrUrl: "https://github.com/krizaka/orazaka-packs/pull/7" }),
    proposal({ id: "c", status: "PROPOSED" }),
    proposal({ id: "d", status: "APPROVED", kindId: "qc-sublet-notice" }),
  ];
  assert.deepEqual(
    promotable(list, "qc-visit-notice").map((p) => p.id),
    ["a"],
  );
  assert.deepEqual(kindsOf(list), ["qc-sublet-notice", "qc-visit-notice"]);
});

test("a refusal reads by its code, else by its status", () => {
  const known = lawCopy.en.refusals;
  assert.equal(refusalKey("golden-minimum-not-met", 409, known), "golden-minimum-not-met");
  assert.equal(refusalKey("law-promotion-off", 503, known), "law-promotion-off");
  assert.equal(refusalKey("toString", 409, known), "invalid-request");
  assert.equal(refusalKey(null, 403, known), "forbidden");
  assert.equal(refusalKey(null, 502, known), "unavailable");
});

test("every 409 code of the promotion has its words", () => {
  for (const code of [
    "proposal-not-approved",
    "already-in-review",
    "kind-mismatch",
    "duplicate-rule",
    "golden-minimum-not-met",
    "law-version-mismatch",
    "quote-too-long",
  ]) {
    assert.equal(refusalKey(code, 409, lawCopy.en.refusals), code);
  }
});

test("the editor's fields give the same rule back, its id and cited law kept, empty texts as null", () => {
  const rule = proposal().rule;
  assert.deepEqual(ruleOf(rule, draftOf(rule)), rule);
  const edited = ruleOf(rule, {
    ...draftOf(rule),
    checkType: "NONE",
    onPass: "  ",
    fixturesPass: "",
    fixturesFail: "",
  });
  assert.equal(edited.check, null);
  assert.equal(edited.onPass, null);
  assert.equal(edited.fixtures, null);
  assert.deepEqual(edited.law, rule.law);
  assert.equal(edited.ruleId, "QC-VISIT-001");
});

test("a rule stored as the model wrote it shows its raw values, and the editor starts it on known values only", () => {
  const raw = {
    ...proposal().rule,
    title: null,
    severity: "CRITICAL",
    method: null,
    check: { type: "MAYBE_PATTERN", pattern: "x" },
    sourceUrl: undefined,
  };
  assert.equal(wordOf(lawCopy.en.severity, raw.severity), "CRITICAL");
  assert.equal(wordOf(lawCopy.en.severity, "BLOCKING"), "Blocking");
  assert.equal(wordOf(lawCopy.en.method, raw.method), "—");
  assert.equal(orDash(raw.title), "—");
  const draft = draftOf(raw);
  assert.deepEqual([draft.title, draft.severity, draft.method, draft.checkType, draft.sourceUrl], ["", "", "", "", ""]);
});

test("a date is YYYY-MM-DD", () => {
  assert.equal(isIsoDate("2026-11-01"), true);
  assert.equal(isIsoDate("2026-13-01"), false);
  assert.equal(isIsoDate("01/11/2026"), false);
});

function leaves(value: unknown, path = ""): Array<[string, string]> {
  if (typeof value === "string") return [[path, value]];
  return Object.entries(value as Record<string, unknown>).flatMap(([key, child]) => leaves(child, `${path}.${key}`));
}

test("French says what English says: the same keys, the same placeholders, no empty message", () => {
  const placeholders = (text: string) =>
    [...text.matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort((a, b) => a.localeCompare(b));
  const en = new Map(leaves(lawCopy.en));
  const fr = new Map(leaves(lawCopy.fr));
  assert.deepEqual(
    [...fr.keys()].sort((a, b) => a.localeCompare(b)),
    [...en.keys()].sort((a, b) => a.localeCompare(b)),
  );
  for (const [key, text] of en) {
    assert.ok(text.length > 0 && (fr.get(key) ?? "").length > 0, `${key} is empty`);
    assert.deepEqual(placeholders(fr.get(key) ?? ""), placeholders(text), key);
  }
});
