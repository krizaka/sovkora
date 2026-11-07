#!/usr/bin/env node
/**
 * Generates the code-derived documentation set under docs/_generated/ — the single source for
 * everything that describes *what the code is* (never hand-edited). Curated design docs
 * (AGENTS.md, VISION_ARCHITECTURE.md, INTERFACES.md, DEVEX_LIFECYCLE.md, guides) stay
 * hand-authored; this generator owns only docs/_generated/.
 *
 * Produces, by scanning the repo:
 *   - architecture.json   modules / dependencies / ports / pipeline / messaging
 *   - ARCHITECTURE.md     module map, dependency edges, interceptor pipeline, messaging
 *   - INTERFACE_CONTRACTS.md  inbound/outbound port contracts per module
 *     (named distinctly from the curated docs/INTERFACES.md design contract)
 *   - USE_CASES.md        UseCase catalog (App Factory)
 *   - INTERCEPTORS.md     interceptor registry + DB-driven order
 *   - API_REFERENCE.md    router REST endpoints
 *   - CLI.md              orazaka CLI commands, subcommands, arguments and options
 *   - BUSINESS.md         the App Factory: intentions, use-cases, dispatcher (orazaka-business)
 *   - TOOLS.md            tools, MCP servers, tool cache and write sandbox (orazaka-tools)
 *   - JOBS.md             job plane, executors, every @Scheduled task, automation connectors
 *   - PACKS.md            reference packs, manifest fields, the pack authors' guide (orazaka-packs)
 *   - ADMIN.md            the SecOps console and every ADMIN-only endpoint
 *   - MODELS.md           AI model catalog (from infra/initdb seed)
 *   - MODULES.md          module map (from modules.json)
 *
 * Output is deterministic (no timestamps) so `--check` is a pure staleness diff.
 *
 * Usage:
 *   node scripts/generate-docs.mjs           regenerate docs/_generated/
 *   node scripts/generate-docs.mjs --check   fail (exit 1) if any output is stale
 */
import { screenRoute } from "./lib/screen-route.mjs";
import { readFileSync, writeFileSync, mkdirSync, existsSync, readdirSync, statSync } from "node:fs";
import { join, basename, relative, resolve } from "node:path";
import { buildModels, catalogue, manifestLicences, readmeLicences } from "./lib/model-registry.mjs";
import { walk } from "./lib/walk.mjs";
import { byNumber, byText, requestMatcherRules, servicePortOf } from "./lib/source-scan.mjs";
import { mdCell, stripTags, xmlDecode } from "./lib/doc-text.mjs";
import { packsSection } from "./lib/pack-blocks.mjs";
import { mdMeasuredRuns } from "./lib/measured-runs.mjs";
import { mdCinema } from "./lib/cinema-measure.mjs";
import { mdLawBakeoff } from "./lib/bakeoff/report.mjs";

const ROOT = process.cwd();
const GEN_DIR = join(ROOT, "docs", "_generated");
const CHECK = process.argv.includes("--check");

const read = (p) => (existsSync(p) ? readFileSync(p, "utf8") : "");

/**
 * The module catalogue (modules.json, ADR-029): every folder of the one Sovkora repository that is a module, in build order.
 * The Krizaka building blocks the services run with (users, billing, the BOM) are krizaka-java's: the generator reads them
 * from a checkout of krizaka-java at the release sovkora-parent imports (`KRIZAKA_JAVA_DIR`, default `.krizaka-java`, the
 * folder CI checks it out to), so the docs describe exactly what the build resolves.
 */
const CATALOGUE = JSON.parse(read(join(ROOT, "modules.json")) || '{"modules":[]}');
const KRIZAKA_JAVA = relative(ROOT, resolve(ROOT, process.env.KRIZAKA_JAVA_DIR || ".krizaka-java")).replaceAll("\\", "/");
const REPOSITORIES = [
  { name: "krizaka-java", path: KRIZAKA_JAVA, kind: "maven", layer: "krizaka", description: "The Krizaka Java building blocks (com.krizaka on Maven Central).", dependsOn: [] },
  ...CATALOGUE.modules,
];

/** The repository a workspace path belongs to (longest matching path prefix). */
const repositoryOf = (relPath) =>
  REPOSITORIES.filter((r) => relPath === r.path || relPath.startsWith(r.path + "/"))
    .sort((a, b) => b.path.length - a.path.length)[0]?.name ?? "sovkora";

/**
 * Concatenated DB bootstrap in execution (alphabetical) order. Each context's file lives in the
 * repository that owns it (<repo>/infra/initdb); the workspace keeps the reset and dev fixtures.
 */
const readInitDb = () => {
  const dirs = [join(ROOT, "infra", "initdb"), ...REPOSITORIES.map((r) => join(ROOT, r.path, "infra", "initdb"))];
  const files = [];
  for (const dir of dirs.filter((d) => existsSync(d))) {
    for (const n of readdirSync(dir).filter((x) => x.endsWith(".sql"))) files.push({ n, p: join(dir, n) });
  }
  // A service that moved to Flyway (krizaka-internal#2255) keeps its seeds in its V<n> baselines: read after the initdb files.
  const baselines = [];
  for (const r of REPOSITORIES) {
    const dir = join(ROOT, r.path, "src", "main", "resources", "db", "migration");
    if (!existsSync(dir)) continue;
    for (const n of readdirSync(dir).filter((x) => /^V\d+__.*\.sql$/.test(x))) baselines.push({ n, p: join(dir, n) });
  }
  const byName = (a, b) => a.n.localeCompare(b.n, undefined, { numeric: true });
  return [...files.toSorted(byName), ...baselines.toSorted(byName)].map((f) => read(f.p)).join("\n");
};

const javaFiles = (dir) => walk(dir, (p) => p.endsWith(".java"));

/** artifactId → module directory, for every Maven module of every cloned repository. */
const MODULES = (() => {
  const index = new Map();
  for (const repo of REPOSITORIES.filter((r) => r.kind === "maven")) {
    const repoDir = join(ROOT, repo.path);
    const candidates = [repoDir, ...(existsSync(repoDir) ? readdirSync(repoDir).map((n) => join(repoDir, n)) : [])];
    for (const dir of candidates) {
      const pom = read(join(dir, "pom.xml"));
      const own = pom.replace(/<parent>[\s\S]*?<\/parent>/, "").match(/<artifactId>([\w-]+)<\/artifactId>/);
      if (own && !/<packaging>pom<\/packaging>/.test(pom)) index.set(own[1], dir);
    }
  }
  return index;
})();

