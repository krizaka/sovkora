"""The routing settings of the document readers: which reader reads which kind of page, as data (krizaka-internal#4198).

The settings are flat keys, the shape the platform's runtime configuration holds (``orazaka_runtime_config``):

* ``reader.default.<kind>`` — the reader of each page kind (``pdf-scan``, ``screenshot``, ``photo``), required;
* ``reader.fallback`` — the reader tried when the default is down or too slow, or empty for none;
* ``reader.vlm.enabled`` — ``true`` lets a generative reader (a vision-language model) be routed; off as seeded;
* ``reader.page-budget`` — the most pages one call may read (1 to 12);
* ``reader.timeout-ms`` — how long a reader may take on one page (100 to 600 000);
* ``reader.cost-micros.<reader>`` — what one page costs on that reader, in millionths of a US dollar (0 when unset).

The default reader of a kind is chosen by the measurement (krizaka-internal#4060) and changed here, never in a product.
Every value is checked when the settings are loaded: a wrong one is refused by its key, never replaced by a guess.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Mapping

from document_reader.port import PageKind

PAGE_BUDGET = (1, 12)
TIMEOUT_MS = (100, 600_000)
COST_MICROS = (0, 1_000_000)
_BOOLEANS = {"true": True, "false": False}
_COST = "reader.cost-micros."


class RoutingError(ValueError):
    """A setting out of its bounds, named by its key (its value is a setting, never a page)."""

    def __init__(self, key: str) -> None:
        super().__init__(f"reader setting out of bounds: {key}")
        self.key = key


def _bounded(settings: Mapping[str, str], key: str, bounds: tuple[int, int]) -> int:
    try:
        value = int(settings.get(key, ""))
    except ValueError:
        raise RoutingError(key) from None
    if not bounds[0] <= value <= bounds[1]:
        raise RoutingError(key)
    return value


@dataclass(frozen=True)
class ReaderRouting:
    defaults: Mapping[PageKind, str]
    fallback: str | None
    vlm_enabled: bool
    page_budget: int
    timeout_ms: int
    costs: Mapping[str, int]

    @classmethod
    def from_settings(cls, settings: Mapping[str, str], *, known: set[str], generative: set[str]) -> ReaderRouting:
        """The routing of ``settings``, each reader one of ``known``; a ``generative`` one only behind the switch."""
        vlm_key = "reader.vlm.enabled"
        if settings.get(vlm_key, "false") not in _BOOLEANS:
            raise RoutingError(vlm_key)
        vlm_enabled = _BOOLEANS[settings.get(vlm_key, "false")]

        def reader(key: str, *, required: bool) -> str | None:
            value = settings.get(key, "").strip()
            if not value and not required:
                return None
            if value not in known or (value in generative and not vlm_enabled):
                raise RoutingError(key)
            return value

        defaults = {kind: reader(f"reader.default.{kind.value}", required=True) for kind in PageKind}
        costs = {}
        for key in settings:
            if key.startswith(_COST):
                name = key.removeprefix(_COST)
                if name not in known:
                    raise RoutingError(key)
                costs[name] = _bounded(settings, key, COST_MICROS)
        return cls(
            defaults=defaults,
            fallback=reader("reader.fallback", required=False),
            vlm_enabled=vlm_enabled,
            page_budget=_bounded(settings, "reader.page-budget", PAGE_BUDGET),
            timeout_ms=_bounded(settings, "reader.timeout-ms", TIMEOUT_MS),
            costs=costs,
        )

    def default_for(self, kind: PageKind) -> str:
        return self.defaults[kind]

    def cost_micros(self, reader: str) -> int:
        return self.costs.get(reader, 0)
