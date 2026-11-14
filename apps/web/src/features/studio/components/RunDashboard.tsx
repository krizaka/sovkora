"use client";

import { useMemo } from "react";
import { Alert } from "@krizaka/ui/alert";
import { extractGenUi, GenUiSurface, readStream, type GenUiSurfaceState } from "@krizaka/ui/genui";
import { GENUI_CATALOG } from "@/components/genui/genUiCatalog";
import { useTranslation } from "@/core/context/LocaleContext";

/** `true` when a run's text result carries a generated dashboard (an `a2ui` fence). */
export function isDashboard(text: string): boolean {
  return extractGenUi(text).found;
}

/** Every child a component names is on the surface, and the surface has its root. */
function complete(surface: GenUiSurfaceState): boolean {
  const present = (id: unknown) => surface.components.has(String(id));
  return (
    present("root") &&
    [...surface.components.values()].every(
      (component) => !Array.isArray(component.children) || component.children.every(present),
    )
  );
}

/**
 * A run's answer drawn as its dashboard (krizaka-internal#3628): the summary, then the A2UI surface through Krizaka UI.
 *
 * All or nothing. A run result is finished, so a fence left open, a message the catalog refuses, or a child that never
 * arrived means the answer is not the one the Studio checked: it is refused as a whole and said so, never printed as
 * raw text and never drawn in part.
 */
export function RunDashboard({ answer }: Readonly<{ answer: string }>) {
  const { t, locale } = useTranslation();
  const drawn = useMemo(() => {
    const extract = extractGenUi(answer);
    const state = readStream(answer, GENUI_CATALOG);
    const surfaces = state.order.flatMap((id) => {
      const surface = state.surfaces.get(id);
      return surface ? [surface] : [];
    });
    const valid = extract.closed && state.errors.length === 0 && surfaces.length > 0 && surfaces.every(complete);
    return valid ? { prose: state.prose.trim(), surfaces } : null;
  }, [answer]);

  if (drawn === null) {
    return (
      <Alert tone="warning" title={t.studio.dashboardRefused}>
        {t.studio.dashboardRefusedBody}
      </Alert>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      {drawn.prose
        .split(/\n{2,}/)
        .filter((paragraph) => paragraph.trim() !== "")
        .map((paragraph) => (
          <p key={paragraph} className="text-sm leading-relaxed text-fg">
            {paragraph}
          </p>
        ))}
      {drawn.surfaces.map((surface) => (
        <GenUiSurface
          key={surface.id}
          surface={surface}
          catalog={GENUI_CATALOG}
          labels={t.chat.genui}
          locale={locale}
        />
      ))}
    </div>
  );
}
