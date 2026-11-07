/** Validation-pipeline tier metadata + theme-class maps. */
export interface ValidationTierConfig {
  id: string;
  stepType: string;
  enabled: boolean;
  executionOrder: number;
  configurationPayload: Record<string, unknown>;
}

/** Tier display metadata. */
export const TIER_META: Record<string, { label: string; badge: string; color: string; description: string }> = {
  STRUCTURAL_A: {
    label: "Tier A — Deterministic JSON Schema",
    badge: "A",
    color: "emerald",
    description: "Zero-token structural JSON validation via Jackson ObjectMapper.",
  },
  SANDBOX_B: {
    label: "Tier B — MCP Sandbox Crash-Test",
    badge: "B",
    color: "sky",
    description: "Code block extraction and isolated MCP sandbox compilation.",
  },
  SEMANTIC_C: {
    label: "Tier C — Semantic Consensus Debate",
    badge: "C",
    color: "violet",
    description: "Critic vs Advocate debate at temperature 0.0 for semantic alignment.",
  },
  TDR_D: {
    label: "Tier D — Test-Driven Response (TDR)",
    badge: "D",
    color: "amber",
    description: "Pre-generates assertion schemas via fast reasoning model and validates responses.",
  },
};

export const COLOR_CLASSES: Record<string, { badge: string; ring: string; glow: string }> = {
  emerald: {
    badge: "bg-success/10 text-success border-success/20",
    ring: "ring-success/30",
    glow: "hover:shadow-success/10",
  },
  sky: {
    badge: "bg-accent/10 text-accent border-accent/20",
    ring: "ring-accent/30",
    glow: "hover:shadow-accent/10",
  },
  violet: {
    badge: "bg-accent/10 text-accent border-accent/20",
    ring: "ring-accent/30",
    glow: "hover:shadow-accent/10",
  },
  amber: {
    badge: "bg-warning/10 text-warning border-warning/20",
    ring: "ring-warning/30",
    glow: "hover:shadow-warning/10",
  },
};
