import React from "react";
import { AppState, Linking, Platform, View } from "react-native";
import { pickMessage } from "@krizaka/discovery";
import { DiscoveryGate as Gate, DiscoveryProvider, type DiscoveryProviderProps } from "@krizaka/discovery/react";
import { ForceUpdateScreen, MaintenanceScreen, OrazakaMark, useTheme } from "@krizaka/ui/native";
import { discovery, UPDATE_URL } from "../config/discovery";
import { useI18n } from "../i18n";

/** The app came back to the foreground → one manifest refresh (a stable function: the provider subscribes once). */
export const onForeground: NonNullable<DiscoveryProviderProps["subscribeForeground"]> = (callback) => {
  const subscription = AppState.addEventListener("change", (state) => state === "active" && callback());
  return () => subscription.remove();
};

const retry = () => void discovery.refresh();

type GateProps = React.ComponentProps<typeof Gate>;
type UpdateGate = Parameters<NonNullable<GateProps["update"]>>[0];
type MaintenanceGate = Parameters<NonNullable<GateProps["maintenance"]>>[0];

function ForceUpdate({ gate }: Readonly<{ gate: UpdateGate }>): React.JSX.Element {
  const { t, locale } = useI18n();
  return (
    <ForceUpdateScreen
      fill
      mark={<OrazakaMark size={64} />}
      title={t("update.forceTitle")}
      message={pickMessage(gate.message, locale)}
      versionLabel={t("update.version", { version: gate.latestAppVersion })}
      updateLabel={t("update.update")}
      onUpdate={() => void Linking.openURL(Platform.OS === "ios" ? UPDATE_URL.ios : UPDATE_URL.android)}
      retryLabel={t("common.retry")}
      onRetry={retry}
    />
  );
}

function Maintenance({ gate }: Readonly<{ gate: MaintenanceGate }>): React.JSX.Element {
  const { t, locale } = useI18n();
  return (
    <MaintenanceScreen
      fill
      mark={<OrazakaMark size={64} />}
      title={t("update.maintenanceTitle")}
      message={pickMessage(gate.message, locale)}
      retryLabel={t("common.retry")}
      onRetry={retry}
    />
  );
}

const renderUpdate = (gate: UpdateGate): React.JSX.Element => <ForceUpdate gate={gate} />;
const renderMaintenance = (gate: MaintenanceGate): React.JSX.Element => <Maintenance gate={gate} />;

/** What the signed manifest decided (krizaka/.github ADR-003): a build below `minAppVersion` or a maintenance window replaces the app. */
export function DiscoveryGate({ children }: Readonly<{ children: React.ReactNode }>): React.JSX.Element {
  return (
    <Gate product="orazaka" update={renderUpdate} maintenance={renderMaintenance}>
      {children}
    </Gate>
  );
}

/** Starts discovery (the cache answers at once; a first launch waits at most a few seconds) and keeps it fresh while the app lives. */
export function Discovery({ children }: Readonly<{ children: React.ReactNode }>): React.JSX.Element {
  const { theme } = useTheme();
  return (
    <DiscoveryProvider
      discovery={discovery}
      subscribeForeground={onForeground}
      loading={<View style={{ flex: 1, backgroundColor: theme.surface0 }} />}
    >
      {children}
    </DiscoveryProvider>
  );
}
