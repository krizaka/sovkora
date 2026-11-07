"use client";

import { IconButton } from "@krizaka/ui/button";
import * as React from "react";
import { Icon } from "@krizaka/orazaka-design-system";
import { useTranslation } from "@/core/context/LocaleContext";
import { MODEL_CATEGORY } from "@/core/constants/capability.constants";

import { Card } from "@krizaka/ui/card";

export interface CatalogModel {
  id?: number;
  modelName: string;
  modelLabel: string;
  category: string;
  options?: string;
  isDefault?: boolean;
  providerName?: string;
}

interface CategoryCardProps {
  category: string;
  models: CatalogModel[];
  onEdit: (model: CatalogModel) => void;
  onDelete: (id: number) => void;
}

export function CategoryCard({ category, models, onEdit, onDelete }: Readonly<CategoryCardProps>) {
  const { t } = useTranslation();

  const getCategoryLabel = React.useCallback(
    (cat: string) => {
      switch (cat) {
        case MODEL_CATEGORY.SPEECH:
          return t.admin.optSpeech;
        case MODEL_CATEGORY.IMAGE:
          return t.admin.optImage;
        case MODEL_CATEGORY.VIDEO:
          return t.admin.optVideo;
        case MODEL_CATEGORY.VISION:
          return t.admin.optVision;
        case MODEL_CATEGORY.AUDIO:
          return t.admin.optAudio;
        case "theme":
          return t.admin.optTheme;
        case "code":
          return t.admin.optCode;
        default:
          return cat;
      }
    },
    [t.admin],
  );

  return (
    <Card.Root className="p-6 bg-surface-1/70 border-border-subtle flex flex-col gap-4 shadow-sm hover:shadow-md transition-shadow duration-200">
      <h3 className="text-lg font-bold text-fg border-b pb-2 border-border-subtle capitalize flex items-center justify-between">
        <span>
          {getCategoryLabel(category)} {t.admin.modelsSuffix}
        </span>
        <span className="text-xs bg-surface-2 text-fg-secondary px-2.5 py-0.5 rounded-sm font-bold">
          {models.length}
        </span>
      </h3>
      {models.length === 0 ? (
        <p className="text-sm text-fg-secondary py-8 text-center italic">{t.admin.noModels}</p>
      ) : (
        <ul className="divide-y divide-border-subtle">
          {models.map((model) => (
            <li key={model.id} className="py-3 flex items-center justify-between group first:pt-0 last:pb-0">
              <article className="space-y-1 pr-4 flex-1 min-w-0">
                <header className="flex items-center gap-2">
                  <h4 className="text-sm font-bold text-fg truncate">{model.modelLabel}</h4>
                  {model.isDefault && (
                    <span className="text-[10px] bg-warning/20 text-warning font-bold px-2 py-0.5 rounded-sm flex items-center gap-1 shrink-0">
                      <Icon name="checkCircle" className="h-2.5 w-2.5" />
                      {t.admin.activeDefault}
                    </span>
                  )}
                </header>
                <p className="text-xs text-fg-muted font-mono truncate">{model.modelName}</p>
                {model.options && (
                  <footer className="text-[10px] text-warning font-semibold bg-warning/5 px-1.5 py-0.5 rounded inline-block">
                    {t.admin.optionsPrefix}
                    {model.options}
                  </footer>
                )}
              </article>
              <section className="flex items-center gap-1.5 shrink-0 opacity-80 group-hover:opacity-100 transition-opacity">
                <IconButton
                  variant="ghost"
                  size="sm"
                  className="w-8 px-0"
                  onClick={() => onEdit(model)}
                  label={t.admin.editModelTitle}
                >
                  <Icon name="edit" className="h-4 w-4" />
                </IconButton>
                <IconButton
                  variant="ghost"
                  size="sm"
                  className="w-8 px-0 hover:text-fg-danger"
                  onClick={() => model.id && onDelete(model.id)}
                  label={t.admin.deleteModelTitle}
                >
                  <Icon name="trash" className="h-4 w-4" />
                </IconButton>
              </section>
            </li>
          ))}
        </ul>
      )}
    </Card.Root>
  );
}