/** A front-matter value, quoted when plain YAML would misread it (a ": " starts a nested mapping). */
const yamlValue = (v) => (/:\s|\s#|^[[\]{}'"&*!|>%@`]/.test(v) ? JSON.stringify(v) : v);

function frontmatter(title, description, category, order) {
  return [
    "---",
    `title: ${yamlValue(title)}`,
    `description: ${yamlValue(description)}`,
    `category: ${category}`,
    `order: ${order}`,
    "generated: true",
    "---",
    "",
    `# ${title}`,
    "",
    // No banner for the reader: `generated: true` above says it to the tools, and the site shows « generated » itself.
  ].join("\n");
}

// ════════════════════════════════════════════════════════════════════════════
// EXTRACTION
// ════════════════════════════════════════════════════════════════════════════

/** Port interface names under a relative ports dir (1 type per file, AGENTS ERR-103). */
function portsIn(moduleDir, relPortDir) {
  const javaSrcDir = join(moduleDir, "src/main/java");
  if (!existsSync(javaSrcDir)) return [];
  return javaFiles(javaSrcDir)
    .filter((f) => f.replaceAll("\\", "/").includes(relPortDir))
    .filter((f) => /\b(?:public\s+)?interface\s+\w+/.test(read(f)))
    .map((f) => basename(f, ".java"))
    .sort();
}

/**
 * A source file found by name under a directory, or a loud failure. The model used to name its
 * sources by full path; when the Java packages moved to com.krizaka.orazaka every one of those
 * paths went dead, and the generator went on writing an empty messaging topology, an empty
 * interceptor registry and an empty use-case catalogue as if nothing had happened. A source the
 * model depends on is now looked up, and its absence stops the build.
 */
function sourceFile(relDir, name) {
  const hit = walk(join(ROOT, relDir), (p) => basename(p) === name && p.includes(`${"/"}src${"/"}main${"/"}`))[0];
  if (!hit) throw new Error(`generate-docs: ${name} not found under ${relDir} — the architecture model depends on it`);
  return hit;
}
/** A source directory found by its trailing path segments (e.g. "business/usecases"), or a loud failure. */
function sourceDir(relDir, tail) {
  const found = [];
  const visit = (dir) => {
    if (!existsSync(dir)) return;
    for (const name of readdirSync(dir)) {
      if (name === "node_modules" || name === "target" || name === ".git" || name === "test") continue;
      const p = join(dir, name);
      if (!statSync(p).isDirectory()) continue;
      if (p.replaceAll("\\", "/").endsWith(`/${tail}`)) found.push(p);
      else visit(p);
    }
  };
  visit(join(ROOT, relDir));
  if (!found.length) throw new Error(`generate-docs: no ${tail}/ under ${relDir} — the architecture model depends on it`);
  return found[0];
}

const xmlText = (s) => xmlDecode(s).replace(/\s+/g, " ").trim();
/** A pom without its <parent>, <build> and <dependencyManagement>: what the module itself declares. */
const ownPom = (pom) =>
  pom
    .replace(/<parent>[\s\S]*?<\/parent>/, "")
    .replace(/<build>[\s\S]*?<\/build>/g, "")
    .replace(/<dependencyManagement>[\s\S]*?<\/dependencyManagement>/g, "")
    .replace(/<profiles>[\s\S]*?<\/profiles>/g, "");

/**
 * Every Maven module of the platform, read the way Maven reads it: from each repository's root
 * pom, down its <modules>. A module under examples/ is a sample, not part of the platform.
 */
function reactorModules() {
  const out = [];
  const visit = (dir, repo) => {
    const pom = read(join(dir, "pom.xml"));
    if (!pom) return;
    const own = ownPom(pom);
    const id = own.match(/<artifactId>([\w.-]+)<\/artifactId>/)?.[1];
    if (/<packaging>pom<\/packaging>/.test(pom)) {
      for (const m of pom.matchAll(/<module>([^<]+)<\/module>/g)) visit(join(dir, m[1].trim()), repo);
      return;
    }
    const rel = relative(ROOT, dir).replaceAll("\\", "/");
    if (!id || rel.split("/").includes("examples")) return;
    // The project's own <version> (outside dependencies, properties and plugins), else its parent's.
    const head = own.replace(/<(dependencies|properties|repositories|pluginRepositories|reporting)>[\s\S]*?<\/\1>/g, "");
    const parentVersion = pom.match(/<parent>[\s\S]*?<version>([^<]+)<\/version>[\s\S]*?<\/parent>/)?.[1] ?? null;
    let version = head.match(/<version>([^<]+)<\/version>/)?.[1] ?? parentVersion;
    if (version?.startsWith("${")) version = parentVersion;
    out.push({ id, dir, path: rel, repository: repo, pom, version, description: xmlText(own.match(/<description>([\s\S]*?)<\/description>/)?.[1] ?? "") || null });
  };
  for (const repo of REPOSITORIES.filter((r) => r.kind === "maven")) visit(join(ROOT, repo.path), repo);
  return out;
}

/** The module's declared dependencies, without the test-scoped ones (they never ship). */
function pomDependencies(pom) {
  const block = ownPom(pom).match(/<dependencies>([\s\S]*?)<\/dependencies>/)?.[1] ?? "";
  return [...block.matchAll(/<dependency>([\s\S]*?)<\/dependency>/g)]
    .map((m) => ({
      artifactId: m[1].match(/<artifactId>([^<]+)<\/artifactId>/)?.[1]?.trim(),
      scope: m[1].match(/<scope>([^<]+)<\/scope>/)?.[1]?.trim() ?? "compile",
      optional: /<optional>\s*true\s*<\/optional>/.test(m[1]),
    }))
    .filter((d) => d.artifactId && d.scope !== "test");
}

/** The HTTP port a Spring Boot service listens on: server.port, or Spring's default 8080. */
function servicePort(dir) {
  const yml = read(join(dir, "src/main/resources/application.yml")) || read(join(dir, "src/main/resources/application.yaml"));
  return servicePortOf(yml) ?? 8080;
}

/**
 * The role of a module in the hexagon (AGENTS.md §2) — one band of the module map, outermost
 * first. Each rule reads a fact of the code or of the workspace manifest, never a list kept here.
 */
const ROLES = [
  { id: "client", title: "Clients", rule: "a UI repository of the workspace manifest (layer app, kind npm)" },
  { id: "service", title: "Services & workers", rule: "a module with a main (SpringApplication.run), or a native worker" },
  { id: "adapter", title: "Adapters", rule: "an imported module that is none of the below: persistence, typed HTTP clients, asset store, bridge" },
  { id: "application", title: "Application", rule: "the orchestration, pipeline and capability libraries around the core (AGENTS.md §2: business, interceptors, tools)" },
  { id: "domain", title: "Domain core", rule: "a `*-core` module — the centre of its hexagon, depending on no outer layer" },
  { id: "contract", title: "Contracts", rule: "a Tier-1 `*-api` module — pure interfaces and records" },
  { id: "platform", title: "Krizaka service kit", rule: "a module of krizaka-service-kit — the cross-cutting code with one author (ADR-073), starters included" },
];
const APPLICATION_LIBRARIES = ["sovkora-engine-business", "sovkora-engine-interceptors", "sovkora-engine-tools"];

function roleOf(m) {
  if (/SpringApplication\.run\(/.test(javaFiles(join(m.dir, "src/main/java")).map(read).join("\n"))) return "service";
  if (m.path.split("/").includes("krizaka-service-kit")) return "platform";
  if (m.id.endsWith("-api")) return "contract";
  if (m.id.endsWith("-core")) return "domain";
  if (APPLICATION_LIBRARIES.includes(m.id)) return "application";
  return "adapter";
}

/** Glob-free match of an AMQP topic binding ("job.*.done", "job.#") against a routing key. */
function topicMatches(binding, key) {
  const b = binding.split("."), k = key.split(".");
  const go = (i, j) => {
    if (i === b.length) return j === k.length;
    if (b[i] === "#") return go(i + 1, j) || (j < k.length && go(i, j + 1));
    if (j === k.length) return false;
    return (b[i] === "*" || b[i] === k[j] || k[j] === "{…}") && go(i + 1, j + 1);
  };
  return go(0, 0);
}

function extractArchitecture() {
  const reactor = reactorModules();
  const known = new Set(reactor.map((m) => m.id));
  const modules = [];
  const dependencies = [];

  // The test kits (`*-test-support`, AGENTS.md §2 Tier 2) are build tooling, not architecture.
  const testOnly = new Set(reactor.filter((k) => k.id.endsWith("-test-support")).map((k) => k.id));
  for (let i = reactor.length - 1; i >= 0; i--) if (testOnly.has(reactor[i].id)) reactor.splice(i, 1);
  known.clear();
  for (const m of reactor) known.add(m.id);

  for (const m of reactor) {
    m.role = roleOf(m);
    for (const d of pomDependencies(m.pom)) {
      if (!known.has(d.artifactId) || d.artifactId === m.id) continue;
      if (dependencies.some((e) => e.from === m.id && e.to === d.artifactId)) continue;
      dependencies.push({ from: m.id, to: d.artifactId, ...(d.scope !== "compile" ? { scope: d.scope } : {}), ...(d.optional ? { optional: true } : {}) });
    }
  }

  // Native workers and UI clients: one repository each, described by the manifest.
  const extra = REPOSITORIES.filter((r) => r.layer === "worker" || (r.layer === "app" && r.kind === "pnpm")).map((r) => ({
    id: r.name,
    dir: join(ROOT, r.path),
    path: r.path,
    repository: r,
    role: r.layer === "worker" ? "service" : "client",
    runtime: r.layer === "worker" ? (r.kind === "python" ? "python" : r.kind) : "node",
    version: null,
    description: r.description,
  }));

  for (const m of [...reactor, ...extra]) {
    const owner = m.repository.layer === "krizaka" ? "krizaka" : "orazaka";
    modules.push({
      id: m.id,
      path: m.path,
      repository: m.repository.name,
      owner,
      role: m.role,
      // The two-value layer the first site schemas read (framework = imported, app = run).
      layer: m.role === "client" || m.role === "service" ? "app" : "framework",
      runtime: m.runtime ?? "jvm",
      version: m.version,
      description: m.description,
      ports: m.runtime ? { inbound: [], outbound: [] } : { inbound: portsIn(m.dir, "/domain/ports/inbound/"), outbound: portsIn(m.dir, "/domain/ports/outbound/") },
    });
  }
  const order = new Map(ROLES.map((r, i) => [r.id, i]));
  modules.sort((a, b) => order.get(a.role) - order.get(b.role) || a.owner.localeCompare(b.owner) * -1 || a.id.localeCompare(b.id));
  dependencies.sort((a, b) => a.from.localeCompare(b.from) || a.to.localeCompare(b.to));

  // What each module packages, transitively: a library's code runs inside every service that
  // depends on it, so that is where its HTTP calls and its messages come from.
  const direct = new Map(modules.map((m) => [m.id, dependencies.filter((d) => d.from === m.id && d.scope !== "provided").map((d) => d.to)]));
  const closure = (id, seen = new Set()) => {
    for (const to of direct.get(id) ?? []) if (!seen.has(to)) { seen.add(to); closure(to, seen); }
    return seen;
  };
  const services = modules.filter((m) => m.role === "service");
  const hostsOf = (id) => {
    const self = modules.find((m) => m.id === id);
    if (self?.role === "service") return [id];
    return services.filter((s) => closure(s.id).has(id)).map((s) => s.id);
  };

  // ── Runtime flows ─────────────────────────────────────────────────────────────────────────
  const flows = [];
  const addFlow = (f) => {
    if (f.from === f.to) return;
    if (!flows.some((x) => x.from === f.from && x.to === f.to && x.kind === f.kind && x.via === f.via)) flows.push(f);
  };
  const jvmServices = reactor.filter((m) => m.role === "service");
  const byPort = new Map(jvmServices.map((m) => [servicePort(m.dir), m.id]));

  // 1. UI clients → the services whose default local address they name.
  for (const c of extra.filter((m) => m.role === "client")) {
    const ports = new Set();
    // Sources only (src/, app/): build output (.next, dist, …) would make the model depend on what ran before it.
    const sources = ["src", "app"].flatMap((d) => walk(join(c.dir, d), (p) => /\.(ts|tsx|js|mjs)$/.test(p) && !/(__tests__|\.test\.|\.spec\.|\/e2e\/|\/tests?\/|\/\.next\/|\/dist\/|\/\.expo\/)/.test(p.replaceAll("\\", "/"))));
    for (const f of sources) {
      for (const m of read(f).matchAll(/localhost:(\d{4,5})/g)) if (byPort.has(Number(m[1]))) ports.add(Number(m[1]));
    }
    for (const port of [...ports].sort(byNumber)) addFlow({ from: c.id, to: byPort.get(port), kind: "http", via: `localhost:${port}` });
  }
  // 2. Route tables (the edge): `- path-prefix: X` + `target: ${VAR:http://localhost:PORT}`.
  for (const s of jvmServices) {
    const yml = read(join(s.dir, "src/main/resources/application.yml"));
    for (const m of yml.matchAll(/-\s*path-prefix:\s*(\S+)\s*\n\s*target:\s*\$\{[\w.-]+:https?:\/\/[\w.-]+:(\d+)\}/g)) {
      const to = byPort.get(Number(m[2]));
      if (to) addFlow({ from: s.id, to, kind: "http", via: `route ${m[1]}` });
    }
  }
  // 3. Typed clients: a service that packages `<x>-client` calls the service of the same repository.
  for (const s of jvmServices) {
    for (const lib of closure(s.id)) {
      if (!lib.endsWith("-client")) continue;
      const repo = reactor.find((m) => m.id === lib)?.repository.name;
      const target = jvmServices.find((m) => m.repository.name === repo && m.id !== s.id);
      if (target) addFlow({ from: s.id, to: target.id, kind: "http", via: lib });
    }
  }

  const initSql = readInitDb();
  const interceptorClasses = extractInterceptors([]);
  const pipeline = [...initSql.matchAll(/\('(\w+)',\s*'[^']+',\s*(\d+),\s*(TRUE|FALSE),\s*'[^']*'\)/g)]
    .map((m) => {
      const cls = interceptorClasses.find((c) => c.name === m[1]);
      return {
        interceptor: m[1],
        order: Number(m[2]),
        enabled: m[3] === "TRUE",
        // Phase 1 (core) runs in the order of the code whatever the row says (ADR-051); phase 2 by this order.
        phase: coreInterceptorKeys().includes(m[1]) ? "core" : "dynamic",
        // A row with no class behind it configures nothing.
        implemented: Boolean(cls),
        ...(cls ? { concern: cls.concern, summary: cls.summary } : {}),
      };
    })
    .sort((a, b) => a.order - b.order);
  const coreChain = coreInterceptorKeys().map((name, i) => {
    const cls = interceptorClasses.find((c) => c.name === name);
    return { interceptor: name, rank: i + 1, ...(cls ? { concern: cls.concern, summary: cls.summary } : {}) };
  });

  const messaging = extractMessaging(reactor, extra);

  // 4. Messages: the services that publish a routing key → the services whose queue it reaches.
  for (const p of messaging.producers) {
    if (p.routingKey === "(dynamic)") continue;
    const keys = p.routingKey === "(capability route)" ? [...new Set(messaging.capabilityRoutes.map((r) => r.routingKey))] : [p.routingKey];
    for (const key of keys) {
      for (const q of messaging.queues.filter((x) => x.exchange === p.exchange && x.binding)) {
        if (!q.binding.split(", ").some((b) => topicMatches(b, key))) continue;
        for (const c of messaging.consumers.filter((x) => x.queue === q.name)) {
          for (const from of hostsOf(p.module)) for (const to of hostsOf(c.module)) {
            if (!flows.some((f) => f.kind === "amqp" && f.from === from && f.to === to && f.via === q.name)) {
              addFlow({ from, to, kind: "amqp", via: q.name, exchange: p.exchange, routingKey: p.routingKey === "(capability route)" ? q.binding : key });
            }
          }
        }
      }
    }
  }
  flows.sort((a, b) => a.kind.localeCompare(b.kind) || a.from.localeCompare(b.from) || a.to.localeCompare(b.to) || a.via.localeCompare(b.via));

  // The module map (modules.json): which folder holds what, in build order.
  const repositories = REPOSITORIES.map((r) => ({
    name: r.name,
    url: r.name === "krizaka-java" ? "https://github.com/krizaka/krizaka-java" : `https://github.com/krizaka/sovkora/tree/main/${r.path}`,
    path: r.path,
    kind: r.kind,
    layer: r.layer,
    description: r.description,
    dependsOn: r.dependsOn,
  }));

  return {
    product: "orazaka",
    source: "generated from code by scripts/generate-docs.mjs — do not hand-edit",
    roles: ROLES,
    repositories,
    modules,
    dependencies,
    flows,
    pipeline,
    coreChain,
    messaging,
    buildingBlocks: extractBuildingBlocks(),
  };
}

/**
 * The Krizaka Java building blocks (krizaka.com /docs/java): every artifact of the Krizaka repositories
 * of the workspace — the build (parent POM, BOM, test kit) included, which the architecture leaves out
 * as tooling — with its version and the real relations between them, read from the POMs:
 *   compile / runtime / provided / test   a <dependency> on another Krizaka artifact (its scope)
 *   parent                                the module inherits that POM (through its repository's reactor)
 *   import                                the module resolves its versions from that BOM (a <scope>import</scope>)
 * Reactor-only POMs (a repository's root, starters/) are not artifacts anyone uses: a module that
 * inherits one is drawn inheriting the first real parent above it, and what the reactor imports
 * (the BOM) is imported by its modules.
 */
const BUILDING_BLOCK_ROLES = [
  { id: "service", title: "Services", rule: "a module with a main (SpringApplication.run): a deployable Krizaka service" },
  { id: "adapter", title: "Adapters", rule: "a `*-client` or `*-persistence` module: typed HTTP client, JPA adapter" },
  { id: "domain", title: "Domain core", rule: "a `*-core` module" },
  { id: "contract", title: "Contracts", rule: "a `*-api` module — interfaces, records, events" },
  { id: "starter", title: "Starters", rule: "a module of krizaka-service-kit/starters — one dependency that switches a module on" },
  { id: "platform", title: "Platform kit", rule: "a library module of krizaka-service-kit" },
  { id: "build", title: "Build", rule: "a module of krizaka-build: the parent POM, the BOM, the test kit" },
];

function extractBuildingBlocks() {
  const poms = [];
  const visit = (dir, repo) => {
    const pom = read(join(dir, "pom.xml"));
    if (!pom) return;
    const own = ownPom(pom);
    const id = own.match(/<artifactId>([\w.-]+)<\/artifactId>/)?.[1];
    const parent = pom.match(/<parent>[\s\S]*?<artifactId>([\w.-]+)<\/artifactId>[\s\S]*?<\/parent>/)?.[1] ?? null;
    const parentVersion = pom.match(/<parent>[\s\S]*?<version>([^<]+)<\/version>[\s\S]*?<\/parent>/)?.[1] ?? null;
    const head = own.replace(/<(dependencies|properties|repositories|pluginRepositories|reporting|modules)>[\s\S]*?<\/\1>/g, "");
    let version = head.match(/<version>([^<]+)<\/version>/)?.[1] ?? parentVersion;
    if (version?.startsWith("${")) version = parentVersion;
    const children = [...pom.matchAll(/<module>([^<]+)<\/module>/g)].map((m) => m[1].trim());
    const imports = [...(pom.match(/<dependencyManagement>([\s\S]*?)<\/dependencyManagement>/)?.[1] ?? "").matchAll(/<dependency>([\s\S]*?)<\/dependency>/g)]
      .filter((m) => /<scope>\s*import\s*<\/scope>/.test(m[1]))
      .map((m) => m[1].match(/<artifactId>([^<]+)<\/artifactId>/)?.[1]?.trim());
    const deps = [...(ownPom(pom).match(/<dependencies>([\s\S]*?)<\/dependencies>/)?.[1] ?? "").matchAll(/<dependency>([\s\S]*?)<\/dependency>/g)].map((m) => ({
      artifactId: m[1].match(/<artifactId>([^<]+)<\/artifactId>/)?.[1]?.trim(),
      scope: m[1].match(/<scope>([^<]+)<\/scope>/)?.[1]?.trim() ?? "compile",
      optional: /<optional>\s*true\s*<\/optional>/.test(m[1]),
    }));
    const rel = relative(ROOT, dir).replaceAll("\\", "/");
    poms.push({
      id,
      dir,
      path: rel,
      repository: repo,
      pom,
      parent,
      version,
      imports,
      deps,
      // A reactor POM lists modules; the parent POM and the BOM list none and are published artifacts.
      reactor: /<packaging>pom<\/packaging>/.test(pom) && children.length > 0,
      packaging: pom.match(/<packaging>(\w+)<\/packaging>/)?.[1] ?? "jar",
      description: xmlText(own.match(/<description>([\s\S]*?)<\/description>/)?.[1] ?? "") || null,
    });
    for (const child of children) visit(join(dir, child), repo);
  };
  // krizaka-build's own reactor inherits krizaka-parent by relativePath: visit the parent explicitly too.
  for (const repo of REPOSITORIES.filter((r) => r.kind === "maven" && r.layer === "krizaka")) visit(join(ROOT, repo.path), repo);
  const byId = new Map(poms.filter((p) => p.id).map((p) => [p.id, p]));
  const artifacts = poms.filter((p) => p.id && !p.reactor && !p.path.split("/").includes("examples"));
  const known = new Set(artifacts.map((a) => a.id));
  if (!known.size) throw new Error(`generate-docs: no Krizaka artifact found in ${KRIZAKA_JAVA}: point KRIZAKA_JAVA_DIR at a krizaka-java checkout of the release sovkora-parent imports`);

  const roleOf = (a) => {
    if (a.path.split("/").includes("krizaka-service-kit")) return a.path.split("/").includes("starters") ? "starter" : "platform";
    if (a.repository.name === "krizaka-build") return "build";
    if (/SpringApplication\.run\(/.test(javaFiles(join(a.dir, "src/main/java")).map(read).join("\n"))) return "service";
    if (a.id.endsWith("-api")) return "contract";
    if (a.id.endsWith("-core")) return "domain";
    return "adapter";
  };
  /** The first real artifact up the parent chain, and every BOM the chain imports. */
  const lineage = (a) => {
    let parent = null;
    const imports = new Set(a.imports);
    for (let p = byId.get(a.parent), guard = 0; p && guard < 10; p = byId.get(p.parent), guard++) {
      for (const i of p.imports) imports.add(i);
      if (!parent && !p.reactor) parent = p.id;
    }
    return { parent: parent ?? (known.has(a.parent) ? a.parent : null), imports: [...imports].filter((i) => known.has(i)) };
  };

  const order = new Map(BUILDING_BLOCK_ROLES.map((r, i) => [r.id, i]));
  const modules = artifacts
    .map((a) => ({
      id: a.id,
      path: a.path,
      repository: a.repository.name,
      role: roleOf(a),
      packaging: a.packaging,
      version: a.version,
      description: a.description,
    }))
    .sort((x, y) => order.get(x.role) - order.get(y.role) || x.repository.localeCompare(y.repository) || x.id.localeCompare(y.id));

  const dependencies = [];
  const add = (d) => {
    if (d.from !== d.to && !dependencies.some((x) => x.from === d.from && x.to === d.to && x.kind === d.kind)) dependencies.push(d);
  };
  for (const a of artifacts) {
    const { parent, imports } = lineage(a);
    if (parent) add({ from: a.id, to: parent, kind: "parent" });
    for (const bom of imports) add({ from: a.id, to: bom, kind: "import" });
    for (const d of a.deps) {
      if (!known.has(d.artifactId)) continue;
      add({ from: a.id, to: d.artifactId, kind: d.scope, ...(d.optional ? { optional: true } : {}) });
    }
  }
  dependencies.sort((x, y) => x.from.localeCompare(y.from) || x.to.localeCompare(y.to) || x.kind.localeCompare(y.kind));
  return { roles: BUILDING_BLOCK_ROLES, modules, dependencies };
}

/**
 * Real AMQP inventory (Phase 0 fitness function). Every module's topology constants are read
 * from the module itself (its AmqpConstants) over the shared contracts (MessagingContract,
 * NotificationRouting); queues are the *_QUEUE / *_BINDING / *_DLQ triples of one file;
 * consumers are @RabbitListener queues — SpEL bean references (`#{settlementQueue.name}`)
 * resolved through the bean that declares the queue — and producers the convertAndSend /
 * outbox calls. `docs build --check` turns topology drift into a build failure.
 */
function extractMessaging(reactor, extra) {
  const constantsOf = (src, base = new Map()) => {
    const map = new Map(base);
    for (let pass = 0; pass < 3; pass++) {
      for (const m of src.matchAll(/String\s+(\w+)\s*=\s*"([^"]+)"\s*;/g)) map.set(m[1], m[2]);
      for (const m of src.matchAll(/String\s+(\w+)\s*=\s*([\w.]+)\s*\+\s*"([^"]+)"\s*;/g)) {
        const v = map.get(m[2].split(".").pop());
        if (v) map.set(m[1], v + m[3]);
      }
      for (const m of src.matchAll(/String\s+(\w+)\s*=\s*([\w.]+)\s*;/g)) {
        const v = map.get(m[2].split(".").pop());
        if (v && !map.has(m[1])) map.set(m[1], v);
      }
    }
    return map;
  };

  // The shared contracts every module may name.
  const contractFiles = reactor.flatMap((m) =>
    walk(join(m.dir, "src/main/java"), (p) => /\/(MessagingContract|NotificationRouting)\.java$/.test(p.replaceAll("\\", "/"))),
  );
  if (!contractFiles.some((f) => f.endsWith("MessagingContract.java"))) {
    throw new Error("generate-docs: MessagingContract.java not found — the messaging topology depends on it");
  }
  const shared = constantsOf(contractFiles.map(read).join("\n"));
  const JOBS = shared.get("JOBS_EXCHANGE");
  const EVENTS = shared.get("EVENTS_EXCHANGE");
  const DLX = shared.get("DLX_EXCHANGE");

  // The Krizaka building blocks name no exchange: they run on the one the host platform gives them
  // (krizaka.messaging.exchanges, EVENTS_EXCHANGE / DLX_EXCHANGE in the workspace env).
  // The contract's key builders — `jobDoneKey(jobId)` is `JOB_EVENT_PREFIX + jobId + JOB_EVENT_DONE_SUFFIX`.
  const keyBuilders = new Map(
    [...contractFiles.map(read).join("\n").matchAll(/static\s+String\s+(\w+)\(\s*String\s+\w+\s*\)\s*\{\s*return\s+([^;]+);/g)].map((m) => [m[1], m[2]]),
  );
  // A routing key or a queue name as the code writes it: a literal, a constant, or a concatenation
  // of both with runtime values (`"job." + jobId + ".done"` → `job.{…}.done`).
  const resolveToken = (raw, local) => {
    const token = raw.trim();
    if (/^(exchanges\.events\(\)|eventsExchange|this\.eventsExchange)$/.test(token)) return EVENTS;
    if (/^(exchanges\.deadLetter\(\)|deadLetterExchange)$/.test(token)) return DLX;
    const call = token.match(/^(?:[\w]+\.)?(\w+)\(([^()]*)\)$/);
    if (call && keyBuilders.has(call[1])) return resolveToken(keyBuilders.get(call[1]), local);
    const parts = token.split(/\s*\+\s*/);
    let known = false;
    const text = parts
      .map((part) => {
        const lit = part.match(/^"([^"]*)"$/);
        if (lit) { known = true; return lit[1]; }
        const v = local.get(part.split(".").pop());
        if (v) { known = true; return v; }
        return "{…}";
      })
      .join("")
      .replace(/(\{…\})+/g, "{…}");
    return known ? text : null;
  };

  const queues = [];
  const consumers = [];
  const producers = [];
  // A job whose key comes from the capability registry (CapabilityRoutingClient, ADR-037) is
  // routed by a row of orazaka_capabilities: the key is data, so it is read from the seed below.
  let routedByCapability = false;
  const addProducer = (module, exchange, routingKey) => {
    if (!exchange || !routingKey) return;
    if (routingKey === "(dynamic)" && exchange === JOBS && routedByCapability) routingKey = "(capability route)";
    if (!producers.some((x) => x.module === module && x.exchange === exchange && x.routingKey === routingKey)) producers.push({ module, exchange, routingKey });
  };
  const addConsumer = (module, queue) => {
    if (queue && !consumers.some((x) => x.module === module && x.queue === queue)) consumers.push({ module, queue });
  };

  for (const mod of reactor) {
    const files = walk(join(mod.dir, "src/main/java"), (p) => p.endsWith(".java"));
    if (!files.length) continue;
    const moduleConstants = constantsOf(
      files.filter((f) => /\/(AmqpConstants|MessagingContract|NotificationRouting)\.java$/.test(f.replaceAll("\\", "/"))).map(read).join("\n"),
      shared,
    );

    // Queues: the *_QUEUE / *_BINDING / *_DLQ triples of each constants file of this module.
    for (const f of files.filter((p) => /\/(AmqpConstants|MessagingContract)\.java$/.test(p.replaceAll("\\", "/")))) {
      const own = constantsOf(read(f), shared);
      const local = [...read(f).matchAll(/String\s+(\w+_QUEUE)\s*=/g)].map((m) => m[1]);
      for (const name of local) {
        const value = own.get(name);
        if (!value) continue;
        const stem = name.slice(0, -"_QUEUE".length);
        // One queue may carry several bindings — the interactive lane binds job.text.* and
        // job.media.analyze (ADR-067); a service's queue binds DONE_BINDING and ERROR_BINDING.
        const fileBindings = [...read(f).matchAll(/String\s+(\w+_BINDING)\s*=/g)].map((m) => m[1]);
        let keys = fileBindings.filter((k) => k === `${stem}_BINDING` || (k.startsWith(`${stem}_`) && k.endsWith("_BINDING")));
        // A file with one queue and unprefixed bindings (DONE_BINDING, ERROR_BINDING) binds them to it
        // when the configuration says so: read the Binding beans of the module.
        if (!keys.length) {
          const config = files.map(read).join("\n");
          const bean = stem.toLowerCase().replace(/_(\w)/g, (_, c) => c.toUpperCase());
          keys = fileBindings.filter((k) => new RegExp(`bind\\(\\s*\\w*${bean}Queue\\s*\\)[\\s\\S]{0,120}?with\\(\\s*(?:AmqpConstants\\.)?${k}\\b`, "i").test(config));
        }
        const binding = keys.map((k) => own.get(k)).filter((v, i, all) => v && all.indexOf(v) === i).join(", ") || null;
        const exchange = value.startsWith(`${JOBS}.`) ? JOBS : EVENTS;
        if (!queues.some((q) => q.name === value)) {
          queues.push({ name: value, exchange, binding, dlq: own.get(`${stem}_DLQ`) ?? null, declaredBy: mod.id });
        }
      }
    }

    for (const file of files) {
      const src = read(file);
      const local = constantsOf(src, moduleConstants);
      routedByCapability = /\bCapabilityRoutingClient\b/.test(src);
      for (const m of src.matchAll(/@RabbitListener\(\s*queues\s*=\s*(\{[^}]*\}|[^,)\n]+)\s*[,)]/g)) {
        for (const token of m[1].replace(/[{}]/g, " ").split(",")) {
          const spel = token.trim().match(/^"#\s*(\w+)\.name\s*"$/) ?? token.trim().match(/^"#\{(\w+)\.name\}"$/);
          if (spel) {
            // `#{settlementQueue.name}`: the queue the bean of that name declares.
            const bean = files.map(read).join("\n").match(new RegExp(`Queue\\s+${spel[1]}\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?(?:new Queue\\(|QueueBuilder\\.durable\\()\\s*([^,)]+)`));
            addConsumer(mod.id, bean ? resolveToken(bean[1], local) : null);
          } else addConsumer(mod.id, resolveToken(token, local));
        }
      }
      for (const m of src.matchAll(/convertAndSend\(\s*([^,()]+),\s*([^,]+?),/g)) {
        const ex = resolveToken(m[1], local);
        addProducer(mod.id, ex, resolveToken(m[2], local) ?? (ex ? "(dynamic)" : null));
      }
      for (const m of src.matchAll(/new OutboxMessage\(([\s\S]*?)\)\s*\)?;/g)) {
        const args = m[1].split(",");
        if (args.length >= 4) {
          const ex = resolveToken(args[2], local);
          addProducer(mod.id, ex, resolveToken(args[3], local) ?? (ex ? "(dynamic)" : null));
        }
      }
      // The studio's own outbox (ADR-067): `appendCommand(aggregate, exchange, routingKey, …)` and
      // `append(aggregate, eventType, …)` on the events exchange, both relayed by OutboxRelay.
      for (const m of src.matchAll(/appendCommand\(([\s\S]*?)\);/g)) {
        const args = m[1].split(",");
        if (args.length >= 4) {
          const ex = resolveToken(args[1], local);
          addProducer(mod.id, ex, resolveToken(args[2], local) ?? (ex ? "(dynamic)" : null));
        }
      }
      for (const m of src.matchAll(/outboxService\.append\(([\s\S]*?)\);/g)) {
        const args = m[1].split(",");
        if (args.length >= 3) addProducer(mod.id, EVENTS, resolveToken(args[1], local) ?? "(dynamic)");
      }
    }
  }

  // Native workers: Python publishers and consumers (a duplicated subset of the contract).
  for (const w of extra.filter((m) => m.runtime === "python")) {
    for (const f of walk(w.dir, (p) => p.endsWith(".py") && !/\/tests?\//.test(p))) {
      const src = read(f);
      const pyConst = new Map([...src.matchAll(/^(\w+)\s*=\s*["']([^"']+)["']/gm)].map((m) => [m[1], m[2]]));
      for (const m of src.matchAll(/basic_consume\(\s*queue\s*=\s*(\w+)/g)) {
        const queue = pyConst.get(m[1]);
        addConsumer(w.id, queue);
        // The worker binds its queue from its declaration (worker.yaml `bindings`, ADR-038).
        const declared = [...read(join(w.dir, "worker.yaml")).matchAll(/^\s*-\s*"(job\.[^"]+)"/gm)].map((b) => b[1]);
        const q = queues.find((x) => x.name === queue);
        if (q && declared.length) q.binding = [...new Set([...(q.binding ? q.binding.split(", ") : []), ...declared])].join(", ");
      }
      const ex = src.match(/EVENTS_EXCHANGE\s*=\s*'([^']+)'/)?.[1];
      const key = src.match(/routing_key=f"([^"]+)"/)?.[1]?.replace(/\{[^}]+\}/g, "{…}");
      if (ex && key) addProducer(w.id, ex, key);
    }
  }

  // The retry policy of the kit's listener container (krizaka.messaging.retry), defaults read from code.
  const retrySrc = read(sourceFile(`${KRIZAKA_JAVA}/krizaka-service-kit/krizaka-messaging`, "ConsumerRetryProperties.java"));
  const retry = {
    maxAttempts: Number(retrySrc.match(/maxAttempts\s*=\s*maxAttempts\s*==\s*null\s*\?\s*(\d+)/)?.[1]),
    initialMs: Number(retrySrc.match(/initial\s*=\s*initial\s*==\s*null\s*\?\s*Duration\.ofMillis\((\d+)\)/)?.[1]),
    multiplier: Number(retrySrc.match(/multiplier\s*=\s*multiplier\s*==\s*null\s*\?\s*([\d.]+)/)?.[1]),
    maxMs: Number(retrySrc.match(/max\s*=\s*max\s*==\s*null\s*\?\s*Duration\.ofSeconds\((\d+)\)/)?.[1]) * 1000,
    property: "krizaka.messaging.retry",
    module: "krizaka-messaging",
  };
  if (Object.values(retry).some((v) => typeof v === "number" && !Number.isFinite(v))) {
    throw new Error("generate-docs: could not read the retry defaults of ConsumerRetryProperties");
  }
  // Which consumers run on the kit's listener container (retry, then <queue>.dlq).
  const deps = new Map(reactor.map((m) => [m.id, pomDependencies(m.pom).map((d) => d.artifactId)]));
  const usesKit = (id, seen = new Set()) => {
    if (id === "krizaka-messaging") return true;
    if (seen.has(id)) return false;
    seen.add(id);
    return (deps.get(id) ?? []).some((d) => usesKit(d, seen));
  };
  for (const c of consumers) c.retry = usesKit(c.module) ? "kit" : "module";

  // The capability routes: (feature_key, handler_key, routing_key, …) rows of orazaka_capabilities.
  const seed = readInitDb();
  const capabilityRoutes = [];
  for (const block of seed.matchAll(/INSERT INTO orazaka_capabilities\s*\(([^)]*)\)\s*VALUES([\s\S]*?);\s*$/gm)) {
    const cols = block[1].split(",").map((c) => c.trim());
    const [fi, hi, ri] = ["feature_key", "handler_key", "routing_key"].map((c) => cols.indexOf(c));
    if (fi < 0 || ri < 0) continue;
    for (const row of block[2].matchAll(/^\(\s*'([^']*)'\s*,\s*'([^']*)'\s*,\s*'([^']*)'/gm)) {
      const vals = [row[1], row[2], row[3]];
      const route = { feature: vals[fi], handler: hi >= 0 ? vals[hi] : null, routingKey: vals[ri] };
      if (!capabilityRoutes.some((r) => r.feature === route.feature)) capabilityRoutes.push(route);
    }
  }
  capabilityRoutes.sort((a, b) => a.routingKey.localeCompare(b.routingKey) || a.feature.localeCompare(b.feature));

  queues.sort((a, b) => a.name.localeCompare(b.name));
  consumers.sort((a, b) => a.module.localeCompare(b.module) || a.queue.localeCompare(b.queue));
  producers.sort((a, b) => a.module.localeCompare(b.module) || a.routingKey.localeCompare(b.routingKey));
  const exchanges = [
    { name: JOBS, type: "topic" },
    { name: EVENTS, type: "topic" },
    { name: DLX, type: "direct" },
  ].filter((e) => e.name);

  return { exchanges, queues, producers, consumers, capabilityRoutes, retry };
}

