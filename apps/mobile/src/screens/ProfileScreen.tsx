import React, { useState } from "react";
import { StyleSheet, View } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import {
  ActionSheet,
  Avatar,
  Badge,
  Button,
  Header,
  Screen,
  Segmented,
  Txt,
  useTheme,
  type Mode,
} from "@krizaka/ui/native";
import { AutomationIcon, CreditsIcon, GlobeIcon, LogoutIcon, ServerIcon, ThemeIcon } from "@krizaka/icons/native";
import { spendableCredits, type Wallet } from "@krizaka/orazaka-shared";
import type { RootStackParamList } from "../navigation/AppNavigator";
import { useApi } from "../core/useApi";
import { useAuth } from "../core/AuthContext";
import { humanizeRole, roleName } from "../core/roles";
import { Loading, LoadError } from "../components/AsyncState";
import { SettingsGroup, SettingsRow } from "../ui/SettingsRow";
import { useI18n, type Key } from "../i18n";
import type { LanguagePreference } from "../i18n/locale";

/** What `/api/v1/profile` returns. */
interface UserDescriptor {
  readonly id: string;
  readonly username: string;
  readonly email: string;
  readonly authorities: readonly string[];
  readonly preferences: Record<string, unknown>;
}

type Props = NativeStackScreenProps<RootStackParamList, "Profile">;

const creditsIcon = (color: string) => <CreditsIcon size={20} color={color} />;
const connectorsIcon = (color: string) => <AutomationIcon size={20} color={color} />;
const systemIcon = (color: string) => <ServerIcon size={20} color={color} />;
const languageIcon = (color: string) => <GlobeIcon size={20} color={color} />;

/**
 * The signed-in actor, and the way out.
 *
 * The profile is re-read from the service rather than shown from the stored session: the token
 * carries who you were when you signed in, which is not the same as who you are now — a role
 * granted this morning would otherwise stay invisible until the next sign-in. The credits row appears only when the
 * billing service answers: nothing is shown in its place.
 */
export function ProfileScreen({ navigation }: Props): React.JSX.Element {
  const { t, locale, preference, setPreference } = useI18n();
  const { theme, mode, setMode } = useTheme();
  const { signOut } = useAuth();
  const profile = useApi<UserDescriptor>("/api/v1/profile");
  const wallet = useApi<Wallet>("/api/v1/billing/wallets/me");
  const [choosingLanguage, setChoosingLanguage] = useState(false);

  const data = profile.data;
  const roleWords = (authority: string): string => {
    const key = `profile.roles.${roleName(authority)}` as Key;
    const text = t(key);
    return text === key ? humanizeRole(authority) : text;
  };
  const languageName = (p: LanguagePreference): string => {
    if (p === "auto") return t("profile.languageAuto");
    return p === "fr" ? "Français" : "English";
  };
  const credits = wallet.data ? spendableCredits(wallet.data) : null;

  return (
    <Screen header={<Header title={t("profile.title")} />}>
      {profile.isLoading && <Loading />}
      {!profile.isLoading && (profile.error !== null || !data) && (
        <LoadError message={profile.error ?? t("profile.loadFailed")} onRetry={profile.reload} />
      )}
      {!profile.isLoading && profile.error === null && data && (
        <>
          <View style={styles.identity}>
            <Avatar
              size="xl"
              alt={t("profile.avatarOf", { name: data.username })}
              defaultAvatar={{ brand: "sovkora", seed: data.id }}
            />
            <Txt variant="title" style={styles.centre}>
              {data.username}
            </Txt>
            <Txt tone="secondary" style={styles.centre}>
              {data.email}
            </Txt>
            <View style={styles.roles}>
              {data.authorities.map((authority) => (
                <Badge key={authority} tone="accent">
                  {roleWords(authority)}
                </Badge>
              ))}
            </View>
          </View>

          {credits !== null && (
            <SettingsGroup title={t("profile.account")}>
              <SettingsRow
                icon={creditsIcon}
                label={t("profile.credits")}
                value={t("profile.creditsValue", {
                  count: credits.toLocaleString(locale === "fr" ? "fr-CA" : "en-CA"),
                })}
              />
            </SettingsGroup>
          )}

          <SettingsGroup title={t("profile.workspace")}>
            <SettingsRow
              icon={connectorsIcon}
              label={t("profile.connectors")}
              hint={t("profile.connectorsHint")}
              onPress={() => navigation.navigate("Connectors")}
            />
            <SettingsRow
              icon={systemIcon}
              label={t("profile.system")}
              hint={t("profile.systemHint")}
              onPress={() => navigation.navigate("System")}
            />
          </SettingsGroup>

          <SettingsGroup title={t("profile.preferences")}>
            <SettingsRow
              icon={languageIcon}
              label={t("profile.language")}
              value={languageName(preference)}
              onPress={() => setChoosingLanguage(true)}
            />
            <View style={styles.theme}>
              <View style={styles.themeLabel}>
                <ThemeIcon size={18} color={theme.accentText} />
                <Txt>{t("profile.theme")}</Txt>
              </View>
              <Segmented<Mode>
                aria-label={t("profile.theme")}
                value={mode}
                onValueChange={setMode}
                options={[
                  { value: "system", label: t("profile.themeSystem") },
                  { value: "light", label: t("profile.themeLight") },
                  { value: "dark", label: t("profile.themeDark") },
                ]}
              />
            </View>
          </SettingsGroup>

          <Button
            label={t("profile.signOut")}
            variant="outline"
            icon={<LogoutIcon size={18} color={theme.textPrimary} />}
            onPress={() => void signOut()}
          />

          <ActionSheet
            open={choosingLanguage}
            onOpenChange={setChoosingLanguage}
            title={t("profile.language")}
            cancelLabel={t("common.cancel")}
            actions={(["auto", "en", "fr"] as const).map((p) => ({
              label: `${languageName(p)}${p === preference ? "  ✓" : ""}`,
              onPress: () => setPreference(p),
            }))}
          />
        </>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  identity: { alignItems: "center", gap: 6, paddingVertical: 8 },
  centre: { textAlign: "center" },
  roles: { flexDirection: "row", flexWrap: "wrap", justifyContent: "center", gap: 8, marginTop: 4 },
  theme: { gap: 10, paddingHorizontal: 14, paddingVertical: 12 },
  themeLabel: { flexDirection: "row", alignItems: "center", gap: 10 },
});
