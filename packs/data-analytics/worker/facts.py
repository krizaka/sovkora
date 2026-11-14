"""The figures of a data-driven answer, computed from the table before any model writes (krizaka-internal#3628).

A model asked to add up a hundred rows gets it wrong (three dev runs, three different totals for the same lamp), so the
sums, counts, rankings, shares and per-period totals are computed here, deterministically, and handed to the model as
numbered facts (``F1``…) and tables (``T1``…). The model chooses which ones to show and explains them; it never writes a
figure of its own (``dashboard.py`` refuses one).

Pure functions over the text the person gave: no clock, no environment, no I/O.
"""

from __future__ import annotations

import csv
import io
import json
import re
import unicodedata
from collections.abc import Iterable, Sequence
from dataclasses import asdict, dataclass, field
from datetime import date, timedelta
from typing import Literal

Language = Literal["en", "fr"]
ColumnKind = Literal["number", "date", "category"]
FactKind = Literal["count", "integer", "number", "money", "percent"]

MAX_ROWS = 5000
MAX_CATEGORIES = 30
MAX_DIMENSIONS = 4
MAX_FACTS = 400

_MONTHS = {
    "en": (
        "Jan",
        "Feb",
        "Mar",
        "Apr",
        "May",
        "Jun",
        "Jul",
        "Aug",
        "Sep",
        "Oct",
        "Nov",
        "Dec",
    ),
    "fr": (
        "janv.",
        "févr.",
        "mars",
        "avr.",
        "mai",
        "juin",
        "juil.",
        "août",
        "sept.",
        "oct.",
        "nov.",
        "déc.",
    ),
}
_MONTHS_LONG = {
    "en": (
        "January",
        "February",
        "March",
        "April",
        "May",
        "June",
        "July",
        "August",
        "September",
        "October",
        "November",
        "December",
    ),
    "fr": (
        "janvier",
        "février",
        "mars",
        "avril",
        "mai",
        "juin",
        "juillet",
        "août",
        "septembre",
        "octobre",
        "novembre",
        "décembre",
    ),
}
_WORDS = {
    "en": {"count": "Rows", "week": "Week", "day": "Day", "month": "Month"},
    "fr": {"count": "Lignes", "week": "Semaine", "day": "Jour", "month": "Mois"},
}

_ISO_DATE = re.compile(r"^(\d{4})[-/](\d{2})[-/](\d{2})(?:[T ].*)?$")
_PLAIN = re.compile(r"^[-+]?\d+(?:\.\d+)?$")
_THOUSANDS = re.compile(r"^[-+]?\d{1,3}(?:,\d{3})+(?:\.\d+)?$")
_COMMA_DECIMAL = re.compile(r"^[-+]?\d+,\d+$")
_SYMBOLS = {"$": None, "€": "EUR", "£": "GBP"}
_CODES = ("CAD", "USD", "EUR", "GBP", "CHF")
# Words naming a money column, and words naming a per-unit figure (a price, a rate) whose sum means nothing.
_MONEY_WORDS = re.compile(
    r"(price|prix|total|amount|montant|revenue|revenu|cost|cout|sales_amount|chiffre|earn|income|paid|paye|spend|"
    r"depense|salaire|salary|budget|ca$|^ca_|cad|usd|eur|gbp|chf)"
)
_INTENSIVE_WORDS = re.compile(
    r"(unit|unitaire|rate|taux|ratio|percent|pourcent|pct|avg|average|moyenne|mean|price|prix)"
)
_EXTENSIVE_WORDS = re.compile(r"(total|sum|somme|amount|montant|revenue|chiffre)")
_QUESTION_CURRENCIES = (
    (
        "CAD",
        re.compile(
            r"\bCAD\b|canadian dollars?|dollars? canadiens?|\bCA\$|\$\s?CA\b",
            re.IGNORECASE,
        ),
    ),
    (
        "USD",
        re.compile(
            r"\bUSD\b|\bUS dollars?|american dollars?|dollars? am[ée]ricains?|\bUS\$",
            re.IGNORECASE,
        ),
    ),
    ("EUR", re.compile(r"\bEUR\b|\beuros?\b|€", re.IGNORECASE)),
    ("GBP", re.compile(r"\bGBP\b|pounds? sterling|livres? sterling|£", re.IGNORECASE)),
    ("CHF", re.compile(r"\bCHF\b|swiss francs?|francs? suisses?", re.IGNORECASE)),
)
_DOLLAR = re.compile(r"\$|\bdollars?\b", re.IGNORECASE)


