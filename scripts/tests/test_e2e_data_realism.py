#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""check-data-realism.py 的 R1–R5 与基线棘轮测试。

每个规则都要有「违规必红」样例，否则规则只是摆设；同时必须有「合法不误报」
样例 —— 误报会让门禁被整体关掉，比没有门禁更糟。
"""
import importlib.util
import json
import os
import shutil
import tempfile
import unittest

TESTS = os.path.dirname(os.path.abspath(__file__))
SCRIPT = os.path.join(os.path.dirname(TESTS), "e2e", "check-data-realism.py")
_spec = importlib.util.spec_from_file_location("e2e_data_realism", SCRIPT)
mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(mod)


def _violations(body, suffix=".py"):
    root = tempfile.mkdtemp(prefix="realism-")
    try:
        path = os.path.join(root, "sample" + suffix)
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(body)
        return mod.scan_file(path, "sample" + suffix)
    finally:
        shutil.rmtree(root, ignore_errors=True)


class RuleTest(unittest.TestCase):
    def test_r1_placeholder_names(self):
        self.assertTrue(_violations('receiver = "张三"\n'))
        self.assertTrue(_violations('receiver = "李四"\n'))
        self.assertFalse(_violations('receiver = "周敏"\n'))

    def test_r2_unseeded_random(self):
        self.assertTrue(_violations("import random\nn = random.randint(1, 9)\n"))
        self.assertTrue(_violations("const amount = Math.random() * 10000;\n", ".mjs"))
        # 幂等键等非数据用途不算违规
        self.assertFalse(_violations(
            "const key = 'IDEM-' + Date.now() + '-' + Math.random().toString(16);\n", ".mjs"))

    def test_r3_round_money(self):
        self.assertTrue(_violations("amount = 8800\n"))
        self.assertTrue(_violations("price: 199.0,\n", ".mjs"))
        self.assertFalse(_violations("amount = 8864.3\n"))
        self.assertFalse(_violations("total = 2863.5\n"))

    def test_r4_uuid_as_display_name(self):
        self.assertTrue(_violations("name = str(uuid4())\n"))
        self.assertFalse(_violations("idempotency_key = str(uuid4())\n"))

    def test_r5_test_placeholders(self):
        self.assertTrue(_violations('address = "测试地址"\n'))
        self.assertTrue(_violations('addr = "某省某市某区 1 号"\n'))
        self.assertFalse(_violations('address = "杭州市高新区科技大道 88 号"\n'))

    def test_docstring_examples_do_not_trip_rules(self):
        body = '"""反例：张三 / amount = 8800 / uuid4() 都不允许。"""\nreal = "周敏"\n'
        self.assertEqual(_violations(body), [])

    def test_line_numbers_survive_comment_stripping(self):
        hits = _violations('\n\n# 注释\namount = 8800\n')
        self.assertEqual(hits[0]["line"], 4)


class BaselineTest(unittest.TestCase):
    HITS = [{"file": "a.py", "rule": "R1"} for _ in range(3)]

    def test_within_baseline_is_green(self):
        # 棘轮语义：实测 == 基线 → 绿；实测 < 基线 → 必须收缩（见下一个用例）
        self.assertEqual(mod.evaluate(self.HITS[:2], {"a.py|R1": 2}), ([], []))

    def test_exceeding_baseline_is_red(self):
        new, shrink = mod.evaluate(self.HITS, {"a.py|R1": 2})
        self.assertEqual(len(new), 1)
        self.assertEqual(shrink, [])

    def test_fixed_violation_requires_baseline_shrink(self):
        new, shrink = mod.evaluate(self.HITS[:1], {"a.py|R1": 3})
        self.assertEqual(new, [])
        self.assertEqual(shrink, ["a.py|R1"])

    def test_main_exit_codes_and_update_baseline(self):
        root = tempfile.mkdtemp(prefix="realism-main-")
        baseline = os.path.join(root, "baseline.json")
        try:
            with open(os.path.join(root, "bad.py"), "w", encoding="utf-8") as fh:
                fh.write('name = "张三"\n')
            self.assertEqual(mod.main(["--root", root, "--baseline", baseline, "--json"]), 1)
            self.assertEqual(mod.main(["--root", root, "--baseline", baseline,
                                       "--update-baseline"]), 0)
            with open(baseline, encoding="utf-8") as fh:
                self.assertEqual(json.load(fh)["entries"], {"bad.py|R1": 1})
            self.assertEqual(mod.main(["--root", root, "--baseline", baseline]), 0)
        finally:
            shutil.rmtree(root, ignore_errors=True)


if __name__ == "__main__":
    unittest.main()
