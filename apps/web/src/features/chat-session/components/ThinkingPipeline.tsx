"use client";

import React from "react";
import { motion, AnimatePresence, useReducedMotion } from "framer-motion";
import { Icon } from "@krizaka/orazaka-design-system";
import type { TranslationDictionary } from "@/core/context/LocaleContext";
import { toPipelineSteps, type ChatPipelineSchema } from "@/core/types/pipeline.types";

interface Props {
  t: TranslationDictionary;
  chatPipeline?: ChatPipelineSchema | null;
  /** Interceptor ids that have finished, in completion order (live progress). */
  completedStageIds?: readonly string[];
}

/** Three-dot fallback when no pipeline schema arrived (e.g. media jobs). */
const DotIndicator: React.FC = () => (
  <section className="bg-surface-1 border border-border-subtle rounded-2xl rounded-tl-md px-5 py-3 flex items-center gap-2 h-11">
    <span className="w-2 h-2 bg-accent rounded-full animate-bounce [animation-delay:0ms]" />
    <span className="w-2 h-2 bg-accent rounded-full animate-bounce [animation-delay:150ms]" />
    <span className="w-2 h-2 bg-accent rounded-full animate-bounce [animation-delay:300ms]" />
  </section>
);

/** Where the pipeline stands: the steps, how many are done, and the one to show (the first unfinished, else the last). */
export function pipelineProgress(chatPipeline: Props["chatPipeline"], done: ReadonlySet<string>) {
  const steps = chatPipeline ? toPipelineSteps(chatPipeline) : [];
  const total = steps.length;
  const doneCount = steps.filter((s) => done.has(s.id)).length;
  const activeIndex = steps.findIndex((s) => !done.has(s.id));
  const allDone = activeIndex === -1;
  // Surface a single step: the first unfinished one, or the last step once the
  // whole chain has completed (so the trace ends on a final state, not blank).
  const current = total > 0 ? steps[allDone ? total - 1 : activeIndex] : null;
  return { steps, total, doneCount, allDone, current };
}

/**
 * Streaming "thinking" trace for the chat. While the assistant is generating, it
 * surfaces the REAL interceptor chain that processed the request — pushed by the
 * Router as the `pipeline-ack` SSE event (AdvancedPipelineSchema), with live
 * progress from the `pipeline-step` events. Only the CURRENT step is shown; it is
 * replaced by the next one as each interceptor completes (one-at-a-time), with a
 * progress counter. No data is invented; if no schema arrived, it falls back to
 * the dot indicator.
 */
export const ThinkingPipeline: React.FC<Props> = ({ t, chatPipeline, completedStageIds }) => {
  const reduce = useReducedMotion();
  const done = React.useMemo(() => new Set(completedStageIds ?? []), [completedStageIds]);
  const { total, doneCount, allDone, current } = pipelineProgress(chatPipeline, done);

  return (
    <div className="flex items-start gap-3 animate-in fade-in slide-in-from-left-3 duration-300">
      <figure className="w-9 h-9 rounded-2xl bg-surface-2 border border-border-subtle flex items-center justify-center text-[11px] font-bold text-fg-secondary">
        {t.chat.ai}
      </figure>

      {chatPipeline && current ? (
        <section className="bg-surface-1 border border-border-subtle rounded-2xl rounded-tl-md px-4 py-3 min-w-[15rem] max-w-md">
          <div className="flex items-center gap-2 mb-2">
            <span className="relative flex h-2 w-2">
              <span className="absolute inline-flex h-full w-full rounded-sm bg-accent opacity-60 motion-safe:animate-ping" />
              <span className="relative inline-flex h-2 w-2 rounded-full bg-accent" />
            </span>
            <span className="text-[11px] font-semibold text-fg-secondary">{t.chat.typing}</span>
            <span className="ml-auto text-[10px] font-mono text-fg-muted">
              {allDone ? total : doneCount + 1}/{total}
            </span>
          </div>

          {/* mode="wait": the active step cross-fades out before the next fades in. */}
          <AnimatePresence mode="wait" initial={false}>
            <motion.div
              key={current.id}
              initial={reduce ? { opacity: 0 } : { opacity: 0, y: 6 }}
              animate={{ opacity: 1, y: 0 }}
              exit={reduce ? { opacity: 0 } : { opacity: 0, y: -6 }}
              transition={{ duration: 0.22, ease: "easeOut" }}
              className="flex items-center gap-2 text-[12px] text-fg"
            >
              {allDone ? (
                <Icon name="check" size={14} className={current.phase === "core" ? "text-accent" : "text-success"} />
              ) : (
                <Icon name="loader" size={14} className="text-accent motion-safe:animate-spin" />
              )}
              <span className="leading-tight font-medium">{current.label}</span>
              {current.phase === "dynamic" && (
                <span className="ml-auto pl-2 text-[9px] font-semibold text-success">routed</span>
              )}
            </motion.div>
          </AnimatePresence>
        </section>
      ) : (
        <DotIndicator />
      )}
    </div>
  );
};
