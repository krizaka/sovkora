"use client";

import { cn } from "@krizaka/ui/cn";
import { PhoneClip } from "@/core/mobile/PhoneClip";
import type { StepId } from "@/core/mobile/mobile";
import { useTranslation } from "@/core/context/LocaleContext";

/** One step of the journey: the phone with its recording, then what the person comes to do, what the screen does, what to notice. */
export function MobileStep({ id, index, reverse }: Readonly<{ id: StepId; index: number; reverse: boolean }>) {
  const { t } = useTranslation();
  const m = t.mobile;
  const s = m.steps[id];
  const titleId = `mobile-step-${id}`;
  return (
    <li>
      <article
        aria-labelledby={titleId}
        className={cn(
          "grid items-center gap-8 md:gap-14",
          reverse ? "md:grid-cols-[1fr_15rem]" : "md:grid-cols-[15rem_1fr]",
        )}
      >
        <div className={reverse ? "md:order-2" : undefined}>
          <PhoneClip id={id} label={s.goal} alt={s.alt} />
        </div>
        <div className="space-y-4">
          <p className="font-mono text-xs font-semibold text-fg-accent">
            {m.step} {index + 1}
          </p>
          <h3 id={titleId} className="font-display text-2xl font-bold tracking-tight text-balance text-fg sm:text-3xl">
            {s.goal}
          </h3>
          <p className="max-w-xl text-base leading-relaxed text-pretty text-fg-secondary">{s.purpose}</p>
          <div>
            <p className="font-mono text-xs font-semibold text-fg-muted">{m.notice}</p>
            <ul className="mt-2 list-disc space-y-1.5 pl-5 text-sm leading-relaxed text-fg-secondary marker:text-fg-accent">
              {s.notice.map((n) => (
                <li key={n}>{n}</li>
              ))}
            </ul>
          </div>
        </div>
      </article>
    </li>
  );
}
