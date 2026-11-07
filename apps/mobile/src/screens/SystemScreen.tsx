import React, { useCallback, useEffect, useState, useSyncExternalStore } from "react";
import { StyleSheet } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import Constants from "expo-constants";
import { Button, DescriptionList, Header, Screen } from "@krizaka/ui/native";
import type { RootStackParamList } from "../navigation/AppNavigator";
import { apiRequest, edgeBase } from "../config/api";
import { discovery, orazakaWebUrl } from "../config/discovery";
import { useAuth } from "../core/AuthContext";
import { useT } from "../i18n";

type Props = NativeStackScreenProps<RootStackParamList, "System">;

/** The edge URL → its host — what a person recognises, not the path. */
export function hostOf(url: string): string {
  const bare = url.replace(/^https?:\/\//, "");
  const slash = bare.indexOf("/");
  return slash < 0 ? bare : bare.slice(0, slash);
}

/**
 * The connection, measured: which server the app talks to, whether it answers right now (and how fast — a real round
 * trip, timed on this phone), who is signed in and which interceptors the session carries. Nothing here is simulated.
 */
export function SystemScreen({ navigation }: Props): React.JSX.Element {
  const t = useT();
  const { session } = useAuth();
  const [latency, setLatency] = useState<number | "down" | null>(null);
  // Where the addresses come from, live: a manifest edit shows here without a restart (krizaka/.github ADR-003).
  useSyncExternalStore(discovery.subscribe, discovery.version, discovery.version);
  const { source } = discovery.getService("orazaka");

  const probe = useCallback(async () => {
    setLatency(null);
    const started = Date.now();
    try {
      await apiRequest("/api/v1/profile", { token: session?.token ?? null });
      setLatency(Date.now() - started);
    } catch {
      setLatency("down");
    }
  }, [session?.token]);

  useEffect(() => {
    void probe();
  }, [probe]);

  let status = t("system.reachable", { ms: latency ?? 0 });
  if (latency === null) status = t("common.loading");
  else if (latency === "down") status = t("system.unreachable");
  const interceptions = session?.activeInterceptions ?? [];

  return (
    <Screen
      header={
        <Header
          title={t("screens.system")}
          size="inline"
          backLabel={t("common.back")}
          onBack={() => navigation.goBack()}
        />
      }
    >
      <DescriptionList
        layout="stacked"
        items={[
          { term: t("system.server"), details: hostOf(edgeBase()) },
          { term: t("system.website"), details: hostOf(orazakaWebUrl()) },
          { term: t("system.source"), details: t(`system.sources.${source}`) },
          { term: t("system.status"), details: status },
          { term: t("system.account"), details: session?.email ?? "—" },
          { term: t("system.version"), details: Constants.expoConfig?.version ?? "—" },
          {
            term: t("system.interceptions"),
            details: interceptions.length > 0 ? String(interceptions.length) : t("system.none"),
          },
        ]}
      />
      <Button variant="secondary" label={t("system.refresh")} onPress={() => void probe()} style={styles.button} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  button: { marginTop: 8 },
});
