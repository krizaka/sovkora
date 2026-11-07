import React, { useCallback, useEffect, useState } from "react";
import { useColorScheme } from "react-native";
import { StatusBar } from "expo-status-bar";
import * as SecureStore from "expo-secure-store";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { ThemeProvider, useTheme, type Mode } from "@krizaka/ui/native";
import { brands } from "@krizaka/tokens/native";

const MODE_KEY = "orazaka.theme";

/** A stored value → a mode; anything unexpected follows the system. */
export function asMode(value: string | null | undefined): Mode {
  return value === "light" || value === "dark" ? value : "system";
}

/** The status bar's icons follow the resolved scheme: light icons on dark, dark on light. */
function Bar(): React.JSX.Element {
  const { scheme } = useTheme();
  return <StatusBar style={scheme === "dark" ? "light" : "dark"} />;
}

/**
 * The @krizaka/ui/native theme with the Sovkora brand (the orange of the mark, from @krizaka/tokens/native) over the
 * platform's roles. Light and dark are both first-class: the app follows the system unless Profile pinned one, and the
 * choice is remembered on the device. The safe-area insets are handed to the kit so its bars clear the home indicator.
 */
export function Themed({ children }: Readonly<{ children: React.ReactNode }>): React.JSX.Element {
  const insets = useSafeAreaInsets();
  useColorScheme();
  const [mode, setMode] = useState<Mode>("system");

  useEffect(() => {
    void SecureStore.getItemAsync(MODE_KEY)
      .then((stored) => setMode(asMode(stored)))
      .catch(() => undefined);
  }, []);

  const onModeChange = useCallback((next: Mode) => {
    setMode(next);
    void SecureStore.setItemAsync(MODE_KEY, next).catch(() => undefined);
  }, []);

  // `system` is resolved by the kit from the device; reading it here re-renders the status bar on a change.

  return (
    <ThemeProvider mode={mode} onModeChange={onModeChange} overrides={brands.orazaka} insets={insets}>
      <Bar />
      {children}
    </ThemeProvider>
  );
}