/** UseCase catalog from business/usecases. */
function extractUseCases() {
  const dir = sourceDir("engine/sovkora-engine-business", "engine/business/usecases");
  return javaFiles(dir)
    .filter((f) => /implements\s+UseCase\b/.test(read(f)))
    .map((f) => {
      const src = read(f);
      const name = basename(f, ".java");
      const capability = src.match(/Capability\.([A-Z]+)/)?.[1] ?? "—";
      const domain = relative(dir, f).split(/[/\\]/)[0];
      // The first javadoc line, with inline tags ({@code X}, {@link X}) rendered as code — stopping
      // at the first "@" truncated "a first-class {@code Intention}" to an unclosed "{", which is
      // not valid MDX on the site.
      const javadoc = (src.match(/\/\*\*\s*\n\s*\*\s*([^\n]+)/)?.[1] ?? "")
        .replace(/\{@\w+\s+([^}]*)\}/g, "`$1`")
        .replace(/[{}]/g, "")
        .trim();
      return { name, capability, domain, summary: javadoc };
    })
    .sort((a, b) => a.name.localeCompare(b.name));
}

/**
 * The Phase 1 core chain, read from PipelineRegistry rather than from the database.
 *
 * A core interceptor runs whatever the config table says — resolveCoreChain never consults
 * `enabled` — so rendering it as "no row, unknown order" reads as unregistered when the truth is
 * the opposite: mandatory and first. The ordering of a non-bypassable control belongs in code, so
 * that is where the registry reads it from (ADR-051).
 */
function coreInterceptorKeys() {
  const src = read(sourceFile("engine/sovkora-engine-core", "PipelineRegistry.java"));
  const block = /CORE_INTERCEPTOR_KEYS\s*=\s*List\.of\(([\s\S]*?)\);/.exec(src);
  return block ? [...block[1].matchAll(/"(\w+)"/g)].map((m) => m[1]) : [];
}

/**
 * The first sentence of the javadoc right above `class <name>`, inline tags rendered as code and
 * braces dropped (the text is compiled as MDX on the site), or null.
 */
function classSummary(src, name) {
  const at = src.search(new RegExp(`\\bclass\\s+${name}\\b`));
  if (at < 0) return null;
  const doc = src.slice(0, at).match(/\/\*\*([\s\S]*?)\*\/[^/]*$/)?.[1];
  if (!doc) return null;
  const text = stripTags(doc
    .split("\n")
    .map((l) => l.replace(/^\s*\*\s?/, ""))
    .filter((l) => !l.startsWith("@"))
    .join(" "))
    .replace(/\{@\w+\s+([^}]*)\}/g, "`$1`")
    .replace(/[{}]/g, "")
    .replace(/\s+/g, " ")
    .trim();
  // "Order 6 — …": the order lives in the database (ADR-027), a number in prose is stale by design.
  const body = text.replace(/^Order\s+\d+\s*[—–-]\s*/, "");
  const first = body.match(/^(.+?[.!?])(\s|$)/)?.[1] ?? body;
  return first || null;
}

/** Interceptor registry: classes + concern package + core rank or DB order. */
function extractInterceptors(pipeline) {
  const dir = sourceDir("engine/sovkora-engine-interceptors", "sovkora/engine/interceptors");
  const orderByName = new Map(pipeline.map((p) => [p.interceptor.toLowerCase(), p]));
  const core = coreInterceptorKeys();
  return javaFiles(dir)
    // An interceptor is a class implementing the core's PromptContextInterceptor SPI (ERR-122),
    // whatever its name says: UserContextResolver and SystemContextInjector lead the core chain.
    .filter((f) => new RegExp(`\\bclass\\s+${basename(f, ".java")}\\b[^{]*\\bimplements\\b[^{]*\\bPromptContextInterceptor\\b`).test(read(f)))
    .map((f) => {
      const name = basename(f, ".java");
      const concern = relative(dir, f).split(/[/\\]/)[0];
      const summary = classSummary(read(f), name);
      const coreRank = core.indexOf(name);
      if (coreRank >= 0) {
        return { name, concern, summary, order: `core ${coreRank + 1}`, enabled: "locked", sort: coreRank - 1000 };
      }
      // By exact key: the fuzzy match it replaces gave UserContextInterceptor the order of UserContextResolver.
      const hit = orderByName.get(name.toLowerCase());
      return { name, concern, summary, order: hit?.order ?? null, enabled: hit?.enabled ?? null, sort: hit?.order ?? 999 };
    })
    .sort((a, b) => a.sort - b.sort || a.name.localeCompare(b.name));
}

/** Router REST endpoints from @RequestMapping + @*Mapping. */
/**
 * The owned Tier-3 library source roots a service hosts, read from its pom.
 *
 * Only Tier-3 can hold a controller: Tier-1 is contracts and Tier-2 is the platform SDK, neither
 * of which owns a transport surface (AGENTS.md §2).
 */
function hostedTier3Dirs(serviceRoot) {
  const pom = read(join(serviceRoot, "pom.xml"));
  const dirs = [];
  for (const m of pom.matchAll(/<artifactId>(sovkora-engine-(?:business|persistence[\w-]*)|krizaka-users-(?:core|persistence))<\/artifactId>/g)) {
    const moduleDir = MODULES.get(m[1]);
    const candidate = moduleDir && join(moduleDir, "src/main/java");
    if (candidate && existsSync(candidate) && !dirs.includes(candidate)) dirs.push(candidate);
  }
  return dirs;
}

/**
 * Authorisation rules from one service's SecurityConfig, most-specific first.
 *
 * Parsed from `.requestMatchers("<pattern>").<rule>` pairs. Comments are stripped first: the
 * configs explain their own rules in prose that names both a matcher and a verb, and a parser
 * that reads the justification as a rule reports the opposite of the truth.
 */
function accessRules(serviceDir) {
  const rules = [];
  for (const f of javaFiles(serviceDir)) {
    if (!f.endsWith("SecurityConfig.java")) continue;
    const code = read(f).replace(/^\s*(\/\/|\*|\/\*).*$/gm, "");
    // krizaka-security's SecurityBaseline.apply(http, serviceRules) declares the shared rules
    // first and `anyRequest().authenticated()` last, around the service's own: the same order
    // Spring Security will evaluate them in (krizaka-java krizaka-service-kit, ADR-073).
    const baseline = code.includes("SecurityBaseline.apply(");
    if (baseline) {
      rules.push({ pattern: "/**", label: "public", httpMethod: "OPTIONS" });
      for (const pattern of ["/actuator/health", "/actuator/info", "/error"]) {
        rules.push({ pattern, label: "public", httpMethod: null });
      }
      rules.push({ pattern: "/internal/v1/**", label: "SERVICE", httpMethod: null });
    }
    for (const { httpMethod, patterns, verb, args } of requestMatcherRules(code)) {
      const label =
        verb === "permitAll"
          ? "public"
          : verb === "authenticated"
            ? "authenticated"
            // " + ", never " | ": a pipe in a cell silently adds a column and the table
            // renders one row shorter than every other, which reads as missing data.
            : args.join(" + ") || verb;
      for (const pattern of patterns) rules.push({ pattern, label, httpMethod });
    }
    const anyRequest = baseline
      ? [null, "authenticated"]
      : code.match(/anyRequest\(\)\s*\.\s*(permitAll|authenticated)\(\)/);
    if (anyRequest) {
      rules.push({
        pattern: "/**",
        label: anyRequest[1] === "permitAll" ? "public" : "authenticated",
        httpMethod: null,
      });
    }
  }
  // Declaration order, deliberately unsorted. Spring Security is first-match-wins, and sorting by
  // specificity gets the answer backwards: billing's `requestMatchers(OPTIONS, "/**").permitAll()`
  // then shadowed everything below it and the reference called 28 authenticated billing endpoints
  // public — the exact opposite of the posture ADR-035 establishes.
  return rules;
}

/**
 * The access label of the first rule that covers a path for a verb, or "—" when none does.
 *
 * @param rules the service's rules, in declaration order
 * @param path the endpoint path
 * @param method the HTTP verb, so a method-scoped rule (preflight) applies only to its own
 */
function accessFor(rules, path, method) {
  const concrete = path.replace(/\{[^}]*\}/g, "x");
  for (const { pattern, label, httpMethod } of rules) {
    if (httpMethod && httpMethod !== method) continue;
    const regex = new RegExp(
      "^" +
        pattern
          .replace(/[.+?^${}()|[\]\\]/g, "\\$&")
          .replace(/\*\*/g, ".*")
          .replace(/(?<!\.)\*/g, "[^/]*") +
        "$",
    );
    if (regex.test(concrete)) return label;
  }
  // Not a formatting placeholder — a finding. A service with no matching rule has no Spring
  // Security on the path at all, which is how `/internal/v1/knowledge/**` came to be reachable
  // with no credential and no `permitAll()` to notice (ADR-035, "Known gap").
  return "⚠ no rule";
}

/**
 * The roles a `@PreAuthorize` expression in `text` requires, as "ADMIN" (or "A + B"), or null.
 *
 * The URL rules of a SecurityConfig were the whole access column, so every billing write — gated
 * by `@PreAuthorize("hasRole('ADMIN')")` on its method — read as open to any authenticated user.
 * An administration surface is exactly the fact a reader looks for in this table.
 */
function preAuthorizeRoles(text) {
  const expr = text.match(/@PreAuthorize\(\s*"([^"]*)"/)?.[1];
  if (!expr) return null;
  const roles = [...expr.matchAll(/has(?:Any)?(?:Role|Authority)\(([^)]*)\)/g)]
    .flatMap((m) => [...m[1].matchAll(/'([^']+)'/g)].map((r) => r[1].replace(/^ROLE_/, "")));
  return roles.length ? [...new Set(roles)].join(" + ") : null;
}

/** The method-level `@PreAuthorize` of the handler a mapping annotation belongs to, or null. */
function preAuthorizeAt(src, index) {
  const before = src.slice(0, index);
  const start = Math.max(before.lastIndexOf(";"), before.lastIndexOf("}"), before.lastIndexOf("*/"));
  const rest = src.slice(index);
  const end = rest.search(/\)\s*(?:throws[^{;]*)?\{/);
  return preAuthorizeRoles(src.slice(start + 1, index + (end < 0 ? 0 : end)));
}

/** A URL rule narrowed by a method-level role: the role, unless the URL rule asks for more. */
function withPreAuthorize(access, roles) {
  if (!roles) return access;
  if (access === "public" || access === "authenticated" || access.split(" + ").some((a) => roles.split(" + ").includes(a))) return roles;
  return `${access}, then ${roles}`;
}

/** First sentence of the javadoc immediately above a mapping, as the endpoint's summary. */
function summaryAbove(src, index) {
  const before = src.slice(0, index);
  // (?!\*\/) stops the block from spanning two javadocs: without it the lazy match starts at the
  // file's first comment and swallows everything up to the method's, so every endpoint inherits
  // the class summary and the table says the same sentence a dozen times.
  const doc = before.match(/\/\*\*((?:(?!\*\/)[\s\S])*)\*\/\s*(?:@[\w.]+(?:\([^)]*\))?\s*)*$/);
  if (!doc) return "";
  const text = doc[1]
    .split("\n")
    .map((l) => l.replace(/^\s*\*\s?/, "").trim())
    .filter((l) => l && !l.startsWith("@"))
    .join(" ");
  const sentence = text.split(/(?<=\.)\s/)[0] ?? "";
  return sentence.replace(/\{@\w+\s+([^}]*)\}/g, "$1").replace(/[{}]/g, "").replace(/\\/g, "\\\\").replace(/\|/g, "\\|").trim();
}

/**
 * Every REST endpoint of every deployable service, with the authorisation rule that guards it.
 *
 * Scans all of `services/`, not the conversation service alone: four services'
 * surfaces — including the credit ledger's `/internal/v1` — were absent from this reference
 * entirely, which is the failure mode a generated document is supposed to prevent.
 *
 * The access column is the point. A path list says what exists; ADR-035 turned "who may call it"
 * into the load-bearing fact, and a fact that lives only in a `SecurityConfig` diff is a fact that
 * erodes.
 */
