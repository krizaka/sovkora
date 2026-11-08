"use client";

import * as React from "react";
import { useQuery, useQueryClient } from "@krizaka/query";

/** Listing metadata for an inbound API key (never carries the plaintext secret). */
export interface ApiKeyInfo {
  id: string;
  name: string;
  keyPrefix: string;
  createdAt: string;
  lastUsedAt: string | null;
}

/** One-time payload returned when a key is created. */
export interface GeneratedKey {
  name: string;
  key: string;
}

/** Per-user cap, mirrored from the backend {@code ApiKeyService.MAX_KEYS_PER_USER}. */
export const MAX_API_KEYS = 10;

type CreateError = "none" | "limit" | "generic";

interface UseApiKeys {
  isLoading: boolean;
  keys: ApiKeyInfo[];
  canCreate: boolean;
  isCreateOpen: boolean;
  draftName: string;
  isSaving: boolean;
  error: CreateError;
  generated: GeneratedKey | null;
  openCreate: () => void;
  closeCreate: () => void;
  setDraftName: (value: string) => void;
  submit: () => Promise<void>;
  dismissGenerated: () => void;
  remove: (id: string) => Promise<void>;
}

const API_KEYS_KEY = ["api-keys"] as const;

/**
 * Owns the inbound API-key list plus the create/reveal lifecycle for
 * {@link ApiKeysSection}. The API returns the plaintext secret exactly once (on
 * create), so it is surfaced through {@link UseApiKeys.generated} and never
 * refetched — the list itself only ever tracks metadata.
 *
 * @param fetchHeaders - Async factory for authenticated request headers.
 */
export function useApiKeys(fetchHeaders: () => Promise<Record<string, string>>): UseApiKeys {
  const [isCreateOpen, setCreateOpen] = React.useState(false);
  const [draftName, setDraftName] = React.useState("");
  const [isSaving, setIsSaving] = React.useState(false);
  const [error, setError] = React.useState<CreateError>("none");
  const [generated, setGenerated] = React.useState<GeneratedKey | null>(null);

  const queryClient = useQueryClient();
  // Keys are optional: a failed call leaves the list empty.
  const list = useQuery({
    queryKey: API_KEYS_KEY,
    queryFn: async () => {
      const res = await fetch("/api/v1/api-keys", { headers: await fetchHeaders() });
      if (!res.ok) throw new Error(`api-keys ${res.status}`);
      return (await res.json()) as ApiKeyInfo[];
    },
  });
  const keys = list.data ?? [];
  const isLoading = list.isPending;
  const load = () => queryClient.invalidateQueries({ queryKey: API_KEYS_KEY });

  const canCreate = keys.length < MAX_API_KEYS;

  const openCreate = () => {
    setDraftName("");
    setError("none");
    setCreateOpen(true);
  };

  const closeCreate = () => {
    setCreateOpen(false);
    setDraftName("");
    setError("none");
  };

  const submit = async () => {
    const name = draftName.trim();
    if (!name) return;
    setIsSaving(true);
    setError("none");
    try {
      const res = await fetch("/api/v1/api-keys", {
        method: "POST",
        headers: await fetchHeaders(),
        body: JSON.stringify({ name }),
      });
      if (res.status === 409) {
        setError("limit");
        return;
      }
      if (!res.ok) {
        setError("generic");
        return;
      }
      const created = (await res.json()) as GeneratedKey;
      setCreateOpen(false);
      setDraftName("");
      setGenerated({ name: created.name, key: created.key });
      await load();
    } catch {
      setError("generic");
    } finally {
      setIsSaving(false);
    }
  };

  const dismissGenerated = () => setGenerated(null);

  const remove = async (id: string) => {
    queryClient.setQueryData<ApiKeyInfo[]>(API_KEYS_KEY, (prev) => (prev ?? []).filter((key) => key.id !== id));
    try {
      await fetch(`/api/v1/api-keys/${id}`, {
        method: "DELETE",
        headers: await fetchHeaders(),
      });
    } catch {
      // Best-effort delete — the row is already gone from the UI.
    }
  };

  return {
    isLoading,
    keys,
    canCreate,
    isCreateOpen,
    draftName,
    isSaving,
    error,
    generated,
    openCreate,
    closeCreate,
    setDraftName,
    submit,
    dismissGenerated,
    remove,
  };
}
