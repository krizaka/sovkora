"use client";

import * as React from "react";
import type { Look } from "@/core/clips/clips";

/** The page's look, so a recording matches it: `html.light` is the light theme (ThemeProvider), anything else dark. */
function subscribe(notify: () => void) {
  const mo = new MutationObserver(notify);
  mo.observe(document.documentElement, { attributes: true, attributeFilter: ["class"] });
  return () => mo.disconnect();
}

export function useLook(): Look {
  return React.useSyncExternalStore(
    subscribe,
    () => (document.documentElement.classList.contains("light") ? "light" : "dark"),
    () => "dark",
  );
}
