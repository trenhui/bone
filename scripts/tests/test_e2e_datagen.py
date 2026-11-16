#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""datagen / scenarios 的契约测试。

锁三件事（少一件，所谓「可复用造数」就会退化）：
  1. **确定性**：同 seed 两次构建必须完全相等（否则 A 查到的 id 在 B 那里不存在）；
  2. **业务不变量**：金额守恒、日期单调、SoD、引用完整（scenarios 自带 invariants）；
  3. **可读性**：不出现机器味占位值，金额带分位，人名/公司名来自共享词表。
"""
import importlib.util
import json
import os
import subprocess
import sys
import unittest

TESTS = os.path.dirname(os.path.abspath(__file__))
E2E = os.path.join(os.path.dirname(TESTS), "e2e")
if E2E not in sys.path:
    sys.path.insert(0, E2E)

from e2e_lib import scenarios  # noqa: E402

DATAGEN = os.path.join(E2E, "datagen.py")
JUNK = ("张三", "李四", "test123", "abc123", "asdf", "lorem", "测试地址", "测试商品", "某省某市某区")


def _run(*args):
    return subprocess.run([sys.executable, DATAGEN] + list(args),
                          capture_output=True, text=True)


class ScenarioTest(unittest.TestCase):
    def test_all_scenarios_registered(self):
        self.assertEqual(
            scenarios.names(),
            ["claim-ai-feedback", "claim-task-assignment", "master-data", "procurement",
             "sales-claim", "tenant-iam"],
        )

    def test_same_seed_is_deterministic(self):
        for name in scenarios.names():
            first = scenarios.build(name, "demo-seed")
            second = scenarios.build(name, "demo-seed")
            self.assertEqual(first, second, "%s 同 seed 不稳定" % name)

    def test_different_seed_changes_data(self):
        for name in scenarios.names():
            a = scenarios.build(name, "seed-a")
            b = scenarios.build(name, "seed-b")
            self.assertNotEqual(a, b, "%s 不同 seed 数据相同，词表未被消费" % name)

    def test_invariants_hold_for_every_scenario(self):
        for name in scenarios.names():
            for seed in ("s1", "s2", "回归-2026"):
                data = scenarios.build(name, seed)
                inv = data["invariants"]
                self.assertTrue(inv["all_ok"],
                                "%s/%s 不变量失败: %s" % (name, seed, inv["checks"]))

    def test_values_are_human_readable(self):
        blob = json.dumps([scenarios.build(n, "readability") for n in scenarios.names()],
                          ensure_ascii=False)
        for junk in JUNK:
            self.assertNotIn(junk, blob, "出现机器味数据: %s" % junk)

    def test_money_has_cents(self):
        """业务金额必须带分位：整百/整千金额是「假数据」的典型特征。"""
        claim = scenarios.build("sales-claim", "money")
        payable = claim["sales_order"]["payable"]
        self.assertNotEqual(round(payable, 2), round(payable / 100) * 100,
                            "订单应付是整百数，疑似随手编造: %s" % payable)
        purchase = scenarios.build("procurement", "money")
        amount = purchase["purchase_order"]["amount"]
        self.assertNotEqual(round(amount, 2), round(amount / 100) * 100)

    def test_tenant_iam_references_are_closed(self):
        data = scenarios.build("tenant-iam", "ref")
        dept_codes = {d["dept_code"] for d in data["depts"]}
        role_codes = {r["role_code"] for r in data["roles"]}
        for user in data["users"]:
            self.assertIn(user["dept_code"], dept_codes)
            self.assertTrue(set(user["role_codes"]) <= role_codes)
            self.assertNotIn("password", user, "口令不得落进数据文件")

    def test_master_data_overlay_points_to_platform_codes(self):
        data = scenarios.build("master-data", "overlay")
        codes = {m["material_code"] for m in data["platform"]["materials"]}
        for overlay in data["tenant_overlay"]["material_overlays"]:
            self.assertIn(overlay["source_material_code"], codes)

    def test_claim_task_assignment_boundaries_and_fallbacks(self):
        """金额段左闭右开、金额未知不误派、停用规则不参与、三级回落可区分。

        这四条是「归属是写入期事实」的地基：任一条退化都会把案件派给错的人，
        而且事后从任务行上看不出错——所以必须由造数断言先钉住语义。
        """
        data = scenarios.build("claim-task-assignment", "assign")
        expect = data["expect"]

        # 左闭右开：恰好等于上界的金额必须落进下一段
        self.assertEqual(expect["small_outpatient"]["ruleId"], 102)
        self.assertEqual(expect["boundary_at_upper"]["ruleId"], 103)

        # 金额未知：不得命中任何设了金额段的规则（只能走通配）
        self.assertEqual(expect["amount_unknown"]["ruleId"], 101)
        unknown_rule = next(r for r in data["rules"] if r["ruleId"] == 101)
        self.assertIsNone(unknown_rule["amountMin"])
        self.assertIsNone(unknown_rule["amountMax"])

        # 停用规则优先级最高（1）却必须 0 命中
        disabled = [r for r in data["rules"] if not r["enabled"]]
        self.assertTrue(disabled, "样本里必须有一条停用规则，否则判不出「停用不参与」")
        disabled_ids = {r["ruleId"] for r in disabled}
        self.assertFalse(disabled_ids & {t["assignRuleId"] for t in data["tasks"]})

        # 三级回落：没配规则 vs 配了但目标全不可用，处置方向不同，不得合并
        self.assertEqual(expect["no_rule"], {"ruleId": None, "reason": "NO_RULE"})
        self.assertEqual(expect["all_ineligible"],
                         {"ruleId": None, "reason": "ALL_INELIGIBLE"})


class DatagenCliTest(unittest.TestCase):
    def test_list_json_lists_scenarios_and_fixtures(self):
        proc = _run("list", "--json")
        self.assertEqual(proc.returncode, 0, proc.stderr)
        doc = json.loads(proc.stdout)
        self.assertEqual([s["name"] for s in doc["scenarios"]], scenarios.names())
        self.assertIn("person", doc["fixtures"])

    def test_scenario_output_is_json(self):
        proc = _run("sales-claim", "--seed", "cli")
        self.assertEqual(proc.returncode, 0, proc.stderr)
        data = json.loads(proc.stdout)
        self.assertEqual(data["scenario"], "sales-claim")
        self.assertEqual(data["seed"], "cli")

    def test_env_output_is_exportable(self):
        proc = _run("tenant-iam", "--seed", "cli", "--format", "env")
        self.assertEqual(proc.returncode, 0, proc.stderr)
        lines = [ln for ln in proc.stdout.splitlines() if ln.strip()]
        self.assertTrue(lines)
        self.assertTrue(all(ln.startswith("export BONE_DATA_") for ln in lines), lines[:3])

    def test_md_output_is_table(self):
        proc = _run("procurement", "--seed", "cli", "--format", "md")
        self.assertEqual(proc.returncode, 0, proc.stderr)
        self.assertIn("| 字段 | 值 |", proc.stdout)

    def test_unknown_scenario_exits_2(self):
        proc = _run("fixture", "not-a-kind")
        self.assertEqual(proc.returncode, 2)

    def test_fixture_count(self):
        proc = _run("fixture", "person", "--count", "3", "--seed", "cli")
        self.assertEqual(proc.returncode, 0, proc.stderr)
        self.assertEqual(len(json.loads(proc.stdout)["values"]), 3)

    def test_fixture_values_are_distinct(self):
        """批量取人名不能出现两个一模一样的值（会让人以为脚本坏了）。"""
        for seed in ("cli", "demo-a", "794471195", "verify-1"):
            proc = _run("fixture", "person", "--count", "5", "--seed", seed)
            self.assertEqual(proc.returncode, 0, proc.stderr)
            values = json.loads(proc.stdout)["values"]
            self.assertEqual(len(values), len(set(values)), "%s 出现重复: %s" % (seed, values))

    def test_fixture_batch_is_stable_for_same_seed(self):
        first = _run("fixture", "person", "--count", "4", "--seed", "cli").stdout
        second = _run("fixture", "person", "--count", "4", "--seed", "cli").stdout
        self.assertEqual(first, second)


if __name__ == "__main__":
    unittest.main()
