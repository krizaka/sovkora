/**
 * @file model-registry.mjs
 * @description The models Orazaka supports, read from the registry its routing reads (`orazaka_models`): the rows the
 * SQL seeds, migrations and deployment data INSERT, and the registry metadata (vendor, family, capabilities, where it
 * runs, licence, context window, status) they UPDATE onto them. Nothing is typed here: a model with no metadata, a
 * licence nobody wrote or a capability outside the vocabulary stops the build (AGENTS.md §4: behaviour lives in data).
 *
 * `buildModels(sql, { smoke })` is pure: SQL text in, the published `models` section out — the unit tests drive it.
 */

/** The closed vocabulary of what a model does. */
export const CAPABILITIES = ["chat", "reasoning", "vision", "code", "embeddings", "image", "video", "speech-to-text", "text-to-speech", "music", "video-to-audio", "video-upscale"];
/** Where a model runs: on the operator's own hardware, or at a provider the workspace opts in to. */
export const RUNS_ON = ["local", "provider"];
/** `internal` rows stay in the catalogue (aliases, runtime variants, test fixtures) but are never published. */
export const STATUSES = ["available", "planned", "internal"];
/** Catalogue rows that are not models (UI accents share the table). */
const NOT_A_MODEL = new Set(["theme"]);

/** Columns the registry metadata lives in, in `orazaka_models`. */
export const METADATA_COLUMNS = ["vendor", "family", "capabilities", "runs_on", "runtime", "open_weights", "licence", "licence_url", "commercial_use", "context_window", "status"];
/** What a published model must carry (an `internal` one only needs its status). */
export const REQUIRED = ["vendor", "family", "capabilities", "runs_on", "runtime", "open_weights", "licence", "commercial_use", "status"];
/** Can a firm that sells use it: yes · conditions (a community licence, share-alike, use restrictions) · no (non-commercial). */
export const COMMERCIAL_USE = ["yes", "conditions", "no"];

/** SQL with its `--` comments dropped, string- and dollar-quote-aware (a comment holds apostrophes and semicolons). */
export function stripComments(sql) {
  let out = "";
  for (let i = 0; i < sql.length; ) {
    const c = sql[i];
    if (c === "'") {
      let j = i + 1;
      while (j < sql.length && !(sql[j] === "'" && sql[j + 1] !== "'")) j += sql[j] === "'" ? 2 : 1;
      out += sql.slice(i, j + 1);
      i = j + 1;
    } else if (c === "$" && /^\$[A-Za-z_]*\$/.test(sql.slice(i))) {
      const tag = /^\$[A-Za-z_]*\$/.exec(sql.slice(i))[0];
      const end = sql.indexOf(tag, i + tag.length);
      const stop = end < 0 ? sql.length : end + tag.length;
      out += sql.slice(i, stop);
      i = stop;
    } else if (c === "-" && sql[i + 1] === "-") {
      while (i < sql.length && sql[i] !== "\n") i++;
    } else {
      out += c;
      i++;
    }
  }
  return out;
}

/** Statements split on `;` outside quotes. */
export function statements(sql) {
  const out = [];
  let cur = "";
  // psql meta-commands (`\c orazaka_db`) end at the line, not at a `;`: left in, they glue to the next statement
  const src = stripComments(sql).replace(/^[ \t]*\\.*$/gm, "");
  for (let i = 0; i < src.length; ) {
    const c = src[i];
    if (c === "'") {
      let j = i + 1;
      while (j < src.length && !(src[j] === "'" && src[j + 1] !== "'")) j += src[j] === "'" ? 2 : 1;
      cur += src.slice(i, j + 1);
      i = j + 1;
    } else if (c === "$" && /^\$[A-Za-z_]*\$/.test(src.slice(i))) {
      const tag = /^\$[A-Za-z_]*\$/.exec(src.slice(i))[0];
      const end = src.indexOf(tag, i + tag.length);
      const stop = end < 0 ? src.length : end + tag.length;
      cur += src.slice(i, stop);
      i = stop;
    } else if (c === ";") {
      out.push(cur.trim());
      cur = "";
      i++;
    } else {
      cur += c;
      i++;
    }
  }
  if (cur.trim()) out.push(cur.trim());
  return out.filter(Boolean);
}

/** One SQL scalar: 'text' (with '' escapes, adjacent strings joined by ||), a number, TRUE/FALSE/NULL. */
function scalar(raw) {
  const t = raw.trim();
  if (/^null$/i.test(t)) return null;
  if (/^true$/i.test(t)) return true;
  if (/^false$/i.test(t)) return false;
  if (/^-?\d+(\.\d+)?$/.test(t)) return Number(t);
  if (/^'/.test(t)) {
    let text = "";
    for (const m of t.matchAll(/'((?:[^']|'')*)'/g)) text += m[1].replaceAll("''", "'");
    return text;
  }
  return { expr: t }; // anything computed (a psql variable, a function): not data this reader can state
}

