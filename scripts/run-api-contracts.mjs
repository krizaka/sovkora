#!/usr/bin/env node
/**
 * Runs the tier-1 HTTP contracts and asserts that every assertion they DECLARE was REPORTED.
 *
 * Why this wrapper exists. httpyac's assert grammar is `?? [js] <selector> <operator> [value]` with
 * the operator last, and an expression it cannot parse does not always fail — one that opens with
 * `[` produces **no tick, no cross, and no error**: the line simply is not in the output. Measured,
 * not guessed (ADR-069 §0.3). So any assertion in this suite may silently not be running, and the
 * suite's own exit code cannot tell you.
 *
 * This is the third application of one property, not a new idea:
 *
 *   Java   — GOV-006 fails a governance rule that completes having examined zero subjects.
 *   Python — test_main.py fails when the tests a file defines differ from the tests the runner
 *            collected, and names the ones never collected.
 *   Here   — a green run must have reported every assertion the sources declare.
 *
 * It also reports the honest request count. httpyac prints "N requests processed" where N is the
 * number of `###` REGIONS, and this suite uses `###` for banner rules and prose as well as for
 * request delimiters — so the number everybody quoted as proof counts decorative lines as passing
 * requests, and cannot go down when a request is deleted. Both numbers are printed from here on.
 *
 * The count is enforced only when httpyac itself succeeded. A failing run has already stopped at
 * `--bail`, and the assertions after the failure legitimately did not run; adding a second,
 * derived failure on top of the real one would bury it.
 */

import { spawn } from "node:child_process";
import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import { byText } from "./lib/source-scan.mjs";
import { toolPath } from "./lib/tool-path.mjs";
import { assertionOf, hasRequestLine } from "./lib/http-contract-lines.mjs";

/** A result line httpyac prints for an assertion that ran. */
const REPORTED = /^\s*(✓|✖)\s/;

/**
 * Every assertion the suite declares, with where it came from.
 *
 * @param {string} directory the api-contracts directory
 * @returns {{assertions: {file: string, text: string}[], requests: number, regions: number}}
 */
function declared(directory) {
  const assertions = [];
  let requests = 0;
  let regions = 0;
  for (const name of readdirSync(directory).filter((f) => f.endsWith(".http")).sort((a, b) => a.localeCompare(b))) {
    const text = readFileSync(join(directory, name), "utf8");
    for (const region of text.split(/^###/m).filter((r) => r.trim())) {
      regions += 1;
      if (hasRequestLine(region)) {
        requests += 1;
      }
    }
    for (const line of text.split("\n")) {
      const assertion = assertionOf(line);
      if (assertion !== null) {
        assertions.push({ file: name, text: assertion });
      }
    }
  }
  return { assertions, requests, regions };
}

const [directory, ...httpyacArgs] = process.argv.slice(2);
if (!directory) {
  console.error("usage: run-api-contracts.mjs <api-contracts-dir> [httpyac args...]");
  process.exit(2);
}

const suite = declared(directory);
const output = [];

const child = spawn(
  toolPath("npx"),
  ["httpyac", "send", `${directory}/**/*.http`, ...httpyacArgs],
  { stdio: ["inherit", "pipe", "inherit"], env: process.env },
);

child.stdout.setEncoding("utf8");
child.stdout.on("data", (chunk) => {
  process.stdout.write(chunk);
  output.push(chunk);
});

child.on("close", (code) => {
  const lines = output.join("").split("\n");
  const reported = lines.filter((line) => REPORTED.test(line)).length;

  console.log(
    `\n[contracts] ${suite.requests} requests in ${suite.regions} regions ` +
      `(httpyac counts regions, and this suite uses ### for banners too)`,
  );
  console.log(`[contracts] assertions declared ${suite.assertions.length}, reported ${reported}`);

  if (code !== 0) {
    // The run already failed and --bail stopped it; the assertions after the failure did not run
    // for a reason that is already on screen. Do not bury it under a derived count mismatch.
    process.exit(code);
  }

  if (reported !== suite.assertions.length) {
    const byFile = new Map();
    for (const assertion of suite.assertions) {
      byFile.set(assertion.file, (byFile.get(assertion.file) ?? 0) + 1);
    }
    console.error(
      `\n[contracts] FAILED — this suite declares ${suite.assertions.length} assertions and the ` +
        `run reported ${reported}. An assertion httpyac cannot parse produces no tick, no cross ` +
        `and no error, so the difference is assertions that did not run and did not say so ` +
        `(ADR-069 §0.3). Declared per file:`,
    );
    for (const [file, count] of [...byFile].sort(([a], [b]) => byText(a, b))) {
      console.error(`  ${count.toString().padStart(3)}  ${file}`);
    }
    console.error(
      "\n  httpyac's grammar is `?? [js] <selector> <operator> [value]` with the operator LAST:" +
        "\n  `===` is split on its inner `==`, and an expression opening with `[` vanishes." +
        "\n  Count the wrong entries and compare that to zero instead.",
    );
    process.exit(1);
  }
  process.exit(0);
});
