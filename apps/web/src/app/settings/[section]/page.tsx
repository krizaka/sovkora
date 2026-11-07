"use client";

import * as React from "react";
import { SettingsView } from "@/features/profile/components/SettingsView";

/** `/settings/<section>` — one section of the settings. */
export default function SettingsSectionPage({ params }: Readonly<{ params: Promise<{ section: string }> }>) {
  const { section } = React.use(params);
  return <SettingsView section={section} />;
}
