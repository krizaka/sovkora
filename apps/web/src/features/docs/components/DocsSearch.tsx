"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { SearchField } from "@krizaka/ui/search-field";
import { useTranslation } from "@/core/context/LocaleContext";
import { pageHref, searchDocs, type SearchEntry } from "@/features/docs/utils/docs-shared";

/** The search of the documentation: pages and their headings, as suggestions under the field (⌘K focuses it). */
export function DocsSearch({ index }: Readonly<{ index: readonly SearchEntry[] }>) {
  const { t } = useTranslation();
  const d = t.docs;
  const router = useRouter();
  const [query, setQuery] = useState("");
  const hits = useMemo(() => searchDocs(index, query), [index, query]);
  const suggestions = hits.map((h) => ({
    value: pageHref(h.slug) + (h.id ? `#${h.id}` : ""),
    label: h.label,
    meta: d.journeys[h.meta as keyof typeof d.journeys]?.title,
  }));
  return (
    <div>
      <SearchField
        label={d.searchLabel}
        placeholder={d.searchPlaceholder}
        shortcut="k"
        value={query}
        onValueChange={setQuery}
        suggestions={suggestions}
        suggestionsLabel={d.searchHint}
        onSuggestionSelect={(s) => router.push(s.value)}
        onSubmit={() => hits[0] && router.push(`${pageHref(hits[0].slug)}${hits[0].id ? `#${hits[0].id}` : ""}`)}
      />
      {query.trim() && hits.length === 0 && (
        <p role="status" className="mt-3 text-sm text-fg-secondary">
          {d.searchEmpty}
        </p>
      )}
    </div>
  );
}
