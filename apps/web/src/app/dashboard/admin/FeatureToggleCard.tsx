"use client";

import React from "react";
import { Card } from "@krizaka/ui/card";
import { Switch } from "@krizaka/ui/switch";
import type { TranslationDictionary } from "@/core/context/LocaleContext";

interface Feature {
  featureKey: string;
  isEnabled: boolean;
}

interface FeatureToggleCardProps {
  features: Feature[];
  onToggle: (featureKey: string, currentStatus: boolean) => void;
  t: TranslationDictionary;
}

/**
 * The capabilities registry overrides: one switch per database-configured feature flag. A product composite on the
 * @krizaka/ui card and switch (role="switch", Space and Enter toggle).
 */
export const FeatureToggleCard: React.FC<FeatureToggleCardProps> = ({ features, onToggle, t }) => (
  <Card.Root className="rounded-2xl border-border-subtle bg-surface-1/70 shadow-sm ">
    <Card.Body padding="lg" className="gap-4">
      <Card.Title className="line-clamp-none border-b border-border-subtle pb-2 text-lg font-bold group-hover:text-fg">
        {t.admin.featureOverridesTitle}
      </Card.Title>
      {features.length === 0 ? (
        <p className="py-4 text-center text-sm italic text-fg-secondary">{t.admin.featureNoOverrides}</p>
      ) : (
        <ul className="grid grid-cols-1 gap-4 md:grid-cols-2">
          {features.map((feat) => {
            const id = `feature-override-${feat.featureKey}`;
            return (
              <li
                key={feat.featureKey}
                className="flex items-center justify-between rounded-xl border border-border-subtle bg-surface-0 p-3.5"
              >
                <label htmlFor={id} className="flex min-w-0 cursor-pointer flex-col gap-0.5 pr-2">
                  <span className="block truncate font-mono text-xs font-bold text-fg">{feat.featureKey}</span>
                  <span className="text-[10px] text-fg-secondary">{t.admin.featureOverrideEnabled}</span>
                </label>
                <Switch
                  id={id}
                  size="sm"
                  checked={feat.isEnabled}
                  onCheckedChange={() => onToggle(feat.featureKey, feat.isEnabled)}
                />
              </li>
            );
          })}
        </ul>
      )}
    </Card.Body>
  </Card.Root>
);
