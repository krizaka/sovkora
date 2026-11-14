"use client";

import { IconButton } from "@krizaka/ui/button";
import { Checkbox } from "@krizaka/ui/checkbox";
import { Select } from "@krizaka/ui/field";
import { ControlledForm } from "@/components/forms/ControlledForm";
import * as React from "react";
import { Button, Icon } from "@krizaka/orazaka-design-system";
import { Input } from "@krizaka/orazaka-design-system";
import { useTranslation } from "@/core/context/LocaleContext";
import { CatalogModel } from "./CategoryCard";
import { CATEGORY_META, HelpTooltip, CategorySelector } from "./ModelDialogParts";
import { ModelDialogHeader } from "./ModelDialogHeader";
import { buildModelCategories } from "./modelDialogCategories";

interface ModelDialogProps {
  isOpen: boolean;
  onClose: () => void;
  formModel: CatalogModel;
  setFormModel: React.Dispatch<React.SetStateAction<CatalogModel>>;
  formMode: "create" | "edit";
  saving: boolean;
  onSubmit: () => void | Promise<void>;
  errorMessage: string | null;
  setErrorMessage: (msg: string | null) => void;
  providers: string[];
}

export function ModelDialog({
  isOpen,
  onClose,
  formModel,
  setFormModel,
  formMode,
  saving,
  onSubmit,
  errorMessage,
  setErrorMessage,
  providers,
}: Readonly<ModelDialogProps>) {
  const { t } = useTranslation();

  if (!isOpen) return null;

  const categories = buildModelCategories(t);

  const catMeta = CATEGORY_META[formModel.category] || CATEGORY_META.code;
  const labelClass = "text-xs font-semibold text-fg-muted flex items-center gap-1.5";
  const inputClass =
    "bg-surface-2 border-border-default text-fg text-sm rounded-xl focus:ring-2 focus:ring-ring transition-all duration-200";

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-overlay animate-in fade-in duration-200">
      <Button
        type="button"
        variant="ghost"
        className="fixed inset-0 h-auto w-auto cursor-default rounded-none border-none bg-transparent hover:bg-transparent"
        aria-label={t.a11y.closeDialog}
        onClick={() => !saving && onClose()}
      />

      <div className="relative w-full max-w-2xl bg-surface-1 border border-border-subtle shadow-2xl rounded-2xl flex flex-col animate-in zoom-in-95 slide-in-from-bottom-3 duration-300 z-10 max-h-[90vh] overflow-hidden">
        {/* Header with gradient accent */}
        <ModelDialogHeader
          catMeta={catMeta}
          title={formMode === "edit" ? t.admin.editModelTitle : t.admin.addModelTitle}
          subtitle={formModel.category ? (categories.find((c) => c.value === formModel.category)?.label ?? "") : ""}
          saving={saving}
          onClose={onClose}
        />

        {/* Error banner */}
        {errorMessage && (
          <div className="mx-5 mt-4 p-3 bg-danger/5 border border-danger/20 rounded-xl flex items-start gap-2.5 text-danger animate-in slide-in-from-top-2 duration-200">
            <Icon name="warning" className="h-4 w-4 flex-shrink-0 mt-0.5" />
            <div className="text-xs font-medium flex-1">{errorMessage}</div>
            <IconButton
              variant="ghost"
              size="sm"
              className="h-6 w-6 px-0"
              onClick={() => setErrorMessage(null)}
              label={t.a11y.closeDetails}
            >
              <Icon name="close" className="h-3.5 w-3.5" />
            </IconButton>
          </div>
        )}

        {/* Form content */}
        <ControlledForm
          id="model-dialog-form"
          onSubmit={onSubmit}
          className="flex-1 overflow-y-auto p-5 space-y-5 scrollbar-thin"
        >
          {/* Category selector — radio card grid */}
          <div className="space-y-2">
            <label className={labelClass}>
              {t.admin.categoryLabel}
              <HelpTooltip text={t.admin.helpCategory} />
            </label>
            <CategorySelector
              categories={categories}
              selectedCategory={formModel.category}
              onSelect={(value) => setFormModel({ ...formModel, category: value })}
              disabled={saving}
            />
          </div>

          {/* Provider + Model Name — 2-column */}
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <label className={labelClass}>
                {t.admin.providerLabel}
                <HelpTooltip text={t.admin.helpProvider} />
              </label>
              <Select
                value={formModel.providerName || "ollama"}
                disabled={saving}
                onChange={(e) => setFormModel({ ...formModel, providerName: e.target.value })}
                className="w-full"
              >
                {providers.map((prov) => (
                  <option key={prov} value={prov}>
                    {prov}
                  </option>
                ))}
              </Select>
            </div>

            <div className="space-y-1.5">
              <label className={labelClass}>
                {t.admin.modelNameLabel}
                <HelpTooltip text={t.admin.helpModelName} />
              </label>
              <Input
                value={formModel.modelName}
                disabled={saving}
                onChange={(e) => setFormModel({ ...formModel, modelName: e.target.value })}
                placeholder={t.admin.placeholderModelName}
                className={inputClass}
              />
            </div>
          </div>

          {/* Model Label + Options — 2-column */}
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <label className={labelClass}>
                {t.admin.modelLabelLabel}
                <HelpTooltip text={t.admin.helpModelLabel} />
              </label>
              <Input
                value={formModel.modelLabel}
                disabled={saving}
                onChange={(e) => setFormModel({ ...formModel, modelLabel: e.target.value })}
                placeholder={t.admin.placeholderModelLabel}
                className={inputClass}
              />
            </div>

            <div className="space-y-1.5">
              <label className={labelClass}>
                {t.admin.optionsLabel}
                <HelpTooltip text={t.admin.helpOptions} />
              </label>
              <Input
                value={formModel.options}
                disabled={saving}
                onChange={(e) => setFormModel({ ...formModel, options: e.target.value })}
                placeholder={t.admin.placeholderOptions}
                className={inputClass}
              />
            </div>
          </div>

          {/* Default checkbox */}
          <div className="flex items-center gap-3 bg-surface-2 p-3.5 rounded-xl border border-border-subtle cursor-pointer hover:border-border-default transition-all duration-200">
            <Checkbox
              id="isDefaultCheckbox"
              checked={formModel.isDefault || false}
              disabled={saving}
              onCheckedChange={(checked) => setFormModel({ ...formModel, isDefault: checked === true })}
            >
              <span className="flex flex-col gap-0.5 select-none cursor-pointer">
                <span className="text-xs font-semibold text-fg">{t.admin.setAsDefaultLabel}</span>
                <span className="text-[10px] text-fg-muted">{t.admin.helpDefault}</span>
              </span>
            </Checkbox>
          </div>
        </ControlledForm>

        {/* Footer */}
        <footer className="flex items-center justify-between p-5 border-t border-border-subtle bg-surface-0">
          <span className="text-[10px] text-fg-muted select-none hidden sm:inline">Esc</span>
          <div className="flex items-center gap-3 ml-auto">
            <Button
              type="button"
              variant="outline"
              disabled={saving}
              onClick={onClose}
              className="rounded-xl border-border-default text-sm"
            >
              {t.admin.cancel}
            </Button>
            <Button
              type="submit"
              form="model-dialog-form"
              disabled={saving}
              className="rounded-xl bg-accent hover:bg-accent-hover text-on-accent font-semibold text-sm flex items-center justify-center gap-1.5 px-5"
            >
              {saving && <Icon name="refresh" className="h-3.5 w-3.5 animate-spin" />}
              <span>{formMode === "edit" ? t.admin.saveChanges : t.admin.createModel}</span>
            </Button>
          </div>
        </footer>
      </div>
    </div>
  );
}
