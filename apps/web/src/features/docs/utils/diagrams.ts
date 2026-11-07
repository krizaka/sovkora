/**
 * @file diagrams.ts
 * @description The schema blocks a doc writes as fenced code — `architecture`, `pipeline`, `sequence`, `states` and the
 * CLI's `cli-*` — parsed and read against the generated model (`content/docs/model.json`, a slice of Sovkora's
 * `architecture.json`). Until the generic diagram components exist in @krizaka/ui, each block is shown as its accessible
 * text version (tables, ordered lists): the content is never dropped. A block that does not parse, or that names a module
 * the model does not have, throws with the line at fault — a page that cannot be right does not build.
 */
import fs from "node:fs";
import path from "node:path";

export interface ModelModule {
  id: string;
  repository: string;
  owner: string;
  role: string;
  layer: string;
  runtime: string | null;
  description: string;
}
export interface DocsModel {
  roles: { id: string; title: string; rule: string }[];
  modules: ModelModule[];
  dependencies: { from: string; to: string }[];
  flows: { from: string; to: string; kind: string; via?: string; exchange?: string; routingKey?: string }[];
  pipeline: {
    interceptor: string;
    order: number;
    enabled: boolean;
    phase: string;
    implemented: boolean;
    concern: string;
    summary: string;
  }[];
  coreChain: { interceptor: string; rank: number; concern: string; summary: string }[];
  cli: {
    alias: string;
    journey: {
      command: string;
      description: string;
      phases?: { id: string; title: string; detail: string }[];
      processes?: { key: string; label: string; port: number | null }[];
    }[];
  };
}

let cached: DocsModel | null = null;
export function loadModel(): DocsModel {
  cached ??= JSON.parse(
    fs.readFileSync(path.join(process.cwd(), "content", "docs", "model.json"), "utf8"),
  ) as DocsModel;
  return cached;
}

export class DiagramError extends Error {
  constructor(kind: string, line: number | null, message: string) {
    super(`${kind} block${line ? `, line ${line}` : ""}: ${message}`);
  }
}

const lines = (source: string) =>
  source
    .split("\n")
    .map((text, i) => ({ text: text.trim(), n: i + 1 }))
    .filter((l) => l.text && !l.text.startsWith("%%"));

// ── architecture ─────────────────────────────────────────────────────────────────────────────────

export interface ArchitectureBlock {
  product: string;
  focus: string | null;
  mode: "build" | "runtime" | "journey" | null;
  journey: string | null;
  owner: string | null;
}

export function parseArchitecture(source: string, model: DocsModel): ArchitectureBlock {
  const fields: Record<string, string> = {};
  for (const { text, n } of lines(source)) {
    const m = /^(\w+)\s*:\s*(.+)$/.exec(text);
    if (!m || !["product", "focus", "mode", "journey", "owner"].includes(m[1]))
      throw new DiagramError("architecture", n, `expected product|focus|mode|journey|owner: value, got "${text}"`);
    fields[m[1]] = m[2].trim();
  }
  if ((fields.product ?? "orazaka") !== "orazaka")
    throw new DiagramError("architecture", null, `unknown product ${fields.product}`);
  if (fields.mode && !["build", "runtime", "journey"].includes(fields.mode))
    throw new DiagramError("architecture", null, `mode is build, runtime or journey, not ${fields.mode}`);
  if (fields.focus && !model.modules.some((m) => m.id === fields.focus))
    throw new DiagramError("architecture", null, `${fields.focus} is not a module of the generated model`);
  return {
    product: "orazaka",
    focus: fields.focus ?? null,
    mode: (fields.mode ?? null) as ArchitectureBlock["mode"],
    journey: fields.journey ?? null,
    owner: fields.owner ?? null,
  };
}

// ── sequence ─────────────────────────────────────────────────────────────────────────────────────

export interface SequenceParticipant {
  key: string;
  label: string;
  /** The module of the generated model this participant is (`@id`). */
  module: ModelModule | null;
}
export type SequenceItem =
  | { type: "section"; title: string }
  | {
      type: "step";
      n: number;
      from: string;
      to: string;
      text: string;
      reply: boolean;
      branch: { kind: "if" | "else"; condition: string } | null;
    };
export interface SequenceBlock {
  participants: SequenceParticipant[];
  items: SequenceItem[];
}

