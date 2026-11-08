import type { LinkingOptions } from "@react-navigation/native";
import appLinks from "../../app-links.json";
import type { RootStackParamList } from "./AppNavigator";

/**
 * Every screen has an address (the second half of "deep links per screen"): the tabs and the Studios by the web's own
 * paths, the other settings sections of the web on the Settings tab, and what only the phone has under `orazaka://`
 * (starting a run, the sign-in flow, a password-reset token). The universal links the app CLAIMS stay `app-links.json`'s:
 * the web serves that list and CI checks it against the web's routes.
 *
 * Universal links / App Links: the addresses `app-links.json` claims (the same file the web app serves its
 * apple-app-site-association and assetlinks from) open the screen that shows the same thing. `orazaka://` stays for
 * deep links of our own. A path with no screen here opens the app where it was.
 */
export const linking: LinkingOptions<RootStackParamList> = {
  prefixes: [`https://${appLinks.host}`, "orazaka://"],
  config: {
    screens: {
      Tabs: {
        screens: {
          Studios: "studios",
          ChatStream: "chat",
          // The web's /studios/runs/<id> is a past run: the phone lists its runs on the Jobs tab.
          Jobs: { path: "jobs", alias: ["studios/runs/:runId"] },
          // account, appearance, workspace, api-keys, webhooks: the web's sections the phone shows as one Settings screen.
          Profile: {
            path: "settings",
            alias: [
              "settings/account",
              "settings/appearance",
              "settings/workspace",
              "settings/api-keys",
              "settings/webhooks",
            ],
          },
        },
      },
      StudioDetail: "studios/:studioKey",
      StudioRun: "studios/:studioKey/run",
      Connectors: { path: "settings/integrations" },
      System: "settings/system",
      Login: "login",
      Register: "register",
      ForgotPassword: "forgot-password",
      ResetPassword: "reset-password/:token",
    },
  },
};
