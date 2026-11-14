"use client";

import { GenUiStream } from "@krizaka/ui/genui";
import React from "react";

import { GENUI_CATALOG } from "@/components/genui/genUiCatalog";
import { useTranslation } from "@/core/context/LocaleContext";

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
        catalog={GENUI_CATALOG}
        streaming={streaming}
        renderProse={renderProse}
        labels={t.chat.genui}
        locale={locale}
        onError={onError}
      />
    </div>
  );
}
