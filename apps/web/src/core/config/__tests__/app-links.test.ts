import { androidFingerprints, appleAppSiteAssociation, assetLinks } from "@/core/config/app-links";

const FP = Array.from({ length: 32 }, (_, i) => i.toString(16).padStart(2, "0"))
  .join(":")
  .toUpperCase();

describe("app links files", () => {
  it("serves no AASA without a well-formed Team ID — never a placeholder", () => {
    expect(appleAppSiteAssociation(undefined)).toBeNull();
    expect(appleAppSiteAssociation("short")).toBeNull();
  });

  it("claims the bundle id under the Team ID, for every path of app-links.json", () => {
    const aasa = appleAppSiteAssociation("ABCDE12345");
    expect(aasa?.applinks.details[0].appIDs).toEqual(["ABCDE12345.com.krizaka.orazaka"]);
    expect(aasa?.applinks.details[0].components.map((c) => c["/"])).toEqual(
      expect.arrayContaining(["/studios", "/studios/*", "/chat", "/jobs", "/settings"]),
    );
  });

  it("keeps only well-formed SHA-256 fingerprints, upper-cased, and serves nothing without one", () => {
    expect(androidFingerprints(` ${FP.toLowerCase()} , nope`)).toEqual([FP]);
    expect(assetLinks([])).toBeNull();
    expect(assetLinks([FP])?.[0].target).toMatchObject({
      package_name: "com.krizaka.orazaka",
      sha256_cert_fingerprints: [FP],
    });
  });
});
