"use client";

import * as React from "react";
import { useQuery, useQueryClient } from "@krizaka/query";
import { PROVIDERS } from "@/features/profile/components/AiProviderLogos";

/** Card provider ids map to the backend provider names used by the credentials API. */
const CREDENTIALS_KEY = ["credentials"] as const;

const toBackendName = (id: string): string => (id === "claude" ? "anthropic" : id);
const fromBackendName = (name: string): string => (name === "anthropic" ? "claude" : name);

/** Add/edit modal state. `mode` drives whether a provider is pickable or fixed. */
export interface ProviderModalState {
  mode: "closed" | "add" | "edit";
  providerId: string | null;
}

const CLOSED: ProviderModalState = { mode: "closed", providerId: null };

interface UseAiProviders {
  isLoading: boolean;
  /** Provider ids with a stored credential, in catalogue order. */
  configuredIds: string[];
  /** Provider ids that can still be added (none stored yet). */
  availableIds: string[];
  modal: ProviderModalState;
  draftKey: string;
  isRevealed: boolean;
  isSaving: boolean;
  error: boolean;
  openAdd: () => void;
  openEdit: (providerId: string) => void;
  selectProvider: (providerId: string) => void;
  closeModal: () => void;
  setDraftKey: (value: string) => void;
  toggleReveal: () => void;
  submit: () => Promise<void>;
  remove: (providerId: string) => Promise<void>;
}

/**
 * Owns all credential data + the add/edit modal lifecycle for the AI providers
 * section. The credentials API stores a single encrypted key per provider and
 * never returns it, so the list tracks configured ids only and edit means
 * "replace the key".
 *
 * @param fetchHeaders - Async factory for authenticated request headers.
 */
export function useAiProviders(fetchHeaders: () => Promise<Record<string, string>>): UseAiProviders {
  const [modal, setModal] = React.useState<ProviderModalState>(CLOSED);
  const [draftKey, setDraftKey] = React.useState("");
  const [isRevealed, setIsRevealed] = React.useState(false);
  const [isSaving, setIsSaving] = React.useState(false);
  const [error, setError] = React.useState(false);

  const queryClient = useQueryClient();
  // Credentials are optional: a failed call leaves nothing configured.
  const credentials = useQuery({
    queryKey: CREDENTIALS_KEY,
    queryFn: async () => {
      const res = await fetch("/api/v1/credentials", { headers: await fetchHeaders() });
      if (!res.ok) throw new Error(`credentials ${res.status}`);
      const data: { providerName: string; configured: boolean }[] = await res.json();
      return data
        .filter((credential) => credential.configured)
        .map((credential) => fromBackendName(credential.providerName));
    },
  });
  const configured = new Set(credentials.data ?? []);
  const isLoading = credentials.isPending;
  const setConfigured = (update: (prev: Set<string>) => Set<string>) =>
    queryClient.setQueryData<string[]>(CREDENTIALS_KEY, (prev) => [...update(new Set(prev ?? []))]);

  const configuredIds = PROVIDERS.map((p) => p.id).filter((id) => configured.has(id));
  const availableIds = PROVIDERS.map((p) => p.id).filter((id) => !configured.has(id));

  const resetDraft = () => {
    setDraftKey("");
    setIsRevealed(false);
    setError(false);
  };

  const openAdd = () => {
    resetDraft();
    setModal({ mode: "add", providerId: availableIds[0] ?? null });
  };

  const openEdit = (providerId: string) => {
    resetDraft();
    setModal({ mode: "edit", providerId });
  };

  const selectProvider = (providerId: string) => setModal((prev) => ({ ...prev, providerId }));

  const closeModal = () => {
    setModal(CLOSED);
    resetDraft();
  };

  const submit = async () => {
    const providerId = modal.providerId;
    if (!providerId || !draftKey.trim()) return;
    setIsSaving(true);
    setError(false);
    try {
      const res = await fetch("/api/v1/credentials", {
        method: "POST",
        headers: await fetchHeaders(),
        body: JSON.stringify({
          providerName: toBackendName(providerId),
          apiKey: draftKey.trim(),
        }),
      });
      if (!res.ok) {
        setError(true);
        return;
      }
      setConfigured((prev) => new Set(prev).add(providerId));
      closeModal();
    } catch {
      setError(true);
    } finally {
      setIsSaving(false);
    }
  };

  const remove = async (providerId: string) => {
    setConfigured((prev) => {
      const next = new Set(prev);
      next.delete(providerId);
      return next;
    });
    if (modal.providerId === providerId) closeModal();
    try {
      await fetch(`/api/v1/credentials/${toBackendName(providerId)}`, {
        method: "DELETE",
        headers: await fetchHeaders(),
      });
    } catch {
      // Best-effort delete — the row is already gone from the UI.
    }
  };

  return {
    isLoading,
    configuredIds,
    availableIds,
    modal,
    draftKey,
    isRevealed,
    isSaving,
    error,
    openAdd,
    openEdit,
    selectProvider,
    closeModal,
    setDraftKey,
    toggleReveal: () => setIsRevealed((value) => !value),
    submit,
    remove,
  };
}
