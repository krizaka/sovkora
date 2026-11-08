import React, { useState } from "react";
import { StyleSheet, View } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { Badge, Button, Header, Screen, Txt, useTheme } from "@krizaka/ui/native";
import type { StudioDetail } from "@krizaka/orazaka-shared";
import type { RootStackParamList } from "../navigation/AppNavigator";
import { useApi } from "../core/useApi";
import { useToken } from "../core/AuthContext";
import { apiRequest } from "../config/api";
import { errorText } from "../i18n/errorText";
import { Loading, LoadError } from "../components/AsyncState";
import { StudioGlyph } from "../components/StudioGlyph";
import { useI18n, type Key } from "../i18n";

type Props = NativeStackScreenProps<RootStackParamList, "StudioDetail">;

interface InstallationResponse {
  readonly id: string;
}

/**
 * One Studio, with what it costs and the button that installs it.
 *
 * Install sends an empty config: the blueprint's own declared defaults fill the gaps server-side,
 * so an actor can start without answering a form. Editing that configuration is a web-console job
 * for now — a JSON-Schema-driven form is not a thing to build twice.
 */
export function StudioDetailScreen({ route, navigation }: Props): React.JSX.Element {
  const { studioKey } = route.params;
  const { t, locale } = useI18n();
  const { theme } = useTheme();
  const bearer = useToken();
  const { data, isLoading, error, reload } = useApi<StudioDetail>(`/api/v1/studios/${studioKey}?locale=${locale}`);
  const [isInstalling, setIsInstalling] = useState(false);
  const [installError, setInstallError] = useState<string | null>(null);

  const header = (
    <Header
      title={data?.label ?? t("screens.studio")}
      size="inline"
      backLabel={t("common.back")}
      onBack={() => navigation.goBack()}
    />
  );

  if (isLoading)
    return (
      <Screen header={header}>
        <Loading />
      </Screen>
    );
  if (error !== null || !data)
    return (
      <Screen header={header}>
        <LoadError message={error ?? t("studios.notFound")} onRetry={reload} />
      </Screen>
    );

  const isToolkit = data.kind === "TOOLKIT";
  const install = async () => {
    // A toolkit Studio is part of the plan, there is nothing to install: its run starts on its key.
    if (isToolkit) {
      navigation.navigate("StudioRun", { installationId: null, label: data.label, studioKey });
      return;
    }
    setIsInstalling(true);
    setInstallError(null);
    try {
      const installation = await apiRequest<InstallationResponse>(`/api/v1/studios/${studioKey}/installations`, {
        method: "POST",
        token: bearer,
        body: { config: {} },
      });
      navigation.navigate("StudioRun", { installationId: installation.id, label: data.label, studioKey });
    } catch (cause: unknown) {
      setInstallError(errorText(cause, t, "studios.installFailed" as Key));
    } finally {
      setIsInstalling(false);
    }
  };

  return (
    <Screen header={header}>
      <View style={styles.hero}>
        <View style={[styles.glyph, { backgroundColor: theme.accentSoft }]}>
          <StudioGlyph studioKey={studioKey} color={theme.accentText} size={30} />
        </View>
        <Txt variant="title">{data.label}</Txt>
        {data.tagline ? <Txt tone="secondary">{data.tagline}</Txt> : null}
        <View style={styles.badges}>
          <Badge tone="accent">{t("studios.credits", { count: data.estimatedCredits })}</Badge>
          <Badge>{t(`studios.pricing.${data.pricing}` as Key)}</Badge>
        </View>
      </View>

      {data.description ? <Txt tone="secondary">{data.description}</Txt> : null}
      {installError !== null && (
        <Txt tone="danger" variant="caption">
          {installError}
        </Txt>
      )}

      {data.locked ? (
        // A locked Studio keeps its page and loses its button: the actor must be able to see what
        // they would be buying, which is the whole reason locked entries stay in the catalogue.
        <Txt tone="secondary" style={styles.locked}>
          {t("studios.notIncluded")}
        </Txt>
      ) : (
        <Button
          testID="studio-open"
          variant="primary"
          label={isToolkit ? t("studios.open") : t("studios.install")}
          size="lg"
          loading={isInstalling}
          onPress={() => void install()}
        />
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  hero: { gap: 8, alignItems: "flex-start" },
  glyph: { width: 56, height: 56, borderRadius: 16, alignItems: "center", justifyContent: "center", marginBottom: 4 },
  badges: { flexDirection: "row", gap: 8, marginTop: 4 },
  locked: { textAlign: "center" },
});
