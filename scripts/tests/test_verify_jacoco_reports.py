"""Regression tests for production-code execution checks in JaCoCo reports."""

from __future__ import annotations

from contextlib import redirect_stderr, redirect_stdout
import io
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

from scripts.verify_jacoco_reports import verify_reports


MODULES = ("demo-app", "automation")
TARGET_CLASS = "org/example/Target"


def _add_counters(parent: ET.Element, lines: tuple[int, int], branches: tuple[int, int]) -> None:
    for kind, (covered, missed) in (("LINE", lines), ("BRANCH", branches)):
        ET.SubElement(parent, "counter", type=kind, covered=str(covered), missed=str(missed))


def _write_report(
    path: Path,
    total_lines: tuple[int, int] = (4, 2),
    total_branches: tuple[int, int] = (3, 1),
    target_lines: tuple[int, int] = (2, 1),
    target_branches: tuple[int, int] = (1, 1),
) -> None:
    root = ET.Element("report", name="verifier-test")
    package = ET.SubElement(root, "package", name="org/example")
    target = ET.SubElement(package, "class", name=TARGET_CLASS)
    _add_counters(target, target_lines, target_branches)
    other = ET.SubElement(package, "class", name="org/example/Other")
    _add_counters(other, (2, 1), (2, 0))
    _add_counters(root, total_lines, total_branches)
    path.parent.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(root).write(path, encoding="utf-8", xml_declaration=True)


class JaCoCoReportVerifierTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        self.root = Path(self.temp_dir.name)
        self.reports = {
            module: (self.root / module / "jacoco.xml", TARGET_CLASS)
            for module in MODULES
        }
        for path, _ in self.reports.values():
            _write_report(path)

    def _verify(self) -> tuple[int, str, str]:
        stdout = io.StringIO()
        stderr = io.StringIO()
        with redirect_stdout(stdout), redirect_stderr(stderr):
            result = verify_reports(self.reports)
        return result, stdout.getvalue(), stderr.getvalue()

    def test_accepts_executed_production_coverage_in_each_module(self) -> None:
        result, output, errors = self._verify()

        self.assertEqual(result, 0)
        self.assertIn("demo-app: 2 classes", output)
        self.assertIn("automation: 2 classes", output)
        self.assertEqual(errors, "")

    def test_rejects_module_reports_with_zero_covered_lines_or_branches(self) -> None:
        for module in MODULES:
            for dimension, counter_name in (("lines", "line"), ("branches", "branch")):
                with self.subTest(module=module, dimension=dimension):
                    path, _ = self.reports[module]
                    if dimension == "lines":
                        _write_report(path, total_lines=(0, 6), target_lines=(0, 3))
                    else:
                        _write_report(path, total_branches=(0, 4), target_branches=(0, 2))

                    result, _, errors = self._verify()

                    self.assertEqual(result, 1)
                    self.assertIn(f"{module} report has no executed production {counter_name} coverage", errors)
                    _write_report(path)

    def test_rejects_unexecuted_designated_class_even_when_module_totals_are_covered(self) -> None:
        path, _ = self.reports["demo-app"]
        _write_report(path, target_lines=(0, 3), target_branches=(0, 2))

        result, _, errors = self._verify()

        self.assertEqual(result, 1)
        self.assertIn(f"Designated production class {TARGET_CLASS} has no executed line coverage", errors)

    def test_rejects_designated_class_without_executed_branch_coverage(self) -> None:
        path, _ = self.reports["automation"]
        _write_report(path, target_branches=(0, 2))

        result, _, errors = self._verify()

        self.assertEqual(result, 1)
        self.assertIn(f"Designated production class {TARGET_CLASS} has no executed branch coverage", errors)


if __name__ == "__main__":
    unittest.main()
