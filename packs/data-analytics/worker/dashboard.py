"""The dashboard of a data-driven answer: the model's plan, checked, then drawn from the facts (krizaka-internal#3628).

The model answers with a small JSON plan — a summary, up to four KPIs, a chart, a table, a note — that names facts
(``F7``) and tables (``T2``) by id. This module refuses a plan outside that shape, a fact or a table that does not
exist, and any figure written in its texts that is not one of the facts; then it writes the A2UI v0.9 surface
(catalog ``krizaka:genui/v1``) itself, every number taken from the facts. A refused plan is never shown: the Studio asks
the model once more with the reasons, then fails with a typed error.

Pure functions: no clock, no environment, no I/O.
"""

from __future__ import annotations

import json
import re
from collections.abc import Sequence

from facts import Fact, Facts, FactTable, Language

SURFACE = "answer"
CATALOG = "krizaka:genui/v1"
VERSION = "v0.9"
CHART_KINDS = ("bar", "line", "area", "pie")
TONES = ("info", "success", "warning")
MAX_KPIS = 4
MAX_TEXT = 600
MAX_LABEL = 80
_KEYS = {
    "plan": {"summary", "kpis", "chart", "table", "note"},
    "kpi": {"label", "fact"},
    "chart": {"title", "kind", "table", "series"},
    "table": {"caption", "table", "columns"},
    "note": {"tone", "title", "text"},
}
_PLACEHOLDER = re.compile(r"\{(F\d+)\}")
_NUMBER = re.compile(r"\d(?:[\d\u00a0\u202f ,.]*\d)?")
# Small whole numbers are words, not figures: « the three products », « top 3 », « week 2 ».
_SMALL = 12


class AnswerRefusedError(Exception):
    """Neither the answer nor its one retry made a dashboard the facts support (an executor fault: never billed)."""


def read_plan(text: str) -> tuple[dict | None, list[str]]:
    """The plan inside the model's answer, or why there is none. A fence or a line of prose around it is packaging."""
    body = text.strip()
    if body.startswith("```"):
        body = body.split("\n", 1)[1] if "\n" in body else ""
        body = body.rsplit("```", 1)[0]
    start, end = body.find("{"), body.rfind("}")
    if start < 0 or end <= start:
        return None, ["the answer is not one JSON object"]
    try:
        plan = json.loads(body[start : end + 1])
    except ValueError:
        return None, ["the answer is not valid JSON"]
    if not isinstance(plan, dict):
        return None, ["the answer is not one JSON object"]
    return plan, []


def check_plan(plan: dict, facts: Facts, question: str) -> list[str]:
    """What the plan breaks: its shape, its ids, every figure in its texts that is not a fact. Empty when it holds."""
    problems: list[str] = []
    _keys(plan, "plan", "the answer", problems)
    summary = plan.get("summary")
    if not isinstance(summary, str) or not summary.strip():
        problems.append("summary is required: one or two sentences")
    texts: list[tuple[str, str]] = [("summary", summary)] if isinstance(summary, str) else []
    kpis = plan.get("kpis", [])
    if not isinstance(kpis, list) or len(kpis) > MAX_KPIS:
        problems.append(f"kpis is a list of at most {MAX_KPIS}")
        kpis = []
    for i, kpi in enumerate(kpis):
        where = f"kpis[{i}]"
        if not isinstance(kpi, dict):
            problems.append(f"{where} is an object")
            continue
        _keys(kpi, "kpi", where, problems)
        _label(kpi.get("label"), f"{where}.label", problems, texts)
        if not isinstance(kpi.get("fact"), str) or facts.fact(kpi["fact"]) is None:
            problems.append(f"{where}.fact is not one of the facts (F1…F{len(facts.facts)})")
    chart = plan.get("chart")
    if chart is not None:
        _check_chart(chart, facts, problems, texts)
    table = plan.get("table")
    if table is not None:
        _check_table(table, facts, problems, texts)
    note = plan.get("note")
    if note is not None:
        _check_note(note, problems, texts)
    if not kpis and chart is None and table is None and note is None:
        problems.append("the answer shows nothing: give kpis, a chart, a table or a note")
    if facts.source == "none" and (kpis or chart is not None or table is not None):
        problems.append("there is no table: answer with a note of tone warning only")
    for where, text in texts:
        problems.extend(_ungrounded(text, where, facts, question))
    return problems


