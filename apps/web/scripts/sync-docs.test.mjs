import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { test } from "node:test";
import { DOCS, JOURNEYS, SPECIAL } from "./docs-manifest.mjs";
import { clean, modelSlice, omitSections, sync } from "./sync-docs.mjs";

test("the manifest: unique slugs, known journeys, sources in the repository's docs", () => {
  const slugs = [...DOCS, ...SPECIAL].map((d) => d.slug);
  assert.equal(new Set(slugs).size, slugs.length);
  const journeys = new Set(JOURNEYS.map((j) => j.id));
  for (const d of [...DOCS, ...SPECIAL]) {
    assert.ok(journeys.has(d.journey), `${d.slug}: journey ${d.journey}`);
    assert.ok(d.title && d.intro, `${d.slug}: title and intro`);
  }
  for (const d of DOCS) assert.match(d.source, /^(docs\/.+\.md|SECURITY\.md)$/);
});

test("contributor and strategy docs are never in the manifest", () => {
  const sources = DOCS.map((d) => d.source);
  for (const internal of [
    "docs/DELIVERY.md",
    "docs/BILLING_ARCHITECTURE.md",
    "docs/PRODUCTION_READINESS_AUDIT.md",
    "docs/ARCHITECTURE_ASSESSMENT.md",
  ])
    assert.ok(!sources.includes(internal), internal);
});

