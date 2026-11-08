"use client";

import * as React from "react";
import { useQuery } from "@krizaka/query";
import { useRouter } from "next/navigation";
import { useAuth } from "@/core/hooks/useAuth";
import { AppShell } from "@/components/layout/AppShell";
import { useTranslation } from "@/core/context/LocaleContext";
import type { CatalogModel } from "./CategoryCard";
import { CategoryCard } from "./CategoryCard";
import { ModelDialog } from "./ModelDialog";
import { Stat, StatGrid } from "@krizaka/ui/stat";
import { PipelineConfigCard } from "./PipelineConfigCard";
import { ValidationPipelineCard } from "./ValidationPipelineCard";
import { AdminToolbar } from "./AdminToolbar";
import { FeatureToggleCard } from "./FeatureToggleCard";
import { useThreadManagement } from "@/features/chat-session/hooks/useThreadManagement";
import { useAdminModels } from "./useAdminModels";
import { CATEGORIES, MODEL_CATEGORY } from "@/core/constants/capability.constants";

export default function AdminDashboardPage() {
  const router = useRouter();
  const { isAuthenticated, isLoading, user } = useAuth();
  const { t } = useTranslation();
  const { threads } = useThreadManagement();
  const userId = user?.id || user?.email || "anonymous";

  const {
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
  } = useAdminModels();

  // Live connections, read every 5 s while an admin is signed in.
  const connections = useQuery({
    queryKey: ["jobs-active-connections"],
    enabled: isAuthenticated && user?.role === "admin",
    refetchInterval: 5000,
    queryFn: async () => {
      const res = await fetchWithAuth("/api/v1/jobs/active-connections");
      if (!res.ok) throw new Error(`active connections ${res.status}`);
      return (await res.json()) as number;
    },
  });
  const activeSessions = connections.data ?? 0;

  const derivedMetrics = React.useMemo(() => {
    if (globalThis.window === undefined || !isAuthenticated) {
      return { tokensUsed: 0, memoryNodes: 0 };
    }

    let totalMessagesCount = 0;
    let totalCharacters = 0;

    if (threads && Array.isArray(threads)) {
      threads.forEach((thread: { conversationId: string }) => {
        const storedMessagesStr = localStorage.getItem(`orazaka_messages_${userId}_${thread.conversationId}`);
        if (storedMessagesStr) {
          try {
            const messages = JSON.parse(storedMessagesStr);
            if (Array.isArray(messages)) {
              totalMessagesCount += messages.length;
              messages.forEach((msg: { content?: string }) => {
                if (msg.content) {
                  totalCharacters += msg.content.length;
                }
              });
            }
          } catch {
            // Silently handle parse failures for corrupted localStorage entries
          }
        }
      });
    }

    return {
      tokensUsed: Math.round(totalCharacters / 4),
      memoryNodes: totalMessagesCount,
    };
  }, [isAuthenticated, threads, userId]);

  const metrics = React.useMemo(() => ({ activeSessions, ...derivedMetrics }), [activeSessions, derivedMetrics]);

  const [isModalOpen, setIsModalOpen] = React.useState(false);
  const [formModel, setFormModel] = React.useState<CatalogModel>({
    modelName: "",
    modelLabel: "",
    category: MODEL_CATEGORY.SPEECH,
    options: "",
    isDefault: false,
  });
  const [formMode, setFormMode] = React.useState<"create" | "edit">("create");

  React.useEffect(() => {
    if (!isLoading) {
      if (!isAuthenticated) router.push("/login");
      else if (user?.role === "admin") void loadModels();
      else router.push("/");
    }
  }, [isLoading, isAuthenticated, user, router, loadModels]);

  const openModal = (mode: "create" | "edit", model?: CatalogModel) => {
    setFormModel(
      model
        ? {
            id: model.id,
            modelName: model.modelName,
            modelLabel: model.modelLabel,
            category: model.category,
            options: model.options || "",
            isDefault: !!model.isDefault,
            providerName: model.providerName || "ollama",
          }
        : {
            modelName: "",
            modelLabel: "",
            category: MODEL_CATEGORY.SPEECH,
            options: "",
            isDefault: false,
            providerName: "ollama",
          },
    );
    setFormMode(mode);
    setErrorMessage(null);
    setIsModalOpen(true);
  };

  const onSubmit = async (e: React.SubmitEvent<HTMLFormElement>) => {
    e.preventDefault();
    const success = await handleSave(formModel, formMode);
    if (success) setIsModalOpen(false);
  };

  if (isLoading || !isAuthenticated || user?.role !== "admin") {
    return (
      <section className="flex min-h-screen items-center justify-center bg-surface-0">
        <span className="text-fg-muted text-sm animate-pulse">{t.admin.loadingCredentials}</span>
      </section>
    );
  }

  return (
    <AppShell>
      <main className="flex-1 overflow-auto p-6 scrollbar-thin ambient-grid bg-surface-0">
        <section className="mx-auto max-w-5xl space-y-6 animate-in fade-in slide-in-from-bottom-3 duration-300">
          <AdminToolbar
            loadingModels={loadingModels}
            errorMessage={errorMessage}
            onRefresh={loadModels}
            onAddModel={() => openModal("create")}
            onClearError={() => setErrorMessage(null)}
            t={t}
          />

          <StatGrid minWidth={13}>
            <Stat
              label={t.dashboard.activeSessions}
              value={metrics.activeSessions.toLocaleString()}
              hint={t.dashboard.runningParallel}
            />
            <Stat
              label={t.dashboard.tokensUsed}
              value={metrics.tokensUsed.toLocaleString()}
              hint={t.dashboard.estimatedCumulative}
            />
            <Stat
              label={t.dashboard.memoryNodes}
              value={metrics.memoryNodes.toLocaleString()}
              hint={t.dashboard.contextSaved}
            />
          </StatGrid>

          <PipelineConfigCard fetchWithAuth={fetchWithAuth} />

          <ValidationPipelineCard fetchWithAuth={fetchWithAuth} />

          {loadingModels && models.length === 0 ? (
            <div className="text-center py-20 text-fg-muted animate-pulse text-sm">{t.admin.loadingModels}</div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {CATEGORIES.map((cat) => (
                <CategoryCard
                  key={cat}
                  category={cat}
                  models={models.filter((m) => m.category === cat)}
                  onEdit={(m) => openModal("edit", m)}
                  onDelete={handleDelete}
                />
              ))}
            </div>
          )}

          <FeatureToggleCard features={features} onToggle={handleToggleFeature} t={t} />
        </section>
      </main>
      <ModelDialog
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        formModel={formModel}
        setFormModel={setFormModel}
        formMode={formMode}
        saving={saving}
        onSubmit={onSubmit}
        errorMessage={errorMessage}
        setErrorMessage={setErrorMessage}
        providers={providers}
      />
    </AppShell>
  );
}
