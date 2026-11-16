#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""统一入口 bone.py 的契约测试：能力目录、结构化结果、退出码、环境体检。

这些断言定义「各 agent 可复用」的最低标准：一条命令发现能力、
一条命令跑指定判据、结果可机读、缺依赖/缺注册表时是运行错误而非假绿。
"""

import contextlib
import io
import json
import os
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
ROOT = SCRIPTS.parent
BONE = SCRIPTS / "bone.py"


def run(*args):
    return subprocess.run(
        [sys.executable, str(BONE), *args], cwd=str(ROOT),
        capture_output=True, text=True,
    )


class ListContractTest(unittest.TestCase):
    def test_list_json_is_a_capability_catalog(self):
        p = run("list", "--format", "json")
        self.assertEqual(p.returncode, 0, p.stdout + p.stderr)
        data = json.loads(p.stdout)
        self.assertEqual(data["schema"], "bone.list.v1")
        self.assertGreaterEqual(data["counts"]["total"]["checks"], 50)
        for c in data["checks"]:
            self.assertTrue(all(k in c for k in ("id", "group", "path", "cmd", "scope")))

    def test_group_filter_narrows_result(self):
        data = json.loads(run("list", "--group", "paging", "--format", "json").stdout)
        self.assertEqual(data["counts"]["listed"], 4)
        self.assertTrue(all(c["group"] == "paging" for c in data["checks"]))

    def test_external_is_discoverable(self):
        data = json.loads(run("list", "--external", "--format", "json").stdout)
        self.assertGreaterEqual(data["external_count"], 10)
        self.assertTrue(any("procura" in e["path"] for e in data["external"]))

    def test_unknown_id_is_infra_error(self):
        p = run("list", "--only", "not-a-real-gate", "--format", "json")
        # list 只是目录过滤，空结果是合法查询；check 才据此处置
        self.assertEqual(p.returncode, 0, p.stdout)
        self.assertEqual(json.loads(p.stdout)["counts"]["listed"], 0)


class CheckContractTest(unittest.TestCase):
    def test_dry_run_does_not_execute(self):
        p = run("check", "--only", "check-orm-banned", "--dry-run", "--format", "json")
        self.assertEqual(p.returncode, 0, p.stdout + p.stderr)
        data = json.loads(p.stdout)
        self.assertEqual(data["summary"]["dry-run"], 1)
        self.assertEqual(data["checks"][0]["status"], "dry-run")
        self.assertEqual(data["checks"][0]["cmd"][1], "scripts/check-orm-banned.py")

    def test_real_check_returns_structured_result(self):
        p = run("check", "--only", "check-orm-banned", "--format", "json")
        self.assertEqual(p.returncode, 0, p.stdout + p.stderr)
        data = json.loads(p.stdout)
        self.assertEqual(data["schema"], "bone.check.v1")
        self.assertEqual(data["summary"]["total"], 1)
        self.assertEqual(data["summary"]["pass"], 1)
        self.assertEqual(data["checks"][0]["status"], "pass")
        self.assertIn("duration_ms", data["checks"][0])

    def test_junit_output_is_parseable(self):
        p = run("check", "--group", "paging", "--dry-run", "--format", "junit")
        self.assertEqual(p.returncode, 0, p.stdout + p.stderr)
        root = ET.fromstring(p.stdout)
        self.assertEqual(root.attrib["tests"], "4")

    def test_unknown_only_returns_infra_error(self):
        p = run("check", "--only", "not-a-real-gate", "--dry-run", "--format", "json")
        self.assertEqual(p.returncode, 2, p.stdout)

    def test_changed_flag_does_not_skip_unscoped_gates(self):
        p = run("check", "--only", "check-orm-banned", "--changed", "--dry-run", "--format", "json")
        self.assertEqual(p.returncode, 0, p.stdout + p.stderr)
        data = json.loads(p.stdout)
        self.assertEqual(data["checks"][0]["status"], "dry-run")
        self.assertIn("changed_files", data)


class SyncAndDoctorTest(unittest.TestCase):
    def test_sync_check_is_green(self):
        p = run("sync", "--check")
        self.assertEqual(p.returncode, 0, p.stdout)
        self.assertTrue(json.loads(p.stdout)["ok"])

    def test_doctor_reports_ready_environment(self):
        p = run("doctor", "--format", "json")
        data = json.loads(p.stdout)
        self.assertEqual(p.returncode, 0, p.stdout)
        self.assertTrue(data["ok"], data["items"])
        names = [i["name"] for i in data["items"]]
        self.assertIn("注册表", names)
        self.assertIn("库外登记", names)
        self.assertIn("依赖 yaml", names)
        self.assertIn("依赖 pymysql", names)


class ExternalCommandTest(unittest.TestCase):
    """库外判据不搬目录也能走同一入口；默认 dry-run，实跑须显式 --run。"""

    def test_dry_run_lists_invokable_only(self):
        p = run("external", "--format", "json")
        self.assertEqual(p.returncode, 0, p.stdout + p.stderr)
        data = json.loads(p.stdout)
        self.assertEqual(data["schema"], "bone.external.run.v1")
        self.assertEqual(data["summary"]["total"], 9)
        self.assertEqual(data["summary"]["dry-run"], 9)
        self.assertTrue(all(c["status"] == "dry-run" for c in data["checks"]))
        self.assertTrue(all(c["cmd"] and c["cmd"][0] in ("python3", "node") for c in data["checks"]))

    def test_only_filter_by_script_stem(self):
        p = run("external", "--only", "check_procura_alignment", "--format", "json")
        self.assertEqual(p.returncode, 0, p.stdout + p.stderr)
        data = json.loads(p.stdout)
        self.assertEqual(data["summary"]["total"], 1)
        self.assertEqual(data["checks"][0]["path"], "tools/check_procura_alignment.py")

    def test_only_non_invokable_is_infra_error(self):
        p = run("external", "--only", "_check_xref", "--format", "json")
        self.assertEqual(p.returncode, 2, p.stdout)

    def test_run_one_external_gate_returns_structured_result(self):
        # 该判据在 check.sh 中已接线且为绿；即使将来出现既有违规也不得让契约测试变红，
        # 只断言结果结构与退出码语义，不断言业务结论。
        p = run("external", "--run", "--only", "check_procura_alignment", "--format", "json")
        self.assertIn(p.returncode, (0, 1), p.stdout + p.stderr)
        data = json.loads(p.stdout)
        self.assertEqual(data["summary"]["total"], 1)
        self.assertEqual(data["checks"][0]["status"], "pass" if p.returncode == 0 else "fail")
        self.assertIn("duration_ms", data["checks"][0])


class ExternalRatchetTest(unittest.TestCase):
    """shadow 观察态 + 只可收缩棘轮：既有欠债可观察，新欠债必须判红。"""

    CONTRAST = "doc/design/lowcode/ui/ui-protocol/check-contrast.py"

    def test_shadow_with_real_baseline_is_green(self):
        p = run("external", "--run", "--report-only", "--ratchet",
                "--only", "check-contrast", "--format", "json")
        self.assertEqual(p.returncode, 0, p.stdout + p.stderr)
        data = json.loads(p.stdout)
        self.assertEqual(data["ratchet"]["new_failures"], [])
        self.assertEqual(data["ratchet"]["stale_entries"], [])
        self.assertEqual(data["checks"][0]["cwd"],
                         "doc/design/lowcode/ui/ui-protocol")

    def test_new_failure_outside_baseline_is_red_even_in_shadow(self):
        with tempfile.TemporaryDirectory() as tmp:
            empty = Path(tmp) / "empty.txt"
            empty.write_text("# 空基线：所有失败都算新增\n", encoding="utf-8")
            p = run("external", "--run", "--report-only", "--ratchet",
                    "--only", "check-contrast", "--ratchet-baseline", str(empty),
                    "--format", "json")
            self.assertEqual(p.returncode, 1, p.stdout)
            data = json.loads(p.stdout)
            self.assertIn(self.CONTRAST, data["ratchet"]["new_failures"])

    def test_missing_baseline_is_infra_error(self):
        p = run("external", "--run", "--ratchet", "--only", "check-contrast",
                "--ratchet-baseline", "/nonexistent/baseline.txt", "--format", "json")
        self.assertEqual(p.returncode, 2, p.stdout)
        self.assertIn("error", json.loads(p.stdout)["ratchet"])

    def test_report_only_semantics_at_execution_layer(self):
        sys.path.insert(0, str(SCRIPTS))
        import bone
        item = [{
            "id": "always-fails", "group": "fixture", "path": "fixture",
            "cmd": [sys.executable, "-c", "import sys; sys.exit(1)"],
            "scope": [],
        }]
        kwargs = dict(
            root=ROOT, env=os.environ.copy(), changed=[], run=True, timeout=30,
            fail_fast=False, output_format="json", junit_dir=None,
            schema="test", suite="test", source="test",
        )
        with contextlib.redirect_stdout(io.StringIO()):
            blocking = bone._run_items(item, report_only=False, **kwargs)
            shadow = bone._run_items(item, report_only=True, **kwargs)
        self.assertEqual(blocking, 1)
        self.assertEqual(shadow, 0)


if __name__ == "__main__":
    unittest.main(verbosity=2)
