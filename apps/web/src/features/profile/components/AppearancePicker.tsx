"use client";

import * as React from "react";
import { RadioGroup } from "@krizaka/ui/radio-group";
import { Icon } from "@krizaka/orazaka-design-system";
import { useTranslation } from "@/core/context/LocaleContext";
import type { Appearance } from "@/core/hooks/useAppearance";
import { AppearanceOption } from "./AppearanceOption";

interface AppearancePickerProps {
  theme: Appearance;
  onThemeChange: (theme: Appearance) => void;
}

/**
 * The appearance picker of the profile: a `RadioGroup` from @krizaka/ui (one tab stop, the arrows move and choose)
 * of every appearance — the three modes and the named themes of the design system — each previewed in its own
 * tokens. Not a theme toggle: the mechanism stays @krizaka/ui/theme, reached through `useAppearance` by the caller.
 *
 * Interactions:
 * - Hover  → "Click to apply" overlay appears
 * - Choose → pulse animation + status bar slides in with confirmation
 * - Active → rotating conic gradient border + checkmark badge
 */
export function AppearancePicker({ theme, onThemeChange }: Readonly<AppearancePickerProps>) {
  const { t } = useTranslation();
  const [statusTheme, setStatusTheme] = React.useState<string | null>(null);
  const [pulsing, setPulsing] = React.useState<Appearance | null>(null);
  const timers = React.useRef<ReturnType<typeof setTimeout>[]>([]);
  React.useEffect(() => () => timers.current.forEach(clearTimeout), []);

  const options: { value: Appearance; label: string; desc: string; icon: React.ReactNode }[] = [
    {
      value: "system",
      label: t.settings.themeSystem,
      icon: <Icon name="laptop" className="h-3.5 w-3.5" />,
      desc: t.settings.themeSystemDesc,
    },
    {
      value: "light",
      label: t.settings.themeLight,
      icon: <Icon name="sun" className="h-3.5 w-3.5" />,
      desc: t.settings.themeLightDesc,
    },
    {
      value: "dark",
      label: t.settings.themeDark,
      icon: <Icon name="moon" className="h-3.5 w-3.5" />,
      desc: t.settings.themeDarkDesc,
    },
    {
      value: "electric",
      label: t.settings.themeElectric,
      icon: <Icon name="spark" className="h-3.5 w-3.5" />,
      desc: t.settings.themeElectricDesc,
    },
    {
      value: "custom",
      label: t.settings.themeCustom,
      icon: <Icon name="shield" className="h-3.5 w-3.5" />,
      desc: t.settings.themeCustomDesc,
    },
    {
      value: "cyberpunk",
      label: t.settings.themeCyberpunk,
      icon: <Icon name="cpu" className="h-3.5 w-3.5" />,
      desc: t.settings.themeCyberpunkDesc,
    },
    {
      value: "solarized",
      label: t.settings.themeSolarized,
      icon: <Icon name="compass" className="h-3.5 w-3.5" />,
      desc: t.settings.themeSolarizedDesc,
    },
  ];

  const choose = (value: string) => {
    const next = value as Appearance;
    if (next === theme) return;
    onThemeChange(next);
    setStatusTheme(options.find((o) => o.value === next)?.label ?? next);
    setPulsing(next);
    timers.current.forEach(clearTimeout);
    timers.current = [setTimeout(() => setPulsing(null), 350), setTimeout(() => setStatusTheme(null), 3000)];
  };

  const headingId = React.useId();

  return (
    <section className="space-y-3">
      <header>
        <hgroup>
          <h4 id={headingId} className="block text-sm font-semibold leading-tight text-fg">
            {t.settings.themeMode.replace(" (theme)", "")}
          </h4>
          <p className="text-xs leading-tight text-fg-secondary">{t.settings.themeChangesInstant}</p>
        </hgroup>
      </header>

      <RadioGroup.Root
        aria-labelledby={headingId}
        value={theme}
        onValueChange={choose}
        className="stagger-children grid grid-cols-2 gap-2.5 sm:grid-cols-4"
      >
        {options.map((opt) => (
          <AppearanceOption
            key={opt.value}
            value={opt.value}
            label={opt.label}
            desc={opt.desc}
            icon={opt.icon}
            isActive={theme === opt.value}
            pulse={pulsing === opt.value}
            clickToApplyLabel={t.settings.themeClickToApply}
          />
        ))}
      </RadioGroup.Root>

      {statusTheme && (
        <p
          key={statusTheme}
          role="status"
          className="theme-status-slide flex items-center gap-2 rounded-lg border border-success/15 bg-success/8 px-3 py-2"
        >
          <span className="relative flex h-2 w-2">
            <span className="absolute inline-flex h-full w-full animate-ping rounded-sm bg-success opacity-75" />
            <span className="relative inline-flex h-2 w-2 rounded-full bg-success" />
          </span>
          <span className="text-xs text-fg/70">
            <strong className="text-fg/90">{statusTheme}</strong> {t.settings.themeApplied}
          </span>
        </p>
      )}
    </section>
  );
}
