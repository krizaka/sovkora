// node --test scripts/test — the regexes SonarCloud S8786 flagged for super-linear backtracking: same answer on valid input,
// and a hostile input of 100k characters finishes in well under a second.
import assert from "node:assert/strict";
import { test } from "node:test";
import { parseDotEnvLine } from "../lib/dotenv-line.mjs";
import { assertionOf, hasRequestLine } from "../lib/http-contract-lines.mjs";
import { databasesOf } from "../dev-migrations.mjs";
import { articleFromXml } from "../lib/bakeoff/corpus.mjs";
import { parseAnswer } from "../lib/bakeoff/score.mjs";
import { keys, references } from "../lib/bakeoff/verify.mjs";
import { stripTags } from "../lib/doc-text.mjs";
import { properties } from "../lib/krizaka-versions.mjs";
import { deriveProbes } from "../lib/migration-probes.mjs";
import { buildModels, catalogue, MODELS_TABLE as TBL, manifestLicences, readmeLicences } from "../lib/model-registry.mjs";
import { planFromPrompts } from "../runpod-batch.mjs";

const N = 100_000;
const within = (ms, fn) => {
  const started = performance.now();
  const result = fn();
  assert.ok(performance.now() - started < ms, `took ${Math.round(performance.now() - started)} ms`);
  return result;
};

test("a .env line: key, value, quotes dropped; blanks and comments are not entries", () => {
  assert.deepEqual(parseDotEnvLine("  DB_USERNAME = \"svc\" "), { key: "DB_USERNAME", value: "svc" });
  assert.deepEqual(parseDotEnvLine("A=b=c"), { key: "A", value: "b=c" });
  assert.equal(parseDotEnvLine("# A=b"), null);
  assert.equal(parseDotEnvLine(""), null);
  within(1000, () => {
    assert.equal(parseDotEnvLine(" ".repeat(N)), null);
    assert.equal(parseDotEnvLine(`A${" ".repeat(N)}x`), null);
    assert.deepEqual(parseDotEnvLine(`A=${" ".repeat(N)}`), { key: "A", value: "" });
  });
});

test("the api-contract lines: an assertion's expression, a region that holds a request", () => {
  assert.equal(assertionOf("?? status == 200  "), "status == 200");
  assert.equal(assertionOf("?? "), null);
  assert.equal(assertionOf("GET /x"), null);
  assert.ok(hasRequestLine("\n  POST /v1/x\nContent-Type: a"));
  assert.ok(!hasRequestLine("just a banner\nGET\n"));
  within(1000, () => {
    assert.equal(assertionOf(`??${" ".repeat(N)}`), null);
    assert.ok(!hasRequestLine("\n".repeat(N) + " ".repeat(N)));
    assert.ok(!hasRequestLine(`GET${" ".repeat(N)}`));
  });
});

test("databasesOf: \\c lines only, once each; a hostile blank run is linear", () => {
  assert.deepEqual(databasesOf("  \\c a\n\\connect b\n\\c a\nSELECT 1;"), ["a", "b"]);
  within(1000, () => assert.deepEqual(databasesOf("\n".repeat(N) + " ".repeat(N) + "x"), []));
});

test("articleFromXml: tags stripped to text, a run of '<' is linear", () => {
  const xml = '<Section Code="se:1"><Label>1</Label><Subsection><Text>One &amp; <b>two</b></Text></Subsection></Section>';
  assert.equal(articleFromXml(xml, "1"), "Article 1\n\nOne & two");
  within(1000, () => articleFromXml(`<Section Code="se:1"><Subsection>${"<".repeat(N)}</Subsection></Section>`, "1"));
});

test("parseAnswer: a fenced answer is unwrapped (json tag or none), an unfenced one is untouched", () => {
  const json = '{"claims":[]}';
  assert.deepEqual(parseAnswer("```json\n" + json + "\n```"), parseAnswer("```" + json + "```"));
  assert.equal(parseAnswer("```json\n" + json + "\n```").fenced, true);
  assert.equal(parseAnswer(json).fenced, false);
  assert.equal(parseAnswer("```").fenced, false);
  within(1000, () => {
    parseAnswer("```" + " ".repeat(N) + "x");
    parseAnswer("```" + " ".repeat(N) + "x```y");
  });
});

