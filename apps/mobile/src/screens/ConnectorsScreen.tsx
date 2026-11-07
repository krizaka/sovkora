import React from "react";
import { FlatList, StyleSheet } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { Badge, Card, Header, Screen, Txt } from "@krizaka/ui/native";
import type { RootStackParamList } from "../navigation/AppNavigator";
import { humanizeCode } from "../core/humanize";
import { useApi } from "../core/useApi";
import { Loading, LoadError, Empty } from "../components/AsyncState";
import { useT } from "../i18n";

/** One connector and whether this actor has configured it. */
interface UserCredential {
  readonly providerName: string;
  readonly configured: boolean;
}

type Props = NativeStackScreenProps<RootStackParamList, "Connectors">;

/**
 * The actor's connectors and their configured state.
 *
 * Read from `/api/v1/credentials`, deliberately — the web `ConnectorCatalogue` renders a hardcoded
 * array with local state, so it shows the same list to everyone whether or not they have set any
 * of it up. This screen asks the service instead; the secret itself never leaves it, only whether
 * one is on file.
 */
export function ConnectorsScreen({ navigation }: Props): React.JSX.Element {
  const t = useT();
  const { data, isLoading, error, reload } = useApi<UserCredential[]>("/api/v1/credentials");

  const body = (): React.JSX.Element => {
    if (isLoading) return <Loading />;
    if (error !== null) return <LoadError message={error} onRetry={reload} />;
    if (!data || data.length === 0) return <Empty message={t("connectors.empty")} />;
    return (
      <FlatList
        contentContainerStyle={styles.list}
        data={data}
        keyExtractor={(credential) => credential.providerName}
        renderItem={({ item }) => (
          <Card.Root>
            <Card.Body padding="sm" style={styles.row}>
              <Txt variant="label">{humanizeCode(item.providerName)}</Txt>
              <Badge tone={item.configured ? "success" : "neutral"} dot={item.configured}>
                {item.configured ? t("connectors.configured") : t("connectors.notConfigured")}
              </Badge>
            </Card.Body>
          </Card.Root>
        )}
      />
    );
  };

  return (
    <Screen
      scroll={false}
      header={
        <Header
          title={t("screens.connectors")}
          size="inline"
          backLabel={t("common.back")}
          onBack={() => navigation.goBack()}
        />
      }
    >
      {body()}
    </Screen>
  );
}

const styles = StyleSheet.create({
  list: { padding: 16, gap: 10 },
  row: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 8 },
});