@dataclass(frozen=True)
class Column:
    """A column of a table: its key (A2UI-safe, ``[A-Za-z0-9_]``), what the person called it, what it holds."""

    key: str
    label: str
    kind: ColumnKind | Literal["count", "period"] = "category"
    money: bool = False
    intensive: bool = False


@dataclass(frozen=True)
class Currency:
    """The currency of the money columns, as the data or the question names it — never assumed."""

    code: str | None
    symbol: str | None
    source: Literal["data", "question"]


@dataclass(frozen=True)
class Fact:
    """One computed figure, cited by its id."""

    id: str
    value: float
    kind: FactKind
    label: str
    key: str | None = None


@dataclass(frozen=True)
class FactTable:
    """Rows a chart or a table draws: one per value of a dimension, a column per measure."""

    id: str
    title: str
    dimension: Column
    measures: tuple[Column, ...]
    rows: tuple[dict, ...]


@dataclass(frozen=True)
class Facts:
    """Everything computed from one table."""

    language: Language
    rows: int
    source: Literal["data", "question", "none"]
    currency: Currency | None = None
    facts: tuple[Fact, ...] = ()
    tables: tuple[FactTable, ...] = ()
    columns: tuple[Column, ...] = field(default_factory=tuple)

    def fact(self, fact_id: str) -> Fact | None:
        return next((f for f in self.facts if f.id == fact_id), None)

    def table(self, table_id: str) -> FactTable | None:
        return next((t for t in self.tables if t.id == table_id), None)

    def to_json(self) -> str:
        return json.dumps(asdict(self), ensure_ascii=False, separators=(",", ":"))


class FactsUnreadableError(ValueError):
    """The facts handed between two steps are not the ones this module wrote."""


def facts_from_json(text: str) -> Facts:
    """The facts a previous step wrote with :meth:`Facts.to_json`."""
    try:
        raw = json.loads(text)
        currency = Currency(**raw["currency"]) if raw.get("currency") else None
        return Facts(
            language=raw["language"],
            rows=int(raw["rows"]),
            source=raw["source"],
            currency=currency,
            facts=tuple(Fact(**f) for f in raw["facts"]),
            tables=tuple(
                FactTable(
                    id=t["id"],
                    title=t["title"],
                    dimension=Column(**t["dimension"]),
                    measures=tuple(Column(**m) for m in t["measures"]),
                    rows=tuple(t["rows"]),
                )
                for t in raw["tables"]
            ),
            columns=tuple(Column(**c) for c in raw["columns"]),
        )
    except (ValueError, KeyError, TypeError) as bad:
        raise FactsUnreadableError("facts") from bad


# ── Reading the table ───────────────────────────────────────────────────────────────────────────────────────────────


def read_table(data: str, question: str) -> tuple[list[str], list[list[str]], Literal["data", "question", "none"]]:
    """The header and rows of the person's table: the data field, else a CSV or JSON pasted in the question."""
    if data.strip():
        header, rows = _parse(data)
        return header, rows, "data" if header else "none"
    for candidate in _embedded(question):
        header, rows = _parse(candidate)
        if header:
            return header, rows, "question"
    return [], [], "none"


def _parse(text: str) -> tuple[list[str], list[list[str]]]:
    stripped = text.strip()
    if stripped[:1] in "[{":
        parsed = _parse_json(stripped)
        if parsed[0]:
            return parsed
    return _parse_csv(stripped)


def _parse_json(text: str) -> tuple[list[str], list[list[str]]]:
    try:
        value = json.loads(text)
    except ValueError:
        return [], []
    if isinstance(value, dict):
        lists = [v for v in value.values() if isinstance(v, list)]
        value = lists[0] if len(lists) == 1 else [value]
    if not isinstance(value, list) or not value or not all(isinstance(r, dict) for r in value):
        return [], []
    header: list[str] = []
    for row in value:
        header.extend(k for k in row if k not in header and not isinstance(row[k], (dict, list)))
    rows = [["" if row.get(k) is None else _cell(row.get(k)) for k in header] for row in value[:MAX_ROWS]]
    return header, rows


