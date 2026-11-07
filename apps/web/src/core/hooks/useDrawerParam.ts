"use client";

import { useCallback } from "react";
import { usePathname, useSearchParams } from "next/navigation";

/**
 * A drawer that is part of the address (`?pack=data-analytics`): it can be linked, and Back closes it.
 *
 * Opening pushes a history entry, closing replaces it. Written through the History API, which Next.js keeps
 * `useSearchParams` in step with, so opening a drawer is not a navigation.
 *
 * @param name - the search parameter that holds the open item's key
 * @returns the open key (or null), and the functions that open and close it
 */
export function useDrawerParam(name: string) {
  const params = useSearchParams();
  const pathname = usePathname();
  const value = params.get(name);

  const write = useCallback(
    (key: string | null) => {
      const next = new URLSearchParams(params.toString());
      if (key) next.set(name, key);
      else next.delete(name);
      const text = next.toString();
      const url = `${pathname}${text ? `?${text}` : ""}`;
      if (key) window.history.pushState(null, "", url);
      else window.history.replaceState(null, "", url);
    },
    [name, params, pathname],
  );

  return { value, open: (key: string) => write(key), close: () => write(null) };
}
