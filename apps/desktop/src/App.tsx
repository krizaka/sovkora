import { useQuery, useQueryClient } from "@krizaka/query";
import { useCallback, useEffect, useState } from "react";
import { SovkoraLogo } from "@krizaka/ui";
import { Badge } from "@krizaka/ui/badge";
import { Button } from "@krizaka/ui/button";
import { TabsContent, TabsList, TabsRoot, TabsTrigger } from "@krizaka/ui/tabs";
import { ThemeToggle, useTheme } from "@krizaka/ui/theme";
import { Alert } from "@krizaka/ui/alert";
import { snapshotKeys, snapshotQuery } from "./lib/queries";
import { api, errorMessage, hostOf, on, type Probe, type Settings, type Snapshot } from "./lib/api";
import { I18nProvider, useI18n, type Locale } from "./lib/i18n";
import { CodePanel } from "./components/code/CodePanel";
import { AutomationsPanel } from "./components/automations/AutomationsPanel";
import { FilesPanel } from "./components/FilesPanel";
import { SettingsPanel } from "./components/SettingsPanel";
import { StackPanel } from "./components/StackPanel";

export function App() {
  const queryClient = useQueryClient();
  const snapshot = useQuery(snapshotQuery());
  const snap = snapshot.data ?? null;
  const error = snapshot.error ? errorMessage(snapshot.error) : null;
  const refresh = useCallback(async () => {
    await queryClient.invalidateQueries({ queryKey: snapshotKeys.all });
  }, [queryClient]);
  const setSnap = useCallback((s: Snapshot) => queryClient.setQueryData(snapshotKeys.all, s), [queryClient]);

  // a pushed event (the probe) is tied to the cache; the listener is cleaned up
  useEffect(() => {
    const off = on<Probe>("stack-probe", (probe) =>
      queryClient.setQueryData<Snapshot>(snapshotKeys.all, (s) => (s ? { ...s, probe } : s)),
    );
    return () => {
      void off.then((f) => f());
    };
  }, [queryClient]);

  const locale: Locale = snap?.settings.locale ?? "en";
  return (
    <I18nProvider locale={locale}>
      <Shell snap={snap} error={error} refresh={refresh} setSnap={setSnap} />
    </I18nProvider>
  );
}

function Shell({ snap, error, refresh, setSnap }: Readonly<{ snap: Snapshot | null; error: string | null; refresh: () => Promise<void>; setSnap: (s: Snapshot) => void }>) {
  const { t, format, locale } = useI18n();
  const { mode } = useTheme();
  const [tab, setTab] = useState("stack");

  const switchLocale = async () => {
    if (!snap) return;
    const next: Settings = { ...snap.settings, locale: locale === "en" ? "fr" : "en" };
    const saved = await api.saveSettings(next);
    setSnap({ ...snap, settings: saved });
  };

  return (
    <div className="flex h-full flex-col bg-surface-1 text-fg">
      <header className="flex items-center gap-3 border-b border-border-subtle px-5 py-3">
        <SovkoraLogo size={34} animated={false} title="Sovkora" />
        <div className="min-w-0">
          <h1 className="text-base font-semibold leading-tight">{t("app.name")}</h1>
          <p className="truncate text-xs text-fg-secondary">{t("app.tagline")}</p>
          <p className="truncate text-xs text-fg-secondary" data-testid="development-status">
            {t("app.development")}
          </p>
        </div>
        {snap && (
          <Badge tone={snap.settings.mode === "remote" ? "accent" : "neutral"} dot>
            {snap.settings.mode === "remote" ? format(t("mode.remote"), { host: hostOf(snap.settings.remoteUrl) }) : t("mode.local")}
          </Badge>
        )}
        <div className="ml-auto flex items-center gap-2">
          <Button size="sm" variant="ghost" onClick={() => void switchLocale()} aria-label="Language">
            {t("common.language")}
          </Button>
          <ThemeToggle variant="ghost" label={(m) => format(t("common.theme"), { mode: m })} data-mode-now={mode} />
          <Button size="sm" variant="primary" shape="cut" onClick={() => void api.openWorkspace().catch(() => undefined)}>
            {t("stack.openWorkspace")}
          </Button>
        </div>
      </header>
      {error && (
        <div className="px-5 pt-3">
          <Alert tone="danger">{format(t("common.error"), { message: error })}</Alert>
        </div>
      )}
      <TabsRoot value={tab} onValueChange={setTab} className="flex min-h-0 flex-1 flex-col">
        <TabsList className="px-5">
          <TabsTrigger value="stack" data-testid="tab-stack">{t("tabs.stack")}</TabsTrigger>
          <TabsTrigger value="files" data-testid="tab-files">{t("tabs.files")}</TabsTrigger>
          <TabsTrigger value="automations" data-testid="tab-automations">{t("tabs.automations")}</TabsTrigger>
          <TabsTrigger value="code">{t("tabs.code")}</TabsTrigger>
          <TabsTrigger value="settings">{t("tabs.settings")}</TabsTrigger>
        </TabsList>
        <main className="min-h-0 flex-1 overflow-auto p-5">
          {snap ? (
            <>
              <TabsContent value="stack"><StackPanel snap={snap} goSettings={() => setTab("settings")} /></TabsContent>
              <TabsContent value="files"><FilesPanel snap={snap} /></TabsContent>
              <TabsContent value="automations"><AutomationsPanel snap={snap} /></TabsContent>
              <TabsContent value="code"><CodePanel /></TabsContent>
              <TabsContent value="settings"><SettingsPanel snap={snap} onSaved={(s) => setSnap(s)} refresh={refresh} /></TabsContent>
            </>
          ) : null}
        </main>
      </TabsRoot>
    </div>
  );
}
