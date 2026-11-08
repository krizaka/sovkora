/**
 * The Java naming convention of ADR-029, as pure functions over the tree (scripts/check-java-layout.mjs runs them in CI,
 * scripts/test/java-layout.test.mjs proves them):
 *   N1  every module's groupId is com.krizaka.sovkora (inherited or declared);
 *   N2  an artifactId is `sovkora-<module>[-<part>]`, lowercase kebab, never -common, -util(s), -impl or -misc;
 *   N3  every class of a module lives under its root package com.krizaka.sovkora.<module>[.<part>], and no package is in
 *       two modules (a split package);
 *   N4  a module folder's leaf name is its artifactId; parent folders group modules by role.
 * The Maven enforcer holds N1 and N2 in the build too; this check adds N3 and N4, which Maven cannot see.
 */

export const GROUP = "com.krizaka.sovkora";
export const ROLES = new Set(["build", "contracts", "engine", "services", "e2e"]);
const BANNED = /-(common|utils?|impl|misc)(-|$)/;
const KEBAB = /^sovkora(-[a-z0-9]+)+$/;

import { stripComments as bare } from "./krizaka-versions.mjs";

/** A pom's own groupId (or its parent's), artifactId and packaging. */
export function coordinates(pom) {
  const xml = bare(pom);
  const parent = /<parent>([\s\S]*?)<\/parent>/.exec(xml)?.[1] ?? "";
  const own = xml.replace(/<(parent|dependencyManagement|dependencies|build|properties|profiles|modules|licenses|scm|reporting)>[\s\S]*?<\/\1>/g, "");
  const tag = (src, t) => new RegExp(String.raw`<${t}>\s*([^<\s]+)\s*</${t}>`).exec(src)?.[1] ?? null;
  return {
    groupId: tag(own, "groupId") ?? tag(parent, "groupId"),
    artifactId: tag(own, "artifactId"),
    packaging: tag(own, "packaging") ?? "jar",
  };
}

/** N3: the root package of a module, from its artifactId (`sovkora-engine-core` → com.krizaka.sovkora.engine.core). */
export function rootPackage(artifactId) {
  const rest = artifactId.replace(/^sovkora-/, "").replace(/-service$/, "");
  return `${GROUP}.${rest.split("-").join(".")}`;
}

/** Which rule each module breaks: [{ rule, module, message }]. `modules`: [{ dir, pom, main: string[], test: string[] }] (packages). */
export function violations(modules) {
  const out = [];
  const owners = new Map();
  for (const m of modules) {
    const c = coordinates(m.pom);
    const where = m.dir || "(root)";
    if (c.groupId !== GROUP) out.push({ rule: "N1", module: where, message: `groupId ${c.groupId}, not ${GROUP}` });
    if (!c.artifactId || !KEBAB.test(c.artifactId) || BANNED.test(c.artifactId)) {
      out.push({ rule: "N2", module: where, message: `artifactId ${c.artifactId} is not sovkora-<module>[-<part>] (banned: -common, -util(s), -impl, -misc)` });
    }
    if (c.packaging === "pom") continue; // an aggregator groups modules by role: no code, no leaf rule
    const leaf = where.split("/").pop();
    if (leaf !== c.artifactId) out.push({ rule: "N4", module: where, message: `the folder is ${leaf}, the artifactId ${c.artifactId}` });
    if (!m.dir.split("/").slice(0, -1).every((p, i) => i > 0 || ROLES.has(p))) {
      out.push({ rule: "N4", module: where, message: `a module sits under a role folder (${[...ROLES].join(", ")})` });
    }
    const root = rootPackage(c.artifactId ?? "");
    for (const p of new Set([...m.main, ...m.test])) {
      if (p !== root && !p.startsWith(`${root}.`)) out.push({ rule: "N3", module: where, message: `package ${p} is outside the module root ${root}` });
    }
    for (const p of new Set(m.main)) {
      const seen = owners.get(p);
      if (seen && seen !== where) out.push({ rule: "N3", module: where, message: `package ${p} is split with ${seen}` });
      owners.set(p, where);
    }
  }
  return out;
}
