import type { DiagramLang } from "@/features/docs/utils/docs";
import type { DiagramData } from "@/features/docs/utils/diagram-data";
import {
  dependenciesOf,
  flowsOf,
  loadModel,
  parseArchitecture,
  parseSequence,
  parseStates,
  type DocsModel,
} from "@/features/docs/utils/diagrams";
import { parsePack, parsePackIndex } from "@/features/docs/utils/packs";
import { DiagramView } from "@/features/docs/components/DiagramView";

/** Parses a schema block against the generated model (a block that cannot be right fails the build) and hands its text version to the view. */
export function DocDiagram({ lang, source }: Readonly<{ lang: DiagramLang; source: string }>) {
  return <DiagramView data={diagramData(lang, source, loadModel())} />;
}

export function diagramData(lang: DiagramLang, source: string, model: DocsModel): DiagramData {
  switch (lang) {
    case "sequence": {
      const b = parseSequence(source, model);
      return {
        kind: "sequence",
        block: { ...b, participants: b.participants.map((p) => ({ key: p.key, label: p.label })) },
      };
    }
    case "pipeline":
      return { kind: "pipeline", coreChain: model.coreChain, pipeline: model.pipeline };
    case "states":
      return { kind: "states", block: parseStates(source) };
    case "architecture": {
      const block = parseArchitecture(source, model);
      const focus = block.focus ? (model.modules.find((m) => m.id === block.focus) ?? null) : null;
      const none = { uses: [] as string[], usedBy: [] as string[], calls: [] as string[], calledBy: [] as string[] };
      if (!focus) return { kind: "architecture", focus, runtime: false, ...none };
      const { uses, usedBy } = dependenciesOf(model, focus.id);
      const { calls, calledBy } = flowsOf(model, focus.id);
      const tag = (f: DocsModel["flows"][number], key: "to" | "from") => {
        const how = f.via ? `${f.kind}: ${f.via}` : f.kind;
        return `${f[key]} (${how})`;
      };
      return {
        kind: "architecture",
        focus,
        runtime: block.mode === "runtime",
        uses,
        usedBy,
        calls: [...new Set(calls.map((f) => tag(f, "to")))],
        calledBy: [...new Set(calledBy.map((f) => tag(f, "from")))],
      };
    }
    case "cli-replay":
      return { kind: "cli-replay", code: source.trim() };
    case "cli-journey":
      return { kind: "cli-journey", alias: model.cli.alias, journey: model.cli.journey };
    case "pack-index":
      return { kind: "pack-index", items: parsePackIndex(source) };
    case "pack":
      return { kind: "pack", pack: parsePack(source) };
    case "cli-commands":
      return { kind: "cli-commands", text: source.trim() };
  }
}