def _keys(value: dict, shape: str, where: str, problems: list[str]) -> None:
    extra = sorted(set(value) - _KEYS[shape])
    if extra:
        problems.append(f"{where} has no property {', '.join(extra)}")


def _label(value: object, where: str, problems: list[str], texts: list, limit: int = MAX_LABEL) -> None:
    if not isinstance(value, str) or not value.strip() or len(value) > limit:
        problems.append(f"{where} is a text of 1 to {limit} characters")
    else:
        texts.append((where, value))


def _check_chart(chart: object, facts: Facts, problems: list[str], texts: list) -> None:
    if not isinstance(chart, dict):
        problems.append("chart is an object")
        return
    _keys(chart, "chart", "chart", problems)
    _label(chart.get("title"), "chart.title", problems, texts)
    if chart.get("kind") not in CHART_KINDS:
        problems.append(f"chart.kind is one of {', '.join(CHART_KINDS)}")
    table = _table_ref(chart.get("table"), "chart.table", facts, problems)
    series = chart.get("series")
    if not isinstance(series, list) or not 1 <= len(series) <= 4:
        problems.append("chart.series is a list of 1 to 4 column keys")
    elif table is not None:
        known = {m.key for m in table.measures}
        problems.extend(
            f"chart.series: {key} is not a column of {table.id} ({', '.join(sorted(known))})"
            for key in series
            if key not in known
        )
        if chart.get("kind") == "pie" and len(series) != 1:
            problems.append("a pie chart has one series")


def _check_table(table: object, facts: Facts, problems: list[str], texts: list) -> None:
    if not isinstance(table, dict):
        problems.append("table is an object")
        return
    _keys(table, "table", "table", problems)
    _label(table.get("caption"), "table.caption", problems, texts)
    found = _table_ref(table.get("table"), "table.table", facts, problems)
    columns = table.get("columns")
    if not isinstance(columns, list) or not 1 <= len(columns) <= 12:
        problems.append("table.columns is a list of 1 to 12 column keys")
    elif found is not None:
        known = [found.dimension.key, *[m.key for m in found.measures]]
        problems.extend(
            f"table.columns: {key} is not a column of {found.id} ({', '.join(known)})"
            for key in columns
            if key not in known
        )


def _check_note(note: object, problems: list[str], texts: list) -> None:
    if not isinstance(note, dict):
        problems.append("note is an object")
        return
    _keys(note, "note", "note", problems)
    if note.get("tone") not in TONES:
        problems.append(f"note.tone is one of {', '.join(TONES)}")
    _label(note.get("title"), "note.title", problems, texts)
    _label(note.get("text"), "note.text", problems, texts, MAX_TEXT)


def _table_ref(value: object, where: str, facts: Facts, problems: list[str]) -> FactTable | None:
    table = facts.table(value) if isinstance(value, str) else None
    if table is None:
        problems.append(f"{where} is not one of the tables (T1…T{len(facts.tables)})")
    return table


# ── Figures in the texts ────────────────────────────────────────────────────────────────────────────────────────────


def _ungrounded(text: str, where: str, facts: Facts, question: str) -> list[str]:
    """The figures a text writes that are none of the facts. A ``{F12}`` placeholder is the way to write one."""
    problems = [
        f"{where}: {{{placeholder}}} is not one of the facts"
        for placeholder in _PLACEHOLDER.findall(text)
        if facts.fact(placeholder) is None
    ]
    allowed = _allowed(facts, question)
    problems.extend(
        f"{where}: the figure {token.strip()} is not one of the facts — write the fact as {{F…}}"
        for token in _NUMBER.findall(_PLACEHOLDER.sub(" ", text))
        if not any(_close(candidate, allowed) for candidate in _readings(token))
    )
    return problems


