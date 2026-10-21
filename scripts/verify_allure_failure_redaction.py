#!/usr/bin/env python3
"""Run a real failing TestNG fixture and verify its completed Allure JSON is redacted."""

from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
import uuid
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
AUTOMATION = ROOT / "automation"
TEST_CLASS = "com.enterprise.automation.tests.platform.AllureFailureRedactionTest"
TEST_METHOD = "intentionallyFailsWithSyntheticUrlSecrets"


def verify_fixture_isolation() -> None:
    suite_file = AUTOMATION / "src" / "test" / "resources" / "suites" / "full.xml"
    suite = ET.parse(suite_file).getroot()
    excluded_groups = {
        group.attrib.get("name")
        for group in suite.findall("./test/groups/run/exclude")
    }
    if "allure-failure-redaction" not in excluded_groups:
        raise RuntimeError("The intentionally failing fixture is not excluded from the general full suite")


def run_maven(arguments: list[str]) -> subprocess.CompletedProcess[str]:
    wrapper = ROOT / ("mvnw.cmd" if os.name == "nt" else "mvnw")
    command = [str(wrapper), *arguments]
    if os.name == "nt":
        return subprocess.run(
            subprocess.list2cmdline(command),
            cwd=ROOT,
            check=False,
            capture_output=True,
            text=True,
            shell=True,
        )
    return subprocess.run(command, cwd=ROOT, check=False, capture_output=True, text=True)


def verify_surefire_failure() -> None:
    report_dir = AUTOMATION / "target" / "surefire-reports"
    all_cases: list[ET.Element] = []
    matching_cases: list[ET.Element] = []
    for report in report_dir.glob("TEST-*.xml"):
        root = ET.parse(report).getroot()
        cases = list(root.iter("testcase"))
        all_cases.extend(cases)
        matching_cases.extend(
            case
            for case in cases
            if case.attrib.get("classname") == TEST_CLASS and case.attrib.get("name") == TEST_METHOD
        )

    if len(all_cases) != 1 or len(matching_cases) != 1:
        raise RuntimeError("Surefire did not record only the one expected synthetic TestNG case")

    case = matching_cases[0]
    failure = case.find("failure")
    if failure is None or case.find("error") is not None or case.find("skipped") is not None:
        raise RuntimeError("The synthetic TestNG case did not finish as its expected test failure")
    if not failure.attrib.get("type", "").endswith("IllegalStateException"):
        raise RuntimeError("The synthetic test failure changed exception type")


def collect_status_details(value: Any) -> list[dict[str, Any]]:
    details: list[dict[str, Any]] = []
    if isinstance(value, dict):
        status_details = value.get("statusDetails")
        if isinstance(status_details, dict):
            details.append(status_details)
        for child in value.values():
            details.extend(collect_status_details(child))
    elif isinstance(value, list):
        for child in value:
            details.extend(collect_status_details(child))
    return details


def verify_allure_result(results_dir: Path, marker: str) -> str:
    matches: list[tuple[Path, dict[str, Any]]] = []
    for result_file in results_dir.glob("*-result.json"):
        result = json.loads(result_file.read_text(encoding="utf-8"))
        if result.get("name") == TEST_METHOD:
            matches.append((result_file, result))

    if len(matches) != 1:
        raise RuntimeError("Allure did not persist exactly one completed result for the synthetic test")

    result_file, result = matches[0]
    if result.get("status") not in {"failed", "broken"}:
        raise RuntimeError("Allure did not preserve the synthetic test's failed or broken status")

    details = collect_status_details(result)
    step_details = collect_status_details(result.get("steps", []))
    if not details or not step_details:
        raise RuntimeError("The persisted result is missing test-level or step-level failure details")

    serialized = result_file.read_text(encoding="utf-8")
    synthetic_values = (
        f"synthetic-user-{marker}",
        f"synthetic-password-{marker}",
        f"synthetic-token-{marker}",
        f"synthetic-typed-password-{marker}",
    )
    if any(value in serialized for value in synthetic_values):
        raise RuntimeError("Synthetic URL secrets remain in the finalized Allure result JSON")

    status_details = result.get("statusDetails")
    message = status_details.get("message", "") if isinstance(status_details, dict) else ""
    expected_message = (
        "Synthetic navigation failed at https://****@redaction.example.test/path"
        "?access_token=****&page=2"
    )
    if expected_message not in message:
        raise RuntimeError("The test-level failure message was not preserved in its redacted form")
    if "password=****" not in message:
        raise RuntimeError("The typed password was not redacted from the persisted test-level failure details")

    step_messages = [str(detail.get("message", "")) for detail in step_details]
    if not any(expected_message in step_message for step_message in step_messages):
        raise RuntimeError("The step-level failure message was not preserved in its redacted form")
    if not any("password=****" in step_message for step_message in step_messages):
        raise RuntimeError("The typed password was not redacted from the persisted step-level failure details")
    return str(result["status"])


def main() -> int:
    marker = uuid.uuid4().hex
    verify_fixture_isolation()
    try:
        with tempfile.TemporaryDirectory(prefix="allure-failure-redaction-") as temporary:
            allure_results = Path(temporary) / "allure-results"
            arguments = [
                "-B",
                "-ntp",
                "clean",
                "test",
                "-Dsuite=allure-failure-redaction",
                f"-Dallure.results.directory={allure_results}",
                f"-Dsynthetic.url.marker={marker}",
                "-Dmaven.compiler.failOnWarning=true",
            ]
            completed = run_maven(arguments)
            if completed.returncode == 0:
                raise RuntimeError("The deliberately failing synthetic test unexpectedly passed")

            verify_surefire_failure()
            allure_status = verify_allure_result(allure_results, marker)
    finally:
        cleanup = run_maven(["-B", "-ntp", "clean"])
        if cleanup.returncode != 0:
            raise RuntimeError("Maven could not remove generated synthetic failure artifacts")

    print(
        "Verified: TestNG preserved the IllegalStateException failure; the finalized Allure result has status "
        f"'{allure_status}' and redacted test-level and step-level details."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, ET.ParseError, json.JSONDecodeError) as error:
        print(f"Allure failure redaction verification failed: {error}", file=sys.stderr)
        raise SystemExit(1) from error
