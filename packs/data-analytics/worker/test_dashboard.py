"""The model chooses and explains; every figure on the dashboard is a fact (krizaka-internal#3628)."""

import json
from pathlib import Path

import dashboard
import facts

HERE = Path(__file__).parent
CSV = (HERE / "fixtures" / "ventes-atelier-octobre.csv").read_text(encoding="utf-8")
# What mistral-3-14B answered on dev for this CSV (run 28954cdf, 2026-11-14): lamp 2,310 $, week Oct 18-24 at 546 $.
RUN_28954CDF = (HERE / "fixtures" / "run-28954cdf-answer.txt").read_text(encoding="utf-8")
QUESTION = (
    "Amounts are in Canadian dollars. Which three products earned the most in October, and which week "
    "(Monday to Sunday) was the best?"
)
COMPUTED = facts.compute(CSV, QUESTION, "en")


def _id(label: str) -> str:
    return next(f.id for f in COMPUTED.facts if f.label == label)


LAMP = _id("sum of total where produit = lampe-gres-creme (rank 1 of 5 by total)")
BEST_WEEK = _id("best week by total: Oct 19\u201325")
TOTAL = _id("sum of total")
BY_PRODUCT = next(t.id for t in COMPUTED.tables if t.title == "by produit")
BY_WEEK = next(t.id for t in COMPUTED.tables if t.dimension.key == "week")


def _plan(**changes) -> dict:
    plan = {
        "summary": (
            f"The stoneware lamp earned the most, {{{LAMP}}}; the best week was Oct 19\u201325 with {{{BEST_WEEK}}}."
        ),
        "kpis": [
            {"label": "Lamp", "fact": LAMP},
            {"label": "Best week (Oct 19\u201325)", "fact": BEST_WEEK},
        ],
        "chart": {
            "title": "Revenue per week",
            "kind": "bar",
            "table": BY_WEEK,
            "series": ["total"],
        },
        "table": {
            "caption": "Revenue per product",
            "table": BY_PRODUCT,
            "columns": ["produit", "total", "count"],
        },
        "note": {
            "tone": "info",
            "title": "A partial first week",
            "text": "The first week has 4 days of data.",
        },
    }
    plan.update(changes)
    return plan


def _messages(answer: str) -> list[dict]:
    body = answer.split("```a2ui\n", 1)[1].rsplit("\n```", 1)[0]
    return [json.loads(line) for line in body.split("\n")]


def test_the_answer_writes_the_lamp_and_the_best_week_from_the_facts():
    answer = dashboard.build_answer(_plan(), COMPUTED)
    prose = answer.split("```a2ui", 1)[0]
    assert "$3,770" in prose
    assert "$1,962" in prose
    assert "Oct 19\u201325" in prose


def test_the_dashboard_numbers_are_the_facts_and_its_rows_the_facts_tables():
    messages = _messages(dashboard.build_answer(_plan(), COMPUTED))
    assert messages[0] == {
        "version": "v0.9",
        "createSurface": {"surfaceId": "answer", "catalogId": "krizaka:genui/v1"},
    }
    components = {c["id"]: c for c in messages[1]["updateComponents"]["components"]}
    assert components["root"]["children"] == ["figures", "chart", "table", "note"]
    assert components["kpi-1"] == {
        "id": "kpi-1",
        "component": "KpiStat",
        "label": "Lamp",
        "value": 3770,
        "format": "money",
        "currency": "CAD",
    }
    assert components["kpi-2"]["value"] == 1962
    assert components["chart"]["data"] == {"path": f"/tables/{BY_WEEK}"}
    assert components["chart"]["x"] == "week"
    tables = messages[2]["updateDataModel"]["value"]
    assert {"week": "Oct 19\u201325", "count": 24, "quantite": 41, "total": 1962} in tables[BY_WEEK]
    assert tables[BY_PRODUCT][0] == {
        "produit": "lampe-gres-creme",
        "count": 25,
        "quantite": 26,
        "total": 3770,
    }


def test_french_figures_are_written_the_french_way():
    computed = facts.compute(CSV, "Les montants sont en dollars canadiens. Quelle semaine ?", "fr")
    lamp = next(f.id for f in computed.facts if f.label.startswith("sum of total where produit = lampe"))
    answer = dashboard.build_answer(
        {
            "summary": f"La lampe : {{{lamp}}}.",
            "kpis": [{"label": "Lampe", "fact": lamp}],
        },
        computed,
    )
    assert answer.startswith("La lampe : 3\u00a0770\u00a0$.")


