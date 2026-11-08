import config from "@/../app-links.json";

/**
 * The two files that make https://<host>/… open the native app: Apple's `apple-app-site-association` and Android's
 * `assetlinks.json`. Paths, bundle id and package come from `app-links.json` (read by `scripts/check-app-links.mjs`
 * too, so the CI rule and the served files cannot drift). The Team ID and the signing fingerprints are deployment
 * settings (`APP_IOS_TEAM_ID`, `APP_ANDROID_CERT_SHA256`): without them a file is not served — never a placeholder.
 */
export interface Aasa {
  applinks: { apps: string[]; details: { appIDs: string[]; components: { "/": string }[] }[] };
}

export function appleAppSiteAssociation(teamId: string | undefined): Aasa | null {
  if (!teamId || !/^[A-Z0-9]{10}$/.test(teamId)) return null;
  return {
    applinks: {
      apps: [],
      details: [{ appIDs: [`${teamId}.${config.iosBundleId}`], components: config.paths.map((p) => ({ "/": p })) }],
    },
  };
}

/** Comma-separated SHA-256 certificate fingerprints → the well-formed ones, upper-cased. */
export function androidFingerprints(raw: string | undefined): string[] {
  return (raw ?? "")
    .split(",")
    .map((s) => s.trim().toUpperCase())
    .filter((s) => /^([0-9A-F]{2}:){31}[0-9A-F]{2}$/.test(s));
}

export function assetLinks(fingerprints: string[]) {
  if (fingerprints.length === 0) return null;
  return [
    {
      relation: ["delegate_permission/common.handle_all_urls"],
      target: { namespace: "android_app", package_name: config.androidPackage, sha256_cert_fingerprints: fingerprints },
    },
  ];
}
