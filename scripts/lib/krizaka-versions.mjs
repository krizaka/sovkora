/**
 * Guard: every `com.krizaka` version Sovkora imports must be on a repository krizaka-java publishes to: the artifact repository (krizaka-java#3345, every build) or Maven Central (public releases only). sovkora-parent names its parent (`krizaka-parent`) and imports the BOM
 * (`krizaka.version`); the BOM names one release per artifact. A version no repository carries makes `./mvnw install` fail
 * late, in the first module that needs it (orazaka#434: the kit's starters exist from 0.3.0, the BOM still said 0.2.0).
 * Pure functions + one fetcher, so the rule is unit-tested (scripts/test/krizaka-versions.test.mjs).
 */

const MAVEN_CENTRAL = "https://repo1.maven.org/maven2";

/** XML without its comments (an unterminated one drops the rest). Scanned, not a regex: a pattern can leave a « <!-- » behind. */
export function stripComments(xml) {
  let out = "";
  let at = 0;
  for (let open = xml.indexOf("<!--"); open >= 0; open = xml.indexOf("<!--", at)) {
    out += xml.slice(at, open);
    const close = xml.indexOf("-->", open + 4);
    if (close < 0) return out;
    at = close + 3;
  }
  return out + xml.slice(at);
}

/** The project's own `<artifactId>` and `<version>` (a pom's `<parent>` and comments removed; version falls back to the parent's). */
export function projectCoordinates(pom) {
  const bare = stripComments(pom);
  const parent = /<parent>([\s\S]*?)<\/parent>/.exec(bare)?.[1] ?? "";
  // Only the project's direct children: nested blocks (dependencies, build, properties…) carry other versions.
  const own = bare.replace(/<(parent|dependencyManagement|dependencies|build|properties|profiles|modules|reporting|licenses|developers|scm|distributionManagement|repositories|pluginRepositories)>[\s\S]*?<\/\1>/g, "");
  const first = (xml, tag) => new RegExp(`<${tag}>\\s*([^<\\s]+)\\s*</${tag}>`).exec(xml)?.[1] ?? null;
  return { artifactId: first(own, "artifactId"), version: first(own, "version") ?? first(parent, "version") };
}

/** `<name>value</name>` properties of a pom's `<properties>` block. */
export function properties(pom) {
  const block = /<properties>([\s\S]*?)<\/properties>/.exec(stripComments(pom))?.[1] ?? "";
  return Object.fromEntries([...block.matchAll(/<([\w.-]+)>\s*([^<]*?)\s*<\/\1>/g)].map((m) => [m[1], m[2]]));
}

/** Every `<dependencyManagement>` entry of a BOM: { group, artifactId, version } with `${…}` resolved from its properties. */
export function managed(bom) {
  const bare = stripComments(bom);
  const props = { ...properties(bare), "project.version": projectCoordinates(bom).version };
  const resolve = (v) => v.replace(/\$\{([^}]+)\}/g, (_, k) => props[k] ?? "");
  return [...bare.matchAll(/<dependency>([\s\S]*?)<\/dependency>/g)].flatMap((m) => {
    const g = /<groupId>\s*([^<\s]+)/.exec(m[1])?.[1];
    const a = /<artifactId>\s*([^<\s]+)/.exec(m[1])?.[1];
    const v = /<version>\s*([^<\s]+)/.exec(m[1])?.[1];
    return g === "com.krizaka" && a && v ? [{ group: g, artifactId: a, version: resolve(v) }] : [];
  });
}

/**
 * The versions to prove: [{ what, group, artifactId, version, reactor }] — `reactor` is the version at which the cloned
 * repositories build that artifact, or null when none does. Checked per ARTIFACT, not per repository: a release can
 * exist on Central and still lack an artifact a later one added (kit 0.2.0 has no krizaka-spring-boot-starter-app-config).
 * @param {{ bom: string, parent: string, built: Map<string,string> }} input
 *   bom / parent: pom texts of krizaka-bom and orazaka-parent; built: artifactId -> version the reactor builds it at.
 */
export function claims({ bom, parent, built = new Map() }) {
  const out = bom ? managed(bom).map((d) => ({ what: "krizaka-bom manages", ...d, reactor: built.get(d.artifactId) ?? null })) : [];
  const bomVersion = properties(parent)["krizaka.version"];
  if (bomVersion) {
    out.push({ what: "sovkora-parent krizaka.version imports", group: "com.krizaka", artifactId: "krizaka-bom", version: bomVersion, reactor: built.get("krizaka-bom") ?? null });
  }
  const parentVersion = parentOf(parent);
  if (parentVersion) {
    out.push({ what: "sovkora-parent inherits", group: "com.krizaka", artifactId: "krizaka-parent", version: parentVersion, reactor: null });
  }
  return out;
}

/** The version of `com.krizaka:krizaka-parent` a pom inherits, or null when it inherits something else. */
export function parentOf(pom) {
  const block = /<parent>([\s\S]*?)<\/parent>/.exec(stripComments(pom))?.[1] ?? "";
  if (!/<artifactId>\s*krizaka-parent\s*<\/artifactId>/.test(block)) return null;
  return /<version>\s*([^<\s]+)\s*<\/version>/.exec(block)?.[1] ?? null;
}

/**
 * Where a com.krizaka version may be published, asked in this order: the repository our builds publish to (krizaka-java#3345,
 * `ARTIFACT_REPOSITORY_URL`, set once in the workflow), then Maven Central, which keeps only the public releases.
 */
export function repositories(env = process.env) {
  const own = env.ARTIFACT_REPOSITORY_URL?.trim().replace(/\/+$/, "");
  return own ? [own, MAVEN_CENTRAL] : [MAVEN_CENTRAL];
}

const pomUrl = (base, { group, artifactId, version }) => `${base}/${group.replaceAll(".", "/")}/${artifactId}/${version}/${artifactId}-${version}.pom`;

/** The text of a pom on the first repository that carries it, or null when none does or none can be reached. */
export async function publishedPom(coords, repos = repositories(), fetchImpl = fetch) {
  for (const base of repos) {
    try {
      const res = await fetchImpl(pomUrl(base, coords));
      if (res.status === 200) return await res.text();
    } catch {
      // unreachable: try the next repository
    }
  }
  return null;
}

/** True when a repository carries the pom; false when every one answers 404; null when one cannot be reached and none has it (never a failure). */
export async function onRepository(coords, repos = repositories(), fetchImpl = fetch) {
  let unreachable = false;
  for (const base of repos) {
    try {
      const res = await fetchImpl(pomUrl(base, coords), { method: "HEAD" });
      if (res.status === 200) return true;
      if (res.status !== 404) unreachable = true;
    } catch {
      unreachable = true;
    }
  }
  return unreachable ? null : false;
}

/** The claims nobody provides: the reactor builds another version and no repository carries the one named. */
export async function unprovided(list, check = onRepository) {
  const missing = [];
  for (const c of list) {
    if (c.reactor === c.version) continue;
    if ((await check(c)) === false) missing.push(c);
  }
  return missing;
}

export function explain(c, repos = repositories()) {
  return `${c.what} ${c.group}:${c.artifactId}:${c.version}, which is on none of ${repos.join(", ")}. Name a krizaka-java release one of them carries (build/sovkora-parent/pom.xml).`;
}
