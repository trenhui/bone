#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""gatekit 框架自身的单元测试（验证共享框架本身可用，非门禁）。"""
import sys
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]  # scripts/tests -> scripts -> repo root
sys.path.insert(0, str(REPO / "scripts" / "lib"))  # 让 gatekit 作为顶层包可导入

from gatekit import Report, repo_root, OK, VIOLATION, ERROR, run, GateError  # noqa: E402
from gatekit.example_gate import self_test as example_self_test  # noqa: E402


class RepoRootTest(unittest.TestCase):
    def test_repo_root_detects_marker(self):
        root = repo_root()
        self.assertTrue(isinstance(root, Path))
        self.assertTrue(
            (root / ".git").exists() or (root / "pom.xml").exists(),
            f"repo_root={root} 未命中任一根标记",
        )

    def test_repo_root_matches_expected(self):
        self.assertEqual(repo_root(), REPO)


class ReportTest(unittest.TestCase):
    def test_ok_by_default(self):
        r = Report()
        self.assertTrue(r.ok)
        self.assertEqual(r.exit_code(), OK)

    def test_fail_makes_violation(self):
        r = Report()
        r.fail("示例违规")
        self.assertFalse(r.ok)
        self.assertEqual(r.exit_code(), VIOLATION)

    def test_info_does_not_affect_ok(self):
        r = Report()
        r.info("仅信息")
        self.assertTrue(r.ok)


class ExampleSelfTestTest(unittest.TestCase):
    def test_example_self_test_passes(self):
        r = Report()
        example_self_test(r)
        self.assertTrue(r.ok, f"example self_test 应全绿：{r._failures}")


class RunContractTest(unittest.TestCase):
    """run() 的契约：返回退出码而非 sys.exit（门禁保留 main() -> int 的前提）。"""

    def test_run_returns_ok_when_no_violation(self):
        def judge(report, args):
            report.info("正常")
        self.assertEqual(run(judge, description="x", argv=[]), OK)

    def test_run_returns_violation(self):
        def judge(report, args):
            report.fail("违规")
        self.assertEqual(run(judge, description="x", argv=[]), VIOLATION)

    def test_run_report_only_suppresses_violation(self):
        def judge(report, args):
            report.fail("违规但仅观察")
        self.assertEqual(run(judge, description="x", argv=["--report-only"]), OK)

    def test_run_gate_error_maps_to_infra(self):
        def judge(report, args):
            raise GateError("运行前提不成立")
        self.assertEqual(run(judge, description="x", argv=[]), ERROR)

    def test_run_self_test_runs_module_fn(self):
        import gatekit.example_gate as eg

        # example_gate 在模块级定义 self_test；run(..., --self-test) 应命中它并返回 OK
        self.assertEqual(run(eg.judge, description="x", argv=["--self-test"]), OK)

    def test_run_self_test_fails_when_module_has_no_self_test(self):
        def judge(report, args):
            report.info("x")
        # 本测试模块的 judge 未定义 module-level self_test，应判红
        self.assertEqual(run(judge, description="x", argv=["--self-test"]), VIOLATION)


if __name__ == "__main__":
    unittest.main(verbosity=2)
