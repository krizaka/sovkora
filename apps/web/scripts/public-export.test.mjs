import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, relative } from "node:path";
import { afterEach, beforeEach, describe, it } from "node:test";
import { fileURLToPath } from "node:url";

import {
  PUBLIC_DOCS,
  PUBLIC_LOCALES,
  PUBLIC_PAGES,
  PUBLIC_ROUTES,
  STAGED_FILES,
  publicDocsIndex,
  publicDocsSources,
} from "./public-routes.mjs";
import { GUARD_BIN, checkRoutes, exportDirOf, guardArgs, routeOf } from "./check-public-export.mjs";

const org = await import("@krizaka/org");
const APP = join(dirname(fileURLToPath(import.meta.url)), "..");

/** The public export of Sovkora (krizaka-internal#4625): a clean export passes; each way out of the list fails. */
describe("the exported routes", () => {
  it("are the home, the docs, the pricing and the privacy page, in French and in English", () => {
    assert.deepEqual(PUBLIC_LOCALES, ["fr", "en"]);
    assert.deepEqual(PUBLIC_PAGES.slice(0, 2), ["", "docs"]);
    assert.deepEqual(PUBLIC_PAGES.slice(-2), ["pricing", "privacy"]);
    for (const route of ["/fr", "/en", "/fr/docs", "/en/docs", "/fr/pricing", "/en/privacy"]) {
      assert.ok(PUBLIC_ROUTES.includes(route), route);
    }
  });

  it("leave out every page that needs an account, a server or a download", () => {
    for (const page of ["login", "register", "download", "desktop", "terms", "open-source", "api", "settings"]) {
      assert.ok(!PUBLIC_ROUTES.some((r) => r.split("/").includes(page)), page);
    }
  });

  it("publish docs pages that exist in the docs index", () => {
    const slugs = JSON.parse(readFileSync(join(APP, "content/docs/index.json"), "utf8")).map((e) => e.slug);
    for (const slug of PUBLIC_DOCS) {
      assert.ok(slugs.includes(slug), slug);
    }
  });

  it("stage only page files that exist", () => {
    for (const { from } of STAGED_FILES) {
      assert.doesNotThrow(() => readFileSync(join(APP, from)), from);
    }
  });
});

describe("the published docs", () => {
  const index = [{ slug: "101" }, { slug: "security" }, { slug: "api" }, { slug: "studio-guide" }];
  it("keep, in order, the entries of the list", () => {
    assert.deepEqual(
      publicDocsIndex(index).map((e) => e.slug),
      ["security", "studio-guide"],
    );
  });
  it("keep the sources of the published pages only: a link to another page is no link", () => {
    assert.deepEqual(publicDocsSources({ "docs/101.md": "101", "SECURITY.md": "security" }), {
      "SECURITY.md": "security",
    });
  });
});

describe("routeOf", () => {
  it("reads an index.html as its folder", () => {
    assert.equal(routeOf("/o", "/o/fr/docs/index.html"), "/fr/docs");
    assert.equal(routeOf("/o", "/o/fr/index.html"), "/fr");
    assert.equal(routeOf("/o", "/o/fr/docs.html"), "/fr/docs");
  });
});

describe("checkRoutes", () => {
  let dir;
  const put = (route, text = "<html></html>") => {
    mkdirSync(join(dir, route), { recursive: true });
    writeFileSync(join(dir, route, "index.html"), text);
  };
  const cleanExport = () => {
    for (const route of PUBLIC_ROUTES) {
      put(route.slice(1));
    }
    // the static host's 404 page of each language is not a route
    for (const locale of PUBLIC_LOCALES) {
      put(`${locale}/404`);
    }
  };
  beforeEach(() => {
    dir = mkdtempSync(join(tmpdir(), "public-export-"));
  });
  afterEach(() => rmSync(dir, { recursive: true, force: true }));

  it("passes on exactly the public routes", () => {
    cleanExport();
    assert.deepEqual(checkRoutes(dir), []);
  });

  for (const route of ["fr/login", "en/desktop", "fr/terms", "en/open-source", "fr/docs/api", "fr/rapport/abc"]) {
    it(`fails on the route /${route}`, () => {
      cleanExport();
      put(route);
      assert.ok(checkRoutes(dir).includes(`route outside the public list: /${route}`));
    });
  }

  it("fails on a missing public route", () => {
    cleanExport();
    rmSync(join(dir, "en/pricing"), { recursive: true });
    assert.ok(checkRoutes(dir).includes("missing public route: /en/pricing"));
  });
});

describe("exportDirOf", () => {
  it("defaults to out-public and refuses a folder outside the app", () => {
    assert.equal(exportDirOf(undefined, "/app"), "/app/out-public");
    assert.throws(() => exportDirOf("../elsewhere", "/app"), /outside the app/);
    assert.throws(() => exportDirOf("/etc", "/app"), /outside the app/);
  });
});

/** The shared guard (@krizaka/config export-guard, krizaka-internal#4623) judges what the pages carry: red on a sign-in link, green on a clean export. */
describe("the shared export guard", () => {
  let inside;
  const guard = (folder) =>
    spawnSync(process.execPath, [GUARD_BIN, ...guardArgs(relative(APP, folder), org)], { cwd: APP, encoding: "utf8" });
  beforeEach(() => {
    inside = mkdtempSync(join(APP, "guard-fixture-"));
    mkdirSync(join(inside, "fr"));
  });
  afterEach(() => rmSync(inside, { recursive: true, force: true }));

  it("refuses a folder that could read as an option or leave the app", () => {
    assert.throws(() => guardArgs("--help", org), /plain relative path/);
    assert.throws(() => guardArgs("a b; rm", org), /plain relative path/);
    assert.equal(guardArgs("out-public", org)[0], "./out-public");
  });

  it("is told Sovkora's status and no allowed amount", () => {
    const args = guardArgs("out-public", org);
    assert.equal(args[args.indexOf("--product") + 1], "sovkora");
    assert.equal(args[args.indexOf("--status") + 1], "development");
    assert.ok(!args.includes("--allow-amount"));
  });

  it("is red on an export with a sign-in link", () => {
    writeFileSync(join(inside, "fr/index.html"), '<html><a href="/fr/sign-in">Sign in</a></html>');
    const run = guard(inside);
    assert.equal(run.status, 1);
    assert.match(run.stdout + run.stderr, /sign-in/i);
  });

  it("is red on a « soon » offer on the pricing page", () => {
    mkdirSync(join(inside, "fr/pricing"));
    writeFileSync(join(inside, "fr/pricing/index.html"), "<html><p>Business: Bientôt</p></html>");
    assert.equal(guard(inside).status, 1);
  });

  it("is red on an amount that is not an example", () => {
    writeFileSync(join(inside, "fr/index.html"), "<html><p>Run cost: $1.76</p></html>");
    assert.equal(guard(inside).status, 1);
  });

  it("is green on a clean export", () => {
    writeFileSync(join(inside, "fr/index.html"), "<html><p>Hello</p></html>");
    assert.equal(guard(inside).status, 0);
  });
});