export function parseSequence(source: string, model: DocsModel): SequenceBlock {
  const participants: SequenceParticipant[] = [];
  const items: SequenceItem[] = [];
  let branch: { kind: "if" | "else"; condition: string } | null = null;
  let n = 0;
  const known = (key: string, line: number) => {
    if (!participants.some((p) => p.key === key))
      throw new DiagramError("sequence", line, `unknown participant ${key} — declare it with "participant ${key}: …"`);
  };
  for (const { text, n: line } of lines(source)) {
    let m: RegExpExecArray | null;
    if ((m = /^participant\s+([\w-]+)\s*:\s*(.+)$/.exec(text))) {
      const value = m[2].trim();
      const id = value.startsWith("@") ? value.slice(1) : null;
      const mod = id ? (model.modules.find((x) => x.id === id) ?? null) : null;
      if (id && !mod) throw new DiagramError("sequence", line, `@${id} is not a module of the generated model`);
      participants.push({ key: m[1], label: id ?? value, module: mod });
    } else if ((m = /^##\s+(.+)$/.exec(text))) {
      items.push({ type: "section", title: m[1].trim() });
    } else if ((m = /^if\s+(.+)$/.exec(text))) {
      branch = { kind: "if", condition: m[1].trim() };
    } else if ((m = /^else(?:\s+(.+))?$/.exec(text))) {
      if (!branch) throw new DiagramError("sequence", line, "else without if");
      branch = { kind: "else", condition: (m[1] ?? "").trim() };
    } else if (text === "end") {
      if (!branch) throw new DiagramError("sequence", line, "end without if");
      branch = null;
    } else if ((m = /^([\w-]+)\s*(-->|->)\s*([\w-]+)\s*:\s*(.+)$/.exec(text))) {
      known(m[1], line);
      known(m[3], line);
      items.push({ type: "step", n: ++n, from: m[1], to: m[3], text: m[4].trim(), reply: m[2] === "-->", branch });
    } else {
      throw new DiagramError("sequence", line, `cannot read "${text}"`);
    }
  }
  if (branch) throw new DiagramError("sequence", null, "an if is never closed with end");
  if (!n) throw new DiagramError("sequence", null, "no step");
  return { participants, items };
}

// ── states ───────────────────────────────────────────────────────────────────────────────────────

export interface StateBlock {
  states: { id: string; label: string | null; initial: boolean; final: boolean }[];
  transitions: { from: string; to: string; label: string }[];
}

/** The `state` and `a -> b` lines of a states block. */
function readStateLines(source: string): Pick<StateBlock, "transitions"> & { labels: Map<string, string> } {
  const labels = new Map<string, string>();
  const transitions: StateBlock["transitions"] = [];
  for (const { text, n } of lines(source)) {
    if (/^enum\s*:/.test(text)) continue;
    const state = /^state\s+([\w-]+)\s*:(.+)$/.exec(text);
    if (state) {
      labels.set(state[1], state[2].trim());
      continue;
    }
    const move = /^([\w-]+)\s*->\s*([\w-]+)\s*:(.+)$/.exec(text);
    if (!move) throw new DiagramError("states", n, `cannot read "${text}"`);
    transitions.push({ from: move[1], to: move[2], label: move[3].trim() });
  }
  return { labels, transitions };
}

export function parseStates(source: string): StateBlock {
  const { labels, transitions } = readStateLines(source);
  if (!transitions.some((t) => t.from === "start")) throw new DiagramError("states", null, "no transition from start");
  const order: string[] = [];
  const queue = ["start"];
  while (queue.length) {
    const at = queue.shift()!;
    for (const t of transitions.filter((x) => x.from === at)) {
      if (t.to !== "end" && !order.includes(t.to)) {
        order.push(t.to);
        queue.push(t.to);
      }
    }
  }
  const unreachable = [...new Set(transitions.flatMap((t) => [t.from, t.to]))].filter(
    (id) => id !== "start" && id !== "end" && !order.includes(id),
  );
  if (unreachable.length) throw new DiagramError("states", null, `unreachable from start: ${unreachable.join(", ")}`);
  return {
    states: order.map((id) => ({
      id,
      label: labels.get(id) ?? null,
      initial: transitions.some((t) => t.from === "start" && t.to === id),
      final: !transitions.some((t) => t.from === id && t.to !== id && t.to !== "end"),
    })),
    transitions,
  };
}

// ── what a module touches ────────────────────────────────────────────────────────────────────────

/** The modules `id` depends on and the modules that depend on it, from the generated model. */
export function dependenciesOf(model: DocsModel, id: string) {
  return {
    uses: model.dependencies.filter((d) => d.from === id).map((d) => d.to),
    usedBy: model.dependencies.filter((d) => d.to === id).map((d) => d.from),
  };
}

/** The runtime calls (requests, events) that leave and reach `id`. */
export function flowsOf(model: DocsModel, id: string) {
  return {
    calls: model.flows.filter((f) => f.from === id),
    calledBy: model.flows.filter((f) => f.to === id),
  };
}
