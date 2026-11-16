#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""共享层 lib/gate.py 的契约测试。

重点是「判据必须有牙」：用临时目录里的迷你判据做正向/负向变异，
验证同一份 `run_gate` 契约在违规、观察态、运行错误三种情形下退出码都正确。
这类断言能回答「删掉实现它会不会变红」，而不是只断函数返回值。
"""

import json
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))

from lib import gate  # noqa: E402

MINI_GATE = '''#!/usr/bin/env python3
import sys
sys.path.insert(0, {scripts!r})
from lib import gate

def judge(ctx):
    r = gate.CheckResult("mini")
    marker = ctx.root / "violation.txt"
    if marker.exists():
        r.add("V1", "error", "发现违规标记", str(marker))
    return r

sys.exit(gate.run_gate("mini", judge))
'''


class RepoAndFilesTest(unittest.TestCase):
    def test_repo_root_points_at_repo(self):
        root = gate.repo_root()
        self.assertTrue((root / "scripts/check.sh").is_file(), root)

    def test_explicit_root_wins(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual(gate.repo_root(tmp), Path(tmp).resolve())

    def test_git_files_never_walks_node_modules(self):
        files = gate.git_files(SCRIPTS.parent)
        self.assertGreater(len(files), 100)
        self.assertFalse(any("node_modules/" in f for f in files))

    def test_iter_files_filters_suffix(self):
        files = gate.iter_files(SCRIPTS.parent, suffixes=(".py",), prefixes=("scripts/lib/",))
        self.assertTrue(files)
        self.assertTrue(all(f.suffix == ".py" for f in files))
        self.assertTrue(all("/scripts/lib/" in str(f) for f in files))

    def test_git_files_on_non_repo_raises_infra(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(gate.InfraError):
                gate.git_files(Path(tmp))


class ReportTest(unittest.TestCase):
    def test_exit_code_semantics(self):
        self.assertEqual(gate.CheckResult("x").exit_code(), gate.EXIT_OK)
        r = gate.CheckResult("x").add("V1", "error", "违规")
        self.assertEqual(r.exit_code(), gate.EXIT_VIOLATION)
        self.assertEqual(r.exit_code(report_only=True), gate.EXIT_OK)
        self.assertEqual(r.to_dict(True)["status"], "report-only")
        self.assertEqual(gate.CheckResult("x").error("缺依赖").exit_code(), gate.EXIT_INFRA)

    def test_info_finding_does_not_block(self):
        r = gate.CheckResult("x").add("I1", "info", "提示")
        self.assertEqual(r.exit_code(), gate.EXIT_OK)

    def test_json_payload_is_stable(self):
        r = gate.CheckResult("demo").add("V1", "warning", "示例", "a.java", 12)
        payload = json.loads(r.render("json"))
        self.assertEqual(payload["schema"], gate.SCHEMA_VERSION)
        self.assertEqual(payload["summary"]["violations"], 1)
        self.assertEqual(payload["findings"][0]["line"], 12)

    def test_junit_is_parseable_xml(self):
        xml = gate.render_junit("suite", [
            {"id": "ok", "status": "pass", "duration_ms": 5},
            {"id": "bad", "status": "fail", "duration_ms": 7, "error": "违规"},
        ])
        root = ET.fromstring(xml)
        self.assertEqual(root.attrib["tests"], "2")
        self.assertEqual(root.attrib["failures"], "1")


class SingleFrameworkTest(unittest.TestCase):
    """防止再长出第二套框架：退出码、异常、根定位都必须来自 gatekit。"""

    def test_exit_codes_delegate_to_gatekit(self):
        sys.path.insert(0, str(SCRIPTS / "lib"))
        import gatekit
        self.assertEqual(gate.EXIT_OK, gatekit.OK)
        self.assertEqual(gate.EXIT_VIOLATION, gatekit.VIOLATION)
        self.assertEqual(gate.EXIT_INFRA, gatekit.ERROR)
        self.assertTrue(issubclass(gate.InfraError, gatekit.GateError))

    def test_repo_root_matches_gatekit(self):
        sys.path.insert(0, str(SCRIPTS / "lib"))
        import gatekit
        self.assertEqual(gate.repo_root(), gatekit.repo_root())


class RatchetTest(unittest.TestCase):
    def test_new_violation_and_stale_entry(self):
        new, stale = gate.ratchet(["a", "b"], ["a", "c"])
        self.assertEqual(new, ["b"])
        self.assertEqual(stale, ["c"])

    def test_baseline_shrinks_only(self):
        new, stale = gate.ratchet(["a"], ["a"])
        self.assertEqual((new, stale), ([], []))

    def test_load_baseline_text_and_json(self):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            (base / "b.txt").write_text("# 注释\na\nb\n", encoding="utf-8")
            (base / "b.json").write_text(json.dumps({"entries": ["a"]}), encoding="utf-8")
            self.assertEqual(gate.load_baseline(base / "b.txt"), ["a", "b"])
            self.assertEqual(gate.load_baseline(base / "b.json"), ["a"])
        with self.assertRaises(gate.InfraError):
            gate.load_baseline(base / "missing.json")


class RunGateContractTest(unittest.TestCase):
    """真实 subprocess 跑迷你判据，验证统一 CLI 契约与负向变异。"""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        (self.root / "scripts").mkdir()
        subprocess.run(["git", "init", "-q"], cwd=str(self.root), check=True)
        script = self.root / "mini_gate.py"
        script.write_text(MINI_GATE.format(scripts=str(SCRIPTS)), encoding="utf-8")
        self.script = script
        self.marker = self.root / "violation.txt"

    def tearDown(self):
        self.tmp.cleanup()

    def _run(self, *extra):
        return subprocess.run(
            [sys.executable, str(self.script), "--root", str(self.root), "--format", "json", *extra],
            cwd=str(self.root), capture_output=True, text=True,
        )

    def test_pass_when_clean(self):
        p = self._run()
        self.assertEqual(p.returncode, gate.EXIT_OK, p.stdout + p.stderr)
        self.assertEqual(json.loads(p.stdout)["status"], "pass")

    def test_negative_probe_detects_violation(self):
        self.marker.write_text("x", encoding="utf-8")
        p = self._run()
        self.assertEqual(p.returncode, gate.EXIT_VIOLATION, p.stdout)
        payload = json.loads(p.stdout)
        self.assertEqual(payload["status"], "fail")
        self.assertEqual(payload["findings"][0]["id"], "V1")

    def test_report_only_is_explicit(self):
        self.marker.write_text("x", encoding="utf-8")
        p = self._run("--report-only")
        self.assertEqual(p.returncode, gate.EXIT_OK, p.stdout)
        self.assertEqual(json.loads(p.stdout)["status"], "report-only")

    def test_bad_root_is_infra_error_not_compliance(self):
        p = subprocess.run(
            [sys.executable, str(self.script), "--root", str(self.root / "nope"), "--format", "json"],
            cwd=str(self.root), capture_output=True, text=True,
        )
        self.assertEqual(p.returncode, gate.EXIT_INFRA, p.stdout)
        self.assertEqual(json.loads(p.stdout)["status"], "error")

    def test_junit_output_is_xml(self):
        p = subprocess.run(
            [sys.executable, str(self.script), "--root", str(self.root), "--format", "junit"],
            cwd=str(self.root), capture_output=True, text=True,
        )
        self.assertEqual(p.returncode, gate.EXIT_OK, p.stdout + p.stderr)
        ET.fromstring(p.stdout)

    def test_junit_dir_writes_file(self):
        dest = self.root / "reports"
        p = subprocess.run(
            [sys.executable, str(self.script), "--root", str(self.root), "--junit-dir", str(dest)],
            cwd=str(self.root), capture_output=True, text=True,
        )
        self.assertEqual(p.returncode, gate.EXIT_OK, p.stdout + p.stderr)
        self.assertTrue((dest / "mini.xml").is_file())


if __name__ == "__main__":
    unittest.main(verbosity=2)
