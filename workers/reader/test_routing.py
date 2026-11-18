"""The routing settings: the default reader per kind is data, bounded, and a wrong value is refused at load."""

import pytest

from document_reader.port import PageKind
from document_reader.routing import ReaderRouting, RoutingError

KNOWN = {"tesseract", "paddleocr", "doctr", "textract", "vlm"}
GENERATIVE = {"vlm"}

SEED = {
    "reader.default.pdf-scan": "paddleocr",
    "reader.default.screenshot": "paddleocr",
    "reader.default.photo": "paddleocr",
    "reader.fallback": "tesseract",
    "reader.vlm.enabled": "false",
    "reader.page-budget": "12",
    "reader.timeout-ms": "30000",
    "reader.cost-micros.textract": "1500",
}


def load(**changes):
    settings = {**SEED, **changes}
    return ReaderRouting.from_settings(settings, known=KNOWN, generative=GENERATIVE)


def test_the_seed_routes_every_kind():
    routing = load()
    assert {kind: routing.default_for(kind) for kind in PageKind} == dict.fromkeys(PageKind, "paddleocr")
    assert routing.fallback == "tesseract"
    assert routing.vlm_enabled is False
    assert routing.page_budget == 12


def test_switching_the_default_reader_is_a_setting():
    routing = load(**{"reader.default.photo": "doctr"})
    assert routing.default_for(PageKind.PHOTO) == "doctr"
    assert routing.default_for(PageKind.SCREENSHOT) == "paddleocr"


def test_a_cost_is_per_reader_and_zero_when_unset():
    routing = load()
    assert routing.cost_micros("textract") == 1500
    assert routing.cost_micros("tesseract") == 0


@pytest.mark.parametrize(
    ("key", "value"),
    [
        ("reader.default.photo", "abbyy"),
        ("reader.fallback", "nobody"),
        ("reader.page-budget", "0"),
        ("reader.page-budget", "13"),
        ("reader.page-budget", "many"),
        ("reader.timeout-ms", "50"),
        ("reader.timeout-ms", "600001"),
        ("reader.vlm.enabled", "yes"),
        ("reader.cost-micros.textract", "-1"),
        ("reader.cost-micros.abbyy", "10"),
    ],
)
def test_a_value_out_of_bounds_is_refused_by_name(key, value):
    with pytest.raises(RoutingError) as refused:
        load(**{key: value})
    assert refused.value.key == key


def test_a_missing_default_is_refused_not_guessed():
    settings = {k: v for k, v in SEED.items() if k != "reader.default.screenshot"}
    with pytest.raises(RoutingError) as refused:
        ReaderRouting.from_settings(settings, known=KNOWN, generative=GENERATIVE)
    assert refused.value.key == "reader.default.screenshot"


def test_a_generative_default_needs_the_vlm_switch():
    with pytest.raises(RoutingError) as refused:
        load(**{"reader.default.photo": "vlm"})
    assert refused.value.key == "reader.default.photo"
    assert load(**{"reader.default.photo": "vlm", "reader.vlm.enabled": "true"}).default_for(PageKind.PHOTO) == "vlm"


def test_a_generative_fallback_needs_the_vlm_switch():
    with pytest.raises(RoutingError):
        load(**{"reader.fallback": "vlm"})


def test_no_fallback_is_a_choice():
    assert load(**{"reader.fallback": ""}).fallback is None
