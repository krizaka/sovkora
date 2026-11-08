"use client";

import { IconButton } from "@krizaka/ui/button";
import React from "react";
import { Button, Icon } from "@krizaka/orazaka-design-system";
import type { TranslationDictionary } from "@/core/context/LocaleContext";

import { cn } from "@krizaka/ui/cn";

interface AdminToolbarProps {
  loadingModels: boolean;
  errorMessage: string | null;
  onRefresh: () => void;
  onAddModel: () => void;
  onClearError: () => void;
  t: TranslationDictionary;
}

/**
 * Top toolbar for the admin dashboard with title, refresh and add model buttons,
 * plus an inline error banner.
 */
export const AdminToolbar: React.FC<AdminToolbarProps> = ({
  loadingModels,
  errorMessage,
  onRefresh,
  onAddModel,
  onClearError,
  t,
}) => (
  <>
    <header className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
      <div className="space-y-1">
        <h2 className="text-3xl font-extrabold tracking-tight text-fg flex items-center gap-2">
          <Icon name="sliders" className="h-8 w-8 text-warning" />
          {t.admin.title}
        </h2>
        <p className="text-fg-secondary text-sm">{t.admin.subtitle}</p>
      </div>
      <div className="flex items-center space-x-2">
        <Button
          variant="outline"
          onClick={onRefresh}
          className="rounded-xl flex items-center space-x-2 text-sm font-semibold border-border-subtle transition-colors hover:bg-surface-1"
        >
          <Icon name="refresh" className={cn("w-4 h-4", loadingModels ? "animate-spin" : "")} />
          <span>{t.admin.refresh}</span>
        </Button>
        <Button
          onClick={onAddModel}
          className="rounded-xl flex items-center space-x-2 text-sm font-semibold bg-warning hover:bg-warning text-on-accent shadow-md border-transparent transition-colors"
        >
          <Icon name="plus" className="w-4 h-4" />
          <span>{t.admin.addModel}</span>
        </Button>
      </div>
    </header>

    {errorMessage && (
      <div className="p-4 bg-danger/20 border border-danger/50 rounded-2xl flex items-start gap-3 text-danger">
        <Icon name="warning" className="h-5 w-5 flex-shrink-0 mt-0.5" />
        <div className="text-sm font-medium flex-1">{errorMessage}</div>
        <IconButton
          variant="ghost"
          size="sm"
          className="h-6 w-6 px-0"
          onClick={onClearError}
          label={t.a11y.closeDetails}
        >
          <Icon name="close" className="h-4 w-4" />
        </IconButton>
      </div>
    )}
  </>
);
