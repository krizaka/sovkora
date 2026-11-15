// The « Law retrieval bake-off » section of the Measured runs page, from docs/measurements/law-bakeoff.json. Nothing is typed by hand: re-run the bake-off, regenerate.
const pct = (x) => (x == null ? "—" : `${Math.round(x * 100)} %`);
const ms = (x) => (x == null ? "—" : `${(x / 1000).toFixed(1)} s`);
const usd = (x) => {
  if (x == null) return "—";
  return x === 0 ? "$0 (local)" : `$${x.toFixed(4)}`;
};
const code = (x) => `\`${x}\``;
const verdictOf = (decision) =>
  decision.winner ? `${code(decision.winner)} (ranking: ${decision.ranking.map(code).join(", ")})` : "no model is safe on this set";
const disqualifiedLine = (x) => `${code(x.id)} (${x.wrongAccepted})`;
const disqualifiedOf = (list) => (list.length ? ": " + list.map(disqualifiedLine).join(", ") : " (none was)");
const row = (cells) => `| ${cells.join(" | ")} |`;

export function mdLawBakeoff(d) {
  const L = [];
  L.push("## Law retrieval bake-off", "");
  L.push(
    `Step 1 of the law chain (ADR-089 cut 7: the grounded legal claims of the retrieval), measured on **${d.measuredOn}** by \`scripts/law-bakeoff.mjs\` on ${d.cases} golden cases (${d.runsPerCase} runs each, prompt \`${d.promptVersion}\`, temperature 0, seed ${d.seed}). Every claim is checked by a port of the engine's deterministic verifier; the cases name articles and hold no article text. Wording is a fixed mechanical rubric (right language, plain text, a complete sentence, a length that reads), not a judge model.`,
    "",
  );
  L.push("| Model | Where | Wrong accepted | Verified claims | Quotes found | Articles recalled | Facts stated | French wording | Latency p50 / p95 | Cost per document | Same verdicts twice |", "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |");
  for (const m of d.models) {
    const a = m.aggregate;
    lines(L, row([`**${m.id}**`, m.local ? "local (Ollama)" : "DigitalOcean serverless", `**${a.wrongAccepted}**`, `${a.verified} / ${a.claims} (${pct(a.verifiedRate)})`, pct(a.quoteFoundRate), pct(a.recall), pct(a.statedRate), pct(a.frenchWording), `${ms(a.latencyP50)} / ${ms(a.latencyP95)}`, usd(m.costPerRunUsd), pct(m.stability)]));
  }
  L.push("");
  L.push(`**Verdict:** ${verdictOf(d.decision)}. A model with one wrong citation accepted is disqualified${disqualifiedOf(d.decision.disqualified)}. Among the safe ones the best accuracy wins; on a tie, the cheapest, local first. The winner is set by a routing row for the step (DB), never in code.`, "");
  L.push(`Spent: **$${d.spentUsd.toFixed(4)}** of the $${d.maxUsd.toFixed(2)} cap (prices of ${d.priceDate}). Run ids: ${d.runIds.length} runs, recorded in \`docs/measurements/law-bakeoff.json\`.`, "");
  if (d.notMeasured?.length) {
    L.push("Steps not measured yet:", "");
    for (const n of d.notMeasured) L.push(`- ${n.step}: ${n.why}`);
    L.push("");
  }
  return L.join("\n");
}

function lines(L, s) {
  L.push(s);
}
