import { useId, type ReactNode } from "react";

export const mono = "font-mono text-[0.85em] text-fg";
export const chip = "rounded-md border border-border-subtle bg-surface-2 px-2 py-0.5 font-mono text-xs text-fg";
export const rowGrid = "grid grid-cols-[2rem_1fr] gap-x-2 text-sm leading-relaxed text-fg-secondary";

export function Figure({ title, children }: Readonly<{ title: string; children: ReactNode }>) {
  const id = useId();
  return (
    <figure aria-labelledby={id} className="my-6 rounded-2xl border border-border-default bg-surface-1/70 p-4 sm:p-5">
      <figcaption id={id} className="mb-3 font-mono text-xs font-semibold text-fg-accent">
        {title}
      </figcaption>
      {children}
    </figure>
  );
}