def _cell(value: object) -> str:
    if isinstance(value, bool):
        return str(value).lower()
    return str(value)


def _parse_csv(text: str) -> tuple[list[str], list[list[str]]]:
    lines = [line for line in text.splitlines() if line.strip()]
    if len(lines) < 2:
        return [], []
    delimiter = max(",;\t|", key=lambda d: lines[0].count(d))
    if lines[0].count(delimiter) == 0:
        return [], []
    records = list(csv.reader(io.StringIO("\n".join(lines)), delimiter=delimiter))
    header = [h.strip() for h in records[0]]
    width = len(header)
    rows = [[c.strip() for c in r] for r in records[1 : MAX_ROWS + 1] if len(r) == width]
    if not rows or any(not h for h in header):
        return [], []
    return header, rows


def _embedded(question: str) -> Iterable[str]:
    """Candidate tables inside a question: a JSON array or object, then the longest run of lines sharing a delimiter."""
    for opener, closer in (("[", "]"), ("{", "}")):
        start, end = question.find(opener), question.rfind(closer)
        if 0 <= start < end:
            yield question[start : end + 1]
    lines = question.splitlines()
    best: list[str] = []
    run: list[str] = []
    for line in lines:
        if run and _delimiters(line) == _delimiters(run[0]) and _delimiters(line) != (0, 0, 0):
            run.append(line)
        else:
            if len(run) > len(best):
                best = run
            run = [line] if _delimiters(line) != (0, 0, 0) else []
    if len(run) > len(best):
        best = run
    if len(best) >= 2:
        # « Here is my data: date,views » — the words before the colon introduce the header, they are not part of it.
        header = best[0]
        if ": " in header.split(",")[0]:
            header = header.rsplit(": ", 1)[1]
        yield "\n".join([header, *best[1:]])


def _delimiters(line: str) -> tuple[int, int, int]:
    return line.count(","), line.count(";"), line.count("\t")


# ── Typing the columns ──────────────────────────────────────────────────────────────────────────────────────────────


def parse_number(cell: str) -> tuple[float | None, str | None]:
    """A cell as a number, and the currency symbol or code written around it (``$12``, ``1 234,50 €``, ``45 CAD``)."""
    text = cell.strip().replace("\u00a0", "").replace("\u202f", "").replace(" ", "")
    unit = None
    for symbol in _SYMBOLS:
        if text.startswith(symbol) or text.endswith(symbol):
            unit, text = symbol, text.strip(symbol)
    for code in _CODES:
        if text.upper().startswith(code) or text.upper().endswith(code):
            unit = code
            text = text[3:] if text.upper().startswith(code) else text[:-3]
    if _THOUSANDS.match(text):
        text = text.replace(",", "")
    elif _COMMA_DECIMAL.match(text):
        text = text.replace(",", ".")
    if not _PLAIN.match(text):
        return None, None
    return float(text), unit


def parse_date(cell: str) -> date | None:
    match = _ISO_DATE.match(cell.strip())
    if not match:
        return None
    try:
        return date(int(match[1]), int(match[2]), int(match[3]))
    except ValueError:
        return None


def column_key(label: str, taken: set[str]) -> str:
    """An A2UI-safe key for a column (``[A-Za-z0-9_]``, at most 64), unique among those already taken."""
    ascii_label = unicodedata.normalize("NFKD", label).encode("ascii", "ignore").decode()
    key = re.sub(r"[^A-Za-z0-9_]+", "_", ascii_label).strip("_")[:56] or "col"
    unique, n = key, 2
    while unique in taken:
        unique, n = f"{key}_{n}", n + 1
    taken.add(unique)
    return unique


def _normalised(label: str) -> str:
    return unicodedata.normalize("NFKD", label.lower()).encode("ascii", "ignore").decode()


