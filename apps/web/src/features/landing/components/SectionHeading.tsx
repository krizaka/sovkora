import { cn } from "@krizaka/ui/cn";

/** The heading of a public-page section: a mono eyebrow in the accent, the title, an optional lead. */
export function SectionHeading({
  eyebrow,
  title,
  lead,
  center = false,
  className,
}: Readonly<{ eyebrow: string; title: string; lead?: string; center?: boolean; className?: string }>) {
  return (
    <header data-reveal className={cn("max-w-2xl space-y-4", center && "mx-auto text-center", className)}>
      <p className="font-mono text-xs font-semibold text-fg-accent">{eyebrow}</p>
      <h2 className="font-display text-3xl font-bold tracking-tight text-balance text-fg sm:text-4xl lg:text-[2.75rem] lg:leading-[1.1]">
        {title}
      </h2>
      {lead && <p className="text-base leading-relaxed text-pretty text-fg-secondary sm:text-lg">{lead}</p>}
    </header>
  );
}
