import type { PackData, PackIndexItem } from "@/features/docs/utils/packs";
import type { DocsModel, ModelModule, SequenceBlock, StateBlock } from "@/features/docs/utils/diagrams";

/** What the client view draws: the parsed block and just the slice of the model it needs. */
export type DiagramData =
  | {
      kind: "sequence";
      block: Omit<SequenceBlock, "participants"> & { participants: { key: string; label: string }[] };
    }
  | { kind: "pipeline"; coreChain: DocsModel["coreChain"]; pipeline: DocsModel["pipeline"] }
  | { kind: "states"; block: StateBlock }
  | {
      kind: "architecture";
      focus: ModelModule | null;
      runtime: boolean;
      uses: string[];
      usedBy: string[];
      calls: string[];
      calledBy: string[];
    }
  | { kind: "cli-replay"; code: string }
  | { kind: "cli-journey"; alias: string; journey: DocsModel["cli"]["journey"] }
  | { kind: "cli-commands"; text: string }
  | { kind: "pack-index"; items: PackIndexItem[] }
  | { kind: "pack"; pack: PackData };
