# Sources of the Québec lease ruleset — verification of 2026-11-12

Issue: krizaka-internal#3127 (unblocks #3128). **Nothing is wired from this file**: every « use » below waits for the
founder's go and the lawyer review (krizaka-restricted#312). Legend: ✔ read on the primary page or API; ◐ reported by a
search tool (Perplexity) and not read verbatim on the primary page — to confirm; ✘ not found.

Limits of this run: the CanLII and LégisQuébec pages are behind a bot wall or are rendered by script, and were not
bypassed. Their wording therefore comes from the GitHub API doc and from cited search answers, and is marked ◐.

## Table

| Source | Exists | Coverage | Access | Licence / terms (page) | Commercial use | Recommendation |
|---|---|---|---|---|---|---|
| **LégisQuébec** statute text | Yes — <https://www.legisquebec.gouv.qc.ca> | Consolidated statutes and regulations (C.c.Q., T-15.01 r. 3) | Website; the open-data XML (below) | ◐ « Pour reproduire, télécharger, stocker, traduire, adapter, publier, communiquer par télécommunication ou représenter en public des informations contenues dans le site LégisQuébec […] il faut obtenir préalablement une autorisation des Publications du Québec et payer les redevances associées » — exception for hyperlinks (<https://www.legisquebec.gouv.qc.ca/fr/contenu/droit_auteur>) | **No, without authorisation and royalties** (◐) | **Use only for links** (and article numbers); text reuse needs Publications du Québec's authorisation |
| **Données Québec « Lois et règlements codifiés du Québec »** (the XML our law corpus reads) | Yes — <https://www.donneesquebec.ca/recherche/dataset/https-donneesquebec-ca-recherche-dataset-edit-lois-et-reglements-quebec> | All consolidated Québec statutes and regulations | Open-data XML download | ✔ CKAN API field `license_id: cc-by-nc-nd` — « Attribution, pas d'utilisation commerciale, pas d'oeuvre dérivée (CC-BY-NC-ND 4.0) » (`/recherche/api/3/action/package_search`, checked 2026-11-12) | **No** (NC), **no derivative** (ND) | **Do not use for a paid product** until the lawyer clears or a licence is obtained. Our `derivation/law/*.json` and `infra/migrations/2026-10-24-law-corpus.sql` are built on it: see Q1 |
| **Consolidation status** | — | — | — | ◐ « Cette codification administrative n'a pas de valeur officielle. » / « Seuls les textes publiés à la Gazette officielle du Québec ou dans les Lois annuelles ont valeur officielle. » (wording to confirm on <https://www.quebec.ca/gouvernement/relations-canadiennes/codification-administrative>); the LégisQuébec FAQ presents the compilation as the official publication, which conflicts | — | A report that cites a law must say: source (LégisQuébec), consolidation date, and that the consolidated version is administrative. Wording to settle in Q2 |
| **CanLII API** (`api.canlii.org`) | Yes — <https://github.com/canlii/API_documentation/blob/master/EN.md> | ✔ Metadata only: case lists, case metadata (title, citation, docket, date, keywords, CanLII URL), citator (`citedCases`, `citingCases`, `citedLegislations`), legislation metadata. No full text. | ✔ « To apply for an API key, please send a message with your contact information to the feedback form » (<https://www.canlii.org/en/feedback/feedback.html>); HTTPS only; payloads ≤ 10 MB | Terms page <https://www.canlii.org/en/info/terms.html> **could not be read** (bot wall). ◐ CanLII prohibits massive downloads and may use a CAPTCHA at high volume (<https://www.canlii.org/en/databases.html>). The API doc states no rate limit, no licence, no attribution rule. Rate limits (2 req/s, 1 at a time, 5,000/day) are third-party-reported only. ✘ Nothing found on AI training | **Unknown** — a key is granted by request; commercial terms not found | **Use only for links**: citation + CanLII URL, once the key and the terms are in writing. No full text, no bulk download, no training |
| CanLII database ids | ✔ `qccq` (Court of Québec) seen in a client's test of `caseBrowse` ◐. ✘ `qctal` and the C.c.Q. id not confirmed | — | The id list comes from `GET /v1/caseBrowse/{lang}/?api_key=` (needs a key) | — | — | Read the real list with the key (Q3) |
| **SOQUIJ open data** (Données Québec) | **No** — ✔ the CKAN catalogue (checked 2026-11-12) lists no SOQUIJ organisation and no jurisprudence dataset; queries `soquij`, `jurisprudence`, `tribunal administratif du logement`, `TAL` return 0. The only « décisions » datasets are the CPTAQ's (cc-by) and an environment offence register — not leases | none | — | — | — | **Do not use** — there is nothing to use |
| **SOQUIJ citizen search / Juris.qc.ca** | Yes — <https://citoyens.soquij.qc.ca> (TAL's French decision search is hosted by SOQUIJ ◐ <https://www.tal.gouv.qc.ca/en/decision/summaries-of-decisions>) | TAL and other tribunals | Web search only | ✘ Reuse terms not found (copyright notice, scraping, commercial use, party names) | **Unknown** | **Do not use** (no scraping, no copy); a link to a decision is the most we could do, and only after Q4 |
| **TAL website** | Yes — <https://www.tal.gouv.qc.ca> | ◐ Summaries of decisions by theme on the TAL site; the TAL lists « The Tribunal's decisions » among documents distributed online | Web | ✘ The TAL copyright page was not found (`/en/copyright` returns 404) | **Unknown** | Use only for links, after Q4 |
| **A2AJ `a2aj/canadian-case-law`** (Hugging Face) | Yes — <https://huggingface.co/datasets/a2aj/canadian-case-law> | ✔ Federal courts and tribunals (CART, CHRT, CITT, CT, SST, TATC…). **No Québec court, no TAL** (the table has no QC code) | Hugging Face / Parquet | ✔ Card: `license: mit` for A2AJ's work, but « Users must also comply with upstream licenses found in the `upstream_license` field […] These upstream licenses may include limits on commercial use » | Per record; not relevant to leases | **Do not use** for this feature (no coverage). Note the privacy warning on the card |
| **A2AJ `a2aj/canadian-laws`** | Yes — <https://huggingface.co/datasets/a2aj/canadian-laws> | ✔ Federal and 12 provinces; Québec = `LEGISLATION-QC` (541), `REGULATIONS-QC` (2,365), consolidations only | Hugging Face | ✔ Card: « Quebec is reproduced from the provincial open-data XML under CC BY-NC-ND 4.0 — note the non-commercial and no-derivatives terms passed through in `upstream_license` » | **No** for Québec | **Do not use** for Québec law (same terms as the XML, with a second layer) |
| **TAL mandatory lease form** | Yes — official forms are on the TAL's electronic-lease platform and printable ◐ <https://www.tal.gouv.qc.ca/fr/bail-electronique> | The form prescribed by the *Règlement sur les formulaires de bail obligatoires* (RLRQ c. T-15.01, r. 3) | Download | ✘ Copyright holder and reuse notice not found | **Unknown** | **Do not reproduce the layout** in test files until Q5 is answered. Make synthetic files with an original layout that carries the same field names |

