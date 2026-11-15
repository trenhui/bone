#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""统一豁免台账的契约测试。

判据重点不是「函数返回值对不对」，而是「例外能不能绕过治理」：
过期、缺 owner、非法日期、未知 status、登记了不存在的路径、
以及最关键的「新增台账文件不登记」都必须判红。
"""

import json
import subprocess
import sys
import tempfile
import unittest
from datetime import date, timedelta
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
ROOT = SCRIPTS.parent
sys.path.insert(0, str(SCRIPTS))

from lib import waivers  # noqa: E402

BONE = SCRIPTS / "bone.py"
REGISTRY = ROOT / waivers.DEFAULT_PATH
TODAY = date(2026, 11, 12)


def run(*args):
    return subprocess.run(
        [sys.executable, str(BONE), *args], cwd=str(ROOT),
        capture_output=True, text=True,
    )


def load():
    return waivers.load(REGISTRY)


def active_entry(data):
    return next(e for e in data["entries"] if e["status"] == "active")


class WaiversRegistryTest(unittest.TestCase):
    def test_scan_and_registry_cover_each_other(self):
        data = load()
        registered = {str(e["path"]) for e in data["entries"]}
        self.assertEqual(len(registered), 24)
        self.assertEqual(waivers.scan(ROOT) - registered, set())

    def test_current_registry_has_no_problems(self):
        data = load()
        problems, warnings = waivers.verify(ROOT, data, today=TODAY)
        self.assertEqual(problems, [])
        self.assertEqual(len(warnings), 23, "23 份存量台账应如实列为待认领，不编造 owner")

    def test_active_entry_is_fully_owned(self):
        entry = active_entry(load())
        for field in ("owner", "reason", "expires"):
            self.assertTrue(str(entry.get(field) or "").strip(), field)
        self.assertEqual(date.fromisoformat(entry["expires"]), date(2026, 12, 12))

    def test_cli_matches_library(self):
        p = run("waivers", "--format", "json")
        self.assertEqual(p.returncode, 0, p.stdout + p.stderr)
        data = json.loads(p.stdout)
        self.assertEqual(data["schema"], waivers.SCHEMA)
        self.assertEqual(data["summary"]["scanned"], 24)
        self.assertEqual(data["summary"]["active"], 1)
        self.assertEqual(data["summary"]["legacy"], 23)
        self.assertEqual(data["summary"]["unregistered"], 0)
        self.assertEqual(data["summary"]["expired"], 0)
        self.assertEqual(data["summary"]["unowned"], 23)

    def test_sync_check_is_green(self):
        p = run("sync", "--check")
        self.assertEqual(p.returncode, 0, p.stdout)


class WaiversNegativeMutationTest(unittest.TestCase):
    def test_expired_waiver_is_red(self):
        data = load()
        active_entry(data)["expires"] = "2000-01-01"
        problems, _ = waivers.verify(ROOT, data, today=TODAY)
        self.assertTrue(any("已过期" in x for x in problems), problems)

    def test_expiring_soon_only_warns(self):
        data = load()
        active_entry(data)["expires"] = (TODAY + timedelta(days=7)).isoformat()
        problems, warnings = waivers.verify(ROOT, data, today=TODAY)
        self.assertEqual(problems, [], problems)
        self.assertTrue(any("临近到期" in x for x in warnings), warnings)

    def test_missing_owner_is_red(self):
        data = load()
        active_entry(data).pop("owner")
        problems, _ = waivers.verify(ROOT, data, today=TODAY)
        self.assertTrue(any("缺 owner" in x for x in problems), problems)

    def test_bad_iso_date_is_red(self):
        data = load()
        active_entry(data)["expires"] = "下个月再说"
        problems, _ = waivers.verify(ROOT, data, today=TODAY)
        self.assertTrue(any("ISO 日期" in x for x in problems), problems)

    def test_unknown_status_is_red(self):
        data = load()
        data["entries"][0]["status"] = "permanent"
        problems, _ = waivers.verify(ROOT, data, today=TODAY)
        self.assertTrue(any("status" in x for x in problems), problems)

    def test_missing_registered_path_is_red(self):
        data = load()
        data["entries"][0]["path"] = "doc/architecture/gone-baseline.json"
        problems, _ = waivers.verify(ROOT, data, today=TODAY)
        self.assertTrue(any("不存在" in x for x in problems), problems)

    def test_unregistered_new_ledger_is_red(self):
        """最关键的绕过路径：新建一份台账但不登记，必须判红。"""
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            subprocess.run(["git", "init", "-q"], cwd=str(root), check=True)
            (root / "fake-baseline.json").write_text("[]", encoding="utf-8")
            (root / "another-allowlist.json").write_text("[]", encoding="utf-8")
            data = {
                "schema": waivers.SCHEMA,
                "policy": {"warn_within_days": 14},
                "entries": [{"path": "fake-baseline.json", "status": "legacy"}],
            }
            problems, _ = waivers.verify(root, data, today=TODAY)
            self.assertTrue(any("未登记" in x and "another-allowlist" in x for x in problems),
                            problems)

    def test_cli_expired_registry_is_red(self):
        data = load()
        active_entry(data)["expires"] = "2000-01-01"
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "waivers.json"
            path.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
            p = run("waivers", "--waivers", str(path), "--format", "json")
            self.assertEqual(p.returncode, 1, p.stdout)
            self.assertGreater(json.loads(p.stdout)["summary"]["expired"], 0)

    def test_missing_registry_is_infra_error(self):
        p = run("waivers", "--waivers", "/nonexistent/waivers.json", "--format", "json")
        self.assertEqual(p.returncode, 2, p.stdout)


if __name__ == "__main__":
    unittest.main(verbosity=2)
