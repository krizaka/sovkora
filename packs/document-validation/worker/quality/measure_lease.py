#!/usr/bin/env python3
"""Measures the Québec lease check on cases built from public TAL sources (krizaka-internal#3128).

Run: python3 worker/quality/measure_lease.py            measure, write results.json, exit 1 when the threshold refuses
     python3 worker/quality/measure_lease.py --check    same, without writing

Offline and deterministic: the rules of the ruleset only, no model, no network. Per rule: found (a case expects FAIL and the rule
fails on the expected passage), missed (expected FAIL, not found), wrong (a FAIL no case expects). A case with no deterministic
rule behind it (`uncoveredBy`) is counted as unmeasured, never as found or missed. The threshold file is read as committed; this
module never edits it.
"""

import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
import rules  # noqa: E402

CASES = HERE / "qc-residential-lease.cases.json"
THRESHOLDS = HERE / "qc-residential-lease.thresholds.json"
RESULTS = HERE / "qc-residential-lease.results.json"

LEASE_HEAD = """BAIL DE LOGEMENT (modèle de démonstration, personnes et adresse fictives)
Formulaire obligatoire du Tribunal administratif du logement.
Section A — Parties
Locateur : Madame Hélène Roy, 120, avenue du Parc, Montréal (Québec).
Locataire : Monsieur Karim Haddad, 45, rue Beaubien, Laval (Québec).
Section B — Logement
3½ situé au 120, avenue du Parc, Montréal.
Section C — Durée
Bail de 12 mois, du 1er septembre 2026 au 31 août 2027.
Section D — Loyer
Loyer mensuel : 1 120 $, payable le premier jour de chaque mois.
Section E — Conditions particulières
"""
LEASE_TAIL = """Section G — Avis du loyer le plus bas
Le loyer le plus bas payé au cours des douze derniers mois précédant le début du bail était de 1 090 $ par mois.
Signé à Montréal le 12 juin 2026.
"""


def lease(case: dict) -> str:
    return LEASE_HEAD + "\n".join(case["clause"]) + "\n" + LEASE_TAIL


def run_case(ruleset: dict, case: dict) -> dict:
    text = lease(case)
    findings, _ = rules.screen(ruleset, text)
    failed = {f["ruleId"]: f for f in findings if f["verdict"] == rules.FAIL}
    expected = {e["ruleId"]: e for e in case["expect"]}
    found, missed = [], []
    for rule_id, e in expected.items():
        hit = failed.get(rule_id)
        span = (hit or {}).get("evidenceSpan") or {}
        if hit and e["passage"].lower() in (span.get("quote") or "").lower():
            found.append(rule_id)
        else:
            missed.append(rule_id)
    wrong = sorted(set(failed) - set(expected))
    return {"id": case["id"], "found": found, "missed": missed, "wrong": wrong, "unmeasured": "uncoveredBy" in case}


def measure() -> dict:
    data = json.loads(CASES.read_text(encoding="utf-8"))
    ruleset = rules.resolve(data["ruleset"], data["asOf"])
    per_case = [run_case(ruleset, c) for c in data["cases"]]
    return {"ruleset": data["ruleset"], "asOf": data["asOf"], "cases": per_case, "rules": per_rule(per_case)}


def per_rule(per_case: list) -> dict:
    table: dict = {}
    for r in per_case:
        for key in ("found", "missed", "wrong"):
            for rule_id in r[key]:
                table.setdefault(rule_id, {"found": 0, "missed": 0, "wrong": 0})[key] += 1
    return dict(sorted(table.items()))


def judge(result: dict, thresholds: dict) -> list:
    """The reasons a version is refused: an empty list means the threshold holds."""
    refusals = []
    for rule_id, limit in thresholds["rules"].items():
        row = result["rules"].get(rule_id, {"found": 0, "missed": 0, "wrong": 0})
        if row["found"] < limit["minFound"]:
            refusals.append(f"{rule_id}: found {row['found']} < minimum {limit['minFound']}")
    for rule_id, row in result["rules"].items():
        cap = thresholds["rules"].get(rule_id, {}).get("maxWrong", thresholds["defaultMaxWrong"])
        if row["wrong"] > cap:
            refusals.append(f"{rule_id}: wrong {row['wrong']} > maximum {cap}")
    critical = set(thresholds["criticalCases"])
    for r in result["cases"]:
        if r["id"] in critical and r["missed"]:
            refusals.append(f"critical case {r['id']} missed {', '.join(r['missed'])}")
    return refusals


def summary(result: dict, refusals: list) -> dict:
    cases = result["cases"]
    return {**result, "unmeasuredCases": sorted(c["id"] for c in cases if c["unmeasured"]), "refused": bool(refusals), "refusals": refusals}


def main() -> int:
    thresholds = json.loads(THRESHOLDS.read_text(encoding="utf-8"))
    result = measure()
    refusals = judge(result, thresholds)
    out = summary(result, refusals)
    for rule_id, row in out["rules"].items():
        print(f"  {rule_id}  found {row['found']}  missed {row['missed']}  wrong {row['wrong']}")
    print("REFUSED: " + "; ".join(refusals) if refusals else "threshold holds")
    if "--check" not in sys.argv:
        RESULTS.write_text(json.dumps(out, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return 1 if refusals else 0


if __name__ == "__main__":
    sys.exit(main())
