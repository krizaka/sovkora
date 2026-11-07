import { loadPublicPrices, priceFor, type PublicPrice } from "@/features/landing/utils/public-prices";

const p = (over: Partial<PublicPrice>): PublicPrice => ({
  key: "orazaka.business",
  unit: "year",
  amountCents: 290000,
  availability: "public",
  limits: {},
  ...over,
});
const flat = (s: string | undefined) => (s ?? "").replace(/\s/g, " ");

describe("priceFor", () => {
  it("shows a public price as the catalogue's cents, in the visitor's language, the currency named", () => {
    const shown = priceFor([p({})], "business", "en");
    expect(shown?.unit).toBe("year");
    expect(flat(shown?.text)).toMatch(/2,900\.00/);
    expect(flat(priceFor([p({})], "business", "fr")?.text)).toMatch(/2 900,00/);
  });

  it("shows nothing for an edition that is preparing, internal or on quote, or that the catalogue does not hold", () => {
    expect(priceFor([p({ availability: "preparing", amountCents: null })], "business", "en")).toBeNull();
    expect(priceFor([p({ availability: "internal" })], "business", "en")).toBeNull();
    expect(priceFor([p({ amountCents: null })], "business", "en")).toBeNull();
    expect(priceFor([], "business", "en")).toBeNull();
  });

  it("a founder-limited price is shown; a zero price is left to the edition's own words", () => {
    expect(priceFor([p({ availability: "founder-limited" })], "business", "en")).not.toBeNull();
    expect(priceFor([p({ key: "orazaka.community", amountCents: 0, unit: "once" })], "community", "en")).toBeNull();
  });
});

describe("loadPublicPrices", () => {
  const realFetch = global.fetch;
  const stub = () => {
    const fn = jest.fn();
    global.fetch = fn as unknown as typeof fetch;
    return fn;
  };
  const OLD = { ...process.env };
  afterEach(() => {
    process.env = { ...OLD };
    global.fetch = realFetch;
  });

  it("reads billing's public catalogue with the service credential, parsed", async () => {
    process.env.ROUTER_URL = "http://edge";
    process.env.PAYMENTS_CATALOGUE_TOKEN = "t";
    const fetchMock = stub().mockResolvedValue({ ok: true, json: async () => [p({})] } as Response);
    expect(await loadPublicPrices()).toEqual([p({})]);
    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toBe("http://edge/api/v1/billing/prices");
    expect((init as RequestInit).headers).toMatchObject({ Authorization: "Bearer t" });
  });

  it("is empty — no figure, no failure — without a credential, on an error status, on a wrong shape, or when billing is down", async () => {
    delete process.env.PAYMENTS_CATALOGUE_TOKEN;
    const fetchMock = stub();
    expect(await loadPublicPrices()).toEqual([]);
    expect(fetchMock).not.toHaveBeenCalled();
    process.env.PAYMENTS_CATALOGUE_TOKEN = "t";
    fetchMock.mockResolvedValueOnce({ ok: false, status: 503 } as Response);
    expect(await loadPublicPrices()).toEqual([]);
    fetchMock.mockResolvedValueOnce({ ok: true, json: async () => [{ key: 1 }] } as Response);
    expect(await loadPublicPrices()).toEqual([]);
    fetchMock.mockRejectedValueOnce(new Error("down"));
    expect(await loadPublicPrices()).toEqual([]);
  });
});
