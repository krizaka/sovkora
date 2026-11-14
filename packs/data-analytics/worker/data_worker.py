"""The data-analytics worker: the deterministic half of the data-driven-ui Studio (krizaka-internal#3628).

``job.data.facts`` computes every figure of the person's table before the model writes; ``job.data.dashboard`` checks
the model's plan against those facts and draws the A2UI surface from them. The model step between the two is the
blueprint's, metered like any step. This worker calls no provider.
"""

import os

import yaml

# broker puts worker-common on the path, so it comes before worker_failure and worker_loop.
# isort: off
import broker  # noqa: F401
import dashboard
import facts
import worker_failure
import worker_loop

# isort: on

HERE = os.path.dirname(os.path.abspath(__file__))
with open(os.path.join(HERE, "worker.yaml"), encoding="utf-8") as declaration:
    DECLARATION = yaml.safe_load(declaration)
LANGUAGE_UNKNOWN = "LANGUAGE_UNKNOWN"
MAX_PROBLEMS = 12


class InvalidJobPayload(worker_failure.InvalidJobPayload):
    """The payload was checked and is unusable — the one cause that bills. Its reason is a code, never a value."""

    REASONS = frozenset({LANGUAGE_UNKNOWN})


def _language(payload: dict) -> facts.Language:
    language = str(payload.get("language") or "en").strip()
    if language not in ("en", "fr"):
        raise InvalidJobPayload(LANGUAGE_UNKNOWN)
    return language  # type: ignore[return-value]


def _facts(payload: dict) -> dict:
    computed = facts.compute(
        str(payload.get("data") or ""),
        str(payload.get("question") or ""),
        _language(payload),
    )
    return {
        "factsJson": computed.to_json(),
        "facts": facts.describe(computed),
        "hasTable": computed.source != "none",
        "rows": computed.rows,
    }


def _dashboard(payload: dict) -> dict:
    """The drawn answer. A first check (``final`` false) reports a refusal so the blueprint can ask again; the final one
    raises :class:`dashboard.AnswerRefusedError` (an executor fault, never billed) when neither answer holds."""
    computed = facts.facts_from_json(str(payload.get("facts") or ""))
    question = str(payload.get("question") or "")
    answers = [str(payload.get("answer") or ""), str(payload.get("retry") or "")]
    drawn, problems = dashboard.assemble(answers, computed, question)
    final = str(payload.get("final") or "false").strip().lower() == "true"
    if drawn is None and final:
        raise dashboard.AnswerRefusedError()
    return {
        "answer": drawn or "",
        "valid": drawn is not None,
        "problems": "\n".join(f"- {p}" for p in problems[:MAX_PROBLEMS]),
    }


HANDLERS = {"job.data.facts": _facts, "job.data.dashboard": _dashboard}
handle = worker_loop.make_handle(HANDLERS)


def main() -> None:
    worker_loop.run("data", DECLARATION, handle)


if __name__ == "__main__":
    main()
