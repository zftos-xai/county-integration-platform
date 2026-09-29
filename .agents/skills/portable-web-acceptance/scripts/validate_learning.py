#!/usr/bin/env python3
"""Validate the county platform Web acceptance learning ledger."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

SCHEMA = "county-platform-acceptance-learning-1.0"
STATUSES = {"active", "retired"}
REQUIRED_FIELDS = {
    "id",
    "status",
    "source",
    "scope",
    "trigger",
    "required_action",
    "regression_check",
    "evidence_required",
    "last_verified",
}
SENSITIVE_KEYS = {"password", "token", "cookie", "secret", "credential"}


def _contains_sensitive_key(value: Any) -> bool:
    """Return whether a nested object uses a field name reserved for secrets."""
    if isinstance(value, dict):
        return any(
            str(key).lower() in SENSITIVE_KEYS or _contains_sensitive_key(child)
            for key, child in value.items()
        )
    if isinstance(value, list):
        return any(_contains_sensitive_key(item) for item in value)
    return False


def validate_ledger(data: Any) -> list[str]:
    """Return structural and safety errors for a project learning ledger."""
    errors: list[str] = []
    if not isinstance(data, dict):
        return ["ledger must be a JSON object"]
    if data.get("schema") != SCHEMA:
        errors.append(f"schema must be {SCHEMA}")
    if _contains_sensitive_key(data):
        errors.append("ledger must not contain secret-bearing field names")
    entries = data.get("entries")
    if not isinstance(entries, list):
        return errors + ["entries must be an array"]

    seen: set[str] = set()
    for index, entry in enumerate(entries):
        label = f"entries[{index}]"
        if not isinstance(entry, dict):
            errors.append(f"{label} must be an object")
            continue
        missing = sorted(REQUIRED_FIELDS - set(entry))
        if missing:
            errors.append(f"{label} misses fields: {', '.join(missing)}")
        entry_id = entry.get("id")
        if not isinstance(entry_id, str) or not entry_id.strip():
            errors.append(f"{label}.id must be a non-empty string")
        elif entry_id in seen:
            errors.append(f"duplicate learning id: {entry_id}")
        else:
            seen.add(entry_id)
        if entry.get("status") not in STATUSES:
            errors.append(f"{label}.status must be active or retired")
        for field in (
            "source",
            "scope",
            "trigger",
            "required_action",
            "regression_check",
        ):
            if not isinstance(entry.get(field), str) or not entry[field].strip():
                errors.append(f"{label}.{field} must be a non-empty string")
        evidence = entry.get("evidence_required")
        if not isinstance(evidence, list) or not evidence or any(
            not isinstance(item, str) or not item.strip() for item in evidence
        ):
            errors.append(f"{label}.evidence_required must contain non-empty strings")
        verified = entry.get("last_verified")
        if verified is not None and (
            not isinstance(verified, str) or len(verified) != 10
        ):
            errors.append(f"{label}.last_verified must be YYYY-MM-DD or null")
    return errors


def main() -> int:
    """Validate one ledger file and report errors without changing it."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("ledger", type=Path)
    args = parser.parse_args()
    try:
        data = json.loads(args.ledger.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        print(f"cannot read ledger: {exc}", file=sys.stderr)
        return 2
    errors = validate_ledger(data)
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print("OK: learning ledger is structurally valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
