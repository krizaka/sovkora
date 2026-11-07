import assert from "node:assert/strict";
import { test } from "node:test";
import { modulesOf, plan } from "../ci-plan.mjs";

const modules = modulesOf(`<modules><module>krizaka/krizaka-build</module><module>e2e/sovkora-e2e</module>
<module>services/edge/sovkora-edge-service</module><module>services/edge/sovkora-edge-service-x</module></modules>`);

test("modules are read from the root pom, longest first", () => {
  assert.equal(modules.length, 4);
  assert.ok(modules.indexOf("services/edge/sovkora-edge-service-x") < modules.indexOf("services/edge/sovkora-edge-service"));
});

test("everything but a pull request runs the full reactor", () => {
  for (const event of ["push", "merge_group", "schedule", "workflow_dispatch"]) {
    assert.equal(plan({ event, files: ["e2e/sovkora-e2e/a.java"], modules }).mode, "full");
  }
});

test("a pull request builds the changed modules and their dependents", () => {
  const p = plan({
    event: "pull_request",
    files: ["e2e/sovkora-e2e/src/A.java", "services/edge/sovkora-edge-service/x/B.java"],
    modules,
  });
  assert.equal(p.mode, "changed");
  assert.equal(p.pl, "services/edge/sovkora-edge-service,e2e/sovkora-e2e");
});

test("a sibling directory with the same prefix is not the module", () => {
  const p = plan({ event: "pull_request", files: ["services/edge/sovkora-edge-service-x/A.java"], modules });
  assert.equal(p.pl, "services/edge/sovkora-edge-service-x");
});

test("what every module shares, or an unknown change set, fails open to full", () => {
  for (const f of ["pom.xml", ".mvn/maven.config", "infra/initdb/10-x.sql", "orazaka.workspace.json", ".github/workflows/ci.yml", "scripts/ci-plan.mjs"]) {
    assert.equal(plan({ event: "pull_request", files: [f, "e2e/sovkora-e2e/A.java"], modules }).mode, "full", f);
  }
  assert.equal(plan({ event: "pull_request", files: [], modules }).mode, "full");
  assert.equal(plan({ event: "pull_request", files: null, modules }).mode, "full");
});

test("a pull request outside every module tests no module", () => {
  const p = plan({ event: "pull_request", files: ["docs/a.md"], modules });
  assert.equal(p.mode, "changed");
  assert.equal(p.pl, "");
});
