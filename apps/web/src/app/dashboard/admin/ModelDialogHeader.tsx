import { useTranslation } from "@/core/context/LocaleContext";
import { IconButton } from "@krizaka/ui/button";
import { Icon } from "@krizaka/orazaka-design-system";
import type { ReactNode } from "react";

import { cn } from "@krizaka/ui/cn";

interface ModelDialogHeaderProps {
  catMeta: { color: string; icon: ReactNode };
  title: string;
  subtitle: string;
  saving: boolean;
  onClose: () => void;
}

/** Category-accented header (icon + title + sub-label + close) for the model dialog. */
export function ModelDialogHeader({ catMeta, title, subtitle, saving, onClose }: Readonly<ModelDialogHeaderProps>) {
  const { t } = useTranslation();
  return (
    <header className="flex items-center justify-between p-5 border-b border-border-subtle bg-surface-0">
      <div className="flex items-center gap-3">
        <span
          className={cn(
            "inline-flex items-center justify-center w-9 h-9 rounded-xl text-lg border",
            catMeta.color,
            "transition-all duration-300",
          )}
        >
          {catMeta.icon}
        </span>
        <div>
          <h3 className="text-base font-semibold text-fg">{title}</h3>
          <p className="text-xs text-fg-muted">{subtitle}</p>
        </div>
      </div>
      <IconButton variant="ghost" disabled={saving} onClick={onClose} label={t.a11y.closeDetails}>
        <Icon name="close" className="h-4 w-4" />
      </IconButton>
    </header>
  );
}
