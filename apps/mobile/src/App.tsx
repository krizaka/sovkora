import React from "react";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { AppNavigator } from "./navigation/AppNavigator";
import { AuthProvider } from "./core/AuthContext";
import { AppCharts } from "./components/AppCharts";
import { Discovery, DiscoveryGate } from "./components/DiscoveryGate";
import { LocaleProvider } from "./i18n";
import { Themed } from "./ui/Themed";

/**
 * The root: the device's language (or the one pinned in Profile), the @krizaka/ui/native theme with the Sovkora brand
 * in light and dark, the backend's address (the signed manifest of krizaka.com, with its update and maintenance gate),
 * the charts, the session, then the navigator.
 */
export default function App(): React.JSX.Element {
  return (
    <SafeAreaProvider>
      <Themed>
        <LocaleProvider>
          <Discovery>
            <DiscoveryGate>
              <AppCharts>
                <AuthProvider>
                  <AppNavigator />
                </AuthProvider>
              </AppCharts>
            </DiscoveryGate>
          </Discovery>
        </LocaleProvider>
      </Themed>
    </SafeAreaProvider>
  );
}
