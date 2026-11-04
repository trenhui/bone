#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""理赔 AI 决策回归评测集门禁（check-claim-ai-evalset.py）的表征测试。

设计要点（沿用本仓反复踩过的「假绿」教训）：
  1. 每条判据都要有**精确归因**的违规用例：断言错误前缀命中该规则，
     不接受「反正返回非空就算抓到了」——红点落在别的规则上说明该用例无判别力；
  2. 每条判据都要有**合规正例**：防止「永远拒绝」式实现蒙混过关；
  3. 防假绿闸必须在可测函数内：直接调 check()，不测 main()；
  4. 额外校验**仓库真源**也通过——夹具自洽但真源已坏时，单测不能全绿。
"""

import importlib.util
import json
import shutil
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GATE_PATH = ROOT / "scripts" / "check-claim-ai-evalset.py"
EVALSET_REL = Path("bone-apps/synap-claim/eval/ai-decision-evalset.json")


def _load(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


gate = _load(GATE_PATH, "bone_claim_ai_evalset_gate_under_test")


def _fixture_root(tmp: Path):
    """构造一个自洽的临时仓库：真源评测集 + 被引用的真源文件 + 4 个测试类。"""
    def put(rel):
        src = ROOT / rel
        assert src.is_file(), f"夹具依赖的真源缺失：{rel}"
        dst = tmp / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)

    put(EVALSET_REL)
    doc = json.loads((ROOT / EVALSET_REL).read_text(encoding="utf-8"))
    for case in doc["cases"]:
        put(case["groundTruthRef"].split("#", 1)[0])
    test_root = ROOT / gate.TEST_ROOT_REL
    for cls in {s["testClass"] for s in doc["suites"]}:
        hits = list(test_root.rglob(cls + ".java"))
        assert len(hits) == 1, f"测试类 {cls} 应唯一命中，实际 {len(hits)}"
        rel = hits[0].relative_to(ROOT)
        put(rel)
    return doc


def _write(tmp: Path, doc):
    (tmp / EVALSET_REL).write_text(json.dumps(doc, ensure_ascii=False, indent=2), encoding="utf-8")


def _errors(errs, rule):
    return [e for e in errs if e.startswith(rule + ":")]


class ClaimAiEvalsetGateTest(unittest.TestCase):
    # 夹具复制涉及十几个真源文件的磁盘 I/O（实测约 2.4s/次），19 个用例各建一份会把
    # 门禁单测拖到 ~51s。改为类级共享一份夹具，每个用例前只重写评测集 JSON（毫秒级）。
    @classmethod
    def setUpClass(cls):
        cls._tmp = tempfile.TemporaryDirectory()
        cls.tmp = Path(cls._tmp.name)
        cls.doc = _fixture_root(cls.tmp)

    @classmethod
    def tearDownClass(cls):
        cls._tmp.cleanup()

    def setUp(self):
        # 每个用例都从合规基线起步，避免上一例的注入污染下一例（还原必绿）。
        _write(self.tmp, self.doc)

    def test_compliant_fixture_is_green(self):
        """合规正例：夹具必须全绿，否则后续所有注入都失去判别力。"""
        self.assertEqual(gate.check(self.tmp), [])

    def test_real_repository_source_is_green(self):
        """真源护栏：夹具自洽不够，仓库里的真源也必须通过。"""
        self.assertEqual(gate.check(ROOT), [])

    def test_a1_schema_version_must_be_one(self):
        bad = json.loads(json.dumps(self.doc))
        bad["schemaVersion"] = 2
        _write(self.tmp, bad)
        self.assertTrue(_errors(gate.check(self.tmp), "A1"))

    def test_a1_missing_scope_is_red(self):
        bad = json.loads(json.dumps(self.doc))
        bad["scope"] = ""
        _write(self.tmp, bad)
        self.assertTrue(_errors(gate.check(self.tmp), "A1"))

    def test_a2_deleting_one_case_is_red(self):
        """核心防退化：删一条用例让门禁变轻，必须被硬编码下界抓住。"""
        bad = json.loads(json.dumps(self.doc))
        bad["cases"].pop()
        _write(self.tmp, bad)
        self.assertTrue(_errors(gate.check(self.tmp), "A2"))

    def test_a2_deleting_a_suite_is_red(self):
        bad = json.loads(json.dumps(self.doc))
        bad["suites"] = [s for s in bad["suites"] if s["id"] != "aiAssessNode"]
        _write(self.tmp, bad)
        self.assertTrue(_errors(gate.check(self.tmp), "A2"))

    def test_a3_pii_free_false_is_red(self):
        bad = json.loads(json.dumps(self.doc))
        bad["cases"][0]["piiFree"] = False
        _write(self.tmp, bad)
        self.assertTrue(_errors(gate.check(self.tmp), "A3"))

    def test_a3_empty_expected_is_red(self):
        bad = json.loads(json.dumps(self.doc))
        bad["cases"][0]["expected"] = {}
        _write(self.tmp, bad)
        self.assertTrue(_errors(gate.check(self.tmp), "A3"))

    def test_a4_symbol_drift_is_red(self):
        """清单说有、代码里没有 —— 第二真源漂移必须判红。"""
        bad = json.loads(json.dumps(self.doc))
        bad["cases"][0]["testSymbol"] = "NO_SUCH_METHOD_XYZ"
        _write(self.tmp, bad)
        self.assertTrue(_errors(gate.check(self.tmp), "A4"))

    def test_a4_missing_test_class_is_red(self):
        bad = json.loads(json.dumps(self.doc))
        bad["cases"][0]["testClass"] = "NoSuchGoldenSetTest"
        _write(self.tmp, bad)
        self.assertTrue(_errors(gate.check(self.tmp), "A4"))

    def test_a5_nonexistent_ground_truth_is_red(self):
        bad = json.loads(json.dumps(self.doc))
        bad["cases"][0]["groundTruthRef"] = "doc/no-such-truth.md#anchor"
        _write(self.tmp, bad)
        self.assertTrue(_errors(gate.check(self.tmp), "A5"))

    def test_a5_missing_regulatory_basis_is_red(self):
        bad = json.loads(json.dumps(self.doc))
        bad["cases"][0]["regulatoryBasis"] = ""
        _write(self.tmp, bad)
        self.assertTrue(_errors(gate.check(self.tmp), "A5"))

    def test_a6_deleting_key_negative_probe_is_red(self):
        """监管最在意：抹掉「无法判定≠拒赔」这条护栏必须当场红。"""
        bad = json.loads(json.dumps(self.doc))
        bad["cases"] = [c for c in bad["cases"] if c["id"] != "AIASSESS-01"]
        _write(self.tmp, bad)
        self.assertTrue(_errors(gate.check(self.tmp), "A6"))

    def test_a6_flipping_all_to_positive_is_red(self):
        bad = json.loads(json.dumps(self.doc))
        for c in bad["cases"]:
            c["kind"] = "positive"
        _write(self.tmp, bad)
        self.assertTrue(_errors(gate.check(self.tmp), "A6"))

    def test_a7_missing_policy_field_is_red(self):
        bad = json.loads(json.dumps(self.doc))
        del bad["groundTruthPolicy"]["frozenSetDiscipline"]
        _write(self.tmp, bad)
        self.assertTrue(_errors(gate.check(self.tmp), "A7"))

    def test_a8_pii_in_evalset_is_red(self):
        """§24：合成手机号也不得入集（门禁按模式判，不按「是否真实」判）。"""
        bad = json.loads(json.dumps(self.doc))
        bad["cases"][0]["name"] = "张某 13800138000"
        _write(self.tmp, bad)
        self.assertTrue(_errors(gate.check(self.tmp), "A8"))

    def test_a9_missing_test_sources_is_red(self):
        """路径失效会让 A4 恒绿 —— 扫描不到源码必须判失败。

        用 monkeypatch 模拟而非真删共享夹具的文件，避免破坏其它用例的合规基线。
        """
        original = gate.java_test_sources
        gate.java_test_sources = lambda root: []
        try:
            self.assertTrue(_errors(gate.check(self.tmp), "A9"))
        finally:
            gate.java_test_sources = original

    def test_a9_sentinel_catches_degenerated_verdict(self):
        """把某个判据改成恒绿，独立哨兵必须报 A9，而不是跟着一起绿。"""
        original = gate.case_field_errors
        gate.case_field_errors = lambda case, tag: []
        try:
            self.assertTrue(_errors(gate.check(self.tmp), "A9"))
        finally:
            gate.case_field_errors = original

    def test_restore_after_injection_is_green(self):
        """还原必绿：注入→必红→写回原文档→必绿，排除「门禁只会拒绝」。"""
        bad = json.loads(json.dumps(self.doc))
        bad["cases"].pop()
        _write(self.tmp, bad)
        self.assertTrue(gate.check(self.tmp))
        _write(self.tmp, self.doc)
        self.assertEqual(gate.check(self.tmp), [])


if __name__ == "__main__":
    unittest.main()