def _type_columns(header: Sequence[str], rows: Sequence[Sequence[str]]) -> tuple[list[Column], set[str]]:
    taken: set[str] = {"count", "week", "day", "month"}
    columns: list[Column] = []
    units: set[str] = set()
    for i, label in enumerate(header):
        cells = [r[i] for r in rows if r[i] != ""]
        key = column_key(label, taken)
        if cells and all(parse_date(c) for c in cells):
            columns.append(Column(key, label, "date"))
            continue
        parsed = [parse_number(c) for c in cells]
        if cells and all(v is not None for v, _ in parsed):
            seen = {u for _, u in parsed if u}
            units |= seen
            name = _normalised(label)
            intensive = bool(_INTENSIVE_WORDS.search(name)) and not _EXTENSIVE_WORDS.search(name)
            money = bool(seen) or bool(_MONEY_WORDS.search(name)) or any(s in label for s in "$€£")
            columns.append(Column(key, label, "number", money=money, intensive=intensive))
            continue
        columns.append(Column(key, label, "category"))
    for label in header:
        units |= {s for s in "$€£" if s in label} | (set(_CODES) & set(re.split(r"[^A-Z]+", label.upper())))
    return columns, units


def detect_currency(units: set[str], question: str) -> Currency | None:
    """The data's own currency (a symbol or a code in its cells or headers), else the one the question names."""
    codes = sorted({_SYMBOLS.get(u, u) for u in units if _SYMBOLS.get(u, u)})
    if len(codes) == 1:
        return Currency(codes[0], _symbol(codes[0]), "data")
    named = [code for code, pattern in _QUESTION_CURRENCIES if pattern.search(question)]
    if "$" in units:
        dollar = [c for c in named if c in ("CAD", "USD")]
        return Currency(dollar[0], "$", "question") if len(dollar) == 1 else Currency(None, "$", "data")
    if len(named) == 1:
        return Currency(named[0], _symbol(named[0]), "question")
    if not named and _DOLLAR.search(question):
        return Currency(None, "$", "question")
    return None


def _symbol(code: str) -> str:
    return {"CAD": "$", "USD": "US$", "EUR": "€", "GBP": "£", "CHF": "CHF"}.get(code, code)


# ── Computing ───────────────────────────────────────────────────────────────────────────────────────────────────────


def compute(data: str, question: str, language: Language) -> Facts:
    """Every figure the answer may cite, from the person's table."""
    header, raw_rows, source = read_table(data, question)
    if not header:
        return Facts(language=language, rows=0, source="none")
    columns, units = _type_columns(header, raw_rows)
    currency = detect_currency(units, question)
    if currency is None:
        columns = [Column(c.key, c.label, c.kind, money=False, intensive=c.intensive) for c in columns]
    rows = [_typed_row(columns, r) for r in raw_rows]
    builder = _Builder(language, currency)
    builder.add(len(rows), "count", "number of rows")
    measures = [c for c in columns if c.kind == "number"]
    for m in measures:
        values = [r[m.key] for r in rows if r[m.key] is not None]
        if not values:
            continue
        kind = builder.kind_of(m, values)
        if not m.intensive:
            builder.add(sum(values), kind, f"sum of {m.label}")
        builder.add(sum(values) / len(values), _mean_kind(kind), f"mean of {m.label} per row")
        builder.add(min(values), kind, f"smallest {m.label} in one row")
        builder.add(max(values), kind, f"largest {m.label} in one row")
    extensive = [m for m in measures if not m.intensive]
    categories = [c for c in columns if c.kind == "category" and 2 <= _distinct(rows, c.key) <= MAX_CATEGORIES]
    for dim in categories[:MAX_DIMENSIONS]:
        groups = _group(rows, lambda r, k=dim.key: r[k] or "—")
        builder.grouped(dim, groups, extensive, f"by {dim.label}", rank=True)
    dates = [c for c in columns if c.kind == "date"]
    if dates:
        builder.periods(dates[0], rows, extensive)
    return Facts(
        language=language,
        rows=len(rows),
        source=source,
        currency=currency,
        facts=tuple(builder.facts),
        tables=tuple(builder.tables),
        columns=tuple(columns),
    )


def _typed_row(columns: Sequence[Column], raw: Sequence[str]) -> dict:
    row: dict = {}
    for column, cell in zip(columns, raw, strict=True):
        if column.kind == "number":
            row[column.key] = parse_number(cell)[0] if cell != "" else None
        elif column.kind == "date":
            row[column.key] = parse_date(cell)
        else:
            row[column.key] = cell
    return row