def _allowed(facts: Facts, question: str) -> list[float]:
    values: list[float] = []
    for f in facts.facts:
        values.append(float(f.value))
        if f.kind == "percent":
            values.append(float(f.value) * 100)
        values.extend(float(n) for n in re.findall(r"\d+", f"{f.key or ''} {f.label}"))
    for t in facts.tables:
        values.extend(float(n) for n in re.findall(r"\d+", t.title))
    values.extend(float(n) for n in re.findall(r"\d+(?:\.\d+)?", question))
    return values


def _readings(token: str) -> list[float]:
    """Every way a figure can be read: ``3,770`` (en) / ``3 770`` (fr) / ``1,5`` (fr) / ``1.5`` (en)."""
    compact = token.replace("\u00a0", "").replace("\u202f", "").replace(" ", "").strip(".,")
    readings = []
    for text in (
        compact.replace(",", ""),
        compact.replace(".", "").replace(",", "."),
        compact,
    ):
        try:
            readings.append(float(text))
        except ValueError:
            continue
    return readings


def _close(candidate: float, allowed: Sequence[float]) -> bool:
    if candidate == int(candidate) and 0 <= candidate <= _SMALL:
        return True
    decimals = 0 if candidate == int(candidate) else 2
    return any(abs(round(value, decimals) - candidate) < 0.0051 or abs(value - candidate) < 0.0051 for value in allowed)


# ── Writing ─────────────────────────────────────────────────────────────────────────────────────────────────────────


def format_fact(fact: Fact, facts: Facts) -> str:
    """A fact as a sentence writes it, in the answer's language: ``$3,770`` / ``3 770 $``, ``44 %``, ``1,962``."""
    language = facts.language
    if fact.kind == "percent":
        number = _number(fact.value * 100, language, 1)
        return f"{number} %" if language == "fr" else f"{number}%"
    if fact.kind == "money" and facts.currency is not None:
        number = _number(fact.value, language, 2 if fact.value != int(fact.value) else 0)
        symbol = _symbol(facts.currency.code, facts.currency.symbol, language)
        return f"{number}\u00a0{symbol}" if language == "fr" else f"{symbol}{number}"
    return _number(fact.value, language, 0 if fact.value == int(fact.value) else 2)


def _symbol(code: str | None, symbol: str | None, language: Language) -> str:
    if code == "USD":
        return "$\u00a0US" if language == "fr" else "US$"
    if code == "CHF":
        return "CHF" if language == "fr" else "CHF\u00a0"
    return symbol or code or ""


def _number(value: float, language: Language, decimals: int) -> str:
    text = f"{value:,.{decimals}f}"
    if language == "fr":
        return text.replace(",", "\u00a0").replace(".", ",")
    return text


def fill(text: str, facts: Facts) -> str:
    """A text with each ``{F12}`` replaced by that fact, written."""
    return _PLACEHOLDER.sub(lambda m: format_fact(facts.fact(m.group(1)), facts), text)  # type: ignore[arg-type]


