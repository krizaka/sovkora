"""The data worker through its routing keys, as the broker delivers a job (krizaka-internal#3628)."""

import json
from pathlib import Path

import pytest

# data_worker imports broker, which puts worker-common (worker_failure) on the path.
# isort: off
import dashboard
import data_worker
import facts
import worker_failure

# isort: on

HERE = Path(__file__).parent
CSV = (HERE / "fixtures" / "ventes-atelier-octobre.csv").read_text(encoding="utf-8")
RUN_28954CDF = (HERE / "fixtures" / "run-28954cdf-answer.txt").read_text(encoding="utf-8")
QUESTION = "Amounts are in Canadian dollars. Which product earned the most, and which week was the best?"


def _run(routing_key: str, payload: dict) -> dict:
    result, consumption = data_worker.handle(routing_key, {"jobId": "j-1", "payload": payload})
    assert consumption["tokens"] == 0, "the worker spends no token"
    return result


def _facts() -> dict:
    return _run("job.data.facts", {"data": CSV, "question": QUESTION, "language": "en"})


def _valid_plan(facts_json: str) -> str:
    computed = facts.facts_from_json(facts_json)
    lamp = next(f.id for f in computed.figures if f.label.startswith("sum of total where produit = lampe"))
    return json.dumps(
        {
            "summary": f"The lamp earned {{{lamp}}}.",
            "kpis": [{"label": "Lamp", "fact": lamp}],
        }
    )


def test_the_facts_job_publishes_the_facts_for_the_model_and_for_the_next_step():
    result = _facts()
    assert result["hasTable"] is True
    assert result["rows"] == 101
    assert "= 3770 — sum of total where produit = lampe-gres-creme" in result["facts"]
    assert facts.facts_from_json(result["factsJson"]).rows == 101


def test_the_first_check_reports_a_refused_answer_so_the_studio_can_ask_again():
    result = _run(
        "job.data.dashboard",
        {"facts": _facts()["factsJson"], "answer": RUN_28954CDF, "question": QUESTION},
    )
    assert result["valid"] is False
    assert result["answer"] == ""
    assert result["problems"].startswith("- ")


def test_the_final_step_draws_the_retry_when_the_first_answer_was_refused():
    computed = _facts()["factsJson"]
    result = _run(
        "job.data.dashboard",
        {
            "facts": computed,
            "answer": RUN_28954CDF,
            "retry": _valid_plan(computed),
            "question": QUESTION,
            "final": "true",
        },
    )
    assert result["valid"] is True
    assert result["answer"].startswith("The lamp earned $3,770.")
    assert "```a2ui\n" in result["answer"]


def test_the_final_step_fails_as_the_executor_s_fault_when_both_answers_are_refused():
    payload = {
        "facts": _facts()["factsJson"],
        "answer": RUN_28954CDF,
        "retry": "{",
        "final": "true",
    }
    with pytest.raises(dashboard.AnswerRefusedError) as refused:
        _run("job.data.dashboard", payload)
    answer = worker_failure.failure_answer("j-1", refused.value)
    assert answer == {
        "jobId": "j-1",
        "error": "AnswerRefusedError",
        "cause": "EXECUTOR_FAULT",
    }


def test_unreadable_facts_are_the_executor_s_fault_not_the_person_s():
    with pytest.raises(facts.FactsUnreadableError) as unreadable:
        _run("job.data.dashboard", {"facts": "{}", "answer": "{}", "final": "true"})
    assert worker_failure.failure_answer("j-1", unreadable.value)["cause"] == "EXECUTOR_FAULT"


def test_an_unknown_language_is_refused_with_its_code():
    with pytest.raises(data_worker.InvalidJobPayload) as refused:
        _run("job.data.facts", {"data": CSV, "question": QUESTION, "language": "de"})
    assert refused.value.reason == "LANGUAGE_UNKNOWN"


def test_the_worker_drains_its_own_family_only():
    assert data_worker.DECLARATION["family"] == "data"
    assert data_worker.DECLARATION["bindings"] == ["job.data.*"]
    assert set(data_worker.HANDLERS) == {"job.data.facts", "job.data.dashboard"}
