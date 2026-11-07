import { imageMimeOf, uploadAsset } from "../assetUpload";
import { parseInputSchema } from "../inputSchema";

describe("uploadAsset — a photo becomes an asset id", () => {
  afterEach(() => jest.restoreAllMocks());

  it("posts the photo as multipart with the bearer and returns the asset id", async () => {
    const calls: { url: string; init: RequestInit }[] = [];
    global.fetch = jest.fn(async (url: unknown, init?: RequestInit) => {
      calls.push({ url: String(url), init: init ?? {} });
      return { ok: true, status: 200, text: async () => JSON.stringify({ assetId: "asset-7" }) } as Response;
    }) as unknown as typeof fetch;

    await expect(uploadAsset({ uri: "file:///tmp/me.png", fileName: "me.png", token: "tok" })).resolves.toBe("asset-7");
    expect(calls[0]!.url).toMatch(/\/api\/v1\/media\/upload$/);
    expect(calls[0]!.init.method).toBe("POST");
    expect(calls[0]!.init.headers).toEqual({ Authorization: "Bearer tok" });
    expect(calls[0]!.init.body).toBeInstanceOf(FormData);
  });

  it.each([
    [{ ok: false, status: 413, text: async () => "" }],
    [{ ok: true, status: 200, text: async () => "{}" }],
    [{ ok: true, status: 200, text: async () => "<html>" }],
  ])("refuses an answer without an asset id", async (answer) => {
    global.fetch = jest.fn(async () => answer as Response) as unknown as typeof fetch;
    await expect(uploadAsset({ uri: "file:///x.jpg", token: null })).rejects.toThrow(/Upload failed/);
  });

  it("guesses the type from the file name", () => {
    expect(imageMimeOf("a.PNG")).toBe("image/png");
    expect(imageMimeOf("a.heic")).toBe("image/heic");
    expect(imageMimeOf(null)).toBe("image/jpeg");
  });
});

describe("parseInputSchema — asset inputs", () => {
  const raw = JSON.stringify({
    properties: {
      image: { type: "string", format: "asset:image" },
      legacy: { type: "string", format: "asset-id", contentMediaType: "image/*" },
      prompt: { type: "string", format: "prose" },
    },
  });
  it("marks the image fields, not the prose", () => {
    const kinds = Object.fromEntries(parseInputSchema(raw).map((f) => [f.key, f.assetKind]));
    expect(kinds).toEqual({ image: "image", legacy: "image", prompt: undefined });
  });
  it("reads a bare `image` (a blueprint published before the pack declared it) as a photo", () => {
    expect(parseInputSchema(JSON.stringify({ properties: { image: { type: "string" } } }))[0]!.assetKind).toBe("image");
  });
});
