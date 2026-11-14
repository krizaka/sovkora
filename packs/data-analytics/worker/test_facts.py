"""The figures are computed, never generated (krizaka-internal#3628): the shop's October sales, added up here."""

from pathlib import Path

import facts

CSV = (Path(__file__).parent / "fixtures" / "ventes-atelier-octobre.csv").read_text(
    encoding="utf-8"
)
QUESTION_EN = (
    "Amounts are in Canadian dollars. Which three products earned the most in October, and which week "
    "(Monday to Sunday) was the best?"
)


def _fact(computed: facts.Facts, label: str) -> facts.Fact:
    found = [f for f in computed.facts if f.label == label]
    assert len(found) == 1, (
        f"{label}: {[f.label for f in computed.facts if label[:20] in f.label]}"
    )
    return found[0]


def test_the_lamp_earned_3770_and_ranks_first():
    computed = facts.compute(CSV, QUESTION_EN, "en")
    lamp = _fact(
        computed, "sum of total where produit = lampe-gres-creme (rank 1 of 5 by total)"
    )
    assert lamp.value == 3770
    assert lamp.kind == "money"
    assert computed.rows == 101
    assert _fact(computed, "sum of total").value == 8576


def test_the_ranking_of_the_products_by_total():
    computed = facts.compute(CSV, QUESTION_EN, "en")
    by_product = next(t for t in computed.tables if t.title == "by produit")
    assert [(r["produit"], r["total"]) for r in by_product.rows] == [
        ("lampe-gres-creme", 3770),
        ("tasse-mouchetee", 1368),
        ("vase-cendre", 1334),
        ("assiette-lin", 1080),
        ("bol-tenmoku", 1024),
    ]


def test_the_best_week_monday_to_sunday_is_oct_19_25_at_1962():
    computed = facts.compute(CSV, QUESTION_EN, "en")
    best = _fact(computed, "best week by total: Oct 19–25")
    assert best.value == 1962
    assert best.key == "Oct 19–25"
    weeks = next(t for t in computed.tables if t.dimension.key == "week")
    assert [(r["week"], r["total"]) for r in weeks.rows] == [
        ("Sep 28–Oct 4", 1443),
        ("Oct 5–11", 1668),
        ("Oct 12–18", 1886),
        ("Oct 19–25", 1962),
        ("Oct 26–Nov 1", 1617),
    ]


def test_a_partial_week_says_how_many_of_its_days_are_in_the_data():
    computed = facts.compute(CSV, QUESTION_EN, "en")
    assert (
        _fact(
            computed, "days of the week Sep 28–Oct 4 inside the data (a partial week)"
        ).value
        == 4
    )
    assert (
        _fact(
            computed, "days of the week Oct 26–Nov 1 inside the data (a partial week)"
        ).value
        == 6
    )


def test_the_weeks_are_written_in_french_for_a_french_answer():
    computed = facts.compute(
        CSV,
        "Les montants sont en dollars canadiens. Quelle semaine a été la meilleure ?",
        "fr",
    )
    assert _fact(computed, "best week by total: 19–25 oct.").value == 1962
    assert computed.currency == facts.Currency("CAD", "$", "question")


def test_the_currency_comes_from_the_data_or_the_question_and_is_never_assumed():
    assert facts.compute(CSV, "Which product sold best?", "en").currency is None
    assert facts.compute(
        "item,price\nmug,12 €\nbowl,8 €", "", "en"
    ).currency == facts.Currency("EUR", "€", "data")
    # The data's own currency wins over the question's.
    assert (
        facts.compute("item,price\nmug,€12", "in dollars?", "en").currency.code == "EUR"
    )
    assert (
        facts.compute("item,total_cad\nmug,12\nbowl,3", "", "en").currency.code == "CAD"
    )
    dollars = facts.compute("item,price\nmug,$12\nbowl,$8", "", "en").currency
    assert dollars == facts.Currency(None, "$", "data")


def test_without_a_currency_no_figure_is_money():
    computed = facts.compute(CSV, "Which product sold best?", "en")
    assert all(f.kind != "money" for f in computed.facts)


def test_a_unit_price_is_never_summed():
    computed = facts.compute(CSV, QUESTION_EN, "en")
    labels = [f.label for f in computed.facts]
    assert "sum of prix_unitaire" not in labels
    assert "mean of prix_unitaire per row" in labels
    assert all(
        "prix_unitaire" not in [m.key for m in t.measures] for t in computed.tables
    )


def test_a_table_pasted_in_the_question_is_read():
    computed = facts.compute(
        "",
        "Voici le CSV fictif : jour,ventes\nLundi,0\nMardi,4\nMontre les ventes.",
        "fr",
    )
    assert computed.source == "question"
    assert computed.rows == 2
    assert _fact(computed, "sum of ventes").value == 4
    assert [c.label for c in computed.columns] == ["jour", "ventes"], (
        "the words before the colon are the question's"
    )


def test_a_json_table_is_read():
    computed = facts.compute(
        '[{"branch":"North","visitors":8},{"branch":"South","visitors":5}]', "", "en"
    )
    assert computed.source == "data"
    assert (
        _fact(
            computed, "sum of visitors where branch = North (rank 1 of 2 by visitors)"
        ).value
        == 8
    )


def test_no_table_gives_no_facts():
    computed = facts.compute("", "What were my sales?", "en")
    assert computed.source == "none"
    assert computed.facts == ()
    assert "No table" in facts.describe(computed)


def test_numbers_written_the_french_or_the_english_way():
    assert facts.parse_number("1 234,50 €") == (1234.5, "€")
    assert facts.parse_number("1,234.5") == (1234.5, None)
    assert facts.parse_number("$12") == (12.0, "$")
    assert facts.parse_number("45 CAD") == (45.0, "CAD")
    assert facts.parse_number("lampe") == (None, None)


def test_the_facts_survive_the_trip_between_two_steps():
    computed = facts.compute(CSV, QUESTION_EN, "en")
    assert facts.facts_from_json(computed.to_json()) == computed


def test_unreadable_facts_are_refused():
    try:
        facts.facts_from_json('{"rows": 3}')
    except facts.FactsUnreadable:
        return
    raise AssertionError("facts without their fields must be refused")


def test_the_model_reads_each_fact_with_its_id():
    text = facts.describe(facts.compute(CSV, QUESTION_EN, "en"))
    assert "Currency of the money figures: CAD (named by the question)." in text
    assert (
        "= 3770 — sum of total where produit = lampe-gres-creme (rank 1 of 5 by total)"
        in text
    )
    assert "T2 — by canal" in text
