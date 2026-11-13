// node --test scripts/test — the guard of the com.krizaka versions (orazaka#434): sovkora-parent and the BOM may only name
// a release Maven Central carries.
import assert from "node:assert/strict";
import { test } from "node:test";
import { claims, explain, managed, onRepository, parentOf, publishedPom, repositories, projectCoordinates, properties, stripComments, unprovided } from "../lib/krizaka-versions.mjs";

const KIT_POM = `<project><parent><groupId>com.krizaka</groupId><artifactId>krizaka-parent</artifactId><version>0.2.0</version></parent>
  <artifactId>krizaka-platform-kit</artifactId><!-- <version>9.9.9</version> --><version>0.3.0</version>
  <dependencyManagement><dependencies><dependency><version>1.2.3</version></dependency></dependencies></dependencyManagement></project>`;
const INHERITING_POM = `<project><parent><artifactId>krizaka-parent</artifactId><version>0.2.0</version></parent>
  <artifactId>krizaka-users</artifactId><dependencies><dependency><version>7.0.0</version></dependency></dependencies></project>`;
const dep = (a, v) => `<dependency><groupId>com.krizaka</groupId><artifactId>${a}</artifactId><version>${v}</version></dependency>`;
const bom = (kit) => `<project><version>0.2.0</version><properties><krizaka-platform-kit.version>${kit}</krizaka-platform-kit.version>
  <java.version>21</java.version></properties><dependencyManagement><dependencies>
  ${dep("krizaka-security", "${krizaka-platform-kit.version}")}${dep("krizaka-spring-boot-starter-app-config", "${krizaka-platform-kit.version}")}
  ${dep("krizaka-test-support", "${project.version}")}<dependency><groupId>org.other</groupId><artifactId>x</artifactId><version>1</version></dependency>
  </dependencies></dependencyManagement></project>`;
const parentPom = (v) => `<project><properties><krizaka.version>${v}</krizaka.version></properties></project>`;
// What the cloned repositories build: the kit at 0.3.0 (it has the starter), the build repository at 0.2.0.
const built = new Map([["krizaka-security", "0.3.0"], ["krizaka-spring-boot-starter-app-config", "0.3.0"], ["krizaka-test-support", "0.2.0"], ["krizaka-bom", "0.2.0"]]);
const central = (published) => async (c) => published.includes(`${c.artifactId}:${c.version}`);
const CENTRAL = "https://repo1.maven.org/maven2";
const SPACES = "https://artifacts.example.test/maven2";

test("reads a pom's own coordinates, not the nested ones, and inherits the parent's version", () => {
  assert.deepEqual(projectCoordinates(KIT_POM), { artifactId: "krizaka-platform-kit", version: "0.3.0" });
  assert.deepEqual(projectCoordinates(INHERITING_POM), { artifactId: "krizaka-users", version: "0.2.0" });
  assert.equal(properties(bom("0.3.0"))["krizaka-platform-kit.version"], "0.3.0");
});

test("resolves the BOM's managed com.krizaka dependencies and ignores the others", () => {
  assert.deepEqual(managed(bom("0.3.0")).map((d) => `${d.artifactId}:${d.version}`),
    ["krizaka-security:0.3.0", "krizaka-spring-boot-starter-app-config:0.3.0", "krizaka-test-support:0.2.0"]);
});

test("the BOM on the version the reactor builds is provided, whatever Central has", async () => {
  const list = claims({ built, bom: bom("0.3.0"), parent: parentPom("0.2.0") });
  assert.deepEqual(await unprovided(list, central([])), []);
});

test("orazaka#434: kit 0.2.0 is on Central but has no starter; the reactor builds 0.3.0 — fails", async () => {
  const list = claims({ built, bom: bom("0.2.0"), parent: parentPom("0.2.0") });
  const missing = await unprovided(list, central(["krizaka-security:0.2.0"]));
  assert.deepEqual(missing.map((c) => c.artifactId), ["krizaka-spring-boot-starter-app-config"]);
  assert.match(explain(missing[0]), /krizaka-spring-boot-starter-app-config:0\.2\.0, which is on none of/);
});

test("a version nobody provides fails, even when no repository builds the artifact", async () => {
  const list = claims({ built: new Map(), bom: bom("0.4.0"), parent: parentPom("0.2.0") });
  assert.equal((await unprovided(list, central([]))).length, 4); // 3 managed + the imported BOM
  assert.match(explain((await unprovided(list, central([])))[0]), /build\/sovkora-parent\/pom\.xml/);
});