/** Splits `a, 'b, c', (d)` on top-level commas. */
function splitTop(text) {
  const parts = [];
  let depth = 0;
  let cur = "";
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (c === "'") {
      let j = i + 1;
      while (j < text.length && !(text[j] === "'" && text[j + 1] !== "'")) j += text[j] === "'" ? 2 : 1;
      cur += text.slice(i, j + 1);
      i = j;
    } else if (c === "(") { depth++; cur += c; }
    else if (c === ")") { depth--; cur += c; }
    else if (c === "," && depth === 0) { parts.push(cur); cur = ""; }
    else cur += c;
  }
  if (cur.trim()) parts.push(cur);
  return parts;
}

/** The rows of `INSERT INTO orazaka_models (cols) VALUES (...), (...)` as objects. */
function insertedRows(stmt) {
  const head = /^INSERT\s+INTO\s+orazaka_models\s*\(([^)]*)\)\s*VALUES\s*/i.exec(stmt);
  if (!head) return [];
  const cols = head[1].split(",").map((c) => c.trim());
  let rest = stmt.slice(head[0].length);
  const conflict = /(?<=\s)ON\s+CONFLICT/i.exec(rest);
  if (conflict) rest = rest.slice(0, conflict.index).trimEnd();
  const rows = [];
  for (let i = 0; i < rest.length; i++) {
    if (rest[i] !== "(") continue;
    let depth = 0;
    let j = i;
    for (; j < rest.length; j++) {
      if (rest[j] === "'") { j++; while (j < rest.length && !(rest[j] === "'" && rest[j + 1] !== "'")) j += rest[j] === "'" ? 2 : 1; }
      else if (rest[j] === "(") depth++;
      else if (rest[j] === ")" && --depth === 0) break;
    }
    const values = splitTop(rest.slice(i + 1, j)).map(scalar);
    rows.push(Object.fromEntries(cols.map((c, k) => [c, values[k]])));
    i = j;
  }
  return rows;
}

/** `UPDATE orazaka_models SET a = .., b = .. WHERE model_name = '..'` → { name, set }, or null. */
function modelUpdate(stmt) {
  const head = /^UPDATE\s+orazaka_models\s+SET\s+/i.exec(stmt);
  if (!head) return null;
  const where = /(?<=\s)WHERE\s+model_name\s*=\s*'((?:[^']|'')*)'$/gi;
  where.lastIndex = head[0].length;
  const m = where.exec(stmt);
  if (!m) return null;
  const set = {};
  for (const part of splitTop(stmt.slice(head[0].length, m.index).trimEnd())) {
    const eq = part.indexOf("=");
    set[part.slice(0, eq).trim()] = scalar(part.slice(eq + 1));
  }
  return { name: m[1].replaceAll("''", "'"), set };
}

/**
 * Applies the SQL files, in order, to an in-memory table: the first INSERT of a name wins (ON CONFLICT DO NOTHING),
 * UPDATEs by model_name apply in order. An UPDATE of a model no INSERT created is an error, not a silent no-op.
 */
export function catalogue(sql) {
  const rows = new Map();
  for (const stmt of statements(sql)) {
    for (const r of insertedRows(stmt)) {
      if (typeof r.model_name === "string" && !rows.has(r.model_name)) rows.set(r.model_name, { model_name: r.model_name, ...r });
    }
    const u = modelUpdate(stmt);
    if (!u) continue;
    const row = rows.get(u.name);
    if (!row) throw new Error(`model registry: UPDATE of ${u.name}, which no INSERT created — the metadata names a model the catalogue does not hold`);
    Object.assign(row, u.set);
  }
  return [...rows.values()];
}

const isText = (v) => typeof v === "string" && v.trim().length > 0;

