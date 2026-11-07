import * as React from "react";

import { cn } from "@krizaka/ui/cn";

/* ─── Inline SVG provider logos ───────────────────────────────────────────
   Rendered tonally via `currentColor` so they inherit theme text/accent
   colors and read correctly across every theme (no hardcoded brand hex). */

const GeminiIcon = () => (
  <svg viewBox="0 0 24 24" className="h-full w-full" fill="currentColor">
    <path d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z" />
  </svg>
);

const ClaudeIcon = () => (
  <svg viewBox="0 0 24 24" className="h-full w-full" fill="currentColor">
    <circle cx="12" cy="12" r="9" opacity="0.15" />
    <circle cx="12" cy="12" r="4.5" />
  </svg>
);

const OpenAIIcon = () => (
  <svg viewBox="0 0 24 24" className="h-full w-full" fill="none" stroke="currentColor" strokeWidth="2">
    <circle cx="12" cy="12" r="9" />
    <circle cx="12" cy="12" r="3" />
    <line x1="12" y1="3" x2="12" y2="9" />
    <line x1="12" y1="15" x2="12" y2="21" />
  </svg>
);

const MistralIcon = () => (
  <svg viewBox="0 0 24 24" className="h-full w-full" fill="currentColor">
    <path d="M12 3L21 18H3L12 3Z" opacity="0.2" />
    <path d="M12 6L18.5 17H5.5L12 6Z" />
  </svg>
);

const GroqIcon = () => (
  <svg viewBox="0 0 24 24" className="h-full w-full" fill="currentColor">
    <path d="M13 2L3 14h7l-1 8l10-12h-7l1-8z" />
  </svg>
);

const OllamaIcon = () => (
  <svg viewBox="0 0 24 24" className="h-full w-full" fill="currentColor">
    <path d="M12 4C8 4 5 7 5 10c0 2 1 3 2 4l-1 5h12l-1-5c1-1 2-2 2-4 0-3-3-6-7-6z" opacity="0.2" />
    <circle cx="9.5" cy="10" r="1.5" />
    <circle cx="14.5" cy="10" r="1.5" />
    <path d="M9 14c0 0 1.5 2 3 2s3-2 3-2" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
  </svg>
);

/* ─── Provider catalogue ─────────────────────────────────────────────────── */

/** Static description of a connectable AI provider. */
export interface ProviderConfig {
  /** Card id; mapped to the backend provider name in the hook. */
  id: string;
  /** Human-readable provider name. */
  name: string;
  /** Brand glyph (rendered in `currentColor`). */
  icon: React.ReactNode;
  /** Example API-key shape shown as the input placeholder. */
  placeholder: string;
}

export const PROVIDERS: ProviderConfig[] = [
  { id: "gemini", name: "Google Gemini", icon: <GeminiIcon />, placeholder: "AIza..." },
  { id: "claude", name: "Anthropic Claude", icon: <ClaudeIcon />, placeholder: "sk-ant-..." },
  { id: "openai", name: "OpenAI", icon: <OpenAIIcon />, placeholder: "sk-..." },
  { id: "mistral", name: "Mistral AI", icon: <MistralIcon />, placeholder: "..." },
  { id: "groq", name: "Groq", icon: <GroqIcon />, placeholder: "gsk_..." },
  { id: "ollama", name: "Ollama (Local)", icon: <OllamaIcon />, placeholder: "http://localhost:11434" },
];

/** Lookup a provider by id. */
export const findProvider = (id: string): ProviderConfig | undefined =>
  PROVIDERS.find((provider) => provider.id === id);

/** Small rounded brand-glyph badge, tinted via `currentColor`. */
export function ProviderGlyph({ provider, active = false }: Readonly<{ provider: ProviderConfig; active?: boolean }>) {
  return (
    <span
      className={cn(
        "inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-md border p-2 transition-colors",
        active ? "border-accent bg-accent-soft text-accent" : "border-border-subtle bg-surface-2 text-fg-secondary",
      )}
    >
      {provider.icon}
    </span>
  );
}