def test_a_valid_plan_holds():
    assert dashboard.check_plan(_plan(), COMPUTED, QUESTION) == []


def test_a_figure_the_model_wrote_itself_is_refused():
    plan = _plan(summary="The lamp earned 2,310 $ and the best week was Oct 18\u201324 at 546 $.")
    problems = dashboard.check_plan(plan, COMPUTED, QUESTION)
    assert any("2,310" in p for p in problems)
    assert any("546" in p for p in problems)


def test_a_figure_written_as_a_fact_value_is_accepted():
    plan = _plan(summary="The lamp earned 3,770 $; the best week, Oct 19\u201325, made 1 962 $.")
    assert dashboard.check_plan(plan, COMPUTED, QUESTION) == []


def test_an_unknown_fact_or_table_is_refused():
    problems = dashboard.check_plan(
        _plan(
            kpis=[{"label": "Lamp", "fact": "F9999"}],
            chart={"title": "t", "kind": "bar", "table": "T99", "series": ["total"]},
        ),
        COMPUTED,
        QUESTION,
    )
    assert any(p.startswith("kpis[0].fact is not one of the facts") for p in problems)
    assert any(p.startswith("chart.table is not one of the tables") for p in problems)


def test_a_property_outside_the_plan_is_refused():
    problems = dashboard.check_plan(
        _plan(kpis=[{"label": "Lamp", "fact": LAMP, "tone": "positive"}]),
        COMPUTED,
        QUESTION,
    )
    assert "kpis[0] has no property tone" in problems


def test_a_series_that_is_not_a_column_of_its_table_is_refused():
    plan = _plan(
        chart={
            "title": "Revenue",
            "kind": "bar",
            "table": BY_WEEK,
            "series": ["revenue"],
        }
    )
    assert any(p.startswith("chart.series: revenue is not a column") for p in dashboard.check_plan(plan, COMPUTED, ""))


def test_malformed_json_is_refused():
    assert dashboard.read_plan('{"summary": "x", "kpis": [}') == (
        None,
        ["the answer is not valid JSON"],
    )
    assert dashboard.read_plan("no json at all") == (
        None,
        ["the answer is not one JSON object"],
    )


def test_a_fenced_plan_is_read():
    plan, problems = dashboard.read_plan("```json\n" + json.dumps(_plan()) + "\n```")
    assert problems == []
    assert plan == _plan()


def test_the_answer_mistral_wrote_on_dev_is_refused_never_drawn():
    drawn, problems = dashboard.assemble([RUN_28954CDF], COMPUTED, QUESTION)
    assert drawn is None
    assert problems


def test_the_retry_is_drawn_when_the_first_answer_is_refused():
    drawn, problems = dashboard.assemble([RUN_28954CDF, json.dumps(_plan())], COMPUTED, QUESTION)
    assert problems == []
    assert "$3,770" in drawn


def test_two_refused_answers_draw_nothing():
    drawn, problems = dashboard.assemble(["{", json.dumps(_plan(kpis=[{"label": "x", "fact": "F0"}]))], COMPUTED, "")
    assert drawn is None
    assert any("kpis[0].fact" in p for p in problems)


def test_without_a_table_only_a_warning_note_is_accepted():
    computed = facts.compute("", "What was my revenue?", "en")
    note = {
        "tone": "warning",
        "title": "No data",
        "text": "Paste a CSV with a header row.",
    }
    assert dashboard.check_plan({"summary": "I cannot read a table.", "note": note}, computed, "") == []
    plan = {
        "summary": "Revenue was high.",
        "kpis": [{"label": "Revenue", "fact": "F1"}],
    }
    assert "there is no table: answer with a note of tone warning only" in dashboard.check_plan(plan, computed, "")
    messages = _messages(dashboard.build_answer({"summary": "I cannot read a table.", "note": note}, computed))
    assert len(messages) == 2, "no data model when nothing is drawn from a table"


def test_the_answer_shows_something():
    assert "the answer shows nothing: give kpis, a chart, a table or a note" in dashboard.check_plan(
        {"summary": "Hello."}, COMPUTED, ""
    )
