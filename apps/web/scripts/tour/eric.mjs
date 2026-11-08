// What Eric — the demo persona `orazaka demo seed` creates (orazaka-cli) — works on in the product tour.
//
// Everything here is invented: Lumen Atelier (a Montréal lighting studio — it sells in Canadian dollars), its lamps,
// its figures, its clients. No real person, company or
// brand. Eric pastes his documents into the conversation, so every answer the tour shows is grounded on text that
// is visible on screen — nothing is claimed that the recording does not show.

export const ERIC = {
  login: "eric.morel@example.com",
  // The local default of `orazaka demo seed`; DEMO_PASSWORD overrides both.
  password: "Demo-Lumen-2026",
};

const Q3_REPORT = `Lumen Atelier — Q3 report (internal)
Revenue: CA$412k (+18% vs Q2). Online shop: 46% of revenue, up from 38%.
Best seller: the Halo table lamp, 1,140 units; the new Dune floor lamp sold 220 units in its first six weeks.
Gross margin: 54% (down 2 points: ceramic glaze costs rose 9%).
Returns: 3.1% of orders, mostly shipping damage on floor lamps.
Team: one new ceramicist hired in August; the kiln upgrade is delayed to January.
Risks: one supplier for glazes; floor-lamp packaging.`;

const SALES_BY_REGION = `region,q2_revenue_k,q3_revenue_k,orders,avg_basket_cad
Quebec,96,118,402,294
Ontario,71,92,305,301
France,88,97,356,272
Belgium,24,31,118,263
Switzerland,19,38,96,396
United States,51,36,140,257`;

const RETURNS_POLICY = `Lumen Atelier — returns policy
1. Standard lamps can be returned within 30 days, unused, in their original box; we refund the full price.
2. Made-to-order lamps (custom glaze or size) cannot be returned, except for a manufacturing defect.
3. A lamp damaged in transit is replaced free of charge if reported within 7 days, with a photo.
4. Return shipping is paid by the client, except for defects and transit damage.`;

/** Held in the application before recording, so the dashboard and the history show real work (not recorded). */
export const PREPARED = [
  {
    prompt: `Summarise this quarterly report for the board in three short bullet points, then one risk to watch.\n\n${Q3_REPORT}`,
  },
  {
    prompt:
      "Draft a short, warm e-mail to Claire, a client whose order of 40 Halo lamps for her hotel will ship one week late because of the kiln upgrade. Offer free express delivery. Sign as Eric, Lumen Atelier.",
  },
  {
    prompt: `Here are our sales by region. What stands out, and where should we focus next quarter? Answer in four lines.\n\n${SALES_BY_REGION}`,
  },
];

/** The server tool the recorded chat is meant to call (orazaka-ai-engine#169): one ALREADY registered on dev, checked by videos-preflight.mjs before any paid run. */
export const PROOF_TOOL = "searchKnowledge";

/** The recorded chat: a question typed, the policy pasted under it, an answer sourced on the policy, then a search of the knowledge base (the tool call). */
export const RECORDED_QUESTION =
  "Ms Laurent wants to return her custom-glaze Halo lamp after 20 days. According to our policy, what do I tell her? Two sentences. Then search our knowledge base for any note on custom-glaze returns and tell me in one line whether it changes the answer.";
export const RECORDED_DOCUMENT = RETURNS_POLICY;

/** The recorded visual: a product shot for the shop, generated on this machine. */
export const RECORDED_VISUAL =
  "Product photograph of the Halo table lamp: a hand-glazed terracotta ceramic base with a natural linen shade, softly lit on a pale oak desk at dusk, window light, shallow depth of field, editorial catalogue style, no text";

/** The home card's visual: the same lamp, for the shop's product page. */
export const SHOWCASE_VISUAL =
  "Product photo of the ceramic table lamp, warm orange glow, dark slate plinth, ink-blue background";

/** The recorded voice-over: the same lamp, read aloud by the local speech engine. */
export const RECORDED_VOICE =
  "Meet Halo, our hand-glazed table lamp. Shaped in Montréal, fired twice, and lit to make an evening feel like home.";

/** The recorded clip: the same lamp, five seconds, a model in the cloud (Wan 2.2 A14B through Orazaka). */
export const RECORDED_VIDEO =
  "Slow cinematic push-in on a hand-glazed terracotta ceramic table lamp with a linen shade on a pale oak desk at dusk; the lamp glows warmer, golden light spreads across the wood grain, dust motes drift in the window light, shallow depth of field, editorial product film";
