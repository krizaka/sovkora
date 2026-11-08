"""document-validation worker: the broker connection is the shared one (../../worker-common/worker_broker.py)."""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "worker-common"))
from worker_broker import connection_parameters, credentials_from_environment, describe  # noqa: F401
