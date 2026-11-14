// node --test scripts/test — a GPU boot builds the worker from this repository's workers/media, never from a retired repository (krizaka-internal#3611).
import { readFileSync } from "node:fs";
import { test } from "node:test";
import assert from "node:assert/strict";

const read = (path) => readFileSync(new URL(`../../${path}`, import.meta.url), "utf8");
const BOOT_FILES = ["infra/gpu/runpod-bootstrap.sh", "infra/gpu/runpod-measure.sh", "infra/gpu/host.sh", "infra/gpu/watchdog.sh", "scripts/lib/gpu-runpod.sh", "scripts/gpu-dev.sh", "scripts/studio-preflight.mjs"];

test("a GPU boot or infra script references no GitHub repository of krizaka other than sovkora", () => {
  for (const file of BOOT_FILES) {
    const repos = [...read(file).matchAll(/(?:github\.com[/:]|repos\/)krizaka\/([\w.-]+)/g)].map((m) => m[1].replace(/\.git$/, ""));
    assert.deepEqual(repos.filter((r) => r !== "sovkora"), [], `${file} names a repository that is not krizaka/sovkora`);
  }
});

test("no GPU boot script names the retired media worker repository (orazaka-worker-*.git, krizaka/orazaka-*)", () => {
  for (const file of BOOT_FILES) {
    assert.doesNotMatch(read(file), /orazaka-worker-[\w-]+\.git|(?:github\.com[/:]|repos\/)krizaka\/orazaka-/, `${file} names a retired orazaka-* repository`);
  }
});

test("the pod gets the worker from workers/media of sovkora, at the commit asked", () => {
  for (const file of ["infra/gpu/runpod-bootstrap.sh", "infra/gpu/runpod-measure.sh"]) {
    const text = read(file);
    assert.match(text, /github\.com\/krizaka\/sovkora\.git/, `${file} clones sovkora`);
    assert.match(text, /workers\/media/, `${file} uses workers/media`);
    assert.match(text, /checkout -q "\$\{WORKER_REF:-main\}"/, `${file} checks out WORKER_REF`);
  }
  assert.match(read("scripts/lib/gpu-runpod.sh"), /ls-remote https:\/\/github\.com\/krizaka\/sovkora\.git refs\/heads\/main/);
});

test("the images-only (STILLS) pod never receives the broker password, the service token or the broker CA", () => {
  const lib = read("scripts/lib/gpu-runpod.sh");
  assert.match(lib, /STILLS/);
  const guard = read("scripts/runpod-guard.py");
  const block = guard.slice(guard.indexOf("def create_body"), guard.indexOf("def cmd_body"));
  assert.match(block, /e\.get\("STILLS"\)/);
  const stills = block.slice(block.indexOf('if e.get("MEASURE")'), block.indexOf("GPU_OUT_URL"));
  assert.match(stills, /STILLS/, "the credential list branches on STILLS");
});
