import type { Capability, ModelEntry } from "@/console/services/platform.schemas";

export interface ProviderSummary {
  provider: string;
  models: number;
  categories: string[];
}

/** The providers with how many models and which categories each carries; models without a provider are "unassigned". */
export function providerSummaries(models: readonly ModelEntry[], providers: readonly string[]): ProviderSummary[] {
  const byProvider = new Map<string, ModelEntry[]>(providers.map((name) => [name, []]));
  for (const model of models) {
    const key = model.providerName ?? "unassigned";
    byProvider.set(key, [...(byProvider.get(key) ?? []), model]);
  }
  return [...byProvider.entries()]
    .map(([provider, list]) => ({
      provider,
      models: list.length,
      categories: [...new Set(list.map((m) => m.category))].sort((x, y) => x.localeCompare(y)),
    }))
    .sort((a, b) => b.models - a.models || a.provider.localeCompare(b.provider));
}

/** A copy of the list with one capability's flag set (the optimistic update; the server's answer replaces it). */
export function withCapability(list: readonly Capability[], featureKey: string, enabled: boolean): Capability[] {
  return list.map((c) => (c.featureKey === featureKey ? { ...c, enabled } : c));
}
