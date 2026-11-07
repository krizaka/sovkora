import { readStoreLinks } from "@/core/config/store-links";

const IOS = "https://apps.apple.com/app/orazaka/id1234567890";
const ANDROID = "https://play.google.com/store/apps/details?id=com.krizaka.orazaka";

describe("the store links of the mobile apps (APP_STORE_IOS_URL, APP_STORE_ANDROID_URL)", () => {
  it("are absent until configured: no button, no dead link", () => {
    expect(readStoreLinks({})).toEqual({ ios: null, android: null });
    expect(readStoreLinks({ APP_STORE_IOS_URL: "  ", APP_STORE_ANDROID_URL: "" })).toEqual({
      ios: null,
      android: null,
    });
  });

  it("each store appears by itself when its URL is set", () => {
    expect(readStoreLinks({ APP_STORE_IOS_URL: IOS })).toEqual({ ios: IOS, android: null });
    expect(readStoreLinks({ APP_STORE_ANDROID_URL: ANDROID })).toEqual({ ios: null, android: ANDROID });
    expect(readStoreLinks({ APP_STORE_IOS_URL: IOS, APP_STORE_ANDROID_URL: ANDROID })).toEqual({
      ios: IOS,
      android: ANDROID,
    });
  });

  it("never offers a TestFlight, an APK, a plain http or a look-alike host to the public", () => {
    for (const bad of [
      "https://testflight.apple.com/join/abc",
      "https://expo.dev/accounts/x/projects/y/builds/z",
      "https://example.test/orazaka.apk",
      "http://apps.apple.com/app/x/id1",
      "https://apps.apple.com.evil.test/app/x",
      "javascript:alert(1)",
      "not a url",
    ]) {
      expect(readStoreLinks({ APP_STORE_IOS_URL: bad }).ios).toBeNull();
      expect(readStoreLinks({ APP_STORE_ANDROID_URL: bad }).android).toBeNull();
    }
  });

  it("keeps each store to its own host", () => {
    expect(readStoreLinks({ APP_STORE_IOS_URL: ANDROID }).ios).toBeNull();
    expect(readStoreLinks({ APP_STORE_ANDROID_URL: IOS }).android).toBeNull();
  });
});
