#!/usr/bin/env python3
"""Validate module-specific JaCoCo reports and print actual line/branch counts."""

from __future__ import annotations

from pathlib import Path
import sys
import xml.etree.ElementTree as ET


REPORTS = {
    "demo-app": (Path("demo-app/target/site/jacoco/jacoco.xml"), "com/enterprise/demoapp/api/OrderApi"),
    "automation": (Path("automation/target/site/jacoco/jacoco.xml"), "com/enterprise/automation/api/ApiClient"),
}


def counters(report: ET.Element) -> dict[str, tuple[int, int]]:
    totals: dict[str, list[int]] = {}
    for counter in report.findall("counter"):
        kind = counter.attrib["type"]
        totals[kind] = [int(counter.attrib["covered"]), int(counter.attrib["missed"])]
    return {kind: (values[0], values[1]) for kind, values in totals.items()}


def percentage(covered: int, missed: int) -> str:
    total = covered + missed
    return f"{100 * covered / total:.1f}%" if total else "n/a"


def main() -> int:
    for module, (path, required_class) in REPORTS.items():
        if not path.is_file():
            print(f"Missing JaCoCo XML report: {path}", file=sys.stderr)
            return 1
        root = ET.parse(path).getroot()
        classes = {node.attrib.get("name") for node in root.findall(".//class")}
        if required_class not in classes:
            print(f"Expected production class {required_class} is absent from {module} report", file=sys.stderr)
            return 1
        report_counters = counters(root)
        for required_counter in ("LINE", "BRANCH"):
            if required_counter not in report_counters:
                print(f"{module} report has no {required_counter} counter", file=sys.stderr)
                return 1
        line_covered, line_missed = report_counters["LINE"]
        branch_covered, branch_missed = report_counters["BRANCH"]
        if line_covered + line_missed == 0 or branch_covered + branch_missed == 0:
            print(f"{module} report has no measurable production {required_counter.lower()} data", file=sys.stderr)
            return 1
        print(
            f"{module}: {len(classes)} classes; lines {line_covered}/{line_covered + line_missed} "
            f"({percentage(line_covered, line_missed)}); branches {branch_covered}/"
            f"{branch_covered + branch_missed} ({percentage(branch_covered, branch_missed)})"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