/** The checks a published model must pass; returns the list of problems (empty = fine). */
export function problems(row) {
  const out = [];
  if (!STATUSES.includes(row.status)) return [`status must be one of ${STATUSES.join(", ")} (got ${JSON.stringify(row.status ?? null)})`];
  if (row.is_default === true && row.commercial_use === "no") out.push("a default model must allow commercial use (commercial_use = 'no' — we sell to firms)");
  if (row.status === "internal") return out;
  for (const col of REQUIRED) {
    const v = row[col];
    if (v === undefined || v === null || (typeof v === "string" && !v.trim())) out.push(`${col} is missing`);
  }
  if (out.length) return out;
  const caps = String(row.capabilities).split(",").map((c) => c.trim());
  for (const c of caps) if (!CAPABILITIES.includes(c)) out.push(`capability "${c}" is not in the vocabulary (${CAPABILITIES.join(", ")})`);
  if (!RUNS_ON.includes(row.runs_on)) out.push(`runs_on must be one of ${RUNS_ON.join(", ")}`);
  if (typeof row.open_weights !== "boolean") out.push("open_weights must be TRUE or FALSE");
  if (!COMMERCIAL_USE.includes(row.commercial_use)) out.push(`commercial_use must be one of ${COMMERCIAL_USE.join(", ")}`);
  if (row.context_window != null && !(Number.isInteger(row.context_window) && row.context_window > 0)) out.push("context_window must be a positive integer");
  if (row.licence_url != null && !/^https:\/\//.test(row.licence_url)) out.push("licence_url must be an https URL");
  for (const col of ["vendor", "family", "runtime", "licence"]) if (!isText(row[col])) out.push(`${col} must be text`);
  return out;
}

/**
 * The published `models` section: every non-internal model of the catalogue, with its registry metadata, sorted for a
 * stable file. `smoke` is the sovereign smoke report, when one exists: a model is "verified" on the date of the last
 * PASSED check that names it.
 */
export function buildModels(sql, { smoke = null, licenceSources = [] } = {}) {
  const verified = verifiedBySmoke(smoke);
  const failures = [];
  const models = [];
  const rows = catalogue(sql);
  failures.push(...licenceDisagreements(rows, licenceSources));
  for (const row of rows) {
    if (NOT_A_MODEL.has(row.category)) continue;
    const list = problems(row);
    if (list.length) {
      failures.push(`  - ${row.model_name}: ${list.join("; ")}`);
      continue;
    }
    if (row.status === "internal") continue;
    models.push({
      id: row.model_name,
      name: String(row.model_label).trim().replace(/\((?:\d+:\d+, )?(?:9:16, GPU|MPS)\)$/, "").trim(),
      vendor: row.vendor,
      family: row.family,
      category: row.category,
      capabilities: String(row.capabilities).split(",").map((c) => c.trim()),
      runsOn: row.runs_on,
      runtime: row.runtime,
      provider: row.provider_name ?? null,
      openWeights: row.open_weights,
      licence: { name: row.licence, url: row.licence_url ?? null },
      commercialUse: row.commercial_use,
      contextWindow: row.context_window ?? null,
      status: row.is_default === true && row.status === "available" ? "default" : row.status,
      lastVerified: verified.get(row.model_name) ?? null,
    });
  }
  if (failures.length) throw new Error(`model registry: ${failures.length} model(s) of orazaka_models fail the registry contract —\n${failures.join("\n")}\n(add the metadata: UPDATE orazaka_models SET ${REQUIRED.join(", ")} … WHERE model_name = '…')`);
  const order = { default: 0, available: 1, planned: 2 };
  return models.sort((a, b) => (a.runsOn === b.runsOn ? 0 : a.runsOn === "local" ? -1 : 1) || order[a.status] - order[b.status] || a.category.localeCompare(b.category) || a.id.localeCompare(b.id));
}

/** model id → ISO date of the last smoke check that PASSED and named it. */
export function verifiedBySmoke(report) {
  const out = new Map();
  if (!report || !Array.isArray(report.checks)) return out;
  const day = String(report.finishedAt ?? report.startedAt ?? "").slice(0, 10);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(day)) return out;
  for (const check of report.checks) {
    if (check.status !== "PASSED") continue;
    for (const m of check.models ?? []) if (!m.startsWith("provider:")) out.set(m, day);
  }
  return out;
}

/** A licence name as a comparable key: "CC-BY 4.0 (SIWIS)" and "CC BY 4.0" meet at ccby40. */
export const licenceKey = (name) => String(name).toLowerCase().replace(/licen[cs]e/g, "").replace(/[^a-z0-9]/g, "");

/**
 * Other files in the workspace also state a model's licence (a README table, a worker's manifest). The registry follows
 * the model's own card, and the build refuses two sources that disagree: a source's text must start with the registry's.
 * \`sources\`: [{ name, licences: Map(model id → text) }].
 */
export function licenceDisagreements(rows, sources) {
  const out = [];
  for (const row of rows) {
    if (!row.licence || row.status === "internal") continue;
    for (const { name, licences } of sources) {
      const said = licences.get(row.model_name);
      if (said === undefined) continue;
      if (!licenceKey(said).startsWith(licenceKey(row.licence))) out.push(`  - ${row.model_name}: ${name} says "${said}", the registry (the model's card) says "${row.licence}"`);
    }
  }
  return out;
}

/** The README table of infra/localai: \`| \`<id>.yaml\` | weights | source | licence |\` → id → licence cell. */
export function readmeLicences(text) {
  const out = new Map();
  for (const line of text.split("\n")) {
    const m = /^\|\s*`([^`]+)\.yaml`\s*\|(.*)\|([^|]*)\|\s*$/.exec(line);
    if (m) out.set(m[1], m[3].trim());
  }
  return out;
}

/** The worker's models.yaml: an entry (two-space indent) followed by its four-space \`licence:\` → id → licence. */
export function manifestLicences(text) {
  const out = new Map();
  let id = null;
  for (const line of text.split("\n")) {
    const entry = /^ {2}([\w.\-]+):\s*$/.exec(line);
    if (entry) id = entry[1];
    const lic = /^ {4}licence:([^\n]*)$/.exec(line);
    const value = lic?.[1].trim();
    if (value && id) out.set(id, value.replace(/^["']|["']$/g, ""));
  }
  return out;
}
