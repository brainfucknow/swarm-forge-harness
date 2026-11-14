"""Parse the SwarmForge catalogue files of a checkout: roles.conf, tiers.conf,
routes.conf. Pure functions over file text, so tests need no checkout."""

from __future__ import annotations

import re
from dataclasses import dataclass, field

_COMMENT = re.compile(r"^\s*#")


def conf_rows(text: str) -> list[list[str]]:
    """The data rows of a swarmforge conf file: whitespace-split fields, no
    comment or blank lines."""
    rows = []
    for line in text.splitlines():
        if not line.strip() or _COMMENT.match(line):
            continue
        rows.append(line.split())
    return rows


@dataclass(frozen=True)
class Role:
    name: str
    tier: str
    runs_as: str
    flags: frozenset[str] = field(default_factory=frozenset)

    @property
    def is_gate(self) -> bool:
        return "gate" in self.flags

    @property
    def is_spec_judge(self) -> bool:
        return "spec-judge" in self.flags


def load_roles(roles_conf: str) -> list[Role]:
    """The roles of roles.conf in file (canonical) order."""
    return [
        Role(name=row[0], tier=row[1], runs_as=row[2], flags=frozenset(row[3:]))
        for row in conf_rows(roles_conf)
    ]


def load_tiers(tiers_conf: str) -> dict[str, dict[str, str]]:
    """Each tier mapped to its per-harness models, keyed by harness column name:
    claude, codex, copilot, kilo, vibe. The vibe column holds the Mistral model
    id the workflow uses."""
    columns = ["tier", "claude", "codex", "copilot", "kilo", "vibe"]
    tiers: dict[str, dict[str, str]] = {}
    for row in conf_rows(tiers_conf):
        tiers[row[0]] = {
            columns[i]: row[i] for i in range(1, min(len(columns), len(row)))
        }
    return tiers


_DEFAULT_MODELS = {
    "frontier": "mistral-large-latest",
    "strong": "mistral-medium-latest",
    "fast": "mistral-small-latest",
}


def mistral_model(tiers: dict[str, dict[str, str]], tier: str) -> str:
    """The Mistral model for a tier: the vibe column of tiers.conf, or the
    tier's default Mistral model when the column says inherit."""
    model = tiers.get(tier, {}).get("vibe", "inherit")
    if model in (None, "", "inherit"):
        return _DEFAULT_MODELS.get(tier, "mistral-medium-latest")
    return model


def load_routes(routes_conf: str) -> dict[str, list[str]]:
    """Each named route mapped to its roles in order."""
    return {
        row[0]: row[1].split(",") for row in conf_rows(routes_conf) if len(row) > 1
    }


def gates(roles: list[Role]) -> list[str]:
    """The gate roles in file order."""
    return [r.name for r in roles if r.is_gate]


def spec_judge(roles: list[Role]) -> str | None:
    """The spec-judge role, or None when roles.conf has none."""
    for r in roles:
        if r.is_spec_judge:
            return r.name
    return None


def stages(roles: list[Role]) -> list[str]:
    """The stage roles in file order: every role that is neither a gate nor
    the spec-judge."""
    return [r.name for r in roles if not r.is_gate and not r.is_spec_judge]


def role_by_name(roles: list[Role], name: str) -> Role | None:
    return next((r for r in roles if r.name == name), None)
