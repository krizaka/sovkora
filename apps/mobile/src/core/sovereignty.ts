/**
 * @file sovereignty.ts
 * @description When may the app say "0 bytes left your network"? Only when it is computed true.
 *
 * Sovkora routes each request to a provider declared in the model catalogue. A provider that runs on the operator's own
 * infrastructure (Ollama, LocalAI) keeps the prompt inside the network; a hosted inference provider does not. The
 * claim is derived from the catalogue entry of the model that answers — and silent when it is not true.
 */

/** One chat model of `/api/v1/models/catalog`. */
export interface CatalogModel {
  readonly modelName: string;
  readonly modelLabel?: string;
  readonly category: string;
  readonly providerName: string;
  readonly isDefault?: boolean;
  readonly compatible?: boolean;
}

/** Providers that run on the operator's own servers: the prompt never crosses the network boundary. */
const SELF_HOSTED_PROVIDERS: ReadonlySet<string> = new Set(["ollama", "localai", "localai-image", "localai-video"]);

/** The chat models a person can pick, default first. */
export function chatModels(catalog: readonly CatalogModel[]): CatalogModel[] {
  return catalog
    .filter((model) => model.category === "chat" && model.compatible !== false)
    .sort((a, b) => Number(b.isDefault === true) - Number(a.isDefault === true));
}

/** The model that answers: the one picked, else the catalogue's default. */
export function answeringModel(catalog: readonly CatalogModel[], picked: string | null): CatalogModel | null {
  const models = chatModels(catalog);
  return models.find((m) => m.modelName === picked) ?? models.find((m) => m.isDefault === true) ?? null;
}

/** True only when the model's provider is self-hosted — the one case where "nothing left your network" holds. */
export function staysOnNetwork(model: CatalogModel | null): boolean {
  return model !== null && SELF_HOSTED_PROVIDERS.has(model.providerName);
}
