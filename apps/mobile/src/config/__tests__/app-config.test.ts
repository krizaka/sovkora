import { readFileSync } from "fs";
import { join } from "path";

// app.json + app.config.js are what EAS Build reads: the Expo project (owner + id) the app's builds belong to, and
// Firebase's google-services.json taken from the build environment only.
const root = join(__dirname, "../../..");
const { expo } = JSON.parse(readFileSync(join(root, "app.json"), "utf8")) as {
  expo: {
    owner: string;
    slug: string;
    extra: { eas: { projectId: string } };
    ios: { bundleIdentifier: string };
    android: { package: string };
  };
};
// eslint-disable-next-line @typescript-eslint/no-require-imports
const appConfig = require(join(root, "app.config.js")) as (ctx: {
  config: typeof expo;
}) => typeof expo & { android: { googleServicesFile?: string } };

describe("app config — EAS project", () => {
  const saved = process.env.GOOGLE_SERVICES_JSON;
  afterEach(() => {
    if (saved === undefined) delete process.env.GOOGLE_SERVICES_JSON;
    else process.env.GOOGLE_SERVICES_JSON = saved;
  });

  it("is linked to the Expo project @oussamaabid/orazaka-mobile", () => {
    expect(expo.owner).toBe("oussamaabid");
    expect(expo.slug).toBe("orazaka-mobile");
    expect(expo.extra.eas.projectId).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);
  });

  it("keeps the store identifiers of the app", () => {
    expect(expo.ios.bundleIdentifier).toBe("com.krizaka.orazaka");
    expect(expo.android.package).toBe("com.krizaka.orazaka");
  });

  it("takes google-services.json from the build environment only, never from the repository", () => {
    delete process.env.GOOGLE_SERVICES_JSON;
    expect(appConfig({ config: expo }).android.googleServicesFile).toBeUndefined();
    process.env.GOOGLE_SERVICES_JSON = "/eas/google-services.json";
    const config = appConfig({ config: expo });
    expect(config.android.googleServicesFile).toBe("/eas/google-services.json");
    expect(config.android.package).toBe("com.krizaka.orazaka");
  });
});