def _distinct(rows: Sequence[dict], key: str) -> int:
    return len({r[key] for r in rows})


def _group(rows: Iterable[dict], key_of) -> dict:
    groups: dict = {}
    for row in rows:
        groups.setdefault(key_of(row), []).append(row)
    return groups


def _mean_kind(kind: FactKind) -> FactKind:
    return "money" if kind == "money" else "number"


def _clean(value: float) -> float:
    rounded = round(value, 6)
    return int(rounded) if rounded == int(rounded) else rounded


class _Builder:
    """Numbers facts and tables in the order they are added, so the same table always gives the same ids."""

    def __init__(self, language: Language, currency: Currency | None) -> None:
        self.language = language
        self.currency = currency
        self.facts: list[Fact] = []
        self.tables: list[FactTable] = []

    def add(self, value: float, kind: FactKind, label: str, key: str | None = None) -> None:
        if len(self.facts) < MAX_FACTS:
            self.facts.append(Fact(f"F{len(self.facts) + 1}", _clean(value), kind, label, key))

    def kind_of(self, column: Column, values: Sequence[float]) -> FactKind:
        if column.money and self.currency is not None:
            return "money"
        return "integer" if all(v == int(v) for v in values) else "number"

    def grouped(
        self,
        dim: Column,
        groups: dict,
        measures: Sequence[Column],
        title: str,
        *,
        rank: bool,
        order: Sequence | None = None,
        labels: dict | None = None,
    ) -> FactTable:
        """A table over ``groups`` and, per group, its count, its sum and share of each measure, and its rank."""
        keys = list(order if order is not None else groups)
        sums = {m.key: {k: sum(r[m.key] or 0 for r in groups[k]) for k in keys} for m in measures}
        if rank and measures:
            primary = next((m for m in measures if m.money), measures[0])
            keys.sort(key=lambda k: (-sums[primary.key][k], str(k)))
        elif rank:
            keys.sort(key=lambda k: (-len(groups[k]), str(k)))
        shown = labels or {k: str(k) for k in keys}
        count = Column("count", _WORDS[self.language]["count"], "count")
        cols = tuple(Column(m.key, m.label, "number", money=m.money, intensive=False) for m in measures)
        rows = []
        for k in keys:
            row = {dim.key: shown[k], "count": len(groups[k])}
            row.update({m.key: _clean(sums[m.key][k]) for m in measures})
            rows.append(row)
        table = FactTable(f"T{len(self.tables) + 1}", title, dim, (count, *cols), tuple(rows))
        self.tables.append(table)
        for m in measures:
            values = [r[m.key] for g in groups.values() for r in g if r[m.key] is not None]
            kind = self.kind_of(m, values)
            total = sum(sums[m.key].values())
            ranked = sorted(keys, key=lambda k, mk=m.key: (-sums[mk][k], str(k)))
            for k in keys:
                place = ranked.index(k) + 1
                where = f"{m.label} where {dim.label} = {shown[k]}"
                self.add(
                    sums[m.key][k],
                    kind,
                    f"sum of {where} (rank {place} of {len(keys)} by {m.label})",
                    shown[k],
                )
                if total:
                    self.add(
                        sums[m.key][k] / total,
                        "percent",
                        f"share of {m.label} for {dim.label} = {shown[k]}",
                        shown[k],
                    )
        for k in keys:
            self.add(
                len(groups[k]),
                "count",
                f"number of rows where {dim.label} = {shown[k]}",
                shown[k],
            )
        return table

    def periods(self, column: Column, rows: Sequence[dict], measures: Sequence[Column]) -> None:
        dated = [r for r in rows if r[column.key] is not None]
        if not dated:
            return
        first, last = (
            min(r[column.key] for r in dated),
            max(r[column.key] for r in dated),
        )
        years = first.year != last.year
        words = _WORDS[self.language]
        days = _group(dated, lambda r: r[column.key])
        weeks = _group(dated, lambda r: r[column.key] - timedelta(days=r[column.key].weekday()))
        months = _group(dated, lambda r: (r[column.key].year, r[column.key].month))
        for name, groups, label_of, title in (
            ("day", days, lambda d: day_label(d, self.language, years), "by day"),
            (
                "week",
                weeks,
                lambda w: week_label(w, self.language),
                "by week (Monday to Sunday)",
            ),
            ("month", months, lambda m: month_label(m, self.language), "by month"),
        ):
            if len(groups) < 2:
                continue
            order = sorted(groups)
            labels = {k: label_of(k) for k in order}
            dim = Column(name, words[name], "period")
            self.grouped(dim, groups, measures, title, rank=False, order=order, labels=labels)
            if name == "week":
                for w in order:
                    if w < first or w + timedelta(days=6) > last:
                        span = (min(w + timedelta(days=6), last) - max(w, first)).days + 1
                        self.add(
                            span,
                            "count",
                            f"days of the week {labels[w]} inside the data (a partial week)",
                            labels[w],
                        )
            for m in measures:
                totals = {k: sum(r[m.key] or 0 for r in groups[k]) for k in order}
                kind = self.kind_of(m, list(totals.values()))
                best = max(order, key=lambda k, t=totals: (t[k], -order.index(k)))
                worst = min(order, key=lambda k, t=totals: (t[k], order.index(k)))
                self.add(
                    totals[best],
                    kind,
                    f"best {name} by {m.label}: {labels[best]}",
                    labels[best],
                )
                self.add(
                    totals[worst],
                    kind,
                    f"weakest {name} by {m.label}: {labels[worst]}",
                    labels[worst],
                )


