// node --test — the one repository stays one repository (ADR-029). Sixteen repositories became folders on 2026-11-08: what the old
// layout needed and the new one does not must not come back, and every folder must be what modules.json says it is.
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { test } from "node:test";

const root = new URL("../../", import.meta.url);
const tracked = execFileSync("git", ["ls-files"], { cwd: root, encoding: "utf8" }).split("\n").filter(Boolean);
const read = (path) => readFileSync(new URL(path, root), "utf8");
const { modules } = JSON.parse(read("modules.json"));

/** Where a former path may still be named: history, the generated docs (rebuilt from the code), and the ledger's former paths. */
const HISTORY = /(^|\/)CHANGELOG[^/]*$|^docs\/_generated\/|^infra\/migrations\/|^docs\/measurements\/|^scripts\/dev-migrations\.mjs$|^scripts\/test\/(dev-migrations|repository-layout)\.test\.mjs$/;
const FORMER = /sovkora-libs\/|orazaka-apps\/|orazaka-end2end|orazaka\.workspace\.json|scripts\/workspace\.mjs|krizaka\/krizaka-build/;

test("no file names a path of the former multi-repository workspace", () => {
  const text = tracked.filter((f) => !HISTORY.test(f) && !/\.(png|jpe?g|webp|gif|ico|svg|mp4|webm|wav|mp3|pdf|jar|lock|bin|keystore)$/i.test(f));
  const offenders = [];
  for (const f of text) {
    let s;
    try {
      s = read(f);
    } catch {
      continue; // a symlink or a file outside the tree
    }
    if (FORMER.test(s)) offenders.push(f);
  }
  assert.deepEqual(offenders, [], `former workspace paths in: ${offenders.join(", ")}`);
});

test("every module of modules.json is a folder of the tree, and every Maven module is under one of them", () => {
  for (const m of modules) assert.ok(existsSync(new URL(`${m.path}/`, root)), `${m.name}: ${m.path} exists`);
  const poms = tracked.filter((f) => f.endsWith("/pom.xml")).map((f) => f.slice(0, -"/pom.xml".length));
  for (const dir of poms) {
    const owner = modules.find((m) => dir === m.path || dir.startsWith(`${m.path}/`) || m.path.startsWith(`${dir}/`));
    assert.ok(owner || dir === "e2e/sovkora-e2e", `${dir} belongs to a module of modules.json`);
  }
});

test("every app of apps/ is in the pnpm workspace, with its own lockfile, and no npm lockfile is left", () => {
  const workspace = read("pnpm-workspace.yaml");
  const apps = [...new Set(tracked.filter((f) => /^apps\/[^/]+\/package\.json$/.test(f)).map((f) => f.split("/").slice(0, 2).join("/")))];
  assert.ok(apps.length >= 4, "web, mobile, cli, desktop");
  for (const app of apps) {
    assert.match(workspace, new RegExp(`^  - ${app}$`, "m"), `${app} is a package of the workspace`);
    assert.ok(tracked.includes(`${app}/pnpm-lock.yaml`), `${app} has its own pnpm lockfile`);
    assert.ok(!tracked.includes(`${app}/package-lock.json`), `${app} has no npm lockfile`);
  }
});

test("no imported folder keeps the files of the repository it was", () => {
  const LEFT = /^(build|contracts|engine|services\/[^/]+(\/sovkora-[^/]+)?|workers\/[^/]+|packs|apps\/(web|mobile|cli))\/(\.github\/|\.claude\/|\.mvn\/|mvnw(\.cmd)?$|CLAUDE\.md$|dup-ratchet\.json$|sonar-ratchet\.json$|sonar-project\.properties$|\.repo-layout\.json$|\.gitleaksignore$|LICENSE-APACHE$|LICENSE\.md$)/;
  const left = tracked.filter((f) => LEFT.test(f));
  assert.deepEqual(left, []);
});

test("one licence layout: FSL at the root, Apache-2.0 in exactly the folders the NOTICE names", () => {
  const notice = read("NOTICE");
  assert.match(read("LICENSE"), /Functional Source License, Version 1\.1, ALv2 Future License/);
  const all = tracked.filter((f) => /(^|\/)LICENSE$/.test(f) && f !== "LICENSE").map((f) => f.slice(0, -"/LICENSE".length));
  // a package inside an Apache-2.0 folder (apps/cli/alias, the `orazaka` npm alias) carries the same licence
  const apache = all.filter((d) => !all.some((o) => o !== d && d.startsWith(`${o}/`))).sort();
  assert.deepEqual(apache, ["apps/cli", "build", "contracts", "packs", "services/edge"]);
  for (const dir of apache) {
    assert.match(read(`${dir}/LICENSE`), /Apache License\s+Version 2\.0/, `${dir}/LICENSE is Apache-2.0`);
    assert.ok(notice.includes(`${dir}/`), `NOTICE names ${dir}/`);
  }
});

test("the dev image builds from this tree and fetches krizaka-java only, at the release sovkora-parent imports", () => {
  const docker = read("infra/deploy/Dockerfile");
  const clones = [...docker.matchAll(/clone[^\n]*https:\/\/github\.com\/krizaka\/([\w-]+)\.git/g)].map((m) => m[1]);
  assert.deepEqual(clones, ["krizaka-java"]);
  assert.match(docker, /<krizaka\.version>/, "the release is read from build/sovkora-parent/pom.xml");
  assert.match(docker, /--branch "v\$version"/);
  assert.doesNotMatch(docker, /MAVEN_GITHUB_PACKAGES_TOKEN|maven\.pkg\.github\.com|ADD https:\/\/github\.com/);
});

test("the dev app's components are built from this repository, the web app from its folder", () => {
  const spec = read("infra/deploy/sovkora-dev.app.yaml");
  const repos = [...spec.matchAll(/^\s+repo: (\S+)$/gm)].map((m) => m[1]);
  assert.ok(repos.length >= 3);
  assert.deepEqual([...new Set(repos)], ["krizaka/sovkora"]);
  assert.match(spec, /- name: web\n\s+github:\n\s+repo: krizaka\/sovkora\n[\s\S]*?source_dir: apps\/web/);
});

test("every krizaka/.github reference in a workflow is pinned by a full commit SHA", () => {
  const workflows = tracked.filter((f) => /^\.github\/workflows\/.+\.ya?ml$/.test(f));
  for (const f of workflows) {
    for (const m of read(f).matchAll(/uses: (krizaka\/\.github\/[^@\s]+)@(\S+)/g)) {
      assert.match(m[2], /^[0-9a-f]{40}$/, `${f}: ${m[1]}`);
    }
  }
});
