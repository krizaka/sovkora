"use client";

import { Button } from "@krizaka/ui/button";
import { cn } from "@krizaka/ui/cn";
import { useQuery } from "@krizaka/query";
import { studiosCopy, type StudiosLocale } from "@/console/core/copy/studios";
import { useConsoleLocale } from "@/console/core/providers/ConsoleLocale";
import { StudioAdminApi } from "@/console/services/studio.admin.api";
import { useStudioAdmin } from "@/console/features/studio/hooks/useStudioAdmin";
import { BlueprintEditor } from "@/console/features/studio/components/BlueprintEditor";
import { BlueprintVersionTable } from "@/console/features/studio/components/BlueprintVersionTable";

/**
 * The Studio Builder.
 *
 * Three panes, as ADR-034 §12.4 describes: the catalogue on the left, the selected
 * Studio's version history, and the authoring editor. The history sits above the
 * editor deliberately — the first question an author has is "what is live right
 * now", and publishing without that answer is how a working version gets replaced
 * by accident.
 */
export function StudioBuilderScreen({ locale }: Readonly<{ locale?: StudiosLocale }>) {
  const fromContext = useConsoleLocale();
  const words = studiosCopy[locale ?? fromContext].builder;
  const { studios, versions, selected, select, isLoading, error, reloadVersions } = useStudioAdmin();
  // A best-effort list: the picker is a convenience, and the server is what actually
  // refuses a step naming a capability that does not exist.
  const featureKeysQuery = useQuery({
    queryKey: ["studio-feature-keys"],
    queryFn: () => StudioAdminApi.featureKeys(),
    retry: false,
  });
  const featureKeys: string[] = featureKeysQuery.data ?? [];

  if (isLoading) {
    return <p className="text-sm text-fg-muted">{words.loading}</p>;
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[220px_1fr]">
      <nav className="space-y-1">
        {studios.map((studio) => (
          <Button
            key={studio.studioKey}
            type="button"
            variant="ghost"
            onClick={() => select(studio.studioKey)}
            className={cn(
              "block h-auto w-full rounded px-3 py-2 text-left text-sm transition-colors",
              selected === studio.studioKey
                ? "bg-surface-2 font-semibold text-fg"
                : "text-fg-secondary hover:bg-surface-2",
            )}
          >
            {studio.label}
            <span className="block font-mono text-[10px] text-fg-muted">{studio.studioKey}</span>
          </Button>
        ))}
        {studios.length === 0 && <p className="text-sm text-fg-muted">{words.empty}</p>}
      </nav>

      <div className="space-y-6">
        {error && <p className="text-sm text-fg-danger">{error}</p>}

        {selected && (
          <>
            <BlueprintVersionTable
              studioKey={selected}
              versions={versions}
              onChanged={reloadVersions}
              locale={locale ?? fromContext}
            />
            <BlueprintEditor
              studioKey={selected}
              featureKeys={featureKeys}
              onSaved={reloadVersions}
              locale={locale ?? fromContext}
            />
          </>
        )}
      </div>
    </div>
  );
}
