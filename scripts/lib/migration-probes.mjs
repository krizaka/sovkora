/**
 * What a migration file leaves behind, as read-only probes (the `verify` mode of scripts/dev-migrations.mjs).
 *
 * A probe is one SQL expression that is true when the effect is there. They are derived from the statements
 * (tables, columns, indexes, named constraints, triggers, functions, the first value of each inserted row); what
 * cannot be derived (an UPDATE, a column type, a DO block, an INSERT … SELECT) is declared in
 * `scripts/dev-migration-probes.json`, keyed by file. A file with no probe at all is `unprobed`, and verify fails on it:
 * a migration is never taken as applied without something that proves it.
 */

const DEFAULT_DB = { "krizaka-billing": "krizaka_billing_db", "krizaka-users": "krizaka_users_db", "orazaka-studio": "orazaka_studio_db" };
/** Database names that were renamed since an old migration was written. */
const RENAMED = { orazaka_billing_db: "krizaka_billing_db" };

const lit = (s) => `'${s.replaceAll("'", "''")}'`;
const ident = (s) => s.replace(/"/g, "").split(".").pop();

/** SQL without `--` comments (string and dollar-quoted bodies are kept as they are). */
export function stripComments(sql) {
  let out = "";
  for (let i = 0; i < sql.length; ) {
    if (sql[i] === "'") {
      let j = i + 1;
      while (j < sql.length && !(sql[j] === "'" && sql[j + 1] !== "'")) j += sql[j] === "'" ? 2 : 1;
      out += sql.slice(i, j + 1);
      i = j + 1;
    } else if (sql[i] === "$" && /^\$[A-Za-z_]*\$/.test(sql.slice(i))) {
      const tag = sql.slice(i).match(/^\$[A-Za-z_]*\$/)[0];
      const end = sql.indexOf(tag, i + tag.length);
      const j = end < 0 ? sql.length : end + tag.length;
      out += sql.slice(i, j);
      i = j;
    } else if (sql[i] === "-" && sql[i + 1] === "-") {
      while (i < sql.length && sql[i] !== "\n") i++;
    } else out += sql[i++];
  }
  return out;
}

/** The file cut at its `\c <db>` lines: [{ db, sql }] (db null before the first one). */
export function sections(sql) {
  const out = [{ db: null, lines: [] }];
  for (const line of sql.split("\n")) {
    const m = line.match(/^\s*\\c(?:onnect)?\s+(\S+)/);
    if (m) out.push({ db: RENAMED[m[1]] ?? m[1], lines: [] });
    else out.at(-1).lines.push(line);
  }
  return out.map((s) => ({ db: s.db, sql: s.lines.join("\n") })).filter((s) => s.sql.trim());
}

/** The database a section without `\c` runs in: the declared one (`databases[file]`), its "Runs in <db>" note, else its repository's, else orazaka_db. */
export function defaultDatabase(file, sql, databases = {}) {
  if (databases[file]) return databases[file];
  const m = sql.match(/runs in (\w+_db)/i);
  if (m) return RENAMED[m[1]] ?? m[1];
  const repo = Object.keys(DEFAULT_DB).find((r) => file.includes(`/${r}/`));
  return repo ? DEFAULT_DB[repo] : "orazaka_db";
}

/** Top-level elements of the VALUES tuples after `from`: [[elem, …], …] as raw text, strings and parentheses respected. */
function valueRows(text, from) {
  const rows = [];
  let i = from, depth = 0, cur = null;
  while (i < text.length) {
    const c = text[i];
    if (c === "'") {
      let j = i + 1;
      while (j < text.length && !(text[j] === "'" && text[j + 1] !== "'")) j += text[j] === "'" ? 2 : 1;
      if (cur !== null) cur.buf += text.slice(i, j + 1);
      i = j + 1;
      continue;
    }
    if (c === "$" && /^\$[A-Za-z_]*\$/.test(text.slice(i))) {
      const tag = text.slice(i).match(/^\$[A-Za-z_]*\$/)[0];
      const end = text.indexOf(tag, i + tag.length);
      const j = end < 0 ? text.length : end + tag.length;
      if (cur !== null) cur.buf += text.slice(i, j);
      i = j;
      continue;
    }
    if (c === "(") {
      depth++;
      if (depth === 1) cur = { elems: [], buf: "" };
      else cur.buf += c;
    } else if (c === ")") {
      depth--;
      if (depth === 0) {
        cur.elems.push(cur.buf.trim());
        rows.push(cur.elems);
        cur = null;
      } else cur.buf += c;
    } else if (c === "," && depth === 1) {
      cur.elems.push(cur.buf.trim());
      cur.buf = "";
    } else if (c === ";" && depth === 0) break;
    else if (/\b(on|returning)/i.test(text.slice(i, i + 12)) && depth === 0 && /^\s*(on\s+conflict|returning)/i.test(text.slice(i))) break;
    else if (cur !== null) cur.buf += c;
    i++;
  }
  return rows;
}

/** Probes derived from one section's statements: [{ label, sql }]. */
export function deriveProbes(sqlRaw) {
  const sql = stripComments(sqlRaw);
  const probes = [];
  const add = (label, expr) => probes.push({ label, sql: expr });
  for (const m of sql.matchAll(/create\s+table\s+(?:if\s+not\s+exists\s+)?([\w."]+)/gi)) add(`table ${ident(m[1])}`, `to_regclass(${lit(ident(m[1]))}) IS NOT NULL`);
  for (const m of sql.matchAll(/drop\s+table\s+(?:if\s+exists\s+)?([\w."]+)/gi)) add(`no table ${ident(m[1])}`, `to_regclass(${lit(ident(m[1]))}) IS NULL`);
  for (const m of sql.matchAll(/alter\s+table\s+(?:if\s+exists\s+)?(?:only\s+)?([\w."]+)\s+([^;]*);/gi)) {
    const table = ident(m[1]);
    for (const c of m[2].matchAll(/add\s+column\s+(?:if\s+not\s+exists\s+)?([\w"]+)/gi))
      add(`column ${table}.${ident(c[1])}`, `EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = ${lit(table)} AND column_name = ${lit(ident(c[1]))})`);
    for (const c of m[2].matchAll(/add\s+constraint\s+([\w"]+)/gi)) add(`constraint ${ident(c[1])}`, `EXISTS (SELECT 1 FROM pg_constraint WHERE conname = ${lit(ident(c[1]))})`);
    for (const c of m[2].matchAll(/drop\s+column\s+(?:if\s+exists\s+)?([\w"]+)/gi))
      add(`no column ${table}.${ident(c[1])}`, `NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = ${lit(table)} AND column_name = ${lit(ident(c[1]))})`);
  }
  for (const m of sql.matchAll(/create\s+(?:unique\s+)?index\s+(?:concurrently\s+)?(?:if\s+not\s+exists\s+)?([\w"]+)/gi)) add(`index ${ident(m[1])}`, `to_regclass(${lit(ident(m[1]))}) IS NOT NULL`);
  for (const m of sql.matchAll(/create\s+(?:or\s+replace\s+)?trigger\s+([\w"]+)/gi)) add(`trigger ${ident(m[1])}`, `EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = ${lit(ident(m[1]))})`);
  for (const m of sql.matchAll(/create\s+or\s+replace\s+function\s+([\w."]+)/gi)) add(`function ${ident(m[1])}`, `EXISTS (SELECT 1 FROM pg_proc WHERE proname = ${lit(ident(m[1]))})`);
  for (const m of sql.matchAll(/insert\s+into\s+([\w."]+)\s*\(([^)]*)\)\s*values/gi)) {
    const table = ident(m[1]);
    const cols = m[2].split(",").map((c) => c.trim().replace(/"/g, ""));
    // The row is identified by the ON CONFLICT columns when there are some, else by every plain string value it carries (a number may have been repriced since).
    const tail = sql.slice(m.index + m[0].length);
    const end = tail.search(/;[ \t]*(\n|$)/);
    const target = (end < 0 ? tail : tail.slice(0, end)).match(/on\s+conflict\s*\(([^)]*)\)/i);
    const keyCols = target ? target[1].split(",").map((c) => c.trim().replace(/"/g, "")) : null;
    for (const row of valueRows(sql, m.index + m[0].length)) {
      const plain = (v) => /^'(?:[^']|'')*'$/.test(v) || /^-?\d+$/.test(v);
      const used = cols.map((c, i) => ({ c, v: row[i] })).filter((x) => x.v !== undefined && plain(x.v) && (keyCols ? keyCols.includes(x.c) : x.v.startsWith("'")));
      if (!used.length) continue;
      const q = (v) => (v.startsWith("'") ? v : lit(v));
      // a date literal ('1970-01-01') is a prefix of the timestamp's text
      const eq = (x) => (/^'\d{4}-\d{2}-\d{2}'$/.test(x.v) ? `${x.c}::text LIKE ${lit(x.v.slice(1, -1) + "%")}` : `${x.c}::text = ${q(x.v)}`);
      add(`row ${table} ${used.map((x) => `${x.c}=${x.v.replaceAll("'", "")}`).join(",")}`, `EXISTS (SELECT 1 FROM ${table} WHERE ${used.map(eq).join(" AND ")})`);
    }
  }
  return probes;
}

/** All probes of a file: [{ db, label, sql, source }], derived then declared (`config.probes[file]` = [{ db?, label, sql }], `config.databases[file]` the database of a file without `\c`, `config.declaredOnly` the files whose statements a later schema replaced (only their declared probes count)). */
export function probesOf(file, sql, config = {}) {
  const declared = config.probes ?? {};
  const dflt = defaultDatabase(file, sql, config.databases);
  const out = [];
  for (const s of config.declaredOnly?.includes(file) ? [] : sections(sql)) {
    const db = s.db ?? dflt;
    for (const p of deriveProbes(s.sql)) out.push({ db, ...p, source: "derived" });
  }
  for (const p of declared[file] ?? []) out.push({ db: p.db ?? dflt, label: p.label, sql: p.sql, source: "declared" });
  return out;
}

/** Judge probe results ({ label → boolean|undefined }): present when all hold, partial when some, missing when none, unprobed without any. */
export function judge(results) {
  if (!results.length) return "unprobed";
  const ok = results.filter((r) => r.ok).length;
  return ok === results.length ? "present" : ok === 0 ? "missing" : "partial";
}
