#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""注册表与库外登记的双向对账测试。

核心判据：注册表不是手写的第二份清单，而是 check.sh 调用行的派生物。
下面用「往真源里插一条假调用」「删掉一条真调用」「登记一个不存在的库外路径」
三种负向变异，证明 sync --check 真的会红（防止守卫恒绿）。
"""

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
ROOT = SCRIPTS.parent
sys.path.insert(0, str(SCRIPTS))

from lib import gate, registry  # noqa: E402

SOURCE = ROOT / registry.DEFAULT_SOURCE
KNOWN_FAILURES = ROOT / "scripts/registry/external-known-failures.txt"
REG = ROOT / registry.DEFAULT_REGISTRY
EXTERNAL = ROOT / registry.DEFAULT_EXTERNAL
CI = ROOT / ".github/workflows/ci.yml"


class RegistryShapeTest(unittest.TestCase):
    def test_registry_is_synced_with_check_sh(self):
        self.assertEqual(registry.diff(SOURCE, REG), [])

    def test_checks_shape(self):
        data = registry.load(REG)
        self.assertEqual(registry.validate_checks(ROOT, data), [])
        self.assertEqual(data["schema"], registry.SCHEMA_CHECKS)
        self.assertEqual(data["counts"]["checks"], len(data["checks"]))
        self.assertGreaterEqual(data["counts"]["checks"], 50)
        ids = [c["id"] for c in data["checks"]]
        self.assertEqual(len(ids), len(set(ids)))
        for c in data["checks"]:
            self.assertEqual(c["cmd"][0], "python3")
            self.assertIsInstance(c["scope"], list)

    def test_self_test_variants_are_recorded(self):
        data = registry.load(REG)
        self.assertEqual(
            sum(len(c["self_tests"]) for c in data["checks"]), 4,
            "check-synapclaim-architecture / module-boundaries / check-engineering-evidence / "
            "check-sclaim-naming 各有一次 --self-test 调用",
        )

    def test_build_is_deterministic(self):
        self.assertEqual(registry.build(SOURCE), registry.build(SOURCE))


class ExternalRegistryTest(unittest.TestCase):
    def test_external_entries_are_valid(self):
        entries = registry.load_external(EXTERNAL)
        self.assertGreaterEqual(len(entries), 10)
        self.assertEqual(registry.verify_external(ROOT, entries, SOURCE, CI), [])

    def test_negative_missing_path(self):
        entries = [{"path": "does/not/exist.py", "callers": ["manual"],
                    "disposition": "register", "invokable": False}]
        problems = registry.verify_external(ROOT, entries, SOURCE, CI)
        self.assertTrue(any("路径不存在" in p for p in problems), problems)

    def test_negative_false_caller(self):
        entries = [{"path": "tools/check_procura_alignment.py",
                    "callers": [".github/workflows/ci.yml", "scripts/check.sh"],
                    "disposition": "register", "invokable": False}]
        # 这条是真登记，应通过；把路径改成 ci.yml 里不存在的脚本则应判红
        self.assertEqual(registry.verify_external(ROOT, entries, SOURCE, CI), [])
        entries[0]["path"] = "doc/_check_diagnosis.py"
        self.assertTrue(registry.verify_external(ROOT, entries, SOURCE, CI))

    def test_negative_bad_disposition(self):
        entries = [{"path": "doc/_check_diagnosis.py", "callers": ["manual"],
                    "disposition": "delete_now", "invokable": False}]
        problems = registry.verify_external(ROOT, entries, SOURCE, CI)
        self.assertTrue(any("disposition" in p for p in problems), problems)


class NegativeMutationTest(unittest.TestCase):
    """改坏真源 / 注册表，sync --check 必须判红。"""

    def _mutated_source(self, line):
        tmp = tempfile.NamedTemporaryFile("w", suffix=".sh", delete=False, encoding="utf-8")
        tmp.write(SOURCE.read_text(encoding="utf-8") + "\n" + line + "\n")
        tmp.close()
        return Path(tmp.name)

    def _sync_check(self, source, reg=REG):
        return subprocess.run(
            [sys.executable, str(SCRIPTS / "bone.py"), "sync", "--check",
             "--source", str(source), "--registry", str(reg)],
            cwd=str(ROOT), capture_output=True, text=True,
        )

    def test_added_call_is_detected(self):
        src = self._mutated_source("if ! python3 scripts/check-brand-new-gate.py; then")
        try:
            p = self._sync_check(src)
            self.assertEqual(p.returncode, 1, p.stdout)
            self.assertIn("check-brand-new-gate", p.stdout)
        finally:
            src.unlink()

    def test_stale_registry_is_detected(self):
        with tempfile.TemporaryDirectory() as tmp:
            stale = Path(tmp) / "checks.json"
            data = registry.load(REG)
            data["checks"] = data["checks"][:-1]
            data["counts"]["checks"] = len(data["checks"])
            stale.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
            p = self._sync_check(SOURCE, stale)
            self.assertEqual(p.returncode, 1, p.stdout)
            self.assertIn("注册表缺失", p.stdout)

    def test_clean_sync_check_is_green(self):
        p = self._sync_check(SOURCE, REG)
        self.assertEqual(p.returncode, 0, p.stdout)


class CallShapeGuardTest(unittest.TestCase):
    """调用行形态守卫：防止注册表静默丢参造成「跑通了但判据降级」的假绿。"""

    def _source(self, line):
        tmp = tempfile.NamedTemporaryFile("w", suffix=".sh", delete=False, encoding="utf-8")
        tmp.write(line + "\n")
        tmp.close()
        return Path(tmp.name)

    def test_real_source_is_simple(self):
        self.assertEqual(registry.verify_call_shapes(SOURCE), [])

    def test_plain_call_is_allowed(self):
        src = self._source("if ! python3 scripts/check-fine.py --strict; then")
        try:
            self.assertEqual(registry.verify_call_shapes(src), [])
        finally:
            src.unlink()

    def test_env_prefix_is_rejected(self):
        src = self._source("if ! BONE_X=1 python3 scripts/check-evil.py --check; then")
        try:
            problems = registry.verify_call_shapes(src)
            self.assertTrue(any("前缀" in x for x in problems), problems)
            p = subprocess.run(
                [sys.executable, str(SCRIPTS / "bone.py"), "sync", "--check", "--source", str(src)],
                cwd=str(ROOT), capture_output=True, text=True,
            )
            self.assertEqual(p.returncode, 1, p.stdout)
            self.assertIn("前缀", p.stdout)
        finally:
            src.unlink()

    def test_quoted_arg_is_rejected(self):
        src = self._source("if ! python3 scripts/check-evil.py --include='a;b'; then")
        try:
            problems = registry.verify_call_shapes(src)
            self.assertTrue(any("复杂参数" in x for x in problems), problems)
        finally:
            src.unlink()


class ExternalInvokableTest(unittest.TestCase):
    def test_all_entries_declare_invokable(self):
        entries = registry.load_external(EXTERNAL)
        self.assertTrue(all(isinstance(e.get("invokable"), bool) for e in entries))
        self.assertEqual(sum(1 for e in entries if e["invokable"]), 9)
        self.assertEqual(registry.verify_external(ROOT, entries, SOURCE, CI), [])

    def test_missing_invokable_is_rejected(self):
        entries = [{"path": "doc/_check_diagnosis.py", "callers": ["manual"],
                    "disposition": "register"}]
        problems = registry.verify_external(ROOT, entries, SOURCE, CI)
        self.assertTrue(any("invokable" in x for x in problems), problems)

    def test_bad_entry_is_rejected(self):
        entries = [{"path": "doc/_check_diagnosis.py", "entry": "echo hi",
                    "invokable": True, "callers": ["manual"], "disposition": "register"}]
        problems = registry.verify_external(ROOT, entries, SOURCE, CI)
        self.assertTrue(any("entry" in x for x in problems), problems)


class ExternalMigrationContractTest(unittest.TestCase):
    """库外整合的登记契约：基线不腐烂、归位前置条件不缺失、cwd 不逃逸。"""

    def test_known_failure_baseline_points_at_registered_entries(self):
        known = {str(e["path"]) for e in registry.load_external(EXTERNAL)}
        baseline = gate.load_baseline(KNOWN_FAILURES)
        self.assertTrue(baseline, "基线非空（当前 1 条既有漂移）")
        for rel in baseline:
            self.assertIn(rel, known)

    def test_pending_migrations_declare_blockers(self):
        entries = registry.load_external(EXTERNAL)
        pending = [e for e in entries if e["disposition"]
                   in ("move_candidate", "consolidate_candidate")]
        self.assertEqual(len(pending), 7)
        self.assertTrue(all(
            isinstance(e.get("migration_blocker"), str) and e["migration_blocker"].strip()
            for e in pending
        ))

    def test_missing_migration_blocker_is_rejected(self):
        entries = [{"path": "doc/_check_diagnosis.py", "callers": ["manual"],
                    "disposition": "move_candidate", "invokable": False}]
        problems = registry.verify_external(ROOT, entries, SOURCE, CI)
        self.assertTrue(any("migration_blocker" in x for x in problems), problems)

    def test_cwd_validation_and_negative_mutations(self):
        entries = [{"path": "doc/_check_diagnosis.py", "callers": ["manual"],
                    "disposition": "register", "invokable": True,
                    "entry": "python3 doc/_check_diagnosis.py", "cwd": "/etc"}]
        self.assertTrue(any("cwd" in x for x in
                            registry.verify_external(ROOT, entries, SOURCE, CI)))
        entries[0]["cwd"] = "doc/../.."
        self.assertTrue(any("cwd" in x for x in
                            registry.verify_external(ROOT, entries, SOURCE, CI)))
        entries[0]["cwd"] = "doc/design/lowcode/ui/ui-protocol"
        self.assertEqual(registry.verify_external(ROOT, entries, SOURCE, CI), [])


if __name__ == "__main__":
    unittest.main(verbosity=2)
