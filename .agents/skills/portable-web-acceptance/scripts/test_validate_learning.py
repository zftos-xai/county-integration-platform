#!/usr/bin/env python3
"""Unit tests for the project acceptance learning ledger validator."""

from __future__ import annotations

import copy
import json
import unittest
from pathlib import Path

from validate_learning import validate_ledger

LEDGER_PATH = (
    Path(__file__).parents[1] / "references" / "project-learning.json"
)


class LearningLedgerTests(unittest.TestCase):
    """Verify active learning entries remain actionable and safe to retain."""

    def setUp(self) -> None:
        self.ledger = json.loads(LEDGER_PATH.read_text(encoding="utf-8"))

    def test_project_ledger_is_valid(self) -> None:
        self.assertEqual(validate_ledger(self.ledger), [])

    def test_entries_need_regression_checks(self) -> None:
        ledger = copy.deepcopy(self.ledger)
        del ledger["entries"][0]["regression_check"]
        errors = validate_ledger(ledger)
        self.assertTrue(any("regression_check" in error for error in errors))

    def test_secret_bearing_fields_are_rejected(self) -> None:
        ledger = copy.deepcopy(self.ledger)
        ledger["entries"][0]["password"] = "must-not-be-stored"
        errors = validate_ledger(ledger)
        self.assertIn("ledger must not contain secret-bearing field names", errors)

    def test_duplicate_ids_are_rejected(self) -> None:
        ledger = copy.deepcopy(self.ledger)
        ledger["entries"].append(copy.deepcopy(ledger["entries"][0]))
        errors = validate_ledger(ledger)
        self.assertTrue(any("duplicate learning id" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
