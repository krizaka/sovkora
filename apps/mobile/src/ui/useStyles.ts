import { useMemo } from "react";
import { useTheme, type Theme } from "@krizaka/ui/native";

/**
 * The styles of a component for the current theme: `const styles = useStyles(makeStyles)` with
 * `const makeStyles = (c: Theme) => StyleSheet.create({ … })`. Dark and light are the same code; no colour is typed
 * in a screen.
 */
export function useStyles<T>(make: (theme: Theme) => T): T {
  const { theme } = useTheme();
  return useMemo(() => make(theme), [make, theme]);
}