function extractApi() {
  // Every deployable service of the workspace: a `*-service` module or the edge, whichever
  // repository holds it (a domain repository nests its service beside its contract and client).
  const services = [...MODULES.entries()]
    .filter(([id]) => id.endsWith("-service") || id === "orazaka-edge")
    .map(([id, dir]) => ({ service: id, root: dir }))
    .sort((a, b) => a.service.localeCompare(b.service));
  const rows = [];
  for (const { service, root } of services) {
    const dir = join(root, "src/main/java");
    if (!existsSync(dir)) continue;
    const rules = accessRules(dir);
    // A service's REST surface is not only what sits in its own module: identity hosts seven
    // controllers from krizaka-java `krizaka-users/krizaka-users-core`, its owned Tier-3 (AGENTS.md §2). Scanning
    // the service directory alone dropped `/api/v1/auth/**` — the login endpoint — from the
    // reference entirely. The libs are read from the pom, so a future move needs no edit here.
    const sources = [dir, ...hostedTier3Dirs(root)];
    for (const f of sources.flatMap((d) => javaFiles(d))) {
      const src = read(f);
      if (!/@RestController/.test(src)) continue;
      const classDeclIndex = src.search(/\n(?:public\s+)?(?:final\s+)?class\s/);
      const classMapping = [...src.matchAll(/@RequestMapping\(\s*"([^"]*)"/g)].find(
        (m) => m.index < classDeclIndex,
      );
      const base = classMapping?.[1] ?? "";
      const controller = basename(f, ".java");
      const classPreAuthorize = preAuthorizeRoles(src.slice(0, classDeclIndex));
      // A *method*-level @RequestMapping is an endpoint answering every verb — the edge's
      // transparent proxy is written that way, and matching only the verb-specific annotations
      // left the whole facade undocumented. It is told from the class-level base by position:
      // the base is declared above the type.
      // The path is captured only when it is genuinely a quoted literal. A permissive `"?...?"`
      // spilled the rest of a multi-line annotation into the cell — `@PostMapping(value = "/code",
      // produces = ...)` documented a path called "/code/produces = MediaType...".
      for (const m of src.matchAll(
        /@(Get|Post|Put|Delete|Patch|Request)Mapping\b(?:\(\s*(?:value\s*=\s*)?(?:"([^"]*)")?)?/g,
      )) {
        if (m[1] === "Request" && m.index === classMapping?.index) continue;
        const method = m[1] === "Request" ? "ANY" : m[1].toUpperCase();
        const suffix = m[2] ? (m[2].startsWith("/") ? m[2] : "/" + m[2]) : "";
        const path = (base + suffix).replace(/\/$/, "") || base || "/";
        rows.push({
          method,
          path,
          controller,
          service: service.replace(/^sovkora-/, ""),
          access: withPreAuthorize(accessFor(rules, path, method), preAuthorizeAt(src, m.index) ?? classPreAuthorize),
          summary: summaryAbove(src, m.index),
        });
      }
    }
  }
  return rows.sort(
    (a, b) => a.service.localeCompare(b.service) || a.path.localeCompare(b.path) || a.method.localeCompare(b.method),
  );
}

/**
 * orazaka CLI commands — as the program registers them, subcommands included.
 *
 * A command file exports one top-level command (`export const x = new Command("pack")`, added to
 * the program in src/index.ts); every other `new Command("…")` or `.command("…")` in the file is
 * one of its subcommands, whether it is declared before the parent and attached with
 * `.addCommand(…)` or chained after it. The flat list this replaced named `orazaka install` twice
 * (the setup wizard and `pack install`) and never named `demo seed`, `db reseed` or `mcp list`:
 * a reference that lists `orazaka build` for `orazaka docs build` sends the reader to a command
 * that does not exist.
 *
 * Each marker's segment — up to the next marker — owns its `.description`, `.argument`, `.alias`
 * and `.option` calls.
 */
function extractCli() {
  const cliRoot = join(ROOT, "apps/cli");
  const dir = join(cliRoot, "src/commands");
  if (!existsSync(dir)) return [];
  const registered = new Set([...read(join(cliRoot, "src/index.ts")).matchAll(/\.addCommand\(\s*(\w+)\s*\)/g)].map((m) => m[1]));
  const str = String.raw`"((?:[^"\\]|\\.)*)"`;
  const commands = [];
  for (const n of readdirSync(dir).filter((x) => x.endsWith(".command.ts")).sort()) {
    const src = read(join(dir, n));
    const exported = new RegExp(String.raw`export const (\w+)\s*=\s*new Command\(\s*"([^"]+)"\s*\)`).exec(src);
    if (!exported || (registered.size && !registered.has(exported[1]))) continue;
    const top = exported[2];
    const topIndex = exported.index + exported[0].indexOf("new Command");
    const markers = [...src.matchAll(/(?:new Command|\.command)\(\s*"([^"]+)"\s*\)/g)];
    markers.forEach((m, i) => {
      const [word, ...inlineArgs] = m[1].trim().split(/\s+/);
      const isTop = m.index === topIndex;
      const segment = src.slice(m.index, markers[i + 1]?.index ?? src.length);
      const description = segment.match(new RegExp(String.raw`\.description\(\s*` + str))?.[1] ?? "";
      const args = [
        ...inlineArgs.map((name) => ({ name, desc: "" })),
        ...[...segment.matchAll(new RegExp(String.raw`\.argument\(\s*` + str + String.raw`(?:\s*,\s*` + str + ")?", "g"))].map((a) => ({
          name: a[1],
          desc: a[2] ?? "",
        })),
      ];
      const aliasList = segment.match(/\.aliases\(\s*\[([^\]]*)\]/)?.[1] ?? segment.match(/\.alias\(\s*("[^"]*")/)?.[1] ?? "";
      const aliases = [...aliasList.matchAll(/"([^"]+)"/g)].map((a) => a[1]);
      const options = [...segment.matchAll(new RegExp(String.raw`\.option\(\s*` + str + String.raw`\s*,\s*` + str + String.raw`(?:\s*,\s*` + str + ")?", "g"))].map(
        (o) => ({ flag: o[1], desc: o[2], default: o[3] ?? null }),
      );
      commands.push({ name: isTop ? top : `${top} ${word}`, top, isTop, order: i, description, args, aliases, options });
    });
  }
  // Each command after its parent's row, subcommands in declaration order; parents alphabetically.
  return commands.sort((a, b) => a.top.localeCompare(b.top) || Number(b.isTop) - Number(a.isTop) || a.order - b.order);
}

/**
 * What the quick start does, in order — the CLI's own words: the phases `install` and `start`
 * announce (`logStep("Phase 1 — Tool Verification")`), and the processes `dev` launches with their
 * ports (PORTS, utils/config.ts — the defaults, each overridable by its env key).
 *
 * krizaka.com draws it as the journey of /docs/orazaka/cli; the same steps are written below the
 * ```cli-journey block of CLI.md for whoever reads the markdown.
 */
function extractCliJourney(commands) {
  const cliRoot = join(ROOT, "apps/cli");
  const src = (name) => read(join(cliRoot, "src/commands", `${name}.command.ts`));
  const description = (name) => commands.find((c) => c.name === name)?.description ?? "";
  // A phase announced inside `if (mode === "full"…)` only runs with `--mode full`.
  const phases = (name) => {
    const code = src(name);
    const fullFrom = code.search(/if\s*\(\s*mode\s*===\s*"full"/);
    return [...code.matchAll(/logStep\(\s*"((?:Phase\s+(\w+)|Workspace)[^"]*)"\s*\)/g)].map((m) => {
      const [head, ...rest] = m[1].split(/\s+—\s+|:\s+/);
      const title = (m[2] ? rest.join(" — ") : head).replace(/\.\.\.$/, "").trim();
      const only = fullFrom >= 0 && m.index > fullFrom ? "--mode full" : "";
      return { id: m[2] ? m[2].toLowerCase() : head.toLowerCase(), title, detail: m[2] ? only : rest.join(" — ") };
    });
  };
  const ports = Object.fromEntries(
    [...read(join(cliRoot, "src/utils/config.ts")).matchAll(/^\s+(\w+):\s*intEnv\([^,]+,\s*(\d+)\)/gm)].map((m) => [m[1], Number(m[2])]),
  );
  const processes = [...src("dev").matchAll(/serviceKey:\s*"(\w+)",\s*label:\s*"([^"]+)"[\s\S]*?port:\s*PORTS\.(\w+)/g)].map((m) => ({
    key: m[1],
    label: m[2],
    port: ports[m[3]] ?? null,
  }));
  // …and the Python workers each worker.yaml declares (ADR-072 §4): discovered at launch, no HTTP port.
  if (/function discoverWorkers\(/.test(src("dev"))) processes.push({ key: "workers", label: "worker.yaml", port: null });
  return [
    { command: "install", description: description("install"), phases: phases("install") },
    { command: "start", description: description("start"), phases: phases("start") },
    { command: "dev", description: description("dev"), processes },
  ];
}

/**
 * The architecture rules that bind the build, and the suites that enforce each one.
 *
 * These are the most load-bearing documentation in the repository and had none: a rule nobody can
 * find is a rule the next author re-litigates. ADR-034's tier decision and ADR-035's security
 * posture both survive only because a suite fails when they are broken — that fact belongs in a
 * document, generated, rather than in a reviewer's memory.
 *
 * Each rule's summary is the first sentence of its javadoc, so the registry is written by
 * whoever writes the rule.
 */
function extractGovernance() {
  const libDir = join(ROOT, "build/sovkora-test-support/src/main/java");
  if (!existsSync(libDir)) return [];

  // One entry per RULE, keyed by holder + name — not one per method. A rule offered as a no-arg
  // convenience beside a JavaClasses variant is one rule with two signatures, and the first
  // version of this function keyed a Map by bare method name: the later overload overwrote the
  // earlier, every call site was attributed to it, and the earlier one rendered "Enforced by
  // **none**" forever. Seven rules read that way — [CFG-001] twice, [EXEC-001], [EXEC-002],
  // [ERR-130]'s support-package rule, [LOG-001] and [DOOR-001] — all of them enforced.
  //
  // That matters more than a cosmetic error, because AGENTS.md §10 makes this column a FINDING
  // when it reads `none`. The eleven previous instances of this project's defect produced a green
  // that meant nothing; this one produced a RED that meant nothing, in the index of every other
  // control. It fails socially rather than silently: the mechanism works and the readers stop
  // looking.
  const rules = new Map();
  for (const f of javaFiles(libDir)) {
    const src = read(f);
    const holder = basename(f, ".java");
    for (const m of src.matchAll(/public static (?:void|boolean) (assert\w+)\s*\(/g)) {
      const key = `${holder}.${m[1]}`;
      const summary = summaryAbove(src, m.index);
      const existing = rules.get(key);
      if (!existing) {
        rules.set(key, { name: m[1], holder, summary, signatures: 1, suites: [] });
      } else {
        existing.signatures += 1;
        // Keep the fullest javadoc of the overload set: the primary carries the reasoning and the
        // delegating convenience usually carries one line.
        if (summary.length > existing.summary.length) existing.summary = summary;
      }
    }
  }

  // Which suites enforce which rule — derived from what a suite actually INVOKES, qualified by the
  // class that holds it, rather than by matching a bare method name anywhere in the file. Two
  // holders may legitimately offer the same method name, and a name that appears in a comment is
  // not a call.
  for (const root of ["services", "engine", "contracts", "build"]) {
    const dir = join(ROOT, root);
    if (!existsSync(dir)) continue;
    for (const f of walk(dir, (x) => x.endsWith("Test.java") || x.endsWith("IT.java"))) {
      const src = read(f);
      const suite = basename(f, ".java");
      for (const rule of rules.values()) {
        // `Holder.assertX(` — how most suites call it.
        const qualified = new RegExp(`\\b${rule.holder}\\s*\\.\\s*${rule.name}\\s*\\(`);
        // `import static ...Holder.assertX;` or `...Holder.*;` then a bare `assertX(`.
        const staticallyImported =
          new RegExp(`import\\s+static\\s+[\\w.]*\\b${rule.holder}\\.(?:${rule.name}|\\*)\\s*;`).test(src) &&
          new RegExp(`(?:^|[^.\\w])${rule.name}\\s*\\(`, "m").test(src);
        if ((qualified.test(src) || staticallyImported) && !rule.suites.includes(suite)) {
          rule.suites.push(suite);
        }
      }
    }
  }
  // Follow delegation inside the rule library. ExecutorCoherenceRules' two rules are enforced in
  // nine suites, but every one of them calls GovernanceRules.assertEveryCapabilityHasAnExecutor,
  // which delegates. Attributing only direct calls reported both as orphans — the instrument still
  // wrong, in the other direction, and it would have read as a finding about the rules rather than
  // about the generator. A rule reached through a facade is enforced by whatever enforces the
  // facade, so suites propagate along those edges to a fixpoint.
  // Follow delegation inside the rule library, per METHOD BODY rather than per file. Scanning the
  // whole file made every rule in GovernanceRules a caller of everything the file mentions, so
  // ExecutorCoherenceRules' two rules inherited the union of nine suites instead of the ones that
  // actually reach them. Approximately derived is not derived.
  //
  // Declarations are not calls: `public static void assertX(` counted as a call to assertX, which
  // silently rehabilitated the only genuinely orphaned rule in the library — the same correction
  // [DOOR-001] needed when a comment counted as a dispatch, and [BILL-001]'s docstring before it.
  const bodyOf = (src, name) => {
    const decl = new RegExp(`public static (?:void|boolean) ${name}\\s*\\(`).exec(src);
    if (!decl) return "";
    let i = src.indexOf("{", decl.index);
    if (i < 0) return "";
    let depth = 0;
    for (let j = i; j < src.length; j += 1) {
      if (src[j] === "{") depth += 1;
      else if (src[j] === "}") {
        depth -= 1;
        if (depth === 0) return src.slice(i, j);
      }
    }
    return src.slice(i);
  };

  const sources = new Map();
  for (const f of javaFiles(libDir)) {
    sources.set(basename(f, ".java"), read(f));
  }
  let changed = true;
  while (changed) {
    changed = false;
    for (const caller of rules.values()) {
      const src = sources.get(caller.holder);
      if (!src || caller.suites.length === 0) continue;
      const body = bodyOf(src, caller.name);
      for (const callee of rules.values()) {
        if (callee === caller) continue;
        const reached =
          callee.holder === caller.holder
            ? new RegExp(`(?:^|[^.\\w])${callee.name}\\s*\\(`, "m").test(body)
            : new RegExp(`\\b${callee.holder}\\s*\\.\\s*${callee.name}\\s*\\(`).test(body);
        if (!reached) continue;
        for (const suite of caller.suites) {
          if (!callee.suites.includes(suite)) {
            callee.suites.push(suite);
            changed = true;
          }
        }
      }
    }
  }

  const all = [...rules.values()];
  for (const r of all) r.suites.sort();
  return all.sort((a, b) => a.holder.localeCompare(b.holder) || a.name.localeCompare(b.name));
}

/**
 * The SQL that builds the model registry, in the order a database sees it: the initdb seeds of every repository, then the
 * workspace's migrations (existing databases), then the deployment data. Nothing in it is typed twice.
 */
const readModelSql = () => {
  const dir = (d) => {
    const p = join(ROOT, d);
    return existsSync(p) ? readdirSync(p).filter((n) => n.endsWith(".sql")).sort().map((n) => read(join(p, n))) : [];
  };
  return [readInitDb(), ...dir("infra/migrations"), ...dir("infra/deploy")].join("\n");
};

/** The last sovereign smoke report, when one is committed (docs/evaluations/sovereign-smoke.json): a model is verified on its date. */
const readSmoke = () => {
  const raw = read(join(ROOT, "docs", "evaluations", "sovereign-smoke.json"));
  return raw ? JSON.parse(raw) : null;
};

/** The models Orazaka supports (registry metadata required — see scripts/lib/model-registry.mjs). */
const extractRegistry = () => {
  const worker = REPOSITORIES.find((r) => r.name === "worker-media");
  const licenceSources = [
    { name: "infra/localai/README.md", licences: readmeLicences(read(join(ROOT, "infra", "localai", "README.md"))) },
    ...(worker ? [{ name: `${worker.path}/models.yaml`, licences: manifestLicences(read(join(ROOT, worker.path, "models.yaml"))) }] : []),
  ];
  return buildModels(readModelSql(), { smoke: readSmoke(), licenceSources });
};

/** The full catalogue for MODELS.md: every non-theme row of orazaka_models with its description. */
function extractModels() {
  return catalogue(readModelSql())
    .filter((r) => r.category !== "theme")
    .map((r) => ({ name: r.model_name, label: r.model_label, category: r.category, description: r.description ?? "", status: r.status ?? "internal" }))
    .sort((a, b) => a.category.localeCompare(b.category) || a.name.localeCompare(b.name));
}

// ════════════════════════════════════════════════════════════════════════════
// MARKDOWN BUILDERS
// ════════════════════════════════════════════════════════════════════════════

/**
 * The module map, from modules.json: which folder of the one Sovkora repository holds what, what it depends on, which
 * modules of the architecture it hosts and which bootstrap SQL it owns. A module added to the catalogue documents itself.
 */
const LAYER_TITLES = {
  krizaka: "Krizaka building blocks — from Maven Central",
  foundation: "Foundation",
  ai: "The AI engine and its services",
  worker: "Native workers",
  app: "Applications",
  content: "Content",
};
const mdRepositories = (a) => {
  const lines = [
    frontmatter(
      "Modules",
      "The folders of the one Sovkora repository — what each holds, what it depends on, and how they build together.",
      "Architecture",
      3,
    ),
  ];
  lines.push(
    `Sovkora is **one repository**, [\`krizaka/sovkora\`](https://github.com/krizaka/sovkora) (ADR-029), made of ` +
      `**${a.repositories.length - 1} modules**; the Krizaka building blocks come from Maven Central.`,
    "",
    "```bash",
    "./mvnw verify          # every Java module, in one reactor",
    "pnpm install           # the clients: apps/web, mobile, cli, desktop",
    "```",
    "",
  );
  for (const [layer, title] of Object.entries(LAYER_TITLES)) {
    const repos = a.repositories.filter((r) => r.layer === layer);
    if (!repos.length) continue;
    lines.push(`## ${title}\n`, "| Module | Kind | What it holds | Depends on |", "|:---|:---|:---|:---|");
    for (const r of repos) {
      const hosted = a.modules.filter((m) => m.repository === r.name).map((m) => `\`${m.id}\``);
      const owns = REPOSITORIES.find((x) => x.name === r.name)?.initdb ?? [];
      const detail = [
        r.description,
        hosted.length ? `Modules: ${hosted.join(", ")}.` : "",
        owns.length ? `Owns ${owns.map((f) => `\`${f}\``).join(", ")}.` : "",
      ]
        .filter(Boolean)
        .join(" ");
      const deps = r.dependsOn.length ? r.dependsOn.map((d) => `\`${d}\``).join(", ") : "—";
      const where = r.name === "krizaka-java" ? `[\`krizaka-java\`](${r.url})` : `[\`${r.path}\`](${r.url})`;
      lines.push(`| ${where} | ${r.kind} | ${detail} | ${deps} |`);
    }
    lines.push("");
  }
  lines.push(
    "## Rules\n",
    "- A module depends only on modules listed **before** it in `modules.json` — the graph has no cycle.",
    "- A domain module holds its contract, its client and its service together; other modules depend on the contract or the client, never on the implementation.",
    "- A module owns its context's bootstrap SQL (`infra/initdb/NN-*.sql`).",
    "- The rules that read the whole tree run in the repository and are reported as skipped — never as passed — outside it.",
  );
  return lines.join("\n") + "\n";
};

const mdArchitecture = (a) => {
  const code = (x) => `\`${x}\``;
  const lines = [frontmatter("Architecture Reference", "Module map, dependencies, runtime flows, pipeline and messaging — extracted from the code.", "Architecture", 2)];
  lines.push(
    "Ports & Adapters: dependencies point toward the core. Each module has one **role** — one ring of the hexagon, outermost first — read from the code by the rule beside it.\n",
    "| Role | Rule | Modules |",
    "|:---|:---|:---|",
  );
  for (const r of a.roles) {
    const ms = a.modules.filter((m) => m.role === r.id).map((m) => code(m.id));
    lines.push(`| **${r.title}** | ${r.rule} | ${ms.join(", ") || "—"} |`);
  }
  lines.push("\n## Modules\n", "| Module | Role | Repository | Version | Inbound ports | Outbound ports |", "|:---|:---|:---|:---|:---|:---|");
  for (const m of a.modules) {
    lines.push(`| ${code(m.id)} | ${m.role} | ${code(m.repository)} | ${m.version ?? "—"} | ${m.ports.inbound.join(", ") || "—"} | ${m.ports.outbound.join(", ") || "—"} |`);
  }
  lines.push("\n## Dependencies\n", "Build dependencies between the modules above (test scope excluded): who packages whom.\n", "| Module | Depends on |", "|:---|:---|");
  for (const m of a.modules) {
    const to = a.dependencies.filter((d) => d.from === m.id).map((d) => code(d.to) + (d.scope ? ` (${d.scope})` : ""));
    if (to.length) lines.push(`| ${code(m.id)} | ${to.join(", ")} |`);
  }
  lines.push(
    "\n## Runtime flows\n",
    "Who calls whom while the platform runs: HTTP (UI defaults, the edge route table, the typed clients a service packages) and AMQP (a published routing key that reaches a queue).\n",
    "| From | To | Kind | Via |",
    "|:---|:---|:---|:---|",
  );
  for (const f of a.flows) lines.push(`| ${code(f.from)} | ${code(f.to)} | ${f.kind} | ${code(f.via)}${f.routingKey ? ` (${code(f.routingKey)})` : ""} |`);
  lines.push("\n## Interceptor pipeline\n", "**Phase 1 — core chain** (fixed in code, non-bypassable, ADR-051)\n", "| # | Interceptor | Concern |", "|:--|:---|:---|");
  for (const c of a.coreChain) lines.push(`| ${c.rank} | ${code(c.interceptor)} | ${c.concern ?? "—"} |`);
  lines.push("\n**Phase 2 — configured chain** (order and switch in `pipeline_interceptor_config`)\n", "| Order | Interceptor | Enabled | Implemented |", "|:--|:---|:--|:--|");
  for (const p of a.pipeline.filter((x) => x.phase === "dynamic")) lines.push(`| ${p.order} | ${code(p.interceptor)} | ${p.enabled ? "✅" : "—"} | ${p.implemented ? "✅" : "⚠ no class"} |`);
  const r = a.messaging.retry;
  lines.push(
    "\n## Messaging topology (AGENTS.md §6)\n",
    "**Exchanges**\n",
    ...a.messaging.exchanges.map((e) => `- ${code(e.name)} (${e.type})`),
    "",
    `**Retry, then dead letter** — a listener on the kit's container (${code(r.module)}, ${code(r.property)}) runs up to ${r.maxAttempts} times, ` +
      `waiting ${r.initialMs} ms ×${r.multiplier} up to ${r.maxMs / 1000} s, then the message goes to ${code("<queue>.dlq")}.`,
  );
  lines.push("\n**Queues**\n", "| Queue | Exchange | Binding | DLQ | Declared by | Consumed by |", "|:---|:---|:---|:---|:---|:---|");
  for (const q of a.messaging.queues) {
    const by = a.messaging.consumers.filter((c) => c.queue === q.name).map((c) => code(c.module) + (c.retry === "kit" ? "" : " (own retry)"));
    lines.push(`| ${code(q.name)} | ${code(q.exchange)} | ${q.binding ? code(q.binding) : "—"} | ${q.dlq ? code(q.dlq) : "—"} | ${code(q.declaredBy)} | ${by.join(", ") || "—"} |`);
  }
  lines.push("\n**Producers**\n", "| Module | Exchange | Routing key |", "|:---|:---|:---|");
  for (const p of a.messaging.producers) lines.push(`| ${code(p.module)} | ${code(p.exchange)} | ${code(p.routingKey)} |`);
  lines.push("\n**Capability routes** (`orazaka_capabilities`: where a job of each capability is published)\n", "| Capability | Handler | Routing key |", "|:---|:---|:---|");
  for (const c of a.messaging.capabilityRoutes) lines.push(`| ${code(c.feature)} | ${c.handler ? code(c.handler) : "—"} | ${code(c.routingKey)} |`);
  return lines.join("\n") + "\n";
};

const mdInterfaces = (a) => {
  const lines = [frontmatter("Interface Contracts (generated)", "Inbound and outbound ports per module, extracted from the code.", "Architecture", 3)];
  for (const m of a.modules.filter((x) => x.ports.inbound.length || x.ports.outbound.length)) {
    lines.push(`## \`${m.id}\`\n`);
    if (m.ports.inbound.length) lines.push("**Inbound ports**\n", ...m.ports.inbound.map((p) => `- \`${p}\``), "");
    if (m.ports.outbound.length) lines.push("**Outbound ports**\n", ...m.ports.outbound.map((p) => `- \`${p}\``), "");
  }
  return lines.join("\n") + "\n";
};

const mdUseCases = (rows) => {
  const lines = [frontmatter("Use-Case Catalog", "App Factory use-cases discovered in orazaka-business.", "Business", 4)];
  lines.push("| Use-case | Capability | Domain | Summary |", "|:---|:---|:---|:---|");
  for (const u of rows) lines.push(`| \`${u.name}\` | ${u.capability} | ${u.domain} | ${u.summary || "—"} |`);
  return lines.join("\n") + "\n";
};

const mdInterceptors = (rows) => {
  const lines = [frontmatter("Interceptor Registry", "Cross-cutting pipeline interceptors and their DB-driven order.", "Core", 5)];
  lines.push("| Order | Interceptor | Concern | Enabled |", "|:--|:---|:---|:--|");
  for (const i of rows) {
    // 🔒 is not "enabled": a core interceptor has no off switch to read (ADR-051 §3).
    const enabled = i.enabled === "locked" ? "🔒 non-bypassable" : i.enabled === null ? "—" : i.enabled ? "✅" : "—";
    lines.push(`| ${i.order ?? "—"} | \`${i.name}\` | ${i.concern} | ${enabled} |`);
  }
  return lines.join("\n") + "\n";
};

const mdApi = (rows) => {
  const lines = [
    frontmatter(
      "API Reference",
      "Every REST endpoint of every service, with the authorisation rule that guards it, extracted from the controllers and their SecurityConfig.",
      "API",
      6,
    ),
  ];
  lines.push(
    "",
    "**Access** is read from each service's `SecurityConfig`, most-specific matcher first — the same",
    "order Spring Security applies. `SERVICE` is the machine-to-machine authority required on",
    "`/internal/v1/**` (ADR-035); `public` means no credential at all, and **⚠ no rule** means",
    "no matcher covers the path — the service has no security on it whatsoever.",
    "",
  );
  let currentService = null;
  for (const r of rows) {
    if (r.service !== currentService) {
      currentService = r.service;
      lines.push("", `## ${currentService}`, "", "| Method | Path | Access | Controller | Summary |", "|:---|:---|:---|:---|:---|");
    }
    lines.push(`| ${r.method} | \`${r.path}\` | ${r.access} | ${r.controller} | ${r.summary} |`);
  }
  return lines.join("\n") + "\n";
};

/**
 * The CLI as data, for the site (architecture.json → `cli`): its packages, its commands with their
 * arguments and options, and the journey of the quick start. krizaka.com renders the command
 * explorer and the journey of /docs/orazaka/cli from it; CLI.md below says the same in markdown.
 */
function cliModel(rows) {
  const cliRoot = join(ROOT, "apps/cli");
  const pkg = JSON.parse(read(join(cliRoot, "package.json")) || "{}");
  const alias = JSON.parse(read(join(cliRoot, "alias/package.json")) || "{}");
  return {
    package: pkg.name ?? null,
    alias: alias.name ?? null,
    version: pkg.version ?? null,
    bin: Object.keys(pkg.bin ?? { orazaka: "" })[0],
    node: pkg.engines?.node ?? null,
    commands: rows.map(({ order, ...c }) => c),
    journey: extractCliJourney(rows),
  };
}

const mdCli = (rows) => {
  const lines = [frontmatter("CLI Reference", "orazaka CLI commands, subcommands, arguments and options, extracted from the command definitions.", "DevEx", 7)];
  const model = cliModel(rows);
  const pkg = { name: model.package, engines: { node: model.node } };
  const alias = { name: model.alias };
  const bin = model.bin;
  const runner = alias.name ? `npx ${alias.name}` : `npx ${pkg.name}`;
  const cell = (s) => mdCell(s || "—");
  const usage = (c) => [`${bin} ${c.name}`, ...c.args.map((a) => a.name)].join(" ");

  // Quick start and dev loop (authored here, single-sourced in the generator — see AGENTS.md §1).
  lines.push(
    "The `orazaka` CLI is the single orchestrator of the platform: it clones and configures the",
    "workspace, starts the infrastructure, runs the whole stack, tests it, builds these docs and",
    "installs packs. No shell script to remember — one command per intent.",
    "",
    "## Quick start",
    "",
    `Requirements: git, JDK 21, Node.js ${pkg.engines?.node ?? ">=22"}, Docker (Python 3.11+ for the media worker).`,
    "",
    "```bash",
    `${runner} install   # no workspace here? clones the platform into ./orazaka, then configures it`,
    "cd sovkora && ./mvnw install && pnpm install",
    `${runner} start     # Docker infrastructure (PostgreSQL + pgvector, Redis, RabbitMQ)`,
    `${runner} dev       # the whole stack: services, web, admin, mobile`,
    "```",
    "",
    // krizaka.com plays a recording of these commands here (a terminal replay of the published CLI).
    "```cli-replay",
    `${runner} install --yes`,
    `${runner} start`,
    `${runner} dev`,
    "```",
    "",
    ...(alias.name
      ? [
          `\`${alias.name}\` on npm is the short name of the canonical package \`${pkg.name}\` — both run the same CLI:`,
          "",
          "```bash",
          `npx ${pkg.name} install      # the same as ${runner} install`,
          `npm install -g ${pkg.name}   # then: ${bin} <command>`,
          "```",
          "",
        ]
      : []),
    "`install --check-only` only reports what is missing. `start`, `dev`, `test` and `docs` run inside",
    "a workspace and say how to get one when there is none.",
    "",
    "## What happens",
    "",
    "What each command of the quick start does, in order — the phases it announces, the processes it launches:",
    "",
    "```cli-journey",
    ...model.journey.flatMap((step) => [
      `${bin} ${step.command} — ${step.description}`,
      ...(step.phases ?? []).map((p) => `  ${/^\d/.test(p.id) ? `${p.id}.` : "·"} ${p.title}${p.detail ? ` (${p.detail})` : ""}`),
      ...(step.processes ?? []).map((p) => `  ${p.label}${p.port ? ` :${p.port}` : " (Python workers, no HTTP port)"}`),
    ]),
    "```",
    "",
    "## Local dev workflow",
    "",
    "The dev loop has two layers — start them in order:",
    "",
    "1. **Infrastructure** — `orazaka start --mode dev` brings up the Docker middleware",
    "   (PostgreSQL+pgvector, Redis, RabbitMQ) and the native AI engines (Ollama, LocalAI,",
    "   video worker). Inference runs natively on macOS Metal — never in Docker.",
    "2. **Applications** — `orazaka dev` spawns the services (edge, conversation router, identity,",
    "   automation, knowledge, job, billing, studio, notifications), the Web client, the Web admin",
    "   and the Expo mobile server in parallel. Prefer to debug the conversation router in your IDE?",
    "   Run `ConversationServiceApplication` there and start the rest with `orazaka dev --skip-router`.",
    "",
    "`orazaka start --mode full` additionally runs the router and the UI for a",
    "hands-off boot. Tear everything down with `orazaka stop`. On a local stack,",
    "`orazaka demo seed` creates the demo persona with its plan, packs and Studios.",
    "",
    "> ℹ️ `start` manages *infrastructure*; `dev` manages *application processes*. They are",
    "> complementary, not alternatives.",
    "",
    "## Commands",
    "",
    // krizaka.com renders the command explorer here (architecture.json → cli.commands); the table is its text version.
    "```cli-commands",
    `${model.commands.filter((c) => c.isTop).length} commands, ${model.commands.filter((c) => !c.isTop).length} subcommands — every one in the table below.`,
    "```",
    "",
    "| Command | Description |",
    "|:---|:---|",
  );
  for (const c of rows) {
    const aka = c.aliases.length ? ` (alias ${c.aliases.map((a) => `\`${a}\``).join(", ")})` : "";
    lines.push(`| \`${cell(usage(c))}\`${aka} | ${cell(c.description)} |`);
  }

  const detailed = rows.filter((c) => c.options.length > 0 || c.args.some((a) => a.desc));
  if (detailed.length) {
    lines.push("", "## Arguments and options", "");
    for (const c of detailed) {
      lines.push(`### \`${usage(c)}\``, "");
      if (c.description) lines.push(`${c.description}`, "");
      lines.push("| Argument / option | Description |", "|:---|:---|");
      for (const a of c.args) lines.push(`| \`${cell(a.name)}\` | ${cell(a.desc)} |`);
      for (const o of c.options) {
        const def = o.default !== null ? ` (default: \`${cell(o.default)}\`)` : "";
        lines.push(`| \`${cell(o.flag)}\` | ${cell(o.desc)}${def} |`);
      }
      lines.push("");
    }
  }
  return lines.join("\n").trimEnd() + "\n";
};

