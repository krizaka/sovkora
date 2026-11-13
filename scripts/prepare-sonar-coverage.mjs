// Before the SonarCloud scan: every part that passed must have left its coverage report, the reports are rewritten with
// repository-relative paths, and every path sonar-project.properties declares must match a file (krizaka-internal#2525).
// A report that never arrives leaves a folder at 0.0 % with nothing red, which is how this repository's clients, workers and
// packs reached SonarCloud empty. Usage: RESULTS='<toJSON(needs)>' node scripts/prepare-sonar-coverage.mjs
import { existsSync, mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { join, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";

const REPORTS = "coverage-reports";
const NORMALIZED = `${REPORTS}/normalized`;

/** The parts that write an lcov report: the CI job that runs them, the repository folder their paths are relative to. */
export const LCOV_PARTS = [
  { part: "web", job: "web", root: "apps/web" },
  { part: "mobile", job: "mobile", root: "apps/mobile" },
  { part: "cli", job: "cli", root: "apps/cli" },
  { part: "scripts", job: "scripts", root: "." },
  { part: "packs", job: "packs-schema", root: "packs" },
];

/** The Python directories of the `python-coverage` job (its matrix in ci.yml): one Cobertura report each. */
export const PYTHON_DIRS = [
  "workers/media",
  "packs/document-validation/worker",
  "packs/message-authenticity/worker",
  "packs/media-safety/worker",
  "packs/echo-toolkit/worker",
  "packs/tenant-screening-qc/worker",
  "packs/identity-document-check/worker",
  "packs/worker-common",
  "packs/profile-photo-check/worker",
];

export const slugOf = (dir) => dir.replaceAll("/", "__");

const POSIX = (path) => path.split(sep).join("/");

/** An lcov `SF:` path, relative to the repository: from a path relative to the part's folder, or absolute under the workspace. */
function repoPath(file, root, partRoot) {
  const absoluteRoot = POSIX(resolve(root));
  if (file.startsWith("/") || /^[A-Za-z]:[\\/]/.test(file)) {
    const unified = file.replaceAll("\\", "/");
    return unified.startsWith(`${absoluteRoot}/`) ? unified.slice(absoluteRoot.length + 1) : unified;
  }
  const clean = file.replace(/^\.\//, "");
  return partRoot === "." ? clean : `${partRoot}/${clean}`;
}

function rewriteLcov(text, root, partRoot) {
  return text.replace(/^SF:(.*)$/gm, (_, file) => `SF:${repoPath(file.trim(), root, partRoot)}`);
}

function rewriteCobertura(text, root, partRoot) {
  return text
    .replace(/<source>[^<]*<\/source>/g, `<source>${POSIX(resolve(root))}</source>`)
    .replace(/(<class\b[^>]*\bfilename=")([^"]*)"/g, (_, head, file) => `${head}${repoPath(file, root, partRoot)}"`);
}

const present = (path) => existsSync(path) && statSync(path).size > 0;

/** The files under `root` a properties pattern matches (`*` inside a name, `**` across folders); node_modules and .git are not walked. */
function matches(root, pattern) {
  if (!/[*]/.test(pattern)) return present(join(root, pattern)) ? [pattern] : [];
  const source = pattern
    .replace(/[.+^${}()|[\]\\]/g, "\\$&")
    .replace(/\*\*\//g, "(?:.*/)?")
    .replace(/\*\*/g, ".*")
    .replace(/\*/g, "[^/]*");
  const expression = new RegExp(`^${source}$`);
  const found = [];
  const walk = (dir) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      if (entry.name === "node_modules" || entry.name === ".git") continue;
      const full = join(dir, entry.name);
      if (entry.isDirectory()) walk(full);
      else if (expression.test(POSIX(relative(root, full))) && statSync(full).size > 0) found.push(POSIX(relative(root, full)));
    }
  };
  walk(root);
  return found;
}

/** What sonar-project.properties declares for a key, split on commas. */
function declared(properties, key) {
  const line = properties.split(/\r?\n/).find((l) => l.startsWith(`${key}=`));
  return line ? line.slice(key.length + 1).split(",").map((s) => s.trim()).filter(Boolean) : [];
}

/**
 * Checks and normalizes the reports in `<root>/coverage-reports/report-*` and returns `{ errors, written }`.
 * `results` is the `needs` context of the sonar job (job id → result): only a job that succeeded owes a report.
 */
export function prepareSonarCoverage({ root, results }) {
  const errors = [];
  const written = [];
  const ran = (job) => results[job] === "success";
  const out = join(root, NORMALIZED);
  mkdirSync(out, { recursive: true });

  for (const { part, job, root: partRoot } of LCOV_PARTS) {
    if (!ran(job)) continue;
    const source = `${REPORTS}/report-${part}/lcov.info`;
    if (!present(join(root, source))) {
      errors.push(`${part}: the job « ${job} » passed but ${source} is missing or empty: its coverage would reach SonarCloud as 0.0 %`);
      continue;
    }
    const target = `${NORMALIZED}/${part}.lcov.info`;
    writeFileSync(join(root, target), rewriteLcov(readFileSync(join(root, source), "utf8"), root, partRoot));
    written.push(target);
  }

  if (ran("python-coverage")) {
    for (const dir of PYTHON_DIRS) {
      const source = `${REPORTS}/report-py-${slugOf(dir)}/coverage.xml`;
      if (!present(join(root, source))) {
        errors.push(`${dir}: the job « python-coverage » passed but ${source} is missing or empty: its coverage would reach SonarCloud as 0.0 %`);
        continue;
      }
      const target = `${NORMALIZED}/${slugOf(dir)}.coverage.xml`;
      writeFileSync(join(root, target), rewriteCobertura(readFileSync(join(root, source), "utf8"), root, dir));
      written.push(target);
    }
  }

  const propertiesFile = join(root, "sonar-project.properties");
  const properties = existsSync(propertiesFile) ? readFileSync(propertiesFile, "utf8") : "";
  const owed = [
    ["sonar.javascript.lcov.reportPaths", LCOV_PARTS.some(({ job }) => ran(job))],
    ["sonar.python.coverage.reportPaths", ran("python-coverage")],
    ["sonar.coverage.jacoco.xmlReportPaths", ran("java")],
  ];
  for (const [key, expected] of owed) {
    if (!expected) continue;
    const patterns = declared(properties, key);
    if (patterns.length === 0) errors.push(`sonar-project.properties declares no ${key}, yet a part that produces it passed`);
    for (const pattern of patterns) {
      if (matches(root, pattern).length === 0) errors.push(`${key}: « ${pattern} » matches no file at scan time`);
    }
  }
  return { errors, written };
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const results = Object.fromEntries(Object.entries(JSON.parse(process.env.RESULTS ?? "{}")).map(([job, { result }]) => [job, result]));
  const { errors, written } = prepareSonarCoverage({ root: process.cwd(), results });
  for (const file of written) console.log(`coverage ready: ${file}`);
  for (const error of errors) console.log(`::error::${error}`);
  process.exit(errors.length === 0 ? 0 : 1);
}
