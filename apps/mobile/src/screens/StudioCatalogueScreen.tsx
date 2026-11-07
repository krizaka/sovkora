import React from "react";
import { FlatList, StyleSheet, View } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { Badge, Card, Header, Screen, useTheme } from "@krizaka/ui/native";
import type { StudioSummary } from "@krizaka/orazaka-shared";
import type { RootStackParamList } from "../navigation/AppNavigator";
import { useApi } from "../core/useApi";
import { Loading, LoadError, Empty } from "../components/AsyncState";
import { StudioGlyph } from "../components/StudioGlyph";
import { useI18n, type Key } from "../i18n";

type Props = NativeStackScreenProps<RootStackParamList, "Studios">;

/**
 * The Studio marketplace (ADR-034).
 *
 * Locked Studios are listed rather than hidden, exactly as on web: the catalogue is the funnel,
 * and a Studio nobody can see is a product nobody buys. Names come from the platform in the app's language.
 */
export function StudioCatalogueScreen({ navigation }: Props): React.JSX.Element {
  const { t, locale } = useI18n();
  const { theme } = useTheme();
  const { data, isLoading, error, reload } = useApi<StudioSummary[]>(`/api/v1/studios?locale=${locale}`);

  const body = (): React.JSX.Element => {
    if (isLoading) return <Loading />;
    if (error !== null) return <LoadError message={error} onRetry={reload} />;
    if (!data || data.length === 0) return <Empty message={t("studios.empty")} />;
    return (
      <FlatList
        data={data}
        keyExtractor={(studio) => studio.studioKey}
        contentContainerStyle={styles.list}
        renderItem={({ item }) => (
          <Card.Root
            testID={`studio-${item.studioKey}`}
            onPress={() => navigation.navigate("StudioDetail", { studioKey: item.studioKey })}
            accessibilityLabel={item.label}
          >
            <Card.Body style={styles.card}>
              <View style={[styles.glyph, { backgroundColor: theme.accentSoft }]}>
                <StudioGlyph studioKey={item.studioKey} color={theme.accentText} />
              </View>
              <View style={styles.text}>
                <Card.Title numberOfLines={1}>{item.label}</Card.Title>
                {item.tagline ? <Card.Description numberOfLines={2}>{item.tagline}</Card.Description> : null}
                <View style={styles.badges}>
                  <Badge tone={item.pricing === "PAID" ? "warning" : "neutral"} size="sm">
                    {t(`studios.pricing.${item.pricing}` as Key)}
                  </Badge>
                  {item.locked ? (
                    <Badge tone="danger" size="sm">
                      {t("studios.locked")}
                    </Badge>
                  ) : null}
                </View>
              </View>
            </Card.Body>
          </Card.Root>
        )}
      />
    );
  };

  return (
    <Screen scroll={false} header={<Header title={t("tabs.studios")} />}>
      {body()}
    </Screen>
  );
}

const styles = StyleSheet.create({
  list: { padding: 16, gap: 12 },
  card: { flexDirection: "row", gap: 14, alignItems: "flex-start" },
  glyph: { width: 44, height: 44, borderRadius: 12, alignItems: "center", justifyContent: "center" },
  text: { flex: 1, gap: 4 },
  badges: { flexDirection: "row", gap: 6, marginTop: 4 },
});
