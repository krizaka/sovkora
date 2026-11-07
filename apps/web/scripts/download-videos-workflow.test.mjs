import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { describe, it } from "node:test";

// A bug means a missing guard: zizmor and Sonar (S7631) refused a workflow that ran with the org's secrets after a workflow_run.
const source = readFileSync(new URL("../.github/workflows/download-videos.yml", import.meta.url), "utf8");
const code = source.replace(/^\s*#.*$/gm, "");

describe("the download videos workflow", () => {
  it("runs on a schedule and on demand, never on a workflow_run", () => {
    assert.match(code, /schedule:/);
    assert.match(code, /workflow_dispatch:/);
    assert.doesNotMatch(code, /workflow_run/);
  });
  it("names the secrets it passes, never inherits them", () => {
    assert.doesNotMatch(code, /secrets:\s*inherit/);
    assert.match(code, /MEDIA_STORAGE_API_KEY: \$\{\{ secrets\.MEDIA_STORAGE_API_KEY \}\}/);
  });
  it("never writes a secret to GITHUB_ENV", () => {
    assert.doesNotMatch(code, /GITHUB_ENV/);
  });
  it("signs in with the persona secrets by name, never a literal or the QA account", () => {
    assert.match(code, /ORAZAKA_USER_EMAIL: \$\{\{ secrets\.E2E_USER \}\}/);
    assert.match(code, /ORAZAKA_USER_PASSWORD: \$\{\{ secrets\.E2E_PASSWORD \}\}/);
    assert.doesNotMatch(code, /qa_user|qa\+user/);
  });
});

describe("the Mac film of the Download page (krizaka-internal#230)", () => {
  it("calls the Sovkora desktop film as a reusable workflow, pinned, and hands its artifact to app-videos", () => {
    assert.match(code, /uses: krizaka\/sovkora\/\.github\/workflows\/desktop-film\.yml@[0-9a-f]{40}/);
    assert.match(code, /artifacts: \|\n\s+showcase-web\n\s+showcase-mac/);
  });
  it("lets the web videos through when the Mac film fails", () => {
    assert.match(code, /!cancelled\(\) && .*needs\.showcase-web\.result == 'success'/);
  });
});
