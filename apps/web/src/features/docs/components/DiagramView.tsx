"use client";

import { CodeBlock } from "@krizaka/ui/code-block";
import { useTranslation } from "@/core/context/LocaleContext";
import type { DocsDiagramLabels } from "@/core/context/translations.docs";
import { PackIndexView, PackView } from "@/features/docs/components/PackCatalogue";
import type { DiagramData } from "@/features/docs/utils/diagram-data";
import { chip, Figure, mono, rowGrid } from "./DiagramFigure";
import { Architecture, CliJourney } from "./DiagramJourneys";

/**
 * The text version of a schema block: tables and ordered lists, one column, read in order. It stands in until @krizaka/ui
 * has the generic diagram components — the content is the same, so nothing a doc draws is lost.
 */
export function DiagramView({ data }: Readonly<{ data: DiagramData }>) {
  const { t } = useTranslation();
  const l = t.docs.diagrams;
  switch (data.kind) {
    case "sequence":
      return <Sequence block={data.block} l={l} />;
    case "pipeline":
      return <Pipeline data={data} l={l} />;
    case "states":
      return <States block={data.block} l={l} />;
    case "architecture":
      return <Architecture data={data} l={l} />;
    case "cli-replay":
      return (
        <CodeBlock
          code={data.code}
          language="bash"
          file={l.terminal}
          copyLabel={t.docs.copy}
          copiedLabel={t.docs.copied}
          className="my-5"
        />
      );
    case "cli-journey":
      return <CliJourney data={data} l={l} />;
    case "pack-index":
      return <PackIndexView items={data.items} />;
    case "pack":
      return <PackView pack={data.pack} />;
    case "cli-commands":
      return <p className="my-4 text-sm text-fg-secondary">{data.text}</p>;
  }
}

function Sequence({
  block,
  l,
}: Readonly<{ block: Extract<DiagramData, { kind: "sequence" }>["block"]; l: DocsDiagramLabels }>) {
  const label = (key: string) => block.participants.find((p) => p.key === key)?.label ?? key;
  return (
    <Figure title={l.sequence}>
      <p className="m-0 font-mono text-xs text-fg-muted">{l.participants}</p>
      <ul className="mb-4 mt-2 flex list-none flex-wrap gap-2 p-0">
        {block.participants.map((p) => (
          <li key={p.key} className={chip}>
            {p.label}
          </li>
        ))}
      </ul>
      <ol className="m-0 list-none space-y-1.5 p-0">
        {block.items.map((it, i) =>
          it.type === "section" ? (
            <li key={i} className="pt-3 font-semibold text-fg">
              {it.title}
            </li>
          ) : (
            <li key={i} className={rowGrid}>
              <span className="pt-0.5 text-right font-mono text-xs text-fg-muted">
                <span className="sr-only">{l.step} </span>
                {it.n}
              </span>
              <span>
                {it.branch && (
                  <span className="mr-2 rounded bg-surface-2 px-1.5 py-0.5 font-mono text-[0.7rem] text-fg-muted">
                    {it.branch.kind} {it.branch.condition}
                  </span>
                )}
                <span className={mono}>{label(it.from)}</span> <span aria-hidden>{it.reply ? "⇠" : "→"}</span>
                <span className="sr-only"> {it.reply ? l.reply : "→"} </span>{" "}
                <span className={mono}>{label(it.to)}</span>
                <span className="text-fg-muted">: </span>
                {it.text}
              </span>
            </li>
          ),
        )}
      </ol>
    </Figure>
  );
}

function Pipeline({ data, l }: Readonly<{ data: Extract<DiagramData, { kind: "pipeline" }>; l: DocsDiagramLabels }>) {
  return (
    <Figure title={l.pipeline}>
      <h4 className="mb-2 mt-0 text-sm font-semibold text-fg">{l.coreChain}</h4>
      <ol className="m-0 mb-5 list-none space-y-2 p-0">
        {data.coreChain.map((c) => (
          <li key={c.interceptor} className={rowGrid}>
            <span className="pt-0.5 text-right font-mono text-xs text-fg-muted">{c.rank}</span>
            <span>
              <span className={mono}>{c.interceptor}</span> <span className="text-fg-muted">· {c.concern}</span>
              <br />
              {c.summary}
            </span>
          </li>
        ))}
      </ol>
      <h4 className="mb-2 mt-0 text-sm font-semibold text-fg">{l.configuredChain}</h4>
      <ol className="m-0 list-none space-y-2 p-0">
        {data.pipeline.map((p) => (
          <li key={p.interceptor} className={rowGrid}>
            <span className="pt-0.5 text-right font-mono text-xs text-fg-muted">{p.order}</span>
            <span>
              <span className={mono}>{p.interceptor}</span>{" "}
              <span className="text-fg-muted">
                · {p.concern} · {p.phase}
              </span>
              {!p.implemented && <span className="ml-2 text-xs text-fg-muted">({l.notBuilt})</span>}
              <br />
              {p.summary}
            </span>
          </li>
        ))}
      </ol>
    </Figure>
  );
}

function States({
  block,
  l,
}: Readonly<{ block: Extract<DiagramData, { kind: "states" }>["block"]; l: DocsDiagramLabels }>) {
  const name = (id: string) =>
    id === "start" ? l.initial : id === "end" ? l.final : (block.states.find((s) => s.id === id)?.label ?? id);
  return (
    <Figure title={l.states}>
      <ol className="m-0 list-none space-y-1.5 p-0">
        {block.transitions.map((tr, i) => (
          <li key={i} className="text-sm leading-relaxed text-fg-secondary">
            <span className={mono}>{name(tr.from)}</span> <span aria-hidden>→</span>
            <span className="sr-only"> → </span> <span className={mono}>{name(tr.to)}</span>
            <span className="text-fg-muted">: </span>
            {tr.label}
          </li>
        ))}
      </ol>
    </Figure>
  );
}
