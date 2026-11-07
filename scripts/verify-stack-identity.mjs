#!/usr/bin/env node
/**
 * Refuses to test a stack that is not this build.
 *
 * Why. The e2e harness does not tear down when the build fails before `post-integration-test`, so a
 * failed run leaves its JVMs listening. Twenty-one minutes were once lost to an `/actuator/health`
 * answered by the previous run's conversation service — but the wasted time is the visible half.
 * **The other half is that a stale stack answers on the right port with the wrong code**, so a green
 * can come from yesterday's build and nothing in the harness could tell the difference. Teardown
 * prevents the waste; this prevents the wrong answer, and only the first had been noticed.
 *
 * How. `spring-boot-maven-plugin:build-info` stamps `build.time` into every service's
 * `META-INF/build-info.properties` at package time, and the service reports it at `/actuator/info`.
 * This compares what each service SAYS against what is on disk for the module the harness started.
 * A process from an earlier build reports an earlier time and is refused by name.
 *
 * It runs in the health gate rather than inside a test runner, because everything in
 * `integration-test` — the Java ITs as well as the HTTP contracts — must be protected, and the
 * first of those to run would otherwise have already talked to the stale process.
 *
 * Usage: verify-stack-identity.mjs <moduleDir>=<baseUrl> ...
 */

import { readFileSync } from "node:fs";
import { join } from "node:path";

/** Reads `build.time` out of a build-info.properties, whose values are colon-escaped. */
function buildTimeOnDisk(moduleDir) {
  const text = readFileSync(join(moduleDir, "target", "classes", "META-INF", "build-info.properties"), "utf8");
  const line = text.split("\n").find((l) => l.startsWith("build.time="));
  if (!line) {
    throw new Error(`no build.time in ${moduleDir}'s build-info.properties`);
  }
  return line.slice("build.time=".length).replace(/\\/g, "").trim();
}

/** Asks a running service which build it is. */
async function buildTimeReported(baseUrl) {
  const response = await fetch(`${baseUrl}/actuator/info`, { signal: AbortSignal.timeout(10_000) });
  if (!response.ok) {
    throw new Error(`${baseUrl}/actuator/info answered ${response.status}`);
  }
  const info = await response.json();
  const time = info?.build?.time;
  if (!time) {
    throw new Error(
      `${baseUrl}/actuator/info reports no build.time — either the service predates the` +
        ` build-info goal, or 'info' is not exposed on this service`,
    );
  }
  return String(time).trim();
}

const targets = process.argv.slice(2).map((pair) => {
  const at = pair.lastIndexOf("=");
  return { moduleDir: pair.slice(0, at), baseUrl: pair.slice(at + 1) };
});

if (targets.length === 0) {
  console.error("usage: verify-stack-identity.mjs <moduleDir>=<baseUrl> ...");
  process.exit(2);
}

const mismatches = [];
for (const { moduleDir, baseUrl } of targets) {
  const expected = buildTimeOnDisk(moduleDir);
  let reported;
  try {
    reported = await buildTimeReported(baseUrl);
  } catch (unreachable) {
    mismatches.push(`${baseUrl} — ${unreachable.message}`);
    continue;
  }
  if (reported !== expected) {
    mismatches.push(
      `${baseUrl} is running a DIFFERENT BUILD: it reports ${reported}, this tree packaged ${expected}`,
    );
  }
}

if (mismatches.length > 0) {
  console.error(
    "\n[identity] refusing to test this stack — a service answering on the right port with the" +
      " wrong code produces a green that belongs to another build:\n  " +
      mismatches.join("\n  ") +
      "\n\n  A failed e2e run does not reach post-integration-test, so its JVMs keep the ports." +
      "\n  Stop them and re-run: pkill -f 'services/.*\\.jar'",
  );
  process.exit(1);
}

console.log(`[identity] ${targets.length} services all report this build's timestamp.`);
