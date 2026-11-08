"use client";

import { useMemo } from "react";

/** One field the install dialog renders, derived from the Studio's config schema. */
export interface ConfigField {
  key: string;
  title: string;
  options: string[] | null;
  defaultValue: string;
}

/**
 * Turns a Studio's raw config schema into the fields the install dialog renders.
 *
 * Generated rather than hand-written per Studio: a bespoke form per profession would
 * make every new Studio a frontend release, which is the deploy-per-Studio failure
 * ADR-034 exists to remove — and it would reappear here if the dialog were static.
 *
 * A malformed schema yields no fields rather than throwing: the install still works
 * (the server validates authoritatively), it just collects nothing, which is a far
 * better failure than a screen that cannot render.
 *
 * @param configSchema - the raw JSON Schema string, or null when the Studio has none
 */
export function useConfigSchema(configSchema: string | null): ConfigField[] {
  return useMemo(() => {
    if (!configSchema) {
      return [];
    }
    try {
      const parsed = JSON.parse(configSchema) as Record<string, unknown>;
      // Two shapes are served: a JSON Schema object (`{ type: "object", properties }`, what the Studio service
      // answers) and a flat map of fields. Reading the first as the second asked « type » and « properties ».
      const fields =
        parsed.type === "object" && typeof parsed.properties === "object" && parsed.properties !== null
          ? (parsed.properties as Record<string, unknown>)
          : parsed;
      return Object.entries(fields).map(([key, raw]) => {
        const spec = (raw ?? {}) as { title?: string; enum?: string[]; default?: string };
        return {
          key,
          title: spec.title ?? key,
          options: Array.isArray(spec.enum) ? spec.enum : null,
          defaultValue: spec.default ?? "",
        };
      });
    } catch {
      return [];
    }
  }, [configSchema]);
}