test("sovkora-parent's krizaka.version is held to the same rule", async () => {
  const list = claims({ built, bom: bom("0.3.0"), parent: parentPom("0.5.0") });
  assert.deepEqual((await unprovided(list, central([]))).map((c) => c.artifactId), ["krizaka-bom"]);
});

test("Central unreachable never fails the build", async () => {
  const probe = { group: "com.krizaka", artifactId: "a", version: "1" };
  assert.equal(await onRepository(probe, [CENTRAL], async () => Promise.reject(new Error("offline"))), null);
  assert.equal(await onRepository(probe, [CENTRAL], async () => ({ status: 503 })), null);
  assert.equal(await onRepository(probe, [CENTRAL], async () => ({ status: 404 })), false);
  assert.deepEqual(await unprovided(claims({ built: new Map(), bom: bom("0.4.0"), parent: parentPom("0.2.0") }), async () => null), []);
});

test("comments are stripped, an unterminated one drops the rest", () => {
  assert.equal(stripComments("a<!-- x --><b/><!-- y --><!-- <!-- z -->c"), "a<b/>c");
  assert.equal(stripComments("a<!-- never closed <version>1</version>"), "a");
  assert.equal(stripComments("no comment"), "no comment");
});

test("ADR-029: the krizaka-parent sovkora-parent inherits is a claim too, and only krizaka-parent counts", async () => {
  const parent = `<project><parent><groupId>com.krizaka</groupId><artifactId>krizaka-parent</artifactId><version>0.9.0</version><relativePath/></parent>
    <properties><krizaka.version>0.12.0</krizaka.version></properties></project>`;
  assert.equal(parentOf(parent), "0.9.0");
  assert.equal(parentOf(`<project><parent><artifactId>sovkora-parent</artifactId><version>1.0.0</version></parent></project>`), null);
  const missing = await unprovided(claims({ parent }), central(["krizaka-bom:0.12.0"]));
  assert.deepEqual(missing.map((c) => `${c.artifactId}:${c.version}`), ["krizaka-parent:0.9.0"]);
  assert.match(explain(missing[0]), /is on none of/);
});

test("the BOM is read from the repositories: its text when published, null when absent or unreachable", async () => {
  const probe = { group: "com.krizaka", artifactId: "krizaka-bom", version: "0.12.0" };
  const seen = [];
  const ok = async (url) => { seen.push(url); return { status: 200, text: async () => "<project/>" }; };
  assert.equal(await publishedPom(probe, [CENTRAL], ok), "<project/>");
  assert.equal(seen[0], "https://repo1.maven.org/maven2/com/krizaka/krizaka-bom/0.12.0/krizaka-bom-0.12.0.pom");
  assert.equal(await publishedPom(probe, [CENTRAL], async () => ({ status: 404 })), null);
  assert.equal(await publishedPom(probe, [CENTRAL], async () => Promise.reject(new Error("offline"))), null);
  assert.deepEqual(claims({ bom: null, parent: parentPom("0.12.0") }).map((c) => c.artifactId), ["krizaka-bom"]);
});

test("#3445: the repository our builds publish to is asked first, from ARTIFACT_REPOSITORY_URL, Central after", async () => {
  assert.deepEqual(repositories({ ARTIFACT_REPOSITORY_URL: `${SPACES}/` }), [SPACES, "https://repo1.maven.org/maven2"]);
  assert.deepEqual(repositories({}), ["https://repo1.maven.org/maven2"]);
  const probe = { group: "com.krizaka", artifactId: "krizaka-bom", version: "0.17.0" };
  const asked = [];
  const onlyOnSpaces = async (url) => { asked.push(url); return { status: url.startsWith(SPACES) ? 200 : 404, text: async () => "<project/>" }; };
  assert.equal(await onRepository(probe, [SPACES, "https://repo1.maven.org/maven2"], onlyOnSpaces), true);
  assert.equal(asked[0], `${SPACES}/com/krizaka/krizaka-bom/0.17.0/krizaka-bom-0.17.0.pom`);
  assert.equal(await publishedPom(probe, [SPACES], onlyOnSpaces), "<project/>");
  const nowhere = async () => ({ status: 404 });
  assert.equal(await onRepository(probe, [SPACES, "https://repo1.maven.org/maven2"], nowhere), false);
});

test("#3445: a version on no configured repository fails and says which were asked", async () => {
  const list = claims({ parent: parentPom("0.17.0") });
  const missing = await unprovided(list, async () => false);
  assert.match(explain(missing[0], [SPACES, "https://repo1.maven.org/maven2"]), /artifacts\.example\.test.*repo1\.maven\.org/);
});
