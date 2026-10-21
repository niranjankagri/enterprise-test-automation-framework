#!/usr/bin/env python3
"""Validate module-specific JaCoCo reports and print executed line/branch counts."""

from __future__ import annotations

from collections.abc import Mapping
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


REPORTS = {
    "demo-app": (Path("demo-app/target/site/jacoco/jacoco.xml"), "com/enterprise/demoapp/api/OrderApi"),
    "automation": (Path("automation/target/site/jacoco/jacoco.xml"), "com/enterprise/automation/api/ApiClient"),
}


def counters(node: ET.Element) -> dict[str, tuple[int, int]]:
    return {
        counter.attrib["type"]: (int(counter.attrib["covered"]), int(counter.attrib["missed"]))
        for counter in node.findall("counter")
    }


def percentage(covered: int, missed: int) -> str:
    total = covered + missed
    return f"{100 * covered / total:.1f}%" if total else "n/a"


def verify_reports(reports: Mapping[str, tuple[Path, str]]) -> int:
    for module, (path, required_class) in reports.items():
        if not path.is_file():
            print(f"Missing JaCoCo XML report: {path}", file=sys.stderr)
            return 1
        try:
            root = ET.parse(path).getroot()
            report_counters = counters(root)
            classes = {node.attrib.get("name"): node for node in root.findall(".//class")}
            class_counters = counters(classes[required_class]) if required_class in classes else {}
        except (ET.ParseError, OSError, KeyError, ValueError) as error:
            print(f"Invalid JaCoCo XML report for {module}: {error.__class__.__name__}", file=sys.stderr)
            return 1

        if required_class not in classes:
            print(f"Expected production class {required_class} is absent from {module} report", file=sys.stderr)
            return 1

        for counter_type in ("LINE", "BRANCH"):
            if counter_type not in report_counters:
                print(f"{module} report has no {counter_type} counter", file=sys.stderr)
                return 1
            covered, missed = report_counters[counter_type]
            if covered + missed == 0:
                print(f"{module} report has no measurable production {counter_type.lower()} data", file=sys.stderr)
                return 1
            if covered == 0:
                print(f"{module} report has no executed production {counter_type.lower()} coverage", file=sys.stderr)
                return 1

            if counter_type not in class_counters:
                print(f"Designated production class {required_class} has no {counter_type} counter", file=sys.stderr)
                return 1
            class_covered, class_missed = class_counters[counter_type]
            if class_covered + class_missed == 0:
                print(f"Designated production class {required_class} has no measurable {counter_type.lower()} data",
                      file=sys.stderr)
                return 1
            if class_covered == 0:
                print(f"Designated production class {required_class} has no executed {counter_type.lower()} coverage",
                      file=sys.stderr)
                return 1

        line_covered, line_missed = report_counters["LINE"]
        branch_covered, branch_missed = report_counters["BRANCH"]
        print(
            f"{module}: {len(classes)} classes; lines {line_covered}/{line_covered + line_missed} "
            f"({percentage(line_covered, line_missed)}); branches {branch_covered}/"
            f"{branch_covered + branch_missed} ({percentage(branch_covered, branch_missed)})"
        )
    return 0


def main() -> int:
    return verify_reports(REPORTS)


if __name__ == "__main__":
    raise SystemExit(main())