## Findings that matter now

1. **The law corpus already built on the CC BY-NC-ND dataset.** `packs/document-validation/derivation/law/*.json` names it as
   its source (« instantané du 2026-10-21 », « non reproduit ici »: only the hash and the URL are committed), and
   `infra/migrations/2026-10-24-law-corpus.sql` creates the corpus tables. Where the article text is stored or shown is not
   audited here. This is the founder's decision, not this ticket's: see Q1.
2. **No public decision dataset covers the TAL.** SOQUIJ has no open data; A2AJ has no Québec cases. CanLII `qctal` is not
   confirmed. Feature 11 (#3128) cannot count on a licensed bulk corpus: measure it on cases that we write ourselves, or on
   cases the user supplies, until Q3 and Q4 are answered.
3. **A decision citation inside a report** is safe only as an identifier (court, number, date) plus a link, never as
   copied text, until the lawyer answers Q6.

## Questions for the lawyer review (krizaka-restricted#312)

- **Q1.** May a paid product quote or store article text taken from the Données Québec XML (CC BY-NC-ND 4.0)? If not, what
  authorisation does Publications du Québec grant, at what royalty, and is a *quote of one article with its number* a
  fair-dealing use (Copyright Act s. 29) or a quotation allowed under the Québec rules on the reuse of legislation?
- **Q2.** What exactly must a report state about a consolidated text (source, consolidation date, « no official value »)?
  The Québec notice and the LégisQuébec FAQ disagree; which governs?
- **Q3.** CanLII: do the API's terms allow showing a citation and a link in a paid report, caching metadata, and what
  attribution is required? Is any use for model training or retrieval excluded? What are the rate limits of our key?
- **Q4.** SOQUIJ / TAL decisions: what are the copyright and reuse terms of decisions found by SOQUIJ's search and on the
  TAL site? Is a link allowed, is a short passage quoted in a report allowed, is scraping excluded?
- **Q5.** May the layout of the TAL mandatory lease form be reproduced in synthetic test files that are committed to this
  repository (Apache-2.0 for `packs/`)? Who holds the copyright (Gouvernement du Québec, TAL)?
- **Q6.** Party names in TAL decisions: are they anonymised at the source? Must we remove them before storing or showing a
  decision, in view of Law 25 and the courts' rules on personal information, even if the decision is public?

## Next steps after the founder's go

- Ask CanLII for a key through the feedback form and read the terms page in a browser; record the real database ids.
- Write to Publications du Québec about Q1 only if the lawyer says authorisation is needed.
- Nothing is wired before that.