const mdGovernance = (rows) => {
  const lines = [
    frontmatter(
      "Governance Rules",
      "The architecture rules enforced at build time, and the suites that enforce each one, extracted from orazaka-test-support.",
      "Architecture",
      8,
    ),
  ];
  lines.push(
    "",
    "A rule with **no suite** is a rule that enforces nothing — it is written, it compiles, and no",
    "build fails when it is violated. Treat an empty cell as a defect, not as a gap.",
    "",
  );
  let holder = null;
  for (const r of rows) {
    if (r.holder !== holder) {
      holder = r.holder;
      lines.push("", `## ${holder}`, "", "| Rule | Enforced by | Summary |", "|:---|:---|:---|");
    }
    const suites = r.suites.length ? r.suites.map((x) => `\`${x}\``).join("<br/>") : "**none**";
    lines.push(`| \`${r.name}\` | ${suites} | ${r.summary} |`);
  }
  return lines.join("\n") + "\n";
};

const CAP_LABEL = { chat: "Chat", reasoning: "Reasoning", vision: "Vision", code: "Code", embeddings: "Embeddings", image: "Image", video: "Video", "speech-to-text": "Speech to text", "text-to-speech": "Text to speech", music: "Music", "video-to-audio": "Video to audio (foley)", "video-upscale": "Video upscale" };
const mdModels = (models, rows) => {
  const lines = [frontmatter("Supported Models", "Every model Orazaka supports: where it runs, its licence, its context window — read from the registry the routing reads.", "Models", 8)];
  const local = models.filter((m) => m.runsOn === "local");
  const provider = models.filter((m) => m.runsOn === "provider");
  lines.push(
    "## Local first",
    "",
    `Orazaka supports **${models.length} models**: **${local.length} run on your own hardware** (Ollama, LocalAI, stable-diffusion.cpp on Metal or CUDA, the media worker)` +
      ` and **${provider.length} are served by a provider** — optional: a provider-hosted model needs a provider row and its API key, which only an operator adds; a fresh install has none.`,
    "",
    "Every figure below is read from `orazaka_models` (the table the routing reads): the vendor, licence and context window are those of each model's own card; `last verified` is the date of the last passing sovereign smoke that named the model, empty until one is recorded.",
    "",
    "## Unfiltered models, your rules",
    "",
    "The catalogue carries open-weight chat models without a vendor's refusal layer — fine-tuned without refusals or",
    "abliterated (the refusal direction removed from the weights). They answer legitimate professional work plainly: a",
    "security team reading a malicious command, a nurse asking for a dose limit, a reporter on a fraud scheme, a novelist",
    "writing a dark scene. None of them is a default; the operator chooses to serve one.",
    "",
    "They are not the policy. The policy is the operator's: a pack declares the domain it refuses (`scopeGuard`) and the",
    "crisis text it answers (`safety`); the engine enforces both first in the pipeline, before any model runs and even when",
    "the AI kill-switch is pulled, and every turn is accounted for. \"Uncensored\" means not over-refusing legitimate work —",
    "it never covers content sexualising minors, explicit sexual content, real persons, weapons or bio-chemical procedures,",
    "working malware, or anything that enables serious harm. Unfiltered is not infallible either: serve the largest model",
    "the machine holds for clinical, legal or security work, and keep a professional in the loop.",
  );
  const table = (title, list) => {
    lines.push("", `## ${title}`, "", "| Model | Vendor · family | Does | Runs | Licence | Context | Status | Last verified |", "|:---|:---|:---|:---|:---|---:|:---|:---|");
    for (const m of list) {
      const lic = m.licence.url ? `[${m.licence.name}](${m.licence.url})` : m.licence.name;
      lines.push(`| \`${m.id}\` — ${m.name} | ${m.vendor} · ${m.family} | ${m.capabilities.map((c) => CAP_LABEL[c]).join(", ")} | ${m.runtime} | ${lic}${m.openWeights ? "" : " (proprietary)"}${m.commercialUse === "no" ? " — **non-commercial**" : m.commercialUse === "conditions" ? " — commercial use under conditions" : ""} | ${m.contextWindow ? m.contextWindow.toLocaleString("en-US") : "—"} | ${m.status} | ${m.lastVerified ?? "—"} |`);
    }
  };
  lines.push("", "How fast and at what price they really run — time, cost and every parameter of the runs Orazaka measured — is on the [Measured runs](MEASURED_RUNS.md) page.");
  table("On your hardware", local);
  table("Optional: served by a provider", provider);
  const hidden = rows.filter((r) => r.status === "internal");
  lines.push("", "## In the catalogue, not published", "", `${hidden.length} rows are aliases, runtime variants or test fixtures of the models above (\`status = 'internal'\`): ${hidden.map((r) => `\`${r.name}\``).join(", ")}.`);
  return lines.join("\n") + "\n";
};

// ════════════════════════════════════════════════════════════════════════════
// ENGINE GUIDES — business layer, tools, jobs & automation, packs, administration
// ════════════════════════════════════════════════════════════════════════════
//
// The five subjects a developer asks about first and the docs answered last: what lives in the
// business layer, what "tools" are, what runs on a schedule, how a pack is written and what an
// administrator controls. Each guide is built from the code that implements it — the prose here
// only joins tables the code fills, so a renamed class or a new endpoint updates the page.

/** The javadoc block right above `index` (its text, tags dropped), or "". */
function javadocAbove(src, index) {
  const doc = src.slice(0, index).match(/\/\*\*((?:(?!\*\/)[\s\S])*)\*\/\s*(?:@[\w.]+(?:\([^)]*\))?\s*)*$/)?.[1];
  return doc ? docText(doc) : "";
}

/** Javadoc text as Markdown-safe prose: inline tags as code, HTML and braces dropped. */
function docText(doc) {
  return stripTags(doc
    .split("\n")
    .map((l) => l.replace(/^\s*\*\s?/, ""))
    .filter((l) => !l.trim().startsWith("@"))
    .join(" "))
    .replace(/\{@\w+\s+([^}]*)\}/g, "`$1`")
    .replace(/[{}]/g, "")
    .replace(/\\/g, "\\\\")
    .replace(/\|/g, "\\|")
    .replace(/\s+/g, " ")
    .trim();
}

const firstSentence = (text) => text.match(/^(.+?[.!?])(\s|$)/)?.[1] ?? text;

/** A type's kind and the first sentence of its javadoc. */
function typeInfo(src, name) {
  const decl = new RegExp(String.raw`\b(class|interface|record|enum|@interface)\s+${name}\b`).exec(src);
  if (!decl) return null;
  const lineStart = src.lastIndexOf("\n", decl.index) + 1;
  // The javadoc sits above the annotations of the type, which sit above its modifiers.
  const head = src.slice(0, lineStart);
  const doc = head.match(/\/\*\*((?:(?!\*\/)[\s\S])*)\*\/\s*(?:@[\w.]+(?:\([^)]*\))?\s*)*$/)?.[1];
  return { kind: decl[1] === "@interface" ? "annotation" : decl[1], summary: doc ? firstSentence(docText(doc)) : "" };
}

/** `@param name text` tags of the javadoc above a type, in declaration order. */
function paramTags(src, name) {
  const decl = new RegExp(String.raw`\b(?:class|interface|record|enum)\s+${name}\b`).exec(src);
  const doc = decl ? src.slice(0, decl.index).match(/\/\*\*((?:(?!\*\/)[\s\S])*)\*\/[^/]*$/)?.[1] : null;
  if (!doc) return [];
  const lines = doc.split("\n").map((l) => l.replace(/^\s*\*\s?/, ""));
  const tags = [];
  for (const line of lines) {
    const m = line.match(/^@param\s+(<?\w+>?)\s*(.*)$/);
    if (m) tags.push({ name: m[1], text: m[2] });
    else if (tags.length && line.trim() && !line.trim().startsWith("@")) tags.at(-1).text += " " + line.trim();
    else if (line.trim().startsWith("@")) tags.push({ name: null, text: "" });
  }
  return tags.filter((t) => t.name && !t.name.startsWith("<")).map((t) => ({ name: t.name, text: docText(t.text) }));
}

/** The components of a record, as `Type name` pairs. */
function recordComponents(src, name) {
  const m = new RegExp(String.raw`record\s+${name}\s*\(([\s\S]*?)\)\s*(?:implements[^{]*)?\{`).exec(src);
  if (!m) return [];
  const parts = [];
  let depth = 0;
  let current = "";
  for (const ch of m[1].replace(/@\w+(\([^)]*\))?\s*/g, "")) {
    if (ch === "<") depth++;
    if (ch === ">") depth--;
    if (ch === "," && depth === 0) {
      parts.push(current);
      current = "";
    } else current += ch;
  }
  if (current.trim()) parts.push(current);
  return parts.map((p) => p.trim().replace(/\s+/g, " ")).map((p) => ({ type: p.replace(/\s+\w+$/, ""), name: p.match(/(\w+)$/)?.[1] }));
}

/** The constants of an enum, comments stripped. */
function enumValues(src, name) {
  // Comments first: a constant's javadoc may hold the ";" that would end the scan early.
  const code = src.replace(/\/\*[\s\S]*?\*\//g, "").replace(/\/\/.*$/gm, "");
  const m = new RegExp(String.raw`enum\s+${name}\s*\{([\s\S]*?)(?:;|\})`).exec(code);
  if (!m) return [];
  return m[1]
    .split(",")
    .map((v) => v.trim().match(/^([A-Z][A-Z0-9_]*)/)?.[1])
    .filter(Boolean);
}

/** Top-level comma split of an argument list (parentheses, brackets and generics respected). */
function splitArgs(text) {
  const out = [];
  let depth = 0;
  let current = "";
  for (const ch of text) {
    if ("([<{".includes(ch)) depth++;
    if (")]>}".includes(ch)) depth--;
    if (ch === "," && depth === 0) {
      out.push(current.trim());
      current = "";
    } else current += ch;
  }
  if (current.trim()) out.push(current.trim());
  return out;
}

/** The repository a file belongs to, by workspace path. */
const repoOfFile = (file) => repositoryOf(relative(ROOT, file).split(/[/\\]/).join("/"));

/** The CREATE TABLE columns of a table in the bootstrap SQL (name and type). */
function tableColumns(sql, table) {
  const m = new RegExp(String.raw`CREATE TABLE(?: IF NOT EXISTS)?\s+${table}\s*\(([\s\S]*?)\n\);`).exec(sql);
  if (!m) return [];
  return m[1]
    .split("\n")
    .map((l) => l.trim().replace(/,$/, ""))
    .filter((l) => l && !/^(PRIMARY KEY|CONSTRAINT|UNIQUE|FOREIGN KEY|CHECK|--)/i.test(l))
    .map((l) => ({ name: l.split(/\s+/)[0], type: l.split(/\s+/).slice(1).join(" ") }));
}

// ── Business layer ──────────────────────────────────────────────────────────

function extractBusiness() {
  const moduleDir = MODULES.get("sovkora-engine-business");
  if (!moduleDir) return null;
  const javaRoot = join(moduleDir, "src/main/java");
  const files = javaFiles(javaRoot);
  const config = files.find((f) => f.endsWith("BusinessAutoConfiguration.java"));
  const base = config ? join(config, "..") : javaRoot;
  const types = files
    .map((f) => {
      const name = basename(f, ".java");
      const info = typeInfo(read(f), name);
      return info && { name, file: f, pkg: relative(base, join(f, "..")).split(/[/\\]/).join("/") || ".", ...info };
    })
    .filter(Boolean)
    .sort((a, b) => a.pkg.localeCompare(b.pkg) || a.name.localeCompare(b.name));
  const byName = new Map(types.map((t) => [t.name, t]));
  const src = (name) => (byName.has(name) ? read(byName.get(name).file) : "");

  const records = ["Intention", "IntentionContext", "UseCaseDescriptor", "UseCaseContext", "RbacPolicy"]
    .filter((n) => byName.get(n)?.kind === "record")
    .map((n) => {
      const docs = new Map(paramTags(src(n), n).map((p) => [p.name, p.text]));
      return { name: n, summary: byName.get(n).summary, components: recordComponents(src(n), n).map((c) => ({ ...c, doc: docs.get(c.name) ?? "" })) };
    });
  const enums = types.filter((t) => t.kind === "enum").map((t) => ({ name: t.name, summary: t.summary, values: enumValues(read(t.file), t.name) }));

  const useCases = types
    .filter((t) => /implements\s+UseCase\b/.test(read(t.file)))
    .map((t) => {
      const s = read(t.file);
      const call = s.match(/new UseCaseDescriptor\(([\s\S]*?)\);/)?.[1];
      const args = call ? splitArgs(call) : [];
      const setOf = (a) => [...(a ?? "").matchAll(/"([^"]+)"/g)].map((m) => m[1]);
      const rbac = args[5] ?? "";
      return {
        name: t.name,
        domain: t.pkg.replace(/^usecases\/?/, ""),
        summary: t.summary,
        id: args[0]?.match(/"([^"]+)"/)?.[1] ?? "—",
        capability: args[1]?.match(/Capability\.(\w+)/)?.[1] ?? "—",
        personas: setOf(args[2]),
        planning: args[3]?.match(/PlanningMode\.(\w+)/)?.[1] ?? "—",
        tools: setOf(args[4]),
        rbac: /PERMIT_ALL/.test(rbac) ? "anyone authenticated" : setOf(rbac).join(" + ") || "—",
      };
    });

  const errors = [...src("UseCaseDispatcherImpl").matchAll(/"(ERR-\d+): ([^"]+)"/g)].map((m) => ({ code: m[1], message: m[2].trim() }));

  // Who calls into the layer from outside it: the transport adapters a request arrives through.
  const outside = walk(join(ROOT, "services"), (p) => p.endsWith(".java") && p.includes(`${"/"}src${"/"}main${"/"}`));
  const callers = outside
    .filter((f) => /\bUseCaseDispatcher\b|\bimplements\s+WorkflowOrchestrator\b/.test(read(f)) && /@(RestController|Component|Service)\b/.test(read(f)))
    .map((f) => {
      const name = basename(f, ".java");
      return { name, repo: repoOfFile(f), summary: typeInfo(read(f), name)?.summary ?? "" };
    })
    .sort((a, b) => a.name.localeCompare(b.name));

  const reference = useCases.find((u) => u.capability === "CHAT") ?? useCases[0];
  const referenceSource = reference ? read(byName.get(reference.name).file).replace(/^import .*\n/gm, "").replace(/\n{3,}/g, "\n\n").trim() : "";
  return { types, records, enums, useCases, errors, callers, reference, referenceSource, repo: repoOfFile(moduleDir) };
}

const mdBusiness = (b) => {
  const lines = [
    frontmatter(
      "Business Layer",
      "The App Factory of orazaka-business: intentions, use-cases, the dispatcher and the ports between business and engine, extracted from the code.",
      "Business",
      3,
    ),
  ];
  if (!b) return lines.join("\n") + "\n";
  const code = (s) => `\`${s}\``;
  lines.push(
    `\`orazaka-business\` (repository \`${b.repo}\`) is the **App Factory** of the engine: the layer that decides`,
    "*what* a request is for, never *how* a model answers it. Every request a client sends becomes one immutable",
    "`Intention`; the `UseCaseDispatcher` resolves it to the `UseCase` that serves its capability, checks the",
    "use-case's RBAC policy against the actor's authorities and runs it. A use-case coordinates the core's inbound",
    "ports (`AiClient`, the Studio run API…) and holds no model logic — that stays in `orazaka-core` and the",
    "interceptor pipeline.",
    "",
    "```sequence",
    "participant client: Client · CLI · agent",
    "participant conversation: @orazaka-conversation-service",
    "participant business: @orazaka-business",
    "participant core: @orazaka-core",
    "client -> conversation: POST /api/v1/intent (IntentController)",
    "conversation -> business: an immutable Intention",
    "business -> business: UseCaseDispatcher resolves the UseCase by capability (UseCaseRegistry)",
    "business -> business: the use-case's RBAC policy, checked against the actor's authorities",
    "business -> core: the use-case runs through an inbound port (AiClient, the Studio run API…)",
    "```",
    "",
    "> **Use-cases are not packs.** A use-case is a technical entry point of the engine, written in Java. The",
    "> business offers customers install — Studios, grouped in packs — are data: see [Packs & Studios](PACKS.md).",
    "",
    "## Where things live",
    "",
    "Ports & adapters, the same layout in every Orazaka module: `api` is the contract other modules may import,",
    "`application` implements it, `domain` holds the model and the ports, and the use-cases are plain classes",
    "discovered by Spring — adding one changes neither the core nor the router.",
    "",
    "| Package | Type | Kind | Role |",
    "|:---|:---|:---|:---|",
  );
  for (const t of b.types) lines.push(`| ${code(t.pkg)} | ${code(t.name)} | ${t.kind} | ${t.summary || "—"} |`);

  lines.push("", "## The contract", "");
  for (const r of b.records) {
    lines.push(`### ${code(r.name)}`, "", r.summary, "", "| Field | Type | Meaning |", "|:---|:---|:---|");
    for (const c of r.components) lines.push(`| ${code(c.name)} | ${code(c.type)} | ${c.doc || "—"} |`);
    lines.push("");
  }
  lines.push("| Enum | Values | Meaning |", "|:---|:---|:---|");
  for (const e of b.enums) lines.push(`| ${code(e.name)} | ${e.values.map(code).join(" · ")} | ${e.summary || "—"} |`);

  lines.push(
    "",
    "## Use-cases",
    "",
    "The registry matches an intention to the **first** use-case whose descriptor serves its capability.",
    "",
    "| Use-case | Id | Capability | Planning | Personas | Required tools | RBAC | Summary |",
    "|:---|:---|:---|:---|:---|:---|:---|:---|",
  );
  for (const u of b.useCases) {
    lines.push(
      `| ${code(u.name)} | ${code(u.id)} | ${u.capability} | ${u.planning} | ${u.personas.map(code).join(", ") || "—"} | ${u.tools.map(code).join(", ") || "—"} | ${u.rbac} | ${u.summary || "—"} |`,
    );
  }
  if (b.errors.length) {
    lines.push("", "Dispatch refuses an intention with:", "", "| Code | When |", "|:---|:---|");
    for (const e of b.errors) lines.push(`| ${code(e.code)} | ${e.message} |`);
  }
  if (b.callers.length) {
    lines.push("", "## Entry points", "", "The adapters that hand work to the business layer:", "", "| Adapter | Repository | Role |", "|:---|:---|:---|");
    for (const c of b.callers) lines.push(`| ${code(c.name)} | ${code(c.repo)} | ${c.summary || "—"} |`);
  }
  lines.push(
    "",
    "Call it over HTTP through the edge with a session token (see the [API reference](API_REFERENCE.md)):",
    "",
    "```bash",
    "curl -X POST http://localhost:8088/api/v1/intent \\",
    '  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \\',
    `  -d '{"capability":"CHAT","prompt":"Summarise our refund policy in three bullet points."}'`,
    "```",
  );
  if (b.reference) {
    lines.push(
      "",
      "## Adding a use-case",
      "",
      "A new product capability is one class: implement `UseCase<I, R>`, declare its `UseCaseDescriptor`",
      "(id, capability, personas, planning mode, required tools, RBAC) and register it as a bean — the registry",
      "discovers it. Personas are Markdown prompts read from `classpath:prompts/<name>.md` by",
      "`MarkdownPromptResolver`. The reference implementation, verbatim:",
      "",
      "```java",
      b.referenceSource,
      "```",
    );
  }
  return lines.join("\n") + "\n";
};

// ── Tools ───────────────────────────────────────────────────────────────────

function extractTools() {
  const moduleDir = MODULES.get("sovkora-engine-tools");
  if (!moduleDir) return null;
  const files = javaFiles(join(moduleDir, "src/main/java"));
  const byName = new Map(files.map((f) => [basename(f, ".java"), f]));
  const src = (n) => (byName.has(n) ? read(byName.get(n)) : "");

  const registry = src("DefaultToolRegistry");
  const tools = [...registry.matchAll(/registerTool\(\s*(?:"([^"]+)"|(\w+))\s*,\s*"((?:[^"\\]|\\.)*)"\s*,\s*(\w+)\.class/g)].map((m) => {
    const name = m[1] ?? registry.match(new RegExp(String.raw`${m[2]}\s*=\s*"([^"]+)"`))?.[1] ?? m[2];
    const input = m[4];
    return { name, description: m[3], input, fields: recordComponents(src(input), input) };
  });

  // How the pipeline picks tools for a turn: ToolInterceptor in orazaka-interceptors.
  const interceptorFile = walk(join(ROOT, "engine"), (p) => basename(p) === "ToolInterceptor.java" && p.includes("/src/main/"))[0];
  const interceptor = interceptorFile ? read(interceptorFile) : "";
  const skippedModels = [...(interceptor.match(/modelLower\.contains[\s\S]*?\)\s*\{/)?.[0] ?? "").matchAll(/contains\("([^"]+)"\)/g)].map((m) => m[1]);
  const demands = [...interceptor.matchAll(/"(\w+)"\.equals\(toolName\)\s*&&\s*(\w+)\(/g)].map((m) => {
    const body = interceptor.match(new RegExp(String.raw`boolean ${m[2]}\([^)]*\)\s*\{([\s\S]*?)\n  \}`))?.[1] ?? "";
    return { tool: m[1], keywords: [...body.matchAll(/contains\("([^"]+)"\)/g)].map((k) => k[1]) };
  });

  const sql = readInitDb();
  const tables = ["platform_tool_configs", "platform_mcp_servers", "user_mcp_servers", "orazaka_tools_cache"].map((t) => ({ table: t, columns: tableColumns(sql, t) }));
  const seeded = [...sql.matchAll(/INSERT INTO platform_tool_configs[^;]*?VALUES\s*([\s\S]*?)(?:ON CONFLICT|;)/g)].flatMap((m) =>
    [...m[1].matchAll(/\('([^']+)'/g)].map((r) => r[1]),
  );

  const sandboxProps = src("SandboxProperties");
  const sandbox = {
    marker: typeInfo(src("McpWriteTool"), "McpWriteTool")?.summary ?? "",
    markerDoc: javadocAbove(src("McpWriteTool"), src("McpWriteTool").search(/public @interface/)),
    prefix: sandboxProps.match(/prefix\s*=\s*"([^"]+)"/)?.[1] ?? "",
    params: paramTags(sandboxProps, "SandboxProperties"),
  };
  const toolsPrefix = src("ToolsProperties").match(/prefix\s*=\s*"([^"]+)"/)?.[1] ?? "";
  return { tools, skippedModels, demands, tables, seeded, sandbox, toolsPrefix, repo: repoOfFile(moduleDir) };
}

const mdTools = (t, api, cli) => {
  const lines = [
    frontmatter(
      "Tools & MCP",
      "What tools are in Orazaka — function calling, MCP servers, tool caching and the write sandbox — extracted from orazaka-tools and the tool interceptor.",
      "Core",
      6,
    ),
  ];
  if (!t) return lines.join("\n") + "\n";
  const code = (s) => `\`${s}\``;
  lines.push(
    `In Orazaka a **tool** is a function a model may call during a turn (Spring AI function calling).`,
    `\`orazaka-tools\` (repository \`${t.repo}\`) holds the tool registry, the bridge to external MCP servers, the`,
    "tool result cache and the sandbox that isolates tools which write. Tools are attached to a turn by the",
    "`ToolInterceptor` of the pipeline (see the [interceptor registry](INTERCEPTORS.md)) — a model only sees the",
    "tools the turn needs.",
    "",
    "## Registered tools",
    "",
    "| Tool | Description | Input | Fields |",
    "|:---|:---|:---|:---|",
  );
  for (const x of t.tools) {
    lines.push(`| ${code(x.name)} | ${mdCell(x.description)} | ${code(x.input)} | ${x.fields.map((f) => code(f.name)).join(", ") || "—"} |`);
  }
  lines.push(
    "",
    "`searchKnowledge` searches the sources the knowledge service holds for the signed-in user (not the web). `analyzePoster` and",
    "`analyzeAudioExtract` are reference tools: they demonstrate the contract and return a fixed analysis.",
    "",
    "## When a tool is attached",
    "",
  );
  if (t.skippedModels.length) {
    lines.push(`- Never on a streaming turn, nor for a vision model (model name containing ${t.skippedModels.map(code).join(", ")}).`);
  }
  for (const d of t.demands) lines.push(`- ${code(d.tool)} only when the turn mentions ${d.keywords.map(code).join(", ")}.`);
  lines.push("- Every other registered tool is offered on every eligible turn.");
  lines.push(
    "",
    "## Configuration in the database",
    "",
    "Tools are configured per deployment, in data: no redeploy to cache a tool or to add an MCP server. A tool",
    "whose `platform_tool_configs` row enables caching is wrapped in `CachingToolCallback`: the same input returns",
    "the stored result for `cache_ttl_seconds`, kept in memory (Caffeine) and in `orazaka_tools_cache` (PostgreSQL).",
    `Static defaults live under \`${t.toolsPrefix}\` in \`application.yml\`.`,
    "",
  );
  for (const tb of t.tables.filter((x) => x.columns.length)) {
    lines.push(`### ${code(tb.table)}`, "", "| Column | Type |", "|:---|:---|");
    for (const c of tb.columns) lines.push(`| ${code(c.name)} | ${code(c.type)} |`);
    lines.push("");
  }
  if (t.seeded.length) lines.push(`Seeded tool configuration: ${t.seeded.map(code).join(", ")}.`, "");
  lines.push(
    "## MCP servers",
    "",
    "Orazaka is an MCP **client**: before a turn, `DefaultMcpOrchestrator` queries every enabled `REMOTE` platform",
    "server and the user's own servers in parallel (one virtual thread each) and adds what they return to the",
    "context.",
    "Platform servers are declared by an administrator; each user may add private ones.",
    "",
  );
  const mcpRows = api.filter((r) => r.path.includes("/mcp/"));
  if (mcpRows.length) {
    lines.push("| Method | Path | Access | Summary |", "|:---|:---|:---|:---|");
    for (const r of mcpRows) lines.push(`| ${r.method} | ${code(r.path)} | ${r.access} | ${r.summary || "—"} |`);
    lines.push("");
  }
  const mcpCli = cli.filter((c) => c.top === "mcp" && !c.isTop);
  if (mcpCli.length) {
    lines.push("From the terminal ([CLI reference](CLI.md)):", "", "```bash");
    for (const c of mcpCli) lines.push(`orazaka ${[c.name, ...c.args.map((a) => a.name)].join(" ")}`.padEnd(34) + `# ${c.description}`);
    lines.push("```", "");
  }
  lines.push("## The write sandbox", "", t.sandbox.markerDoc, "");
  if (t.sandbox.params.length) {
    lines.push("| Property | Meaning |", "|:---|:---|");
    for (const p of t.sandbox.params) lines.push(`| ${code(`${t.sandbox.prefix}.${p.name.replace(/[A-Z]/g, (c) => "-" + c.toLowerCase())}`)} | ${p.text} |`);
  }
  return lines.join("\n").trimEnd() + "\n";
};

// ── Jobs, schedules & automation ────────────────────────────────────────────

/** Every @Scheduled method of the platform: where, how often, and what it does. */
function extractSchedules() {
  const rows = [];
  for (const repo of REPOSITORIES) {
    for (const f of javaFiles(join(ROOT, repo.path)).filter((p) => p.includes(`${"/"}src${"/"}main${"/"}`))) {
      const s = read(f);
      const scheduled = [...s.matchAll(/@Scheduled\(([\s\S]*?)\)\s*(?:@[\w.]+(?:\([^)]*\))?\s*)*(?:public\s+|protected\s+)?void\s+(\w+)\s*\(/g)];
      for (const m of scheduled) {
        const attrs = m[1];
        const value = (key) => attrs.match(new RegExp(String.raw`${key}\s*=\s*(?:"([^"]*)"|([\w_ ]+))`));
        const cron = value("cron");
        const delay = value("fixedDelayString") ?? value("fixedDelay");
        const rate = value("fixedRateString") ?? value("fixedRate");
        const describe = (kind, v) => {
          if (!v) return null;
          const literal = v[1] ?? v[2].replace(/_/g, "");
          const prop = literal.match(/^\$\{([^:}]+):?([^}]*)\}$/);
          const shown = (v) => (kind === "cron" ? `\`${v}\`` : v);
          return prop ? `${kind} ${shown(prop[2] || "?")} (\`${prop[1]}\`)` : `${kind} ${shown(literal)}`;
        };
        const ms = (text) => text.replace(/(\d+)(?= \(|$)/, (n) => (Number(n) >= 1000 ? `${Number(n) / 1000} s` : `${n} ms`));
        const schedule = cron ? describe("cron", cron) : delay ? ms(describe("every", delay)) : rate ? ms(describe("every", rate)) : "—";
        const name = basename(f, ".java");
        rows.push({
          repo: repo.name,
          type: name,
          method: m[2],
          schedule,
          // One task per class: the class says what it is for. Several: each method says which one it is.
          summary: (scheduled.length === 1 ? typeInfo(s, name)?.summary : firstSentence(javadocAbove(s, m.index))) || typeInfo(s, name)?.summary || "",
        });
      }
    }
  }
  return rows.sort((a, b) => a.repo.localeCompare(b.repo) || a.type.localeCompare(b.type));
}

function extractJobs(arch) {
  const executors = [];
  for (const f of javaFiles(join(ROOT, "services")).filter((p) => p.includes("/src/main/"))) {
    const s = read(f);
    const key = s.match(/String handlerKey\(\)\s*\{\s*return\s+"([^"]+)"/)?.[1];
    if (key) executors.push({ key, type: basename(f, ".java"), repo: repoOfFile(f), summary: typeInfo(s, basename(f, ".java"))?.summary ?? "" });
  }
  const contract = walk(join(ROOT, "contracts"), (p) => basename(p) === "JobExecutor.java" && p.includes("/src/main/"))[0];
  const listener = walk(join(ROOT, "services"), (p) => basename(p) === "JobListener.java" && p.includes("/src/main/"))[0];

  const auto = REPOSITORIES.find((r) => r.name === "automation");
  const autoDir = auto ? join(ROOT, auto.path) : null;
  const autoFile = (n) => (autoDir ? walk(autoDir, (p) => basename(p) === n && p.includes("/src/main/"))[0] : undefined);
  const dispatcher = read(autoFile("ConnectorDispatcher.java") ?? "");
  const connectors = [...dispatcher.matchAll(/case\s+"(\w+)"\s*->\s*(\w+)\(/g)].map((m) => {
    const body = dispatcher.match(new RegExp(String.raw`void ${m[2]}\([^)]*\)\s*\{([\s\S]*?)\n  \}`))?.[1] ?? "";
    const statements = body.split(";").map((x) => x.trim()).filter(Boolean);
    const logsOnly = statements.length > 0 && statements.every((x) => x.startsWith("logger."));
    return { type: m[1], behaviour: logsOnly ? "logs the action (no outbound call yet)" : firstSentence(body.match(/logger\.info\(\s*"([^"]+)"/)?.[1] ?? "dispatched") };
  });
  const statusSrc = read(autoFile("AutomationJobStatus.java") ?? "");
  const constants = read(autoFile("AmqpConstants.java") ?? "");
  const constant = (n) => constants.match(new RegExp(String.raw`${n}\s*=\s*([^;]+);`))?.[1]?.replace(/AUTOMATION_QUEUE\s*\+\s*"([^"]*)"/, (_, x) => `${constants.match(/AUTOMATION_QUEUE\s*=\s*"([^"]+)"/)?.[1]}${x}`).replace(/"/g, "");
  const yml = read(autoDir ? join(autoDir, "src/main/resources/application.yml") : "");
  const quartz = {
    store: yml.match(/job-store-type:\s*(\S+)/)?.[1],
    name: yml.match(/instanceName:\s*(\S+)/)?.[1],
    threads: yml.match(/threadCount:\s*(\S+)/)?.[1],
    clustered: yml.match(/isClustered:\s*(\S+)/)?.[1],
  };
  // Who publishes automation jobs, besides the service itself.
  const producers = javaFiles(join(ROOT, "services"))
    .filter((p) => p.includes("/src/main/") && !(autoDir && p.startsWith(autoDir)))
    .filter((p) => /"job\.automation\.[\w*]+"/.test(read(p)))
    .map((p) => ({ type: basename(p, ".java"), repo: repoOfFile(p), key: read(p).match(/"(job\.automation\.[\w*]+)"/)[1] }));
  return {
    routes: arch.messaging.capabilityRoutes,
    executors: executors.sort((a, b) => a.key.localeCompare(b.key)),
    contract: contract ? typeInfo(read(contract), "JobExecutor")?.summary : "",
    lifecycle: listener ? typeInfo(read(listener), "JobListener") : null,
    listenerDoc: listener ? javadocAbove(read(listener), read(listener).search(/@Component\s*\npublic class JobListener/)) : "",
    schedules: extractSchedules(),
    automation: {
      connectors,
      statuses: enumValues(statusSrc, "AutomationJobStatus"),
      queue: constant("AUTOMATION_QUEUE"),
      binding: constant("AUTOMATION_BINDING"),
      dlq: constant("AUTOMATION_DLQ"),
      telemetry: constant("TELEMETRY_ROUTING_KEY"),
      agentPrefix: constant("AGENT_DISPATCH_PREFIX"),
      quartz,
      producers,
      repo: auto?.name,
    },
  };
}

const mdJobs = (j, api, cli) => {
  const lines = [
    frontmatter(
      "Jobs, Schedules & Automation",
      "Everything Orazaka runs without a user waiting: the job plane, its executors and workers, every scheduled task and the automation connectors — extracted from the code.",
      "Core",
      7,
    ),
  ];
  const code = (s) => `\`${s}\``;
  lines.push(
    "Three mechanisms run work in the background, each with one owner:",
    "",
    "- **The job plane** (`orazaka-job-service`) — asynchronous AI work: a capability is published as a job on",
    "  RabbitMQ and executed in process or by a worker. Studio runs and `ASYNC` intentions use it.",
    "- **Scheduled tasks** — sweepers and probes inside each service (`@Scheduled`), configured by properties.",
    `- **Automation** (\`${j.automation.repo}\`) — connector actions (Slack, Jira, the CLI agent…) and its`,
    "  Quartz scheduler.",
    "",
    "## The job plane",
    "",
    j.listenerDoc,
    "",
    "A capability row (`orazaka_capabilities`) says **where** a job goes (`routing_key`, the queue a process drains)",
    "and **which code** runs it (`handler_key`). Both are data: a pack adds a capability with a row, not a deploy.",
    "",
    "| Capability | Handler | Routing key | Runs in |",
    "|:---|:---|:---|:---|",
  );
  const exec = new Map(j.executors.map((e) => [e.key, e]));
  for (const r of j.routes) {
    const e = r.handler ? exec.get(r.handler) : null;
    lines.push(`| ${code(r.feature)} | ${r.handler ? code(r.handler) : "—"} | ${code(r.routingKey)} | ${e ? `${code(e.type)} (in process)` : "a worker (see the [worker protocol](../WORKER_PROTOCOL.md))"} |`);
  }
  lines.push("", "### In-process executors", "", j.contract, "", "| Handler key | Executor | Repository | Summary |", "|:---|:---|:---|:---|");
  for (const e of j.executors) lines.push(`| ${code(e.key)} | ${code(e.type)} | ${code(e.repo)} | ${e.summary || "—"} |`);
  const internal = api.filter((r) => r.path.startsWith("/internal/v1/capabilities") || r.path.startsWith("/internal/v1/workers"));
  if (internal.length) {
    lines.push("", "### Capability and worker registries", "", "Service-to-service (`SERVICE` token): how a pack registers a capability and a worker announces itself.", "", "| Method | Path | Summary |", "|:---|:---|:---|");
    for (const r of internal) lines.push(`| ${r.method} | ${code(r.path)} | ${r.summary || "—"} |`);
  }
  const jobApi = api.filter((r) => r.path.startsWith("/api/v1/jobs"));
  if (jobApi.length) {
    lines.push("", "### Following and approving jobs", "", "| Method | Path | Access | Summary |", "|:---|:---|:---|:---|");
    for (const r of jobApi) lines.push(`| ${r.method} | ${code(r.path)} | ${r.access} | ${r.summary || "—"} |`);
  }

  lines.push(
    "",
    "## Scheduled tasks",
    "",
    "Every `@Scheduled` method of the platform. A property in parentheses overrides the interval (or cron) in",
    "the service's `application.yml` or environment.",
    "",
    "| Repository | Task | Schedule | What it does |",
    "|:---|:---|:---|:---|",
  );
  for (const s of j.schedules) lines.push(`| ${code(s.repo)} | ${code(`${s.type}.${s.method}`)} | ${s.schedule} | ${s.summary || "—"} |`);

  const a = j.automation;
  lines.push(
    "",
    "## Automation",
    "",
    `Connector actions are jobs too: a producer publishes on ${code(a.binding)} (exchange \`orazaka.jobs\`), the`,
    `automation service consumes ${code(a.queue)} (failures dead-letter to ${code(a.dlq)}), runs the connector and`,
    `reports each state change on ${code(a.telemetry)} (exchange \`orazaka.events\`).`,
    "",
  );
  if (a.producers.length) {
    lines.push("| Producer | Repository | Routing key |", "|:---|:---|:---|");
    for (const p of a.producers) lines.push(`| ${code(p.type)} | ${code(p.repo)} | ${code(p.key)} |`);
    lines.push("");
  }
  lines.push("| Connector | Behaviour |", "|:---|:---|");
  for (const c of a.connectors) lines.push(`| ${code(c.type)} | ${c.type === "CLI_AGENT" ? `forwarded to the user's CLI agent on ${code(`${a.agentPrefix}{userId}`)}` : c.behaviour} |`);
  lines.push("", `Automation job states: ${a.statuses.map(code).join(" · ")}.`);
  const agent = cli.find((c) => c.name === "agent listen");
  if (agent) {
    lines.push(
      "",
      "The CLI agent runs on the user's machine and connects **outbound** (no inbound port):",
      "",
      "```bash",
      "orazaka login",
      `orazaka agent listen   # ${agent.description}`,
      "```",
    );
    const agentApi = api.filter((r) => r.path.startsWith("/api/v1/agent"));
    if (agentApi.length) {
      lines.push("", "| Method | Path | Access | Summary |", "|:---|:---|:---|:---|");
      for (const r of agentApi) lines.push(`| ${r.method} | ${code(r.path)} | ${r.access} | ${r.summary || "—"} |`);
    }
  }
  if (a.quartz.store) {
    lines.push(
      "",
      `The service embeds a Quartz scheduler (\`${a.quartz.name}\`): ${a.quartz.store.toUpperCase()} job store in its own database, ${a.quartz.threads} threads, clustered: ${a.quartz.clustered}.`,
    );
  }
  return lines.join("\n").trimEnd() + "\n";
};

// ── Packs ───────────────────────────────────────────────────────────────────

/**
 * A YAML reader for the subset the pack manifests and their translations use: block mappings and
 * sequences, plain and quoted scalars, folded (`>`, `>-`) and literal (`|`, `|-`) blocks, comments,
 * and flow collections on one or several lines (`[a, b]` becomes a list of scalars; a flow mapping is
 * kept as its source text — the docs never read inside one). The generator has no dependency by
 * design (it runs before anything is installed), and the manifests are the platform's own files,
 * validated against pack.schema.json — this is not a general YAML parser and does not pretend to be.
 */
function parseYaml(text) {
  const raw = text.replace(/\r\n?/g, "\n").split("\n");
  const stripComment = (line) => {
    let quote = null;
    for (let i = 0; i < line.length; i++) {
      const c = line[i];
      if (quote) {
        if (c === quote) quote = null;
      } else if (c === '"' || c === "'") quote = c;
      else if (c === "#" && (i === 0 || /\s/.test(line[i - 1]))) return line.slice(0, i).replace(/\s+$/, "");
    }
    return line.replace(/\s+$/, "");
  };
  const lines = raw.map((l) => ({ indent: l.match(/^ */)[0].length, text: stripComment(l).trim(), raw: l }));
  let i = 0;
  const skip = () => {
    while (i < lines.length && !lines[i].text) i++;
  };
  const scalar = (v) => {
    if (v === "" || v === "~" || v === "null") return null;
    if (v === "true") return true;
    if (v === "false") return false;
    if (/^-?\d+(\.\d+)?$/.test(v)) return Number(v);
    if (v.startsWith('"')) return JSON.parse(v);
    if (v.startsWith("'")) return v.slice(1, -1).replace(/''/g, "'");
    return v;
  };
  const balance = (s) => [...s].reduce((n, c) => n + ("[{".includes(c) ? 1 : "]}".includes(c) ? -1 : 0), 0);
  const value = (v, ownIndent) => {
    if (/^[>|][-+]?$/.test(v)) {
      const folded = v[0] === ">";
      const chomp = v.endsWith("-");
      const body = [];
      let blockIndent = null;
      while (i < lines.length && (!lines[i].raw.trim() || lines[i].indent > ownIndent)) {
        if (blockIndent === null && lines[i].raw.trim()) blockIndent = lines[i].indent;
        body.push(lines[i].raw.slice(blockIndent ?? 0));
        i++;
      }
      while (body.length && !body.at(-1).trim()) body.pop();
      const textOut = folded ? body.join("\n").replace(/([^\n])\n(?=[^\n])/g, "$1 ") : body.join("\n");
      return chomp ? textOut : `${textOut}\n`; // clip and keep alike: the trailing blank lines were dropped above
    }
    if (v.startsWith("[") || v.startsWith("{")) {
      let source = v;
      while (balance(source) > 0 && i < lines.length) source += " " + lines[i++].text;
      if (source.startsWith("[")) {
        const inner = source.slice(1, -1).trim();
        if (!/[[{]/.test(inner)) return inner ? inner.split(",").map((x) => scalar(x.trim())) : [];
      }
      return source;
    }
    return scalar(v);
  };
  const block = (indent) => {
    skip();
    if (i >= lines.length || lines[i].indent < indent) return null;
    const at = lines[i].indent;
    if (lines[i].text.startsWith("- ") || lines[i].text === "-") {
      const out = [];
      while (i < lines.length) {
        skip();
        if (i >= lines.length || lines[i].indent !== at || !(lines[i].text.startsWith("- ") || lines[i].text === "-")) break;
        const rest = lines[i].text.slice(1).trim();
        const itemIndent = at + 2;
        if (!rest) {
          i++;
          out.push(block(itemIndent));
        } else if (/^[\w.-]+:(\s|$)/.test(rest) || /^"[^"]+":(\s|$)/.test(rest)) {
          // A mapping that starts on the dash line: re-read the line as if indented.
          lines[i] = { ...lines[i], indent: itemIndent, text: rest };
          out.push(mapping(itemIndent));
        } else {
          i++;
          out.push(value(rest, at));
        }
      }
      return out;
    }
    return mapping(at);
  };
  const mapping = (at) => {
    const out = {};
    while (i < lines.length) {
      skip();
      if (i >= lines.length || lines[i].indent !== at || lines[i].text.startsWith("- ")) break;
      const m = lines[i].text.match(/^("[^"]+"|'[^']+'|[^:]+?):(?:\s+(.*))?$/);
      if (!m) throw new Error(`parseYaml: cannot read line ${i + 1}: ${lines[i].raw}`);
      const key = scalar(m[1].trim());
      const rest = (m[2] ?? "").trim();
      i++;
      if (rest) out[key] = value(rest, at);
      else {
        skip();
        // A sequence may sit at the key's own indent (`key:\n- a`), a mapping must be deeper.
        const next = lines[i];
        out[key] = next && (next.indent > at || (next.indent === at && next.text.startsWith("- "))) ? block(next.indent) : null;
      }
    }
    return out;
  };
  return block(0) ?? {};
}

/**
 * The pack catalogue, read from orazaka-packs: every bundle's manifest, its translations, its
 * Studios (with what their blueprint asks and runs) and its rulesets. It goes into
 * architecture.json so the site sells the packs that exist — the list used to be written by hand
 * on the site and showed one pack the repository never had. `showcase` is false for a bundle the
 * catalogue keeps off the shelf (`catalog.status: DRAFT`, e.g. the platform's test pack).
 */
function extractPackCatalog() {
  const repo = REPOSITORIES.find((r) => r.name === "packs");
  const dir = repo ? join(ROOT, repo.path) : null;
  if (!dir || !existsSync(dir)) return [];
  const text = (o) => (o ? { label: o.label ?? null, tagline: o.tagline ?? null, description: o.description ?? null } : null);
  return readdirSync(dir)
    .filter((n) => existsSync(join(dir, n, "pack.yaml")))
    .sort()
    .map((name) => {
      const root = join(dir, name);
      const m = parseYaml(read(join(root, "pack.yaml")));
      const i18nDir = join(root, "i18n");
      const i18n = existsSync(i18nDir)
        ? Object.fromEntries(
            readdirSync(i18nDir)
              .filter((f) => f.endsWith(".yaml"))
              .sort()
              .map((f) => [f.replace(/\.yaml$/, ""), parseYaml(read(join(i18nDir, f)))]),
          )
        : {};
      const locales = Object.keys(i18n);
      const status = m.catalog?.status ?? (m.catalog ? "PUBLISHED" : "UNLISTED");
      const studios = (m.studios ?? []).map((s) => {
        const bpPath = join(root, s.blueprint ?? `studios/${s.key}/blueprint.json`);
        const bp = existsSync(bpPath) ? JSON.parse(read(bpPath)) : null;
        const steps = bp?.definition?.steps ?? [];
        return {
          key: s.key,
          profession: s.profession ?? null,
          iconKey: s.iconKey ?? null,
          pricing: s.pricing ?? null,
          status: s.status ?? null,
          estimatedCredits: bp?.estimatedCredits ?? null,
          steps: steps.length,
          stepKinds: [...new Set(steps.map((x) => x.kind))],
          inputs: Object.entries(bp?.inputSchema?.properties ?? {}).map(([key, p]) => ({ key, title: p.title ?? null })),
          outputs: (bp?.definition?.outputs ?? []).map((o) => ({ key: o.key, type: o.type ?? null })),
          text: Object.fromEntries(locales.map((l) => [l, text(i18n[l]?.studios?.[s.key])])),
        };
      });
      const rulesetsDir = join(root, "rulesets");
      const rulesets = existsSync(rulesetsDir)
        ? readdirSync(rulesetsDir)
            .sort()
            .map((id) => {
              const versions = readdirSync(join(rulesetsDir, id)).filter((f) => f.endsWith(".json")).sort();
              const latest = versions.length ? JSON.parse(read(join(rulesetsDir, id, versions.at(-1)))) : {};
              return { id, label: latest.label ?? id, jurisdiction: latest.jurisdiction ?? null, versions: versions.map((v) => v.replace(/\.json$/, "")), rules: (latest.rules ?? []).length };
            })
        : [];
      return {
        key: m.key,
        dir: name,
        version: m.version ?? null,
        tier: m.tier ?? null,
        kind: m.kind ?? null,
        distribution: m.distribution ?? null,
        regulatoryClass: m.regulatoryClass ?? null,
        status,
        showcase: status !== "DRAFT",
        category: m.catalog?.categoryKey ?? null,
        categorySortWeight: m.catalog?.category?.sortWeight ?? null,
        iconKey: m.catalog?.iconKey ?? null,
        sortWeight: m.catalog?.sortWeight ?? null,
        pricing: m.pricing ? { priceCents: m.pricing.priceCents ?? 0, includedCredits: m.pricing.includedCredits ?? 0 } : null,
        capabilities: (m.requires?.capabilities ?? []).map((c) => c.key),
        workers: (m.requires?.workers ?? []).map((w) => w.name),
        scopeGuard: Boolean(m.scopeGuard),
        consent: Boolean(m.consent),
        safety: Boolean(m.safety),
        rulesets,
        text: Object.fromEntries(locales.map((l) => [l, text(i18n[l]?.pack)])),
        categoryText: Object.fromEntries(locales.map((l) => [l, i18n[l]?.category ? { label: i18n[l].category.label ?? null, description: i18n[l].category.description ?? null } : null])),
        studios,
      };
    })
    .sort((a, b) => (b.sortWeight ?? 0) - (a.sortWeight ?? 0) || a.key.localeCompare(b.key));
}


/** Top-level scalars and the `studios:` keys of a pack.yaml — the fields the catalogue table shows. */
function packSummary(yaml) {
  const scalar = (k) => yaml.match(new RegExp(String.raw`^${k}:\s*([^\s#]+)`, "m"))?.[1] ?? "—";
  const block = (k) => yaml.match(new RegExp(String.raw`^${k}:\s*\n((?:[ \t]+.*\n|\s*\n)*)`, "m"))?.[1] ?? "";
  const studios = [...block("studios").matchAll(/^  - key:\s*(\S+)/gm)].map((m) => m[1]);
  const requires = block("requires");
  const capabilities = [...requires.matchAll(/^    - key:\s*(\S+)/gm)].map((m) => m[1]);
  const workers = [...requires.matchAll(/^    - name:\s*(\S+)/gm)].map((m) => m[1]);
  return {
    key: scalar("key"),
    version: scalar("version"),
    tier: scalar("tier"),
    distribution: scalar("distribution"),
    regulatoryClass: scalar("regulatoryClass"),
    onShelf: /^catalog:/m.test(yaml),
    studios,
    capabilities,
    workers,
  };
}

function extractPacks() {
  const repo = REPOSITORIES.find((r) => r.name === "packs");
  if (!repo) return null;
  const dir = join(ROOT, repo.path);
  const schema = JSON.parse(read(join(dir, "pack.schema.json")) || "{}");
  const packs = (existsSync(dir) ? readdirSync(dir) : [])
    .filter((n) => existsSync(join(dir, n, "pack.yaml")))
    .sort()
    .map((n) => ({ dir: n, ...packSummary(read(join(dir, n, "pack.yaml"))) }));
  const fields = Object.entries(schema.properties ?? {}).map(([name, p]) => ({
    name,
    required: (schema.required ?? []).includes(name),
    values: p.enum ?? null,
    description: firstSentence((p.description ?? "").replace(/\s+/g, " ").trim()),
  }));
  const guide = read(join(dir, "CONTRIBUTING.md"));
  return { repo: repo.name, packs, fields, guide };
}

const mdPacks = (p, cli, catalog) => {
  const lines = [
    frontmatter(
      "Packs & Studios",
      "The packs that extend Orazaka without code — the reference catalogue, every manifest field, and how to write, validate and install a pack — read from orazaka-packs.",
      "Business",
      5,
    ),
  ];
  if (!p) return lines.join("\n") + "\n";
  const code = (s) => `\`${s}\``;
  lines.push(
    "A **Studio** is a business workflow — a versioned DAG of steps (model calls, transforms, human approvals,",
    "connectors) with its own input form. A **pack** is a directory that ships Studios: a `pack.yaml` manifest,",
    "its translations, the blueprints and, for a `WORKER` pack, its own worker. Installing a pack writes data —",
    "capability rows, a price, catalogue entries — and changes no code.",
    "",
    "> **Packs are not use-cases.** A pack is a business offer — Studios a customer installs and runs. A",
    "> use-case is a technical entry point of the engine (`chat.assistant`, `studio.run`…) declared",
    "> in Java in `orazaka-business` ([business layer](BUSINESS.md)); every Studio run goes through the `studio.run` one.",
    "",
  );
  const shown = catalog.filter((x) => x.showcase);
  if (shown.length) {
    lines.push(...packsSection(shown));
    const hidden = catalog.filter((x) => !x.showcase);
    if (hidden.length) lines.push(`Kept off the shelf (\`catalog.status: DRAFT\`): ${hidden.map((x) => code(x.key)).join(", ")} — installable for the platform's tests, never sold.`, "");
  }
  lines.push(
    `## The reference packs (${code(p.repo)})`,
    "",
    // Three columns: the cards above carry tier, distribution and class; this is the index of what to install.
    "| Pack | Version | Studios |",
    "|:---|:---|:---|",
  );
  for (const x of p.packs) lines.push(`| ${code(x.key)} | ${x.version} | ${x.studios.map(code).join(" · ") || "—"} |`);
  const packCli = cli.filter((c) => (c.top === "pack" || c.top === "studio") && !c.isTop);
  if (packCli.length) {
    lines.push("", "## From the terminal", "", "| Command | What it does |", "|:---|:---|");
    for (const c of packCli) lines.push(`| ${code(["orazaka", c.name, ...c.args.map((a) => a.name)].join(" "))} | ${c.description} |`);
  }
  lines.push("", "## Manifest fields", "", "From `pack.schema.json` — the schema `orazaka pack validate` checks against.", "", "| Field | Required | Values | Meaning |", "|:---|:---|:---|:---|");
  for (const f of p.fields) lines.push(`| ${code(f.name)} | ${f.required ? "yes" : "—"} | ${f.values ? f.values.map(code).join(" · ") : "—"} | ${mdCell(f.description) || "—"} |`);
  if (p.guide) {
    // The pack authors' guide, as written in orazaka-packs — one heading level down, under this page.
    const body = p.guide
      .replace(/^# .*\n+/, "")
      .replace(/^(#{1,5}) /gm, "#$1 ")
      // Its links are relative to the workspace root: docs → the sibling page, anything else → GitHub.
      .replace(/\]\(docs\/([A-Z_]+\.md(?:#[^)]*)?)\)/g, "](../$1)")
      .replace(/\]\((?!https?:|#|\.\.\/)([^)]+)\)/g, (_, path) => `](https://github.com/krizaka/sovkora/tree/main/${path})`);
    lines.push("", "## Writing a pack", "", body.trim());
  }
  return lines.join("\n").trimEnd() + "\n";
};

// ── Administration ──────────────────────────────────────────────────────────

function extractAdmin(api) {
  // The operator console lives in orazaka-web-client (host routing: admin.orazaka.com), not in a repository of its own.
  const repo = REPOSITORIES.find((r) => r.name === "web");
  const src = repo ? join(ROOT, repo.path, "src") : null;
  const appDir = src ? join(src, "app", "console") : null;
  const dir = src ? join(src, "console") : null;
  const screens = [];
  const called = new Set();
  let auth = { authority: null, provider: null };
  if (dir && existsSync(dir)) {
    for (const f of walk(appDir, (p) => basename(p) === "page.tsx").sort()) {
      const s = read(f);
      const route = screenRoute(relative(appDir, join(f, "..")));
      const at = s.search(/export default function/);
      const tabs = [...s.matchAll(/\{\s*id:\s*"(\w+)",\s*label:\s*"([^"]+)"\s*\}/g)].map((m) => m[2]);
      screens.push({ route, summary: firstSentence(javadocAbove(s, at)), tabs });
    }
    for (const f of walk(join(dir, "services"), (p) => p.endsWith(".ts"))) {
      for (const m of read(f).matchAll(/["`](\/api\/v1\/[^"`?]+)/g)) called.add(m[1].replace(/\$\{[^}]+\}/g, "{}"));
    }
    const options = read(join(dir, "core/auth/auth-options.ts"));
    auth = {
      authority: options.match(/ADMIN_AUTHORITY\s*=\s*"([^"]+)"/)?.[1] ?? null,
      provider: /CredentialsProvider/.test(options) ? "credentials" : null,
    };
  }
  const norm = (path) => path.replace(/\{[^}]*\}/g, "{}");
  const consoleRows = api.filter((r) => called.has(norm(r.path)));
  const adminOnly = api.filter((r) => r.access === "ADMIN" || /then ADMIN$/.test(r.access));
  screens.sort((x, y) => x.route.localeCompare(y.route));
  return { repo: repo?.name, screens, consoleRows, adminOnly, auth };
}

/**
 * Captures of the console, taken on a local stack and hosted on the Krizaka CDN (never committed — PR captures and
 * doc captures live on Bunny Storage). E-mail addresses are redacted. Re-take them when a screen changes.
 */
const ADMIN_CAPTURES = [
  ["02-billing-usage", "Billing — consumption per capability and model, refusals included"],
  ["05-billing-plans", "Billing — the plans and what each one grants"],
  ["04-billing-pricebook", "Billing — the price book: credits per unit for every capability and model, versioned"],
  ["09-studios-builder", "Studios — the blueprint editor: versions, credits, steps and forms"],
].map(([file, caption]) => ({ url: `https://krizaka-assets.b-cdn.net/pr-assets/orazaka/admin-console/${file}.png`, caption }));

const mdAdmin = (a) => {
  const lines = [
    frontmatter(
      "Administration",
      "What an Orazaka administrator controls — the SecOps console, every ADMIN-only endpoint, and how the console is secured — extracted from the code.",
      "Operations",
      2,
    ),
  ];
  const code = (s) => `\`${s}\``;
  lines.push(
    "Everything commercial and operational in Orazaka is **data an administrator edits** — models, the",
    "interceptor pipeline, capabilities, MCP servers, plans, prices, wallets, Studios — never a redeploy. An",
    `administrator is an account holding \`${a.auth.authority ?? "ROLE_ADMIN"}\`, granted by the identity service; every`,
    "administrative endpoint checks it on the server (`@PreAuthorize`), whatever client calls it.",
    "",
    `## The SecOps console (${code(a.repo ?? "orazaka-web-client")})`,
    "",
    "A Next.js console served by `orazaka-web-client` on the admin host (`admin.orazaka.com`, host routing), started with the web client by `orazaka dev`.",
    "",
    "| Screen | What it is for | Tabs |",
    "|:---|:---|:---|",
  );
  for (const s of a.screens) lines.push(`| ${code(s.route)} | ${s.summary || "—"} | ${s.tabs.join(" · ") || "—"} |`);
  lines.push(
    "",
    ...ADMIN_CAPTURES.flatMap((c) => [`![${c.caption}](${c.url})`, "", `*${c.caption}.*`, ""]),
    "> The console's screens are in French for now, and it ships no sign-in page yet",
    "> (tracked in `orazaka-web-client`): every administrative",
    "> action is also an endpoint, listed below.",
    "",
    "### How the console is secured",
    "",
    `- **Sign-in by credentials only** — no social login: the role that matters is granted by the identity service, and an account without \`${a.auth.authority ?? "ROLE_ADMIN"}\` is refused a console session.`,
    "- **A backend-for-frontend** — the browser never reaches a service port: `/api/v1/**` is proxied server-side, the session token injected as `Authorization: Bearer`.",
    "- **No internal surface** — `/internal/v1/**` (credit holds and settlements, registries) is not in the edge's route table, so the console cannot reach it by construction.",
  );
  if (a.consoleRows.length) {
    lines.push("", "### What the console calls", "", "| Method | Path | Access | Summary |", "|:---|:---|:---|:---|");
    for (const r of a.consoleRows) lines.push(`| ${r.method} | ${code(r.path)} | ${r.access} | ${r.summary || "—"} |`);
  }
  lines.push(
    "",
    "## Every ADMIN-only endpoint",
    "",
    "What an administrator can do, by service — from the console, the CLI or any HTTP client with an",
    "administrator's token (through the edge, `http://localhost:8088` locally).",
  );
  let current = null;
  for (const r of a.adminOnly) {
    if (r.service !== current) {
      current = r.service;
      lines.push("", `### ${current}`, "", "| Method | Path | Summary |", "|:---|:---|:---|");
    }
    lines.push(`| ${r.method} | ${code(r.path)} | ${r.summary || "—"} |`);
  }
  return lines.join("\n").trimEnd() + "\n";
};

// ════════════════════════════════════════════════════════════════════════════
// MAIN
// ════════════════════════════════════════════════════════════════════════════

// The docs read krizaka-java's code too: stop first, and say where it is expected, rather than die inside the model registry.
if (!existsSync(join(ROOT, KRIZAKA_JAVA, "pom.xml"))) {
  console.error(`generate-docs: krizaka-java is not at ${KRIZAKA_JAVA}: check it out there at the release sovkora-parent imports, or set KRIZAKA_JAVA_DIR`);
  process.exit(1);
}

const api = extractApi();
const cli = extractCli();
const registry = extractRegistry();
const arch = { ...extractArchitecture(), packs: extractPackCatalog(), models: registry, cli: cliModel(cli) };
const outputs = {
  "architecture.json": JSON.stringify(arch, null, 2) + "\n",
  "ARCHITECTURE.md": mdArchitecture(arch),
  "MODULES.md": mdRepositories(arch),
  "INTERFACE_CONTRACTS.md": mdInterfaces(arch),
  "USE_CASES.md": mdUseCases(extractUseCases()),
  "INTERCEPTORS.md": mdInterceptors(extractInterceptors(arch.pipeline)),
  "API_REFERENCE.md": mdApi(api),
  "CLI.md": mdCli(cli),
  "BUSINESS.md": mdBusiness(extractBusiness()),
  "TOOLS.md": mdTools(extractTools(), api, cli),
  "JOBS.md": mdJobs(extractJobs(arch), api, cli),
  "PACKS.md": mdPacks(extractPacks(), cli, arch.packs),
  "ADMIN.md": mdAdmin(extractAdmin(api)),
  "GOVERNANCE.md": mdGovernance(extractGovernance()),
  "MODELS.md": mdModels(registry, extractModels()),
  "MEASURED_RUNS.md": frontmatter("Measured runs", "Time, cost and every parameter of the video and image runs Orazaka made, per model and provider — generated from the run records.", "Models", 9) + mdMeasuredRuns(JSON.parse(read(join(ROOT, "docs/measurements/runs.json")))) +
    (existsSync(join(ROOT, "docs/measurements/cinema.json")) ? `\n${mdCinema(JSON.parse(read(join(ROOT, "docs/measurements/cinema.json")))).join("\n")}` : "") +
    (existsSync(join(ROOT, "docs/measurements/law-bakeoff.json")) ? `\n${mdLawBakeoff(JSON.parse(read(join(ROOT, "docs/measurements/law-bakeoff.json"))))}` : ""),
};

if (CHECK) {
  const stale = Object.entries(outputs).filter(([name, content]) => read(join(GEN_DIR, name)) !== content);
  if (stale.length) {
    console.error("✗ docs are stale — run `orazaka docs build`. Out of date:");
    for (const [name] of stale) console.error(`  - docs/_generated/${name}`);
    process.exit(1);
  }
  console.log(`✓ docs up to date (${Object.keys(outputs).length} generated files).`);
} else {
  mkdirSync(GEN_DIR, { recursive: true });
  for (const [name, content] of Object.entries(outputs)) writeFileSync(join(GEN_DIR, name), content);
  console.log(
    `docs generated → docs/_generated/ (${arch.modules.length} modules, ${arch.dependencies.length} deps, ` +
      `${Object.keys(outputs).length} files).`
  );
}
