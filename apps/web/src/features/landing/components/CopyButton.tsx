"use client";

import * as React from "react";
import { CheckIcon, CopyIcon } from "@krizaka/icons";
import { Button } from "@krizaka/ui/button";

/** Copies a text to the clipboard and says so (aria-live); says so too when the browser refuses. */
export function CopyButton({
  text,
  label,
  done,
  failed,
}: Readonly<{ text: string; label: string; done: string; failed: string }>) {
  const [state, setState] = React.useState<"idle" | "done" | "failed">("idle");
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setState("done");
    } catch {
      setState("failed");
    }
    setTimeout(() => setState("idle"), 2400);
  };
  return (
    <span className="inline-flex items-center gap-2">
      <Button type="button" variant="ghost" size="sm" onClick={copy}>
        {state === "done" ? <CheckIcon size={14} /> : <CopyIcon size={14} />}
        {label}
      </Button>
      <span role="status" aria-live="polite" className="text-xs text-fg-secondary">
        {state === "done" ? done : state === "failed" ? failed : ""}
      </span>
    </span>
  );
}
