"use client";

import type { ComponentProps } from "react";
import Link from "next/link";
import { Markdown } from "@krizaka/ui/markdown";
import { useTranslation } from "@/core/context/LocaleContext";

/** Next's `Link` for the links of a page (an `a` without `href` stays a plain anchor). */
function DocLink({ href, ...props }: ComponentProps<"a">) {
  return href ? <Link href={href} {...props} /> : <a {...props} />;
}

/**
 * A page's prose on the Krizaka tokens (`Markdown` of @krizaka/ui): headings with anchors, tables that scroll inside
 * their frame, highlighted code with a copy button. Links to other pages stay in the router; the others were prepared by
 * `prepareBody` (never a dead link).
 */
export function DocMarkdown({ text }: Readonly<{ text: string }>) {
  const { t } = useTranslation();
  return (
    <Markdown headingIds linkAs={DocLink} codeLabels={{ copy: t.docs.copy, copied: t.docs.copied }}>
      {text}
    </Markdown>
  );
}
