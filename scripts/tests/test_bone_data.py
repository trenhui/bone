#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""`bone.py data` 的 agent 复用契约：一个入口发现 / 取数 / 体检，不复制造数逻辑。"""

import json
import subprocess
import sys
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
ROOT = SCRIPTS.parent
BONE = SCRIPTS / "bone.py"


def run(*args):
    return subprocess.run(
        [sys.executable, str(BONE), *args], cwd=str(ROOT),
        capture_output=True, text=True,
    )


class DataCatalogTest(unittest.TestCase):
    def test_list_exposes_assets_scenarios_and_invariants(self):
        p = run("data", "list", "--format", "json")
        self.assertEqual(p.returncode, 0, p.stdout + p.stderr)
        d = json.loads(p.stdout)
        self.assertEqual(d["schema"], "bone.data.v1")
        self.assertEqual(d["mode"], "list")
        self.assertTrue(d["health"]["ok"], d["health"]["problems"])
        self.assertIn("scenarios", d["assets"])
        self.assertIn("wordlist", d["assets"])
        names = {s["name"] for s in d["scenarios"]}
        self.assertEqual(names, {"sales-claim", "procurement", "tenant-iam", "master-data",
                                 "claim-ai-feedback"})
        self.assertIn("amount", d["fixtures"])
        self.assertTrue(all(v["all_ok"] and v["checks"] for v in d["invariants"].values()),
                        "每个企业场景都必须带非空且成立的不变量")
        self.assertTrue(d["coverage"], "派生映射不能为空（hook --write-map 生成）")

    def test_scenario_is_deterministic_and_readable(self):
        args = ("data", "scenario", "sales-claim", "--seed", "fixed-a", "--format", "json")
        a, b = run(*args), run(*args)
        self.assertEqual(a.returncode, 0, a.stdout + a.stderr)
        self.assertEqual(b.returncode, 0, b.stdout + b.stderr)
        self.assertEqual(a.stdout, b.stdout, "同 seed 必须产出同一份数据")
        d = json.loads(a.stdout)
        self.assertEqual(d["scenario"], "sales-claim")
        self.assertIn("story", d)
        self.assertTrue(d["invariants"]["all_ok"])
        for junk in ("张三", "test123", "abc123", "测试商品"):
            self.assertNotIn(junk, a.stdout)

    def test_fixture_delegates_to_datagen(self):
        p = run("data", "fixture", "person", "--count", "3", "--seed", "fixed-a", "--format", "json")
        self.assertEqual(p.returncode, 0, p.stdout + p.stderr)
        d = json.loads(p.stdout)
        self.assertEqual(d["count"], 3)
        self.assertEqual(len(d["values"]), 3)

    def test_validate_is_green_on_current_tree(self):
        p = run("data", "validate", "--format", "json")
        self.assertEqual(p.returncode, 0, p.stdout + p.stderr)
        d = json.loads(p.stdout)
        self.assertEqual(d["schema"], "bone.data.v1")
        self.assertTrue(d["report"]["ok"], d["report"].get("problems"))


if __name__ == "__main__":
    unittest.main(verbosity=2)
