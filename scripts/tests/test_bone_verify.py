#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""聚合入口与 CLI 能力目录的契约测试。

注意：这里**只测 --quick**，不能在 unittest 里跑 `verify` 全量——
它的 tests 步骤本身就是 unittest discover，会造成无限递归。
全量 verify 由 CI guard / 人工执行，不在单测内自举。
"""

import json
import re
import subprocess
import sys
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
ROOT = SCRIPTS.parent
BONE = SCRIPTS / "bone.py"
README = SCRIPTS / "README.md"


def run(*args):
    return subprocess.run(
        [sys.executable, str(BONE), *args], cwd=str(ROOT),
        capture_output=True, text=True,
    )


class VerifyContractTest(unittest.TestCase):
    def test_quick_returns_unified_health_payload(self):
        p = run("verify", "--quick", "--format", "json")
        self.assertEqual(p.returncode, 0, p.stdout + p.stderr)
        data = json.loads(p.stdout)
        self.assertEqual(data["schema"], "bone.verify.v1")
        self.assertEqual(data["mode"], "quick")
        self.assertEqual(data["summary"]["failed"], 0)
        self.assertEqual(
            [s["name"] for s in data["steps"]], ["sync", "waivers", "doctor"]
        )
        self.assertTrue(all(s["rc"] == 0 for s in data["steps"]))
        self.assertTrue(all(s["summary"] is not None for s in data["steps"]),
                        "每个 JSON 步骤都应能解析出 summary")

    def test_quick_never_runs_heavy_steps(self):
        """负向：--quick 绝不能偷偷跑 59 判据或全量单测（否则会自举递归）。"""
        p = run("verify", "--quick", "--format", "json")
        names = [s["name"] for s in json.loads(p.stdout)["steps"]]
        self.assertNotIn("checks", names)
        self.assertNotIn("tests", names)
        self.assertNotIn("external", names)

    def test_text_format_is_readable(self):
        p = run("verify", "--quick", "--format", "text")
        self.assertEqual(p.returncode, 0, p.stdout + p.stderr)
        self.assertIn("快速 自检: 3 步 / 失败 0 步", p.stdout)


class CliCatalogContractTest(unittest.TestCase):
    """把编目铁律从脚本文件扩展到 CLI 命令：双向对账，防文档/命令漂移。"""

    def _cli_commands(self):
        p = run("--help")
        self.assertEqual(p.returncode, 0, p.stdout + p.stderr)
        match = re.search(r"\{([a-z][a-z0-9,]+)\}", p.stdout)
        self.assertIsNotNone(match, "argparse 帮助里应有子命令枚举: %s" % p.stdout)
        return set(match.group(1).split(","))

    def _documented_commands(self):
        text = README.read_text(encoding="utf-8")
        return set(re.findall(r"bone\.py\s+([a-z][a-z0-9-]*)", text))

    def test_every_cli_command_is_documented(self):
        missing = self._cli_commands() - self._documented_commands()
        self.assertEqual(missing, set(),
                         "CLI 有命令未在 scripts/README.md 编目（agent 发现不到）: %s" % missing)

    def test_every_documented_command_exists(self):
        stale = self._documented_commands() - self._cli_commands()
        self.assertEqual(stale, set(),
                         "README 登记了不存在的 bone.py 命令: %s" % stale)


class VerifyFailClosedTest(unittest.TestCase):
    """负向变异：结构化步骤「退出 0 却没结果」不能算通过。"""

    @staticmethod
    def _load_bone():
        import importlib.util
        spec = importlib.util.spec_from_file_location("bone_under_test", BONE)
        module = importlib.util.module_from_spec(spec)
        assert spec.loader is not None
        spec.loader.exec_module(module)
        return module

    def test_json_step_without_output_is_infra_error(self):
        import argparse
        import contextlib
        import io
        from unittest import mock

        bone = self._load_bone()
        args = argparse.Namespace(
            root=str(ROOT), quick=True, format="json", timeout=600, fail_fast=False,
        )
        buf = io.StringIO()
        with mock.patch.object(bone, "_root", return_value=ROOT), \
             mock.patch.object(bone.subprocess, "run") as run, \
             contextlib.redirect_stdout(buf):
            run.return_value = subprocess.CompletedProcess([], 0, "", "")
            rc = bone.cmd_verify(args)
        self.assertEqual(rc, bone.gate.EXIT_INFRA)
        data = json.loads(buf.getvalue())
        self.assertEqual(data["summary"]["failed"], 3)
        self.assertTrue(
            all(s["summary"].get("error") for s in data["steps"]),
            "每步都要标出「结构化输出缺失」，不能兜底成通过",
        )

    def test_unittest_step_parses_ran_count(self):
        bone = self._load_bone()
        summary = bone._step_summary(
            {"json": False}, "", "Ran 79 tests in 3.2s\n\nOK\n", 0
        )
        self.assertEqual(summary["tests"], 79)
        self.assertEqual(summary["rc"], 0)

    def test_failed_step_exposes_failed_checker_details(self):
        bone = self._load_bone()
        payload = {"checks": [
            {"id": "check-ok", "status": "pass", "stdout_tail": "ok"},
            {"id": "check-bad", "path": "scripts/check-bad.py", "status": "fail",
             "rc": 1, "stdout_tail": "R4: 未接线", "stderr_tail": ""},
        ]}
        failures = bone._step_failures(json.dumps(payload), 1)
        self.assertEqual(len(failures), 1)
        self.assertEqual(failures[0]["id"], "check-bad")
        self.assertIn("R4", str(failures[0]["detail"]))
        self.assertEqual(bone._step_failures(json.dumps(payload), 0), [])

    def test_full_mode_reports_shadow_debt_at_top_level(self):
        """全量模式下 shadow 欠债不能只藏在步骤里：failed=0 也要显式计数。"""
        import argparse
        import contextlib
        import io
        from unittest import mock

        bone = self._load_bone()
        args = argparse.Namespace(
            root=str(ROOT), quick=False, format="json", timeout=600, fail_fast=False,
        )

        def fake_run(cmd, **_kwargs):
            if "-m" in cmd:
                return subprocess.CompletedProcess(
                    cmd, 0, "", "Ran 1 test in 0.1s\n\nOK\n"
                )
            fail = 2 if cmd[2] == "external" else 0
            return subprocess.CompletedProcess(
                cmd, 0, json.dumps({"summary": {"total": 2, "fail": fail}}), ""
            )

        buf = io.StringIO()
        with mock.patch.object(bone, "_root", return_value=ROOT), \
             mock.patch.object(bone.subprocess, "run", side_effect=fake_run), \
             contextlib.redirect_stdout(buf):
            rc = bone.cmd_verify(args)
        self.assertEqual(rc, 0)
        data = json.loads(buf.getvalue())
        self.assertEqual(data["mode"], "full")
        self.assertEqual(len(data["steps"]), 6)
        self.assertEqual(data["summary"]["failed"], 0)
        self.assertEqual(data["summary"]["observed_debt"], 2)


if __name__ == "__main__":
    unittest.main(verbosity=2)