# ── Labels ──────────────────────────────────────────────────────────────────────────────────────────────────────────


def day_label(day: date, language: Language, with_year: bool = False) -> str:
    month = _MONTHS[language][day.month - 1]
    text = f"{month} {day.day}" if language == "en" else f"{day.day} {month}"
    return f"{text} {day.year}" if with_year else text


def week_label(monday: date, language: Language) -> str:
    """A Monday-to-Sunday week, its two ends joined by an en dash.

    ``Oct 19-25``, ``Sep 28-Oct 4`` (en); ``19-25 oct.``, ``28 sept.-4 oct.`` (fr).
    """
    sunday = monday + timedelta(days=6)
    months = _MONTHS[language]
    year = f" {sunday.year}" if monday.year != sunday.year else ""
    if language == "en":
        if monday.month == sunday.month:
            return f"{months[monday.month - 1]} {monday.day}\u2013{sunday.day}"
        return f"{months[monday.month - 1]} {monday.day}\u2013{months[sunday.month - 1]} {sunday.day}{year}"
    if monday.month == sunday.month:
        return f"{monday.day}\u2013{sunday.day} {months[monday.month - 1]}"
    return f"{monday.day} {months[monday.month - 1]}\u2013{sunday.day} {months[sunday.month - 1]}{year}"


def month_label(year_month: tuple[int, int], language: Language) -> str:
    year, month = year_month
    return f"{_MONTHS_LONG[language][month - 1]} {year}"


# ── For the model ───────────────────────────────────────────────────────────────────────────────────────────────────


def describe(facts: Facts) -> str:
    """The facts and tables as the model reads them: one line each, the raw value and what it is."""
    if facts.source == "none":
        return "No table could be read from the data or the question: there are no facts and no tables."
    lines = [f"Rows: {facts.rows}."]
    if facts.currency and facts.currency.code:
        lines.append(f"Currency of the money figures: {facts.currency.code} (named by the {facts.currency.source}).")
    elif facts.currency:
        lines.append(f"Money figures are in {facts.currency.symbol} (which dollar is not said).")
    else:
        lines.append("No currency is named: never write one.")
    lines.append("Facts (write one in a text as {F12}):")
    for f in facts.facts:
        unit = " (a fraction: 0.25 is 25 %)" if f.kind == "percent" else ""
        lines.append(f"{f.id} = {f.value}{unit} — {f.label}")
    lines.append("Tables (a chart or a table draws one; its columns are the keys below):")
    for t in facts.tables:
        keys = ", ".join([t.dimension.key, *[m.key for m in t.measures]])
        lines.append(f"{t.id} — {t.title}: columns {keys} ({len(t.rows)} rows)")
    return "\n".join(lines)
