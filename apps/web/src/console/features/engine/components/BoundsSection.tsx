"use client";

import type { FeatureToolsWords } from "@/console/core/copy/featureTools";
import { AddBound } from "./AddBound";
import { BoundRow, type BoundKind, type Range, type Save } from "./BoundRow";

/** One bound as the server lists it, whatever it counts (tool rounds or structured answers). */
export type BoundItem = Readonly<{
  featureKey: string;
  isDefault: boolean;
  value: number;
}>;

/** A titled list of editable bounds and the form that adds one: the same for tool rounds and structured answers. */
export function BoundsSection({
  kind,
  title,
  lead,
  bounds,
  range,
  words,
  onSave,
  onRemove,
}: Readonly<{
  kind: BoundKind;
  title: string;
  lead?: string;
  bounds: readonly BoundItem[];
  range: Range;
  words: FeatureToolsWords;
  onSave: Save;
  onRemove: (featureKey: string) => Promise<string | null>;
}>) {
  const id = `${kind}-bounds`;
  return (
    <section aria-labelledby={id} className="flex flex-col gap-3">
      <h2 id={id} className="text-lg font-semibold text-fg">
        {title}
      </h2>
      {lead && <p className="max-w-3xl text-sm text-fg-secondary">{lead}</p>}
      <div className="grid gap-4 lg:grid-cols-2">
        {bounds.map((bound) => (
          <BoundRow
            key={bound.featureKey}
            kind={kind}
            bound={bound}
            value={bound.value}
            range={range}
            words={words}
            onSave={onSave}
            onRemove={onRemove}
          />
        ))}
        <AddBound kind={kind} range={range} words={words} onSave={onSave} />
      </div>
    </section>
  );
}
