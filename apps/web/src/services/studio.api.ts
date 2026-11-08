import { attachArtefactLabels } from "@/features/studio/utils/artefactLabel";
import { asConsentStep, type ConsentAnswer, type ErasureReceipt } from "@/features/studio/consent/consent";
import { restRequest } from "@/services/rest-client";
import {
  BlueprintVersionSchema,
  ComposerStudioSchema,
  InstallationSchema,
  PackCategorySchema,
  PackSummarySchema,
  RunSchema,
  StudioDetailSchema,
  StudioSummarySchema,
  type BlueprintVersion,
  type ComposerStudio,
  type Installation,
  type PackCategory,
  type PackSummary,
  type Run,
  type StudioDetail,
  type StudioSummary,
} from "@krizaka/orazaka-shared";

/**
 * Stateless adapter for the Studio marketplace.
 *
 * Every response is parsed rather than cast. The detail payload carries the JSON
 * Schema the run form is generated from, so a shape change must fail loudly here
 * rather than render as an empty form the user cannot submit.
 *
 * No Authorization header: the BFF proxy injects the session token server-side
 * (AGENTS.md §8 — the browser never reaches :8096, only the Next.js catch-all).
 */
export const StudioApi = {
  /**
   * The catalogue.
   *
   * Locked Studios come back too — the grid greys them and shows the upsell, because
   * a product the user cannot see is a product they cannot want (ADR-034 §4).
   *
   * @param profession - the trade to filter on, or null for everything
   * @param locale - the caller's locale; untranslated rows fall back to the base row
   */
  fetchCatalogue: async (profession: string | null, locale: string): Promise<StudioSummary[]> => {
    const query = new URLSearchParams({ locale });
    if (profession) {
      query.set("profession", profession);
    }
    const data = await restRequest<unknown[]>(`/api/v1/studios?${query.toString()}`);
    return (data ?? []).map((studio) => StudioSummarySchema.parse(studio));
  },

  /**
   * The Pack marketplace: what is on sale, on which shelf, and at what price.
   *
   * Served by the Studio context rather than by billing since ADR-036 — a Pack's name,
   * icon and bundle are catalogue data, and billing now answers only "how much". A card
   * whose `priceCents` is null is a card billing could not price; render "—", never
   * "free".
   *
   * @param categoryKey - the shelf to browse, or null for every shelf
   * @param locale - the caller's locale; untranslated packs fall back to a translation
   */
  fetchPacks: async (categoryKey: string | null, locale: string): Promise<PackSummary[]> => {
    const query = new URLSearchParams({ locale });
    if (categoryKey) {
      query.set("category", categoryKey);
    }
    const data = await restRequest<unknown[]>(`/api/v1/studios/packs?${query.toString()}`);
    return (data ?? []).map((pack) => PackSummarySchema.parse(pack));
  },

  /**
   * The marketplace shelves, already localised.
   *
   * Fetched rather than hardcoded: the shelf list used to be a closed enum in this
   * package with its French labels written into two components, so adding one was a
   * deploy that also had to invent a translation.
   *
   * @param locale - the caller's locale
   */
  fetchPackCategories: async (locale: string): Promise<PackCategory[]> => {
    const data = await restRequest<unknown[]>(`/api/v1/studios/packs/categories?locale=${encodeURIComponent(locale)}`);
    return (data ?? []).map((category) => PackCategorySchema.parse(category));
  },

  /**
   * One Studio's detail, including the schemas its forms are generated from.
   *
   * @param studioKey - the Studio's stable key
   * @param locale - the caller's locale
   */
  fetchDetail: async (studioKey: string, locale: string): Promise<StudioDetail | null> => {
    const data = await restRequest<unknown>(
      `/api/v1/studios/${encodeURIComponent(studioKey)}?locale=${encodeURIComponent(locale)}`,
    );
    return data ? StudioDetailSchema.parse(data) : null;
  },

  /**
   * A Studio's version history with changelogs.
   *
   * @param studioKey - the Studio's stable key
   */
  fetchVersions: async (studioKey: string): Promise<BlueprintVersion[]> => {
    const data = await restRequest<unknown[]>(`/api/v1/studios/${encodeURIComponent(studioKey)}/versions`);
    return (data ?? []).map((version) => BlueprintVersionSchema.parse(version));
  },

  /** Everything this user has installed — the "My Studios" tab. */
  fetchInstallations: async (): Promise<Installation[]> => {
    const data = await restRequest<unknown[]>("/api/v1/studios/installations");
    return (data ?? []).map((installation) => InstallationSchema.parse(installation));
  },

  /**
   * Installs a Studio, pinning its newest published version.
   *
   * @param studioKey - the Studio to install
   * @param config - the answers to its config schema, collected once
   * @param consent - for a wellbeing (REGULATED) Studio: the statement's version, the age attestation and the
   *   region. Without them the service answers 451, which comes back as a `ConsentRequiredError` carrying the
   *   statement to show.
   */
  install: async (
    studioKey: string,
    config: Record<string, string>,
    consent?: ConsentAnswer,
    locale?: string,
  ): Promise<Installation | null> => {
    // The locale makes the server answer a 451 with the statement in the person's language.
    const query = locale ? `?locale=${encodeURIComponent(locale)}` : "";
    const data = await restRequest<unknown>(`/api/v1/studios/${encodeURIComponent(studioKey)}/installations${query}`, {
      method: "POST",
      body: { config, ...consent },
    }).catch((error: unknown) => {
      throw asConsentStep(error);
    });
    return data ? InstallationSchema.parse(data) : null;
  },

  /**
   * Replaces an installation's configuration.
   *
   * Wholesale rather than a patch: the dialog always submits the complete form, and
   * a merge would make clearing a field unexpressible.
   *
   * @param installationId - the installation to reconfigure
   * @param config - the complete new answers
   */
  updateConfig: async (installationId: string, config: Record<string, string>): Promise<Installation | null> => {
    const data = await restRequest<unknown>(`/api/v1/studios/installations/${encodeURIComponent(installationId)}`, {
      method: "PATCH",
      body: { config },
    });
    return data ? InstallationSchema.parse(data) : null;
  },

  /**
   * Moves the pin to the newest published version.
   *
   * Never implicit: a prompt change is a product change, so the user agrees to it
   * (ADR-034 §5).
   *
   * @param installationId - the installation to upgrade
   */
  upgrade: async (installationId: string): Promise<Installation | null> => {
    const data = await restRequest<unknown>(
      `/api/v1/studios/installations/${encodeURIComponent(installationId)}/upgrade`,
      { method: "POST" },
    );
    return data ? InstallationSchema.parse(data) : null;
  },

  /**
   * Uninstalls, keeping the configuration for a future reinstall.
   *
   * @param installationId - the installation to revoke
   */
  uninstall: async (installationId: string): Promise<void> => {
    await restRequest<unknown>(`/api/v1/studios/installations/${encodeURIComponent(installationId)}`, {
      method: "DELETE",
    });
  },

  /**
   * Erases everything a wellbeing pack holds of this person: entries, inputs and outputs, files, installations and
   * the consent they recorded. Permanent and idempotent; the service leaves only a record with no content.
   *
   * @param packKey - the pack, e.g. `bien-etre`
   */
  eraseWellbeingData: async (packKey: string): Promise<ErasureReceipt | null> =>
    restRequest<ErasureReceipt>(`/api/v1/studios/packs/${encodeURIComponent(packKey)}/my-data`, { method: "DELETE" }),

  /**
   * Erases one entry (a run of a protected pack) with its inputs, outputs and files. Idempotent.
   *
   * @param runId - the entry
   */
  eraseEntry: async (runId: string): Promise<void> => {
    await restRequest<unknown>(`/api/v1/studios/runs/${encodeURIComponent(runId)}`, { method: "DELETE" });
  },

  /**
   * Starts a run.
   *
   * Answers with an id, not a result: a run is a saga of durable jobs that can
   * last minutes, and its progress arrives over the existing job stream.
   *
   * @param installationId - the installation to execute
   * @param inputs - the answers to its run form, validated again server-side
   */
  startRun: async (installationId: string, inputs: Record<string, unknown>): Promise<Run | null> => {
    const data = await restRequest<unknown>(
      `/api/v1/studios/installations/${encodeURIComponent(installationId)}/runs`,
      { method: "POST", body: { inputs } },
    ).catch((error: unknown) => {
      throw asConsentStep(error);
    });
    return data ? RunSchema.parse(data) : null;
  },

  /**
   * Starts a run of a Studio addressed by its key (ADR-061).
   *
   * The only way to run a TOOLKIT Studio: its installation is derived from entitlement
   * and has no id. A refusal arrives as one 409 shape whatever its cause — not entitled,
   * not installed — carrying the pack to open.
   *
   * @param studioKey - the Studio to run
   * @param inputs - the answers to its run form, validated again server-side
   */
  startStudioRun: async (studioKey: string, inputs: Record<string, unknown>): Promise<Run | null> => {
    const data = await restRequest<unknown>(`/api/v1/studios/${encodeURIComponent(studioKey)}/runs`, {
      method: "POST",
      body: { inputs },
    });
    return data ? RunSchema.parse(data) : null;
  },

  /**
   * The Studios that belong in the chat composer, for this actor.
   *
   * Replaces the capability row the chat bar was built from (ADR-068 §3). Locked ones
   * come back too, for the reason the catalogue keeps them: a capability the user cannot
   * see is one they cannot want.
   *
   * @param locale - the caller's locale; the label and icon come from the pack's i18n
   */
  fetchComposerStudios: async (locale: string): Promise<ComposerStudio[]> => {
    const query = new URLSearchParams({ locale });
    const data = await restRequest<unknown[]>(`/api/v1/studios/composer?${query.toString()}`);
    return (data ?? []).map((studio) => ComposerStudioSchema.parse(studio));
  },

  /** One run with its per-step states and outputs. */
  fetchRun: async (runId: string): Promise<Run | null> => {
    const data = await restRequest<unknown>(`/api/v1/studios/runs/${encodeURIComponent(runId)}`);
    return data ? attachArtefactLabels(RunSchema.parse(data), data) : null;
  },

  /** This user's run history, most recent first. */
  fetchRuns: async (): Promise<Run[]> => {
    const data = await restRequest<unknown[]>("/api/v1/studios/runs");
    return (data ?? []).map((run) => attachArtefactLabels(RunSchema.parse(run), run));
  },

  /** Resolves an approval step, resuming the run. */
  approveRun: async (runId: string): Promise<void> => {
    await restRequest<unknown>(`/api/v1/studios/runs/${encodeURIComponent(runId)}/approve`, { method: "POST" });
  },

  /** Cancels a run and releases its credit hold. */
  cancelRun: async (runId: string): Promise<void> => {
    await restRequest<unknown>(`/api/v1/studios/runs/${encodeURIComponent(runId)}/cancel`, { method: "POST" });
  },
} as const;
