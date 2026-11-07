import React from "react";
import { StyleSheet, View } from "react-native";
import { Button, EmptyState, Spinner } from "@krizaka/ui/native";
import { useT } from "../i18n";

/** The waiting state, identical on every screen that loads: the @krizaka/ui/native spinner. */
export function Loading(): React.JSX.Element {
  const t = useT();
  return (
    <View style={styles.centre}>
      <Spinner label={t("common.loading")} />
    </View>
  );
}

interface LoadErrorProps {
  readonly message: string;
  readonly onRetry: () => void;
}

/**
 * The failed state, with a retry: the @krizaka/ui/native empty state and button.
 *
 * The web client factored the same three blocks into `StudioAsyncState` after they had drifted
 * apart across five screens; starting the mobile features with one component avoids repeating
 * that history here.
 */
export function LoadError({ message, onRetry }: Readonly<LoadErrorProps>): React.JSX.Element {
  const t = useT();
  return (
    <View style={styles.centre}>
      <EmptyState
        title={message}
        action={<Button label={t("common.retry")} variant="outline" size="sm" onPress={onRetry} />}
      />
    </View>
  );
}

interface EmptyProps {
  readonly message: string;
}

/** Nothing to show, which is not an error and must not look like one: the @krizaka/ui/native empty state. */
export function Empty({ message }: Readonly<EmptyProps>): React.JSX.Element {
  return (
    <View style={styles.centre}>
      <EmptyState title={message} />
    </View>
  );
}

const styles = StyleSheet.create({
  centre: { flex: 1, alignItems: "center", justifyContent: "center", padding: 24 },
});
