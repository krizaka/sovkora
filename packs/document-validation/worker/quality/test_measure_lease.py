#!/usr/bin/env python3
"""The Québec lease measure (krizaka-internal#3128): the cases are well formed, the committed results are the current ones, and a
deliberately broken ruleset is refused by the threshold.

Run: python3 worker/quality/test_measure_lease.py
"""

import copy
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import measure_lease as m  # noqa: E402

CASES = json.loads(m.CASES.read_text(encoding="utf-8"))["cases"]
THRESHOLDS = json.loads(m.THRESHOLDS.read_text(encoding="utf-8"))
TOKENS = ("tribunal", "year", "citation", "date", "url", "kind", "citationStatus")


def test_there_are_20_to_30_cases_each_with_its_source_and_no_party_name():
    assert 20 <= len(CASES) <= 30, len(CASES)
    assert len({c["id"] for c in CASES}) == len(CASES)
    for c in CASES:
        assert all(t in c["source"] for t in TOKENS), c["id"]
        assert c["source"]["kind"] in ("decision", "tal-guidance", "control"), c["id"]
        assert c["source"]["kind"] == "control" or c["source"]["url"], c["id"]
        assert c["clauseIdea"] and c["clause"], c["id"]
        assert c["expect"] or c["source"]["kind"] == "control" or "uncoveredBy" in c, c["id"]
        assert " c. " not in json.dumps(c, ensure_ascii=False), f"{c['id']} names parties"


def test_every_expected_passage_is_in_the_case_text_and_every_threshold_rule_has_cases():
    for c in CASES:
        for e in c["expect"]:
            assert e["passage"].lower() in m.lease(c).lower(), c["id"]
    expecting = {}
    for c in CASES:
        for e in c["expect"]:
            expecting[e["ruleId"]] = expecting.get(e["ruleId"], 0) + 1
    for rule_id, limit in THRESHOLDS["rules"].items():
        assert expecting.get(rule_id) == limit["casesExpecting"], rule_id
    assert set(THRESHOLDS["criticalCases"]) <= {c["id"] for c in CASES}


def test_the_committed_results_are_the_current_measure():
    committed = json.loads(m.RESULTS.read_text(encoding="utf-8"))
    current = m.summary(m.measure(), m.judge(m.measure(), THRESHOLDS))
    assert committed == current, "the results file is stale: run measure_lease.py and commit it"


def test_a_complete_result_passes_the_threshold():
    cases = [{"id": c["id"], "found": [e["ruleId"] for e in c["expect"]], "missed": [], "wrong": [], "unmeasured": False} for c in CASES]
    result = {"cases": cases, "rules": m.per_rule(cases)}
    assert m.judge(result, THRESHOLDS) == []


def test_a_deliberately_broken_ruleset_is_refused_by_the_threshold():
    ruleset = m.rules.resolve("qc-residential-lease", "2026-10-22")
    broken = copy.deepcopy(ruleset)
    for rule in broken["rules"]:
        if rule["ruleId"] in ("QC-LEASE-004", "QC-LEASE-005"):
            rule["check"]["pattern"] = "(?!x)x"  # never matches: the check the prompt stands for is gone
    cases = [m.run_case(broken, c) for c in CASES]
    refusals = m.judge({"cases": cases, "rules": m.per_rule(cases)}, THRESHOLDS)
    assert any("QC-LEASE-004: found 0" in r for r in refusals), refusals
    assert any("critical case deposit-security-one-month" in r for r in refusals), refusals


def test_a_wrong_finding_is_refused():
    ok = [{"id": "x", "found": [], "missed": [], "wrong": ["QC-LEASE-006"], "unmeasured": False}]
    result = {"cases": ok, "rules": m.per_rule(ok)}
    assert any("QC-LEASE-006: wrong 1 > maximum 0" in r for r in m.judge(result, THRESHOLDS))


if __name__ == "__main__":
    failures = 0
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            try:
                fn()
                print(f"  ok   {name}")
            except AssertionError as exc:
                failures += 1
                print(f"  FAIL {name}: {str(exc)[:400]}")
    print(f"\n{'FAILED' if failures else 'all green'} — {failures} failure(s)")
    sys.exit(1 if failures else 0)
