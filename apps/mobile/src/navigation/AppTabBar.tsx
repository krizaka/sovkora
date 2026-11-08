import React from "react";
import type { BottomTabBarProps } from "@react-navigation/bottom-tabs";
import { TabBar, type TabBarItem } from "@krizaka/ui/native";
import { ChatIcon, ClockIcon, SettingsIcon, StudioIcon } from "@krizaka/icons/native";
import { useT } from "../i18n";
import type { TabParamList } from "./AppNavigator";

type Tab = keyof TabParamList;

/**
 * The four sections of the app, in order — each an @krizaka/icons glyph and a word that is never truncated. The same
 * sections and words as the web rail (orazaka-web-client core/config/app-sections.ts), which adds Home, Packs and Usage:
 * Studios · Chat · Jobs · Settings (the account, its connectors and the system — the `Profile` route).
 */
function useItems(): readonly TabBarItem<Tab>[] {
  const t = useT();
  return [
    { value: "Studios", label: t("tabs.studios"), icon: (_s, color) => <StudioIcon size={24} color={color} /> },
    { value: "ChatStream", label: t("tabs.chat"), icon: (_s, color) => <ChatIcon size={24} color={color} /> },
    { value: "Jobs", label: t("tabs.jobs"), icon: (_s, color) => <ClockIcon size={24} color={color} /> },
    { value: "Profile", label: t("tabs.settings"), icon: (_s, color) => <SettingsIcon size={24} color={color} /> },
  ];
}

/** React Navigation's `tabBar`, drawn by the kit's TabBar (safe area, accent, a tick on change). */
export function AppTabBar({ state, navigation }: Readonly<BottomTabBarProps>): React.JSX.Element {
  const t = useT();
  const items = useItems();
  const current = state.routes[state.index]?.name as Tab;
  const go = (name: Tab) => {
    const route = state.routes.find((r) => r.name === name);
    if (!route) return;
    const event = navigation.emit({ type: "tabPress", target: route.key, canPreventDefault: true });
    if (!event.defaultPrevented) navigation.navigate(name);
  };
  return <TabBar items={items} value={current} onValueChange={go} aria-label={t("tabs.label")} />;
}
