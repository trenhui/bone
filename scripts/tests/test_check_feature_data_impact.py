#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""功能变更 → 造数资产同步判据的负向变异测试（判据必须有牙）。

只测纯函数与离线注入（--changed-file / 临时清单），不依赖当前 git 暂存区，
避免并发工作区状态让单测忽绿忽红。
"""

import copy
import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from datetime import date
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
ROOT = SCRIPTS.parent
CLI = SCRIPTS / "check-feature-data-impact.py"
REG = SCRIPTS / "registry" / "data-assets.json"
TODAY = date(2026, 11, 12)

CLAIM_ENTITY = (
    "bone-apps/synap-claim/synap-claim-app/src/main/java/com/bone/claim/"
    "domain/model/claim/ClaimCase.java"
)
CLAIM_CONTROLLER = (
    "bone-apps/synap-claim/synap-claim-app/src/main/java/com/bone/claim/"
    "adapter/web/controller/ClaimCaseController.java"
)
SCENARIOS = "scripts/e2e/e2e_lib/scenarios.py"


def _module():
    spec = importlib.util.spec_from_file_location("feature_data_impact", CLI)
    mod = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(mod)
    return mod


def _data():
    return json.loads(REG.read_text(encoding="utf-8"))


def _cli(*args):
    return subprocess.run(
        [sys.executable, str(CLI), *args], cwd=str(ROOT),
        capture_output=True, text=True,
    )


class SyncObligationTest(unittest.TestCase):
    def setUp(self):
        self.mod = _module()
        self.data = _data()

    def test_data_contract_change_without_asset_sync_is_violation(self):
        details, problems = self.mod.evaluate(ROOT, self.data, [CLAIM_ENTITY], TODAY)
        self.assertEqual([d["domain"] for d in details], ["claim"])
        self.assertTrue(details[0]["sync_required"])
        self.assertEqual(details[0]["synced_in_this_change"], [])
        self.assertEqual(len(problems), 1)
        self.assertIn("C3", problems[0])

    def test_syncing_scenarios_in_same_change_clears_violation(self):
        _, problems = self.mod.evaluate(ROOT, self.data, [CLAIM_ENTITY, SCENARIOS], TODAY)
        self.assertEqual(problems, [])
        details, _ = self.mod.evaluate(ROOT, self.data, [CLAIM_ENTITY, SCENARIOS], TODAY)
        self.assertIn(SCENARIOS, details[0]["synced_in_this_change"])

    def test_plain_endpoint_change_only_reminds(self):
        details, problems = self.mod.evaluate(ROOT, self.data, [CLAIM_CONTROLLER], TODAY)
        self.assertEqual(problems, [])
        self.assertFalse(details[0]["sync_required"])

    def test_valid_ack_allows_unsynced_change_but_expired_one_does_not(self):
        fresh = copy.deepcopy(self.data)
        fresh["acks"] = [{"domain": "claim", "owner": "qa", "reason": "纯字段改名，不影响造数口径",
                          "expires": "2026-12-01"}]
        _, problems = self.mod.evaluate(ROOT, fresh, [CLAIM_ENTITY], TODAY)
        self.assertEqual(problems, [])
        self.assertEqual(self.mod.data_assets.verify_acks(fresh, TODAY), [])

        expired = copy.deepcopy(fresh)
        expired["acks"][0]["expires"] = "2026-10-01"
        _, problems = self.mod.evaluate(ROOT, expired, [CLAIM_ENTITY], TODAY)
        self.assertEqual(len(problems), 1)
        self.assertIn("已过期", self.mod.data_assets.verify_acks(expired, TODAY)[0])

    def test_ack_without_owner_or_iso_date_is_rejected(self):
        for field, value in (("owner", ""), ("expires", "not-a-date")):
            broken = copy.deepcopy(self.data)
            broken["acks"] = [{"domain": "claim", "owner": "qa",
                               "reason": "理由充分说明为何暂不同步", "expires": "2026-12-01"}]
            broken["acks"][0][field] = value
            self.assertTrue(self.mod.data_assets.verify_acks(broken, TODAY))

    def test_unknown_scenario_or_asset_is_rejected(self):
        broken = copy.deepcopy(self.data)
        broken["domains"]["claim"]["scenario"] = "not-a-scenario"
        self.assertTrue(any("不在 scenarios.py" in p
                            for p in self.mod.data_assets.verify_static(ROOT, broken)))
        broken = copy.deepcopy(self.data)
        broken["domains"]["claim"]["assets"] = ["not-registered"]
        self.assertTrue(any("未登记资产" in p
                            for p in self.mod.data_assets.verify_static(ROOT, broken)))

    def test_coverage_drift_is_rejected(self):
        broken = copy.deepcopy(self.data)
        broken["coverage"] = {}
        self.assertTrue(any("coverage 派生段已漂移" in p
                            for p in self.mod.data_assets.verify_static(ROOT, broken)))
        self.assertEqual(self.mod.data_assets.verify_static(ROOT, self.data), [])

    def test_all_enterprise_scenarios_have_nonempty_green_invariants(self):
        report, problems = self.mod.data_assets.scenario_report(ROOT)
        self.assertEqual(problems, [])
        # 只校验核心场景仍在且全绿；新增场景（如 claim-ai-feedback）不应让本用例失效
        self.assertLessEqual({"sales-claim", "procurement", "tenant-iam", "master-data"},
                             set(report))
        self.assertTrue(all(v["all_ok"] and v["checks"] for v in report.values()))


class CliContractTest(unittest.TestCase):
    def test_strict_blocks_unsynced_data_contract_change(self):
        with tempfile.TemporaryDirectory() as tmp:
            changed = Path(tmp) / "changed.txt"
            changed.write_text(CLAIM_ENTITY + "\n", encoding="utf-8")
            p = _cli("--staged", "--strict", "--json", "--changed-file", str(changed))
            self.assertEqual(p.returncode, 1, p.stdout + p.stderr)
            d = json.loads(p.stdout)
            self.assertFalse(d["ok"])
            self.assertTrue(any("C3" in x for x in d["sync_problems"]))

    def test_non_strict_only_reports_sync_obligation(self):
        with tempfile.TemporaryDirectory() as tmp:
            changed = Path(tmp) / "changed.txt"
            changed.write_text(CLAIM_ENTITY + "\n", encoding="utf-8")
            p = _cli("--staged", "--json", "--changed-file", str(changed))
            self.assertEqual(p.returncode, 0, p.stdout + p.stderr)
            d = json.loads(p.stdout)
            self.assertFalse(d["ok"])
            self.assertEqual(len(d["sync_problems"]), 1)

    def test_write_map_refreshes_derived_coverage(self):
        with tempfile.TemporaryDirectory() as tmp:
            reg = Path(tmp) / "data-assets.json"
            doc = _data()
            doc["coverage"] = {}
            reg.write_text(json.dumps(doc, ensure_ascii=False), encoding="utf-8")
            p = _cli("--write-map", "--registry", str(reg), "--json")
            self.assertEqual(p.returncode, 0, p.stdout + p.stderr)
            written = json.loads(reg.read_text(encoding="utf-8"))
            self.assertTrue(written["coverage"])
            self.assertIn("claim", written["coverage"])
            self.assertTrue(json.loads(p.stdout)["ok"], "刷新后清单必须通过静态自检")

    def test_broken_registry_is_infra_error_not_silent_pass(self):
        with tempfile.TemporaryDirectory() as tmp:
            reg = Path(tmp) / "bad.json"
            reg.write_text('{"schema": "wrong"}', encoding="utf-8")
            p = _cli("--registry", str(reg), "--json")
            self.assertEqual(p.returncode, 2, p.stdout + p.stderr)
            self.assertFalse(json.loads(p.stdout)["ok"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
