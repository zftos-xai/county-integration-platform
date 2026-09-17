#!/usr/bin/env python3
"""Audit whether a repository exposes executable engineering guardrails."""

from __future__ import annotations

import argparse
import json
from dataclasses import asdict, dataclass
from pathlib import Path


@dataclass(frozen=True)
class Check:
    """Represent one repository governance check."""

    key: str
    passed: bool
    detail: str
    required: bool = True


def contains_any(path: Path, needles: tuple[str, ...]) -> bool:
    """Return whether a text file contains at least one expected marker."""

    if not path.is_file():
        return False
    content = path.read_text(encoding="utf-8", errors="ignore").lower()
    return any(needle.lower() in content for needle in needles)


def has_files(root: Path, patterns: tuple[str, ...]) -> bool:
    """Return whether the repository contains a file matching any pattern."""

    return any(next(root.glob(pattern), None) is not None for pattern in patterns)


def audit(root: Path) -> tuple[list[str], list[Check]]:
    """Detect stacks and evaluate their minimum governance surfaces."""

    package_json = root / "package.json"
    pom_xml = root / "pom.xml"
    nested_pom = has_files(root, ("*/pom.xml",))
    sql_present = has_files(root, ("**/*.sql",))
    vue_present = has_files(root, ("**/*.vue",))
    node_present = package_json.is_file()
    java_present = pom_xml.is_file() or nested_pom or has_files(root, ("**/*.java",))

    stacks = [
        name
        for name, present in (
            ("node", node_present),
            ("vue", vue_present),
            ("java", java_present),
            ("sql", sql_present),
        )
        if present
    ]

    ci_present = has_files(
        root,
        (
            ".github/workflows/*.yml",
            ".github/workflows/*.yaml",
            ".gitlab-ci.yml",
            "Jenkinsfile",
            "azure-pipelines.yml",
        ),
    )
    verify_present = (
        (root / "tools/verify.sh").is_file()
        or (root / "Makefile").is_file() and contains_any(root / "Makefile", ("verify:", "check:", "test:"))
        or package_json.is_file() and contains_any(package_json, ('"verify"', '"check"'))
    )

    checks = [
        Check("repository-guidance", (root / "AGENTS.md").is_file(), "root AGENTS.md defines project-local constraints"),
        Check("editor-defaults", (root / ".editorconfig").is_file(), ".editorconfig defines deterministic text defaults"),
        Check("human-onboarding", (root / "README.md").is_file(), "README documents setup and verification"),
        Check("standards", (root / "docs/standards").is_dir(), "docs/standards contains human-readable policy"),
        Check("local-verifier", verify_present, "one local verification entry point is available"),
        Check("continuous-integration", ci_present, "CI configuration exists and should reuse local checks"),
    ]

    if node_present:
        checks.extend(
            [
                Check("node-typecheck", contains_any(package_json, ('"typecheck"', '"lint"')), "Node scripts expose type or lint checks"),
                Check("node-build", contains_any(package_json, ('"build"',)), "Node scripts expose a production build"),
            ]
        )
    if java_present:
        java_guideline = root / "docs/standards/java-coding-guidelines.md"
        checks.append(Check("java-guideline", java_guideline.is_file(), "Java projects document package, Javadoc, test, and persistence rules"))
    if sql_present:
        sql_guideline = root / "docs/standards/sql-coding-guidelines.md"
        sql_verifier = has_files(
            root,
            ("tools/*sql*verif*", "tools/*verif*sql*", "tools/*migration*check*"),
        )
        checks.extend(
            [
                Check("sql-guideline", sql_guideline.is_file(), "SQL migration and compatibility rules are documented"),
                Check("sql-verifier", sql_verifier, "SQL migration structure has a deterministic verifier"),
            ]
        )
    if vue_present:
        frontend_guideline = root / "docs/standards/frontend-coding-guidelines.md"
        checks.append(Check("frontend-guideline", frontend_guideline.is_file(), "Vue and TypeScript delivery rules are documented"))

    return stacks, checks


def main() -> int:
    """Run the command-line audit and return a process exit code."""

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repository", nargs="?", default=".", help="repository root to audit")
    parser.add_argument("--json", action="store_true", help="emit machine-readable JSON")
    parser.add_argument("--strict", action="store_true", help="exit non-zero when a required check fails")
    args = parser.parse_args()

    root = Path(args.repository).expanduser().resolve()
    if not root.is_dir():
        parser.error(f"repository does not exist: {root}")

    stacks, checks = audit(root)
    failed = [check for check in checks if check.required and not check.passed]

    if args.json:
        print(
            json.dumps(
                {
                    "repository": str(root),
                    "detectedStacks": stacks,
                    "checks": [asdict(check) for check in checks],
                    "passed": not failed,
                },
                ensure_ascii=False,
                indent=2,
            )
        )
    else:
        print(f"Repository: {root}")
        print(f"Detected stacks: {', '.join(stacks) if stacks else 'none'}")
        for check in checks:
            marker = "PASS" if check.passed else "FAIL"
            print(f"[{marker}] {check.key}: {check.detail}")
        print(f"Summary: {len(checks) - len(failed)}/{len(checks)} required checks passed")

    return 1 if args.strict and failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
