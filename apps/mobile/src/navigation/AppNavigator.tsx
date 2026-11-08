/**
 * @file AppNavigator.tsx
 * @description Root navigation for the Sovkora mobile client.
 *
 * Two stacks, chosen by whether a session exists: the auth flow, or the app. Inside the app, four tabs — Studios, Chat,
 * Jobs, Settings — drawn by the kit's TabBar with @krizaka/icons; Connectors and System are reached from Settings. The
 * navigation theme is the kit's, so light and dark are the same code.
 */

import React, { useMemo } from "react";
import { View, ActivityIndicator, StyleSheet } from "react-native";
import { NavigationContainer, DefaultTheme, type Theme as NavTheme } from "@react-navigation/native";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { createBottomTabNavigator } from "@react-navigation/bottom-tabs";
import { useTheme } from "@krizaka/ui/native";

import { LoginScreen } from "../screens/LoginScreen";
import { RegisterScreen } from "../screens/RegisterScreen";
import { ForgotPasswordScreen } from "../screens/ForgotPasswordScreen";
import { ResetPasswordScreen } from "../screens/ResetPasswordScreen";
import { ChatStreamScreen } from "../screens/ChatStreamScreen";
import { StudioCatalogueScreen } from "../screens/StudioCatalogueScreen";
import { StudioDetailScreen } from "../screens/StudioDetailScreen";
import { StudioRunScreen } from "../screens/StudioRunScreen";
import { JobDetailScreen } from "../screens/JobDetailScreen";
import { JobsScreen } from "../screens/JobsScreen";
import { ProfileScreen } from "../screens/ProfileScreen";
import { ConnectorsScreen } from "../screens/ConnectorsScreen";
import { SystemScreen } from "../screens/SystemScreen";
import { AppTabBar } from "./AppTabBar";
import { useAuth } from "../core/AuthContext";
import { useKeepAlive } from "../core/keepAlive";
import { linking } from "./linking";
import { useSignupOpen } from "../config/signupOpen";

/** Routes reachable without a session. */
export type AuthStackParamList = {
  Login: undefined;
  Register: undefined;
  ForgotPassword: undefined;
  ResetPassword: { token: string };
};

/** Routes reachable only with one. */
export type AppStackParamList = {
  Tabs: undefined;
  StudioDetail: { studioKey: string };
  /** `installationId` is null for a TOOLKIT Studio (image, video, voice…): it has no installation, a run is started on its key. */
  StudioRun: { installationId?: string | null; label?: string; studioKey: string };
  /** One job, opened from the Jobs tab: what it made. */
  JobDetail: { jobId: string };
  Connectors: undefined;
  System: undefined;
};

/** The tab destinations. */
export type TabParamList = {
  Studios: undefined;
  ChatStream: undefined;
  Jobs: undefined;
  Profile: undefined;
};

/**
 * The union every screen types its props against.
 *
 * One list rather than three because a screen should not have to know which stack hosts it — and
 * because `ResetPassword` is reachable from the auth flow while `Profile` is not.
 */
export type RootStackParamList = AuthStackParamList & AppStackParamList & TabParamList;

const AuthStack = createNativeStackNavigator<AuthStackParamList>();
const AppStack = createNativeStackNavigator<AppStackParamList>();
const Tab = createBottomTabNavigator<TabParamList>();

const renderTabBar: React.ComponentProps<typeof Tab.Navigator>["tabBar"] = (props) => <AppTabBar {...props} />;

function TabNavigator(): React.JSX.Element {
  return (
    <Tab.Navigator tabBar={renderTabBar} screenOptions={{ headerShown: false }}>
      <Tab.Screen name="Studios" component={StudioCatalogueScreen} />
      <Tab.Screen name="ChatStream" component={ChatStreamScreen} />
      <Tab.Screen name="Jobs" component={JobsScreen} />
      <Tab.Screen name="Profile" component={ProfileScreen} />
    </Tab.Navigator>
  );
}

export function AppNavigator(): React.JSX.Element {
  const { session, isRestoring } = useAuth();
  const { theme: c, scheme } = useTheme();
  useKeepAlive(session?.token ?? null);
  const signupOpen = useSignupOpen();

  const navTheme = useMemo<NavTheme>(
    () => ({
      ...DefaultTheme,
      dark: scheme === "dark",
      colors: {
        ...DefaultTheme.colors,
        primary: c.accent,
        background: c.surface0,
        card: c.surface1,
        text: c.textPrimary,
        border: c.borderSubtle,
        notification: c.accent,
      },
    }),
    [c, scheme],
  );

  // The stored session is read asynchronously. Rendering the auth stack during that gap would
  // flash the login screen at an already-signed-in user on every cold start.
  if (isRestoring) {
    return (
      <View style={[styles.splash, { backgroundColor: c.surface0 }]}>
        <ActivityIndicator color={c.accent} />
      </View>
    );
  }

  const screenOptions = { headerShown: false, contentStyle: { backgroundColor: c.surface0 } } as const;

  return (
    <NavigationContainer theme={navTheme} linking={linking}>
      {session === null ? (
        <AuthStack.Navigator screenOptions={screenOptions}>
          <AuthStack.Screen name="Login" component={LoginScreen} />
          {signupOpen && <AuthStack.Screen name="Register" component={RegisterScreen} />}
          <AuthStack.Screen name="ForgotPassword" component={ForgotPasswordScreen} />
          <AuthStack.Screen name="ResetPassword" component={ResetPasswordScreen} />
        </AuthStack.Navigator>
      ) : (
        <AppStack.Navigator screenOptions={screenOptions}>
          <AppStack.Screen name="Tabs" component={TabNavigator} />
          <AppStack.Screen name="StudioDetail" component={StudioDetailScreen} />
          <AppStack.Screen name="StudioRun" component={StudioRunScreen} />
          <AppStack.Screen name="JobDetail" component={JobDetailScreen} />
          <AppStack.Screen name="Connectors" component={ConnectorsScreen} />
          <AppStack.Screen name="System" component={SystemScreen} />
        </AppStack.Navigator>
      )}
    </NavigationContainer>
  );
}

const styles = StyleSheet.create({
  splash: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
  },
});
