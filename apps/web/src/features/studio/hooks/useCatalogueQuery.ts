"use client";

import { useCallback, useMemo } from "react";
import { usePathname, useSearchParams } from "next/navigation";
import { readQuery, writeQuery } from "@/features/studio/catalogue/catalogue";
import type { CatalogueQuery } from "@/features/studio/catalogue/catalogue.types";

/**
 * The list's filters, read from and written to the address (every screen is a URL): `?q=` the search, `?show=`
 * ready · more, `?trade=` one trade.
 *
 * Filters replace the history entry, because typing is not a trail of pages. Written through the History API, which
 * Next.js keeps `useSearchParams` in step with, so a keystroke is not a navigation.
 */
export function useCatalogueQuery() {
  const params = useSearchParams();
  const pathname = usePathname();
  const query = useMemo(() => readQuery(params), [params]);

  const setQuery = useCallback(
    (next: Partial<CatalogueQuery>) => {
      window.history.replaceState(null, "", `${pathname}${writeQuery(new URLSearchParams(params.toString()), next)}`);
    },
    [params, pathname],
  );

  return { query, setQuery };
}
