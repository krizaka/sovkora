"""uv.lock resolves exactly the pins of requirements.txt (the set CI installs): a bump of one without the other fails."""

import re
import tomllib
from pathlib import Path

HERE = Path(__file__).parent
PIN = re.compile(r"^([A-Za-z0-9_.-]+)==([^\s;]+)")


def _locked() -> dict[str, set[str]]:
    versions: dict[str, set[str]] = {}
    for package in tomllib.loads((HERE / "uv.lock").read_text())["package"]:
        versions.setdefault(package["name"].lower(), set()).add(package["version"].split("+")[0])
    return versions


def test_every_pin_of_requirements_is_what_the_lock_resolves():
    locked = _locked()
    pins = [m.groups() for line in (HERE / "requirements.txt").read_text().splitlines() if (m := PIN.match(line))]
    assert pins
    for name, version in pins:
        assert version.split("+")[0] in locked.get(name.lower(), set()), f"{name}=={version} is not in uv.lock"
