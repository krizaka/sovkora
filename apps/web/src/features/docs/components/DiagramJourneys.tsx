"use client";

import { useTranslation } from "@/core/context/LocaleContext";
import type { DocsDiagramLabels } from "@/core/context/translations.docs";
import type { DiagramData } from "@/features/docs/utils/diagram-data";
import { chip, Figure, mono, rowGrid } from "./DiagramFigure";

export function Architecture({
  data,
  l,
}: Readonly<{ data: Extract<DiagramData, { kind: "architecture" }>; l: DocsDiagramLabels }>) {
  const { focus } = data;
  if (!focus) {
    return (
      <Figure title={l.architecture}>
        <p className="m-0 text-sm text-fg-secondary">{l.moduleMap}</p>
      </Figure>
    );
  }
  const list = (items: string[]) =>
    items.length ? (
      <ul className="m-0 flex list-none flex-wrap gap-1.5 p-0">
        {items.map((m) => (
          <li key={m} className={chip}>
            {m}
          </li>
        ))}
      </ul>
    ) : (
      <p className="m-0 text-sm text-fg-muted">{l.none}</p>
    );
  return (
    <Figure title={`${l.architecture} · ${focus.id}`}>
      <p className="m-0 text-sm leading-relaxed text-fg-secondary">{focus.description}</p>
      <dl className="my-3 grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1 text-sm">
        <dt className="text-fg-muted">{l.repository}</dt>
        <dd className="m-0 font-mono text-xs text-fg">{focus.repository}</dd>
        <dt className="text-fg-muted">{l.role}</dt>
        <dd className="m-0 font-mono text-xs text-fg">{focus.role}</dd>
        <dt className="text-fg-muted">{l.owner}</dt>
        <dd className="m-0 font-mono text-xs text-fg">{focus.owner}</dd>
      </dl>
      <div className="grid gap-4 md:grid-cols-2">
        <div>
          <h4 className="mb-2 mt-0 text-sm font-semibold text-fg">{data.runtime ? l.calls : l.uses}</h4>
          {list(data.runtime ? data.calls : data.uses)}
        </div>
        <div>
          <h4 className="mb-2 mt-0 text-sm font-semibold text-fg">{data.runtime ? l.calledBy : l.usedBy}</h4>
          {list(data.runtime ? data.calledBy : data.usedBy)}
        </div>
      </div>
    </Figure>
  );
}

export function CliJourney({
  data,
}: Readonly<{ data: Extract<DiagramData, { kind: "cli-journey" }>; l: DocsDiagramLabels }>) {
  const { t } = useTranslation();
  return (
    <Figure title={t.docs.diagrams.sequence}>
      <ol className="m-0 list-none space-y-4 p-0">
        {data.journey.map((c, i) => (
          <li key={c.command} className={rowGrid}>
            <span className="pt-0.5 text-right font-mono text-xs text-fg-muted">{i + 1}</span>
            <div>
              <p className="m-0">
                <span className={mono}>
                  {data.alias} {c.command}
                </span>
                {" — "}
                {c.description}
              </p>
              {(c.phases?.length ?? 0) > 0 && (
                <ul className="mb-0 mt-1.5 list-disc space-y-0.5 pl-5">
                  {c.phases!.map((p) => (
                    <li key={p.id}>
                      {p.title}
                      {p.detail && <span className="text-fg-muted"> ({p.detail})</span>}
                    </li>
                  ))}
                </ul>
              )}
              {(c.processes?.length ?? 0) > 0 && (
                <ul className="mb-0 mt-1.5 flex list-none flex-wrap gap-1.5 p-0">
                  {c.processes!.map((p) => (
                    <li key={p.key} className={chip}>
                      {p.label}
                      {p.port ? ` :${p.port}` : ""}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </li>
        ))}
      </ol>
    </Figure>
  );
}