test("references: statute references found, trailing punctuation trimmed, hostile input linear", () => {
  assert.ok(keys("voir l'article 1457, et l.r.q. 2001").size >= 1);
  assert.ok(references("https://example.org/a/b). see.").size >= 1);
  within(1000, () => {
    references(`article ${"1".repeat(N)}!`);
    references(`https://example.org/${".".repeat(N)}`);
    references("art. " + "9".repeat(N) + " " + "RLRQ c " + "A".repeat(N));
  });
});

test("stripTags: nested tags are removed, a run of '<' is linear", () => {
  assert.equal(stripTags("a <b>bold</b> word"), "a bold word");
  assert.ok(!stripTags("<<b>script>alert(1)<</b>/script>").includes("<"));
  within(1000, () => stripTags("<".repeat(N) + "a"));
});

test("properties: values trimmed, hostile whitespace linear", () => {
  assert.deepEqual(properties("<properties>\n <a.b>\n 1.2.3 \n</a.b><c> </c></properties>"), { "a.b": "1.2.3", c: "" });
  within(1000, () => properties(`<properties><a>${" ".repeat(N)}x<</properties>`));
});

test("deriveProbes: an ALTER TABLE yields its column and constraint probes, even with no action text", () => {
  const sql = "ALTER TABLE IF EXISTS ONLY t ADD COLUMN IF NOT EXISTS c int, ADD CONSTRAINT k UNIQUE (c);\nALTER TABLE u ;";
  const labels = deriveProbes(sql).map((p) => p.label);
  assert.ok(labels.includes("column t.c") && labels.includes("constraint k"));
  within(1000, () => deriveProbes(`ALTER TABLE t${" ".repeat(N)}x`));
});

const SEED = `INSERT INTO ${TBL} (model_name, model_label, category, options, provider_name, is_default) VALUES\n('m', 'M (MPS)', 'chat', NULL, 'ollama', TRUE)\n  ON   CONFLICT (model_name) DO NOTHING;\nUPDATE ${TBL} SET family = 'F', status = 'WHERE x' WHERE model_name = 'm';`;

test("catalogue: the INSERT stops at ON CONFLICT and the UPDATE ends at its WHERE", () => {
  const [row, ...rest] = catalogue(SEED);
  assert.equal(rest.length, 0);
  assert.equal(row.family, "F");
  assert.equal(row.status, "WHERE x");
  assert.equal(row.model_label, "M (MPS)");
  assert.equal(catalogue(`UPDATE ${TBL} SET a = 1;`).length, 0);
});

test("catalogue and buildModels: hostile whitespace and quotes are linear", () => {
  within(1000, () => {
    catalogue(`INSERT INTO ${TBL} (model_name) VALUES ('a')${" ".repeat(N)}x;`);
    catalogue(`UPDATE ${TBL} SET a = 1${" ".repeat(N)}x;`);
    catalogue(`UPDATE ${TBL} SET ${"WHERE ".repeat(N / 6)};`);
    assert.throws(() => buildModels(SEED.replace("(MPS)", `${" ".repeat(N)}(MPS)${" ".repeat(N)}x`)));
  });
});

test("licence tables: README rows and manifest entries read as before", () => {
  assert.equal(readmeLicences("| `a.yaml` | 1 GB | hf | Apache-2.0  |\n| nope |").get("a"), "Apache-2.0");
  assert.equal(manifestLicences('  a:\n    licence: "MIT"  \n  b:\n    licence:\n').get("a"), "MIT");
  within(1000, () => {
    readmeLicences("| `a.yaml` |" + "|".repeat(N) + "x");
    readmeLicences("| `a.yaml` |" + " ".repeat(N) + "x");
    manifestLicences("  a:\n    licence:" + " ".repeat(N));
  });
});

test("planFromPrompts: a [refs] prefix keeps the scene, a bare line is a scene, hostile spacing is linear", () => {
  const plan = planFromPrompts("[hero] a quiet street\nplain scene", { hero: "R" });
  assert.deepEqual(plan.clips.map((c) => c.scene), ["a quiet street", "plain scene"]);
  assert.deepEqual(plan.clips[0].references, ["R"]);
  within(1000, () => planFromPrompts(`[a]${" ".repeat(N)}`, { a: "R" }));
});
