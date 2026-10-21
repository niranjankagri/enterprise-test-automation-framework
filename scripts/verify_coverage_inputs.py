#!/usr/bin/env python3
"""Verify that JaCoCo inputs all came from this workflow's tested source revision."""

from __future__ import annotations

import argparse
from pathlib import Path
import sys


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("root", type=Path, help="Root directory containing downloaded coverage artifacts")
    parser.add_argument("expected_sha", help="Workflow GITHUB_SHA that every artifact must identify")
    parser.add_argument("expected_count", type=int, help="Number of successful test jobs expected to contribute data")
    args = parser.parse_args()

    manifests = sorted(args.root.rglob("coverage-source.sha"))
    execution_files = sorted(args.root.rglob("jacoco.exec"))
    if len(manifests) != args.expected_count or len(execution_files) != args.expected_count:
        print(
            f"Expected {args.expected_count} coverage manifests and execution files; "
            f"found {len(manifests)} manifests and {len(execution_files)} execution files",
            file=sys.stderr,
        )
        return 1

    manifest_dirs = {manifest.parent.resolve() for manifest in manifests}
    execution_dirs = {execution.parent.resolve() for execution in execution_files}
    if manifest_dirs != execution_dirs:
        print("Coverage manifests and execution files do not have one-to-one artifact ownership", file=sys.stderr)
        return 1

    for manifest in manifests:
        artifact_dir = manifest.parent
        execution_data = artifact_dir / "jacoco.exec"
        if not execution_data.is_file() or execution_data.stat().st_size == 0:
            print(f"Missing or empty JaCoCo execution data in {artifact_dir}", file=sys.stderr)
            return 1
        tested_sha = manifest.read_text(encoding="utf-8").strip()
        if tested_sha != args.expected_sha:
            print(f"Coverage source revision mismatch in {artifact_dir}", file=sys.stderr)
            return 1

    print(f"Verified {len(manifests)} non-empty JaCoCo inputs for source revision {args.expected_sha}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