test("clean removes the front matter and keeps the diagram fences", () => {
  const out = clean("---\ntitle: x\n---\n# T\n\n```sequence\na -> b: hi\n```\n");
  assert.ok(out.startsWith("# T"));
  assert.match(out, /```sequence/);
});

test("omitSections drops a section by heading, numbering ignored, and leaves fenced '## ' lines alone", () => {
  const md = "# T\n\n## 1. Keep\nok\n\n## 7. Costs and more\nsecret\n```\n## Costs\n```\n\n## 8. Tail\nkept\n";
  const out = omitSections(md, ["Costs"]);
  assert.ok(out.includes("ok") && out.includes("kept"));
  assert.ok(!out.includes("secret"));
});

test("modelSlice keeps what the diagram blocks read and nothing more", () => {
  const slice = modelSlice({
    roles: [],
    modules: [
      {
        id: "m",
        repository: "r",
        owner: "o",
        role: "x",
        layer: "l",
        runtime: null,
        description: "d",
        path: "p",
        version: "1",
      },
    ],
    dependencies: [],
    flows: [],
    pipeline: [],
    coreChain: [],
    cli: { alias: "orazaka", journey: [], commands: [1, 2] },
    packs: [1],
  });
  assert.deepEqual(Object.keys(slice.modules[0]).sort(), [
    "description",
    "id",
    "layer",
    "owner",
    "repository",
    "role",
    "runtime",
  ]);
  assert.ok(!("packs" in slice) && !("commands" in slice.cli));
});

test("sync writes the pages, the index, the model slice and the source map from a checkout", () => {
  const src = fs.mkdtempSync(path.join(os.tmpdir(), "ora-"));
  const out = fs.mkdtempSync(path.join(os.tmpdir(), "docs-"));
  fs.mkdirSync(path.join(src, "docs", "_generated"), { recursive: true });
  for (const d of DOCS) {
    fs.mkdirSync(path.dirname(path.join(src, d.source)), { recursive: true });
    fs.writeFileSync(path.join(src, d.source), `---\ntitle: ${d.slug}\n---\n# ${d.slug}\n`);
  }
  fs.writeFileSync(
    path.join(src, "docs", "_generated", "architecture.json"),
    JSON.stringify({
      roles: [],
      modules: [],
      dependencies: [],
      flows: [],
      pipeline: [],
      coreChain: [],
      cli: { alias: "orazaka", journey: [] },
    }),
  );
  const n = sync(src, out);
  assert.equal(n, DOCS.length + SPECIAL.length);
  const index = JSON.parse(fs.readFileSync(path.join(out, "index.json"), "utf8"));
  assert.equal(index.find((d) => d.slug === "mobile").file, null);
  assert.equal(JSON.parse(fs.readFileSync(path.join(out, "sources.json"), "utf8"))["docs/CONCEPTS.md"], "concepts");
  assert.ok(fs.readFileSync(path.join(out, "concepts.md"), "utf8").startsWith("# concepts"));
  assert.equal(index.find((d) => d.slug === "studio-guide").clip, "studio");
  assert.equal(index.find((d) => d.slug === "api").clip, null);
});

test("the published content has no link to an internal document and no <https://…> autolink", () => {
  const dir = path.join(path.dirname(new URL(import.meta.url).pathname), "..", "content", "docs");
  for (const f of fs.readdirSync(dir).filter((x) => x.endsWith(".md"))) {
    const md = fs.readFileSync(path.join(dir, f), "utf8");
    assert.ok(
      !/DELIVERY\.md|BILLING_ARCHITECTURE|PRODUCTION_READINESS_AUDIT|ARCHITECTURE_ASSESSMENT/.test(md),
      `${f} links an internal document`,
    );
    assert.ok(!/<https?:\/\//.test(md), `${f} has a literal autolink`);
  }
});

test("the Measured runs page is published beside Supported models, from the generated source, and the models page links to it", () => {
  const entry = DOCS.find((d) => d.slug === "measured-runs");
  assert.equal(entry.source, "docs/_generated/MEASURED_RUNS.md");
  assert.equal(entry.journey, DOCS.find((d) => d.slug === "models").journey);
  assert.equal(entry.generated, true);
  const dir = path.join(path.dirname(new URL(import.meta.url).pathname), "..", "content", "docs");
  const page = fs.readFileSync(path.join(dir, "measured-runs.md"), "utf8");
  for (const text of [
    "Measured runs",
    "## Per model",
    "## Parameters",
    "## Failures, timeouts and limits",
    "## What it cost",
    "measured again when the setup changes",
  ])
    assert.ok(page.includes(text), `missing: ${text}`);
  // The link is written against the source file; sources.json resolves it to the page at render time.
  assert.match(fs.readFileSync(path.join(dir, "models.md"), "utf8"), /\(MEASURED_RUNS\.md\)/);
  assert.equal(
    JSON.parse(fs.readFileSync(path.join(dir, "sources.json"), "utf8"))["docs/_generated/MEASURED_RUNS.md"],
    "measured-runs",
  );
});

test("removedPublicRoutes reads src/core/config/redirects.ts; a doc linking one of them is refused", async () => {
  const { removedPublicRoutes, removedLinks } = await import("./sync-docs.mjs");
  const routes = removedPublicRoutes();
  assert.ok(routes.includes("/gallery") && routes.includes("/features"));
  assert.deepEqual(removedLinks("See [the demos](/gallery) and https://www.orazaka.com/features/privacy"), [
    "/gallery",
    "/features",
  ]);
  assert.deepEqual(removedLinks("[models](/docs/models), [gallery pages](/galleryx) and a gallery of images"), []);
});

test("sync fails when a synced doc links a removed public route", () => {
  const src = fs.mkdtempSync(path.join(os.tmpdir(), "orazaka-src-"));
  const out = fs.mkdtempSync(path.join(os.tmpdir(), "orazaka-out-"));
  for (const d of DOCS) {
    fs.mkdirSync(path.dirname(path.join(src, d.source)), { recursive: true });
    fs.writeFileSync(
      path.join(src, d.source),
      d.slug === "101" ? "# T\n[demos](https://www.orazaka.com/gallery)\n" : "# T\n",
    );
  }
  fs.mkdirSync(path.join(src, "docs", "_generated"), { recursive: true });
  assert.throws(() => sync(src, out), /101.*\/gallery/);
});

test("the short path: the reading path is a handful of pages, the rest is the folded reference", () => {
  const pages = [...DOCS, ...SPECIAL];
  const reading = pages.filter((d) => d.journey !== "reference");
  assert.ok(reading.length <= 7, `${reading.length} pages to read`);
  assert.deepEqual(
    JOURNEYS.map((j) => j.id),
    ["start", "guides", "packs", "reference"],
  );
  for (const d of pages.filter((d) => d.journey === "reference"))
    assert.ok(d.group, `${d.slug}: a reference page sits in a group`);
});

test("a page's clip is one of the recordings of src/core/clips/clips.ts", () => {
  const ids = [
    ...fs
      .readFileSync(new URL("../src/core/clips/clips.ts", import.meta.url), "utf8")
      .match(/CLIP_IDS = \[([^\]]*)\]/)[1]
      .matchAll(/"(\w+)"/g),
  ].map((m) => m[1]);
  for (const d of DOCS.filter((d) => d.clip)) assert.ok(ids.includes(d.clip), `${d.slug}: unknown clip ${d.clip}`);
});
