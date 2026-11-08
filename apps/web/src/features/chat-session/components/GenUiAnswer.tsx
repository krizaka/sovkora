"use client";

import { chartsGenUiCatalog } from "@krizaka/charts/genui";
import { baseGenUiCatalog, GenUiStream } from "@krizaka/ui/genui";
import React from "react";

import { useTranslation } from "@/core/context/LocaleContext";

/** The components a model may draw in the chat: Krizaka UI's base catalog and the charts' (ADR-082, catalog krizaka:genui/v1). */
const CATALOG = { ...baseGenUiCatalog, ...chartsGenUiCatalog };

/** `true` when an answer carries a generated interface (an `a2ui` fence, complete or still streaming). */
export function hasGenUi(content: string): boolean {
  return content.includes("```a2ui");
}

/**
 * A model's answer whose `a2ui` block is drawn as a dashboard while it streams (A2UI v0.9, krizaka/orazaka#207): the
 * prose through the chat's markdown, the surfaces through Krizaka UI — every message validated, nothing outside the
 * catalog drawn, the text itself when nothing valid arrived. What was refused is logged, never shown.
 */
export function GenUiAnswer({
  content,
  streaming,
  renderProse,
}: Readonly<{ content: string; streaming: boolean; renderProse: (prose: string) => React.ReactNode }>) {
  const { t, locale } = useTranslation();
  const onError = React.useCallback((error: string) => console.warn("[genui] refused:", error), []);
  return (
    <div className="flex min-w-[min(36rem,70vw)] flex-col gap-3" data-testid="genui-answer">
      <GenUiStream
        text={content}
        catalog={CATALOG}
        streaming={streaming}
        renderProse={renderProse}
        labels={t.chat.genui}
        locale={locale}
        onError={onError}
      />
    </div>
  );
}
