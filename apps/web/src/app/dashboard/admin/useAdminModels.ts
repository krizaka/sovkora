"use client";

import * as React from "react";
import { getSession } from "next-auth/react";
import type { CatalogModel } from "@/app/dashboard/admin/CategoryCard";
import { useTranslation } from "@/core/context/LocaleContext";
import { HTTP_METHOD } from "@/core/constants/http.constants";

interface UseAdminModelsReturn {
  models: CatalogModel[];
  loadingModels: boolean;
  errorMessage: string | null;
  setErrorMessage: (msg: string | null) => void;
  providers: string[];
  features: { featureKey: string; isEnabled: boolean }[];
  loadModels: () => Promise<void>;
  handleSave: (formModel: CatalogModel, formMode: "create" | "edit") => Promise<boolean>;
  handleDelete: (id: number) => Promise<void>;
  handleToggleFeature: (featureKey: string, currentStatus: boolean) => Promise<void>;
  saving: boolean;
  fetchWithAuth: (url: string, options?: RequestInit) => Promise<Response>;
}

/**
 * Custom hook encapsulating all admin model CRUD operations, feature toggles,
 * and authenticated API calls. Extracts data-fetching logic from the admin page.
 */
export function useAdminModels(): UseAdminModelsReturn {
  const { t } = useTranslation();

  const [models, setModels] = React.useState<CatalogModel[]>([]);
  const [loadingModels, setLoadingModels] = React.useState(true);
  const [errorMessage, setErrorMessage] = React.useState<string | null>(null);
  const [providers, setProviders] = React.useState<string[]>(["ollama", "localai", "localai-video", "localai-image"]);
  const [features, setFeatures] = React.useState<{ featureKey: string; isEnabled: boolean }[]>([]);
  const [saving, setSaving] = React.useState(false);

  const fetchWithAuth = React.useCallback(async (url: string, options: RequestInit = {}) => {
    const session = await getSession();
    return fetch(url, {
      ...options,
      headers: {
        "Content-Type": "application/json",
        ...(session?.user?.id ? { Authorization: `Bearer ${session.user.id}` } : {}),
        ...options.headers,
      },
    });
  }, []);

  const loadModels = React.useCallback(async () => {
    setLoadingModels(true);
    setErrorMessage(null);
    try {
      const res = await fetchWithAuth("/api/v1/models/catalog");
      if (!res.ok) throw new Error(`Load failed: ${res.status}`);
      setModels(await res.json());

      const provRes = await fetchWithAuth("/api/v1/models/providers");
      if (provRes.ok) {
        setProviders(await provRes.json());
      }

      const featRes = await fetchWithAuth("/api/v1/features/all");
      if (featRes.ok) {
        setFeatures(await featRes.json());
      }
    } catch (err: unknown) {
      setErrorMessage(err instanceof Error ? err.message : t.admin.errorLoadingModels);
    } finally {
      setLoadingModels(false);
    }
  }, [fetchWithAuth, t.admin]);

  const handleSave = React.useCallback(
    async (formModel: CatalogModel, formMode: "create" | "edit"): Promise<boolean> => {
      if (!formModel.modelName || !formModel.modelLabel) {
        setErrorMessage(t.admin.nameAndLabelRequired);
        return false;
      }
      setSaving(true);
      setErrorMessage(null);
      try {
        const url = formMode === "edit" ? `/api/v1/models/${formModel.id}` : "/api/v1/models";
        const res = await fetchWithAuth(url, {
          method: formMode === "edit" ? HTTP_METHOD.PUT : "POST",
          body: JSON.stringify(formModel),
        });
        if (!res.ok) {
          const errorData = await res.json().catch(() => ({}));
          throw new Error(errorData.error || `Save failed: ${res.status}`);
        }
        await loadModels();
        return true;
      } catch (err: unknown) {
        setErrorMessage(err instanceof Error ? err.message : t.admin.errorSavingModel);
        return false;
      } finally {
        setSaving(false);
      }
    },
    [fetchWithAuth, loadModels, t.admin],
  );

  const handleToggleFeature = React.useCallback(
    async (featureKey: string, currentStatus: boolean) => {
      try {
        const res = await fetchWithAuth(`/api/v1/features/${encodeURIComponent(featureKey)}`, {
          method: HTTP_METHOD.PUT,
          body: JSON.stringify({ featureKey, isEnabled: !currentStatus }),
        });
        if (!res.ok) throw new Error(t.errors.featureToggleFailed);
        await loadModels();
      } catch (err: unknown) {
        setErrorMessage(err instanceof Error ? err.message : t.errors.featureToggleFailed);
      }
    },
    [fetchWithAuth, loadModels, t.errors],
  );

  const handleDelete = React.useCallback(
    async (id: number) => {
      if (!confirm(t.admin.confirmDelete)) return;
      setLoadingModels(true);
      setErrorMessage(null);
      try {
        const res = await fetchWithAuth(`/api/v1/models/${id}`, {
          method: "DELETE",
        });
        if (!res.ok) {
          const errorData = await res.json().catch(() => ({}));
          throw new Error(errorData.error || `Delete failed: ${res.status}`);
        }
        await loadModels();
      } catch (err: unknown) {
        setErrorMessage(err instanceof Error ? err.message : t.admin.errorDeletingModel);
        setLoadingModels(false);
      }
    },
    [fetchWithAuth, loadModels, t.admin],
  );

  return {
    models,
    loadingModels,
    errorMessage,
    setErrorMessage,
    providers,
    features,
    loadModels,
    handleSave,
    handleDelete,
    handleToggleFeature,
    saving,
    fetchWithAuth,
  };
}