def build_answer(plan: dict, facts: Facts) -> str:
    """The answer the run page draws: the summary, then the A2UI v0.9 surface whose every number is a fact."""
    components: list[dict] = []
    children: list[str] = []
    data: dict[str, list] = {}
    kpis = [_kpi(i, k, facts) for i, k in enumerate(plan.get("kpis", []))]
    if len(kpis) == 1:
        children.append(kpis[0]["id"])
    elif kpis:
        components.append({"id": "figures", "component": "Row", "children": [k["id"] for k in kpis]})
        children.append("figures")
    components.extend(kpis)
    if plan.get("chart") is not None:
        components.append(_chart(plan["chart"], facts, data))
        children.append("chart")
    if plan.get("table") is not None:
        components.append(_table(plan["table"], facts, data))
        children.append("table")
    if plan.get("note") is not None:
        note = plan["note"]
        components.append(
            {
                "id": "note",
                "component": "Callout",
                "tone": note["tone"],
                "title": fill(note["title"], facts),
                "text": fill(note["text"], facts),
            }
        )
        children.append("note")
    root = {"id": "root", "component": "Column", "children": children}
    lines = [
        {
            "version": VERSION,
            "createSurface": {"surfaceId": SURFACE, "catalogId": CATALOG},
        },
        {
            "version": VERSION,
            "updateComponents": {
                "surfaceId": SURFACE,
                "components": [root, *components],
            },
        },
    ]
    if data:
        lines.append(
            {
                "version": VERSION,
                "updateDataModel": {
                    "surfaceId": SURFACE,
                    "path": "/tables",
                    "value": data,
                },
            }
        )
    body = "\n".join(json.dumps(line, ensure_ascii=False, separators=(",", ":")) for line in lines)
    return f"{fill(plan['summary'].strip(), facts)}\n\n```a2ui\n{body}\n```"


def _format(kind: str, facts: Facts) -> dict:
    if kind == "money" and facts.currency is not None and facts.currency.code:
        return {"format": "money", "currency": facts.currency.code}
    if kind == "percent":
        return {"format": "percent"}
    if kind in ("count", "integer"):
        return {"format": "integer"}
    return {"format": "number"}


def _kpi(index: int, kpi: dict, facts: Facts) -> dict:
    fact = facts.fact(kpi["fact"])
    assert fact is not None  # checked by check_plan
    return {
        "id": f"kpi-{index + 1}",
        "component": "KpiStat",
        "label": fill(kpi["label"], facts),
        "value": fact.value,
        **_format(fact.kind, facts),
    }


def _column_kind(table: FactTable, key: str, facts: Facts) -> str:
    column = next(c for c in table.measures if c.key == key)
    if column.kind == "count":
        return "count"
    if column.money and facts.currency is not None:
        return "money"
    values = [r[key] for r in table.rows]
    return "integer" if all(v == int(v) for v in values) else "number"


def _chart(chart: dict, facts: Facts, data: dict) -> dict:
    table = facts.table(chart["table"])
    assert table is not None  # checked by check_plan
    data[table.id] = list(table.rows)
    labels = {m.key: m.label for m in table.measures}
    kinds = {_column_kind(table, key, facts) for key in chart["series"]}
    shared = _format(kinds.pop(), facts) if len(kinds) == 1 else {}
    return {
        "id": "chart",
        "component": "ChartCard",
        "title": fill(chart["title"], facts),
        "kind": chart["kind"],
        "x": table.dimension.key,
        "series": [{"key": k, "label": labels[k]} for k in chart["series"]],
        "data": {"path": f"/tables/{table.id}"},
        **shared,
    }


def _table(spec: dict, facts: Facts, data: dict) -> dict:
    table = facts.table(spec["table"])
    assert table is not None  # checked by check_plan
    data[table.id] = list(table.rows)
    columns = []
    for key in spec["columns"]:
        if key == table.dimension.key:
            columns.append({"key": key, "label": table.dimension.label})
            continue
        label = next(m.label for m in table.measures if m.key == key)
        columns.append(
            {
                "key": key,
                "label": label,
                **_format(_column_kind(table, key, facts), facts),
            }
        )
    return {
        "id": "table",
        "component": "Table",
        "caption": fill(spec["caption"], facts),
        "columns": columns,
        "rows": {"path": f"/tables/{table.id}"},
    }


def assemble(answers: Sequence[str], facts: Facts, question: str) -> tuple[str | None, list[str]]:
    """The first answer whose plan holds, drawn; else ``None`` and the reasons the last one was refused."""
    problems: list[str] = ["no answer"]
    for text in answers:
        if not text.strip():
            continue
        plan, problems = read_plan(text)
        if plan is not None:
            problems = check_plan(plan, facts, question)
        if not problems and plan is not None:
            return build_answer(plan, facts), []
    return None, problems
