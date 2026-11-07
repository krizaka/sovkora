"use client";

import { IconButton } from "@krizaka/ui/button";
import * as React from "react";
import { useTranslation } from "@/core/context/LocaleContext";
import { Icon } from "@krizaka/orazaka-design-system";

/** A value in small mono with one copy icon (an identifier only support needs). */
export function CopyableField({ value }: Readonly<{ value: string }>) {
  const { t } = useTranslation();
  const [copied, setCopied] = React.useState(false);

  const handleCopy = async () => {
    await navigator.clipboard.writeText(value);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <>
      <code className="select-all break-all font-mono text-xs text-fg-secondary">{value}</code>
      <IconButton variant="ghost" size="sm" onClick={handleCopy} className="w-8 shrink-0 px-0" label={t.a11y.copy}>
        {copied ? (
          <Icon name="check" className="h-3.5 w-3.5 text-fg-success" />
        ) : (
          <Icon name="copy" className="h-3.5 w-3.5" />
        )}
      </IconButton>
    </>
  );
}
