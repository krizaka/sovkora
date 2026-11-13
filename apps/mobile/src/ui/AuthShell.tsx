import React from "react";
import { StyleSheet, View } from "react-native";
import { Mascot, SovkoraMark, Screen, Txt } from "@krizaka/ui/native";

import { useT } from "../i18n";

interface Props {
  readonly title: string;
  readonly subtitle: string;
  readonly children: React.ReactNode;
}

/**
 * The frame of every signed-out screen: the Sovkora mark and its duck (the mascot of Sovkora everywhere, orazaka#516), a title, a line,
 * the honest « in development » status the family says, then the form — the kit's `Screen`.
 */
export function AuthShell({ title, subtitle, children }: Readonly<Props>): React.JSX.Element {
  const t = useT();
  return (
    <Screen padding={24} contentStyle={styles.content}>
      <View style={styles.head}>
        <SovkoraMark size={40} />
        <Mascot bird="duck" mood="welcome" size={96} />
        <Txt variant="display" style={styles.centre}>
          {title}
        </Txt>
        <Txt tone="secondary" style={styles.centre}>
          {subtitle}
        </Txt>
        <Txt tone="secondary" style={styles.centre} testID="development-status">
          {t("auth.development")}
        </Txt>
      </View>
      {children}
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: { gap: 16, justifyContent: "center", flexGrow: 1 },
  head: { alignItems: "center", gap: 8, marginBottom: 8 },
  centre: { textAlign: "center" },
});
