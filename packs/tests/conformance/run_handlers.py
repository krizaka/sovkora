#!/usr/bin/env python3
"""Calls a pack worker's own handler for each capability of a request, with no broker and no model.

    python3 run_handlers.py <pack-dir> < requests.json  ->  one answer per request on stdout

A request is ``{"routingKey": "...", "input": {...}}``; an answer is ``{"result": {...}}`` or
``{"error": "<type name>"}`` (the type name only: a payload, a document or a message never goes into
an answer, as in the workers themselves). The handler is found from the worker's own declarations:
a ``HANDLERS`` table of routing key -> function, else the ``handle(routing_key, job)`` the shared
loop builds, else the reference worker's ``_handle(job)``. It is the code that runs in production,
called the way the loop calls it.
"""

import importlib
import json
import sys
from pathlib import Path


def worker_modules(worker_dir: Path):
    sys.path.insert(0, str(worker_dir))
    for source in sorted(worker_dir.glob("*.py")):
        if source.name.startswith("test_") or source.stem in ("broker", "fetch_models"):
            continue
        try:
            yield importlib.import_module(source.stem)
        except ImportError:  # a module whose library is absent here (a model it needs) is not a candidate
            continue


def find_handler(modules, routing_key: str):
    for module in modules:
        table = getattr(module, "HANDLERS", None)
        if isinstance(table, dict) and routing_key in table:
            return lambda payload, fn=table[routing_key]: fn(payload)
    for module in modules:
        handle = getattr(module, "handle", None)
        if callable(handle) and not isinstance(getattr(module, "HANDLERS", None), dict):
            return lambda payload, fn=handle: fn(routing_key, {"payload": payload})[0]
    for module in modules:
        # The reference worker's own `_handle(job)`: the one handler that takes the job and no routing key.
        underscore = getattr(module, "_handle", None)
        if callable(underscore) and module.__name__.endswith("_worker"):
            return lambda payload, fn=underscore: fn({"payload": payload})[0]
    return None


def main() -> None:
    worker_dir = Path(sys.argv[1]).resolve() / "worker"
    requests = json.load(sys.stdin)
    modules = list(worker_modules(worker_dir))
    answers = []
    for request in requests:
        handler = find_handler(modules, request["routingKey"])
        if handler is None:
            answers.append({"error": "NO_HANDLER"})
            continue
        try:
            answers.append({"result": handler(request["input"])})
        except (ArithmeticError, LookupError, TypeError, ValueError, RuntimeError) as failure:
            answers.append({"error": type(failure).__name__})
    json.dump(answers, sys.stdout)


if __name__ == "__main__":
    main()
