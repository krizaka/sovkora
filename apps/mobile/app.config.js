// The Expo project (owner + extra.eas.projectId) is in app.json — public, not a secret.
// One value differs per build and never lives in git, read by EAS Build (or a local build) from the environment:
//   GOOGLE_SERVICES_JSON  path to google-services.json (Firebase project `orazaka`, push on Android) — an EAS file
//                         environment variable
module.exports = function appConfig({ config }) {
  return {
    ...config,
    android: {
      ...config.android,
      ...(process.env.GOOGLE_SERVICES_JSON ? { googleServicesFile: process.env.GOOGLE_SERVICES_JSON } : {}),
    },
  };
};
