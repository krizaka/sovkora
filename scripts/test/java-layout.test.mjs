// node --test — the Java naming convention of ADR-029 (scripts/lib/java-layout.mjs): each rule fails on its own violation,
// the tree of this repository is checked by scripts/check-java-layout.mjs (CI step « The Java layout »).
import assert from "node:assert/strict";
import { test } from "node:test";
import { coordinates, rootPackage, violations } from "../lib/java-layout.mjs";

const pom = (artifactId, { groupId = "", packaging = "jar", parentGroup = "com.krizaka.sovkora" } = {}) =>
  `<project><parent><groupId>${parentGroup}</groupId><artifactId>sovkora-parent</artifactId><version>1</version></parent>
   ${groupId ? `<groupId>${groupId}</groupId>` : ""}<artifactId>${artifactId}</artifactId><packaging>${packaging}</packaging>
   <dependencies><dependency><groupId>x</groupId><artifactId>other</artifactId></dependency></dependencies></project>`;
const module = (dir, artifactId, main = [], options = {}) => ({ dir, pom: pom(artifactId, options), main, test: [] });
const rules = (list) => violations(list).map((v) => v.rule);

test("coordinates read the pom's own artifactId, the inherited groupId and the packaging", () => {
  assert.deepEqual(coordinates(pom("sovkora-engine-core")), { groupId: "com.krizaka.sovkora", artifactId: "sovkora-engine-core", packaging: "jar" });
  assert.equal(coordinates(pom("sovkora-x", { groupId: "com.other" })).groupId, "com.other");
  assert.equal(coordinates(`<project><!-- <artifactId>no</artifactId> --><artifactId>sovkora-y</artifactId></project>`).artifactId, "sovkora-y");
});

test("N3: the root package follows the artifactId, without the product prefix and the -service suffix", () => {
  assert.equal(rootPackage("sovkora-engine-core"), "com.krizaka.sovkora.engine.core");
  assert.equal(rootPackage("sovkora-conversation-service"), "com.krizaka.sovkora.conversation");
  assert.equal(rootPackage("sovkora-studio-api"), "com.krizaka.sovkora.studio.api");
  assert.equal(rootPackage("sovkora-test-support"), "com.krizaka.sovkora.test.support");
});

test("a module that follows every rule passes", () => {
  assert.deepEqual(violations([module("engine/sovkora-engine-core", "sovkora-engine-core", ["com.krizaka.sovkora.engine.core.domain"])]), []);
});

test("N1: a groupId other than the product's fails", () => {
  assert.deepEqual(rules([module("engine/sovkora-engine-core", "sovkora-engine-core", [], { groupId: "com.krizaka" })]), ["N1"]);
});

test("N2: a code name, a missing product prefix and a banned suffix fail", () => {
  assert.ok(rules([module("engine/orazaka-core", "orazaka-core")]).includes("N2"));
  assert.ok(rules([module("engine/sovkora-engine-common", "sovkora-engine-common")]).includes("N2"));
  assert.ok(rules([module("engine/sovkora-engine-utils", "sovkora-engine-utils")]).includes("N2"));
  assert.ok(rules([module("engine/Sovkora-Core", "Sovkora-Core")]).includes("N2"));
});

test("N3: a class outside its module root fails, and so does a package split across two modules", () => {
  assert.deepEqual(rules([module("engine/sovkora-engine-core", "sovkora-engine-core", ["com.krizaka.sovkora.core"])]), ["N3"]);
  const split = [
    module("contracts/sovkora-crypto", "sovkora-crypto", ["com.krizaka.sovkora.crypto.port"]),
    module("contracts/sovkora-crypto-x", "sovkora-crypto-x", ["com.krizaka.sovkora.crypto.port", "com.krizaka.sovkora.crypto.x"]),
  ];
  assert.ok(violations(split).some((v) => v.rule === "N3" && /split/.test(v.message)));
});

test("N4: the folder is the artifactId, under a role folder; an aggregator is a role folder", () => {
  assert.deepEqual(rules([module("engine/core", "sovkora-engine-core", ["com.krizaka.sovkora.engine.core"])]), ["N4"]);
  assert.deepEqual(rules([module("libs/sovkora-engine-core", "sovkora-engine-core", ["com.krizaka.sovkora.engine.core"])]), ["N4"]);
  assert.deepEqual(rules([module("services/studio", "sovkora-studio", [], { packaging: "pom" })]), []);
});
