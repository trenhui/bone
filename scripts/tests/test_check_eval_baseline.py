#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""G0 基线门禁（check-eval-baseline.py）与冻结集引擎（frozen_set.py）的表征测试。

设计要点（对应本仓反复踩过的「假绿」教训）：
  1. 每条判据都要有**精确归因**的违规用例：断言错误前缀命中该规则，
     不接受「反正返回非空就算抓到了」——红点落在别的规则上说明该用例无判别力。
  2. 每条判据都要有**合规正例**：防止「永远拒绝」式实现蒙混过关。
  3. 防假绿闸必须在可测函数内：直接调 check()/verify_manifest()，不测 main()。
  4. 门禁侧独立哨兵（E2）用「退化版引擎」做变异：把引擎改成恒绿，
     门禁必须仍然判红——否则 E2 只是「问引擎你自己觉得对吗」。
"""

import importlib.util
import json
import os
import shutil
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPTS = ROOT / "scripts"
EVAL = ROOT / "bone-ai" / "bone-ai-rag" / "eval"
ENGINE_PATH = EVAL / "frozen_set.py"
GATE_PATH = SCRIPTS / "check-eval-baseline.py"


def _load(path: Path, name: str):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


engine = _load(ENGINE_PATH, "bone_g0_engine_under_test")
gate = _load(GATE_PATH, "bone_g0_gate_under_test")

EXPECTED_DIMS = list(engine.SLICE_DIMENSIONS)
WIRED_SYMBOLS = ("MicrometerDecisionMetricsAdapter", "DecisionMetricsPort", "MicrometerLlmMetrics")


def _metrics_doc():
    """合规指标字典（与真源同构，独立构造以免真源修改后测试跟着放宽）。"""
    def metric(mid):
        return {
            "id": mid,
            "name": "指标 " + mid,
            "definition": "口径说明",
            "unit": "ratio(0..1)",
            "targetRef": "契约 §11.1",
            "slices": list(EXPECTED_DIMS),
            "status": "declared",
            "sourceSymbol": None,
            "blockingReason": "未接线的原因",
        }
    return {
        "schemaVersion": 1,
        "status": "skeleton",
        "sliceDimensions": list(EXPECTED_DIMS),
        "metrics": [metric(m) for m in gate.EXPECTED_METRIC_IDS],
        "wiredObservability": [
            {"id": "w" + str(i), "sourceSymbol": s, "status": "wired"}
            for i, s in enumerate(WIRED_SYMBOLS)
        ],
    }


class FrozenSetEngineTest(unittest.TestCase):
    """引擎判据：正例放行 + 每条 V 规则精确命中。"""

    def test_compliant_skeleton_passes(self):
        self.assertEqual(engine.verify_manifest(engine.skeleton_manifest()), [])

    def test_compliant_active_passes(self):
        doc = engine._compliant_active()
        self.assertEqual(engine.verify_manifest(doc), [])

    def test_self_test_is_green_and_covers_all_rules(self):
        ok, failures = engine.self_test()
        self.assertTrue(ok, failures)
        # 新增判据却没写反例 ⇒ self_test 必须自己报出来
        self.assertEqual(len(engine.RULES), 11)

    def assert_rule(self, doc, rule):
        errs = engine.verify_manifest(doc)
        self.assertTrue(
            any(e.startswith(rule + ":") for e in errs),
            f"期望精确命中 {rule}，实际 {errs}",
        )

    def test_v0_rejects_empty_and_version_drift(self):
        self.assert_rule(None, "V0")
        self.assert_rule({}, "V0")
        d = engine.skeleton_manifest(); d["schemaVersion"] = 99
        self.assert_rule(d, "V0")

    def test_v3_hash_tampering_detected(self):
        d = engine._compliant_active()
        d["cases"][0]["slices"]["tenant"] = "tampered"
        self.assert_rule(d, "V3")

    def test_v4_target_size_cannot_impersonate_collected(self):
        d = engine.skeleton_manifest(); d["collected"] = 300
        self.assert_rule(d, "V4")

    def test_v6_frozen_leak_into_train_detected(self):
        d = engine._compliant_active()
        d["cases"].append({
            "caseId": "LEAK", "split": "train", "stratum": "positive",
            "payloadHash": d["cases"][0]["payloadHash"],
            "slices": {dim: f"{dim}-a" for dim in EXPECTED_DIMS},
        })
        d["collected"] = len(d["cases"])
        d["frozenHash"] = engine.freeze_hash(d["cases"])
        self.assert_rule(d, "V6")

    def test_v7_active_requires_slice_diversity(self):
        d = engine._compliant_active()
        for c in d["cases"]:
            c["slices"]["tenant"] = "single-value"
        d["frozenHash"] = engine.freeze_hash(d["cases"])
        self.assert_rule(d, "V7")

    def test_v10_skeleton_forbids_claimed_achievement(self):
        d = engine.skeleton_manifest(); d["accuracyAchieved"] = 0.99
        self.assert_rule(d, "V10")


class BaselineGateTest(unittest.TestCase):
    """门禁判据：用临时仓库根注入，不依赖真实工作树的脏文件。"""

    def setUp(self):
        self.root = Path(tempfile.mkdtemp(prefix="g0-baseline-"))
        self.eval_dir = self.root / gate.EVAL_REL
        self.eval_dir.mkdir(parents=True)
        shutil.copy2(ENGINE_PATH, self.eval_dir / "frozen_set.py")
        self.manifest_path = self.eval_dir / "frozen_set.skeleton.json"
        self.metrics_path = self.eval_dir / "baseline-metrics.json"
        self.write_manifest(engine.skeleton_manifest())
        self.write_metrics(_metrics_doc())
        self.write_sources(WIRED_SYMBOLS)

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def write_manifest(self, doc):
        self.manifest_path.write_text(json.dumps(doc, ensure_ascii=False), encoding="utf-8")

    def write_metrics(self, doc):
        self.metrics_path.write_text(json.dumps(doc, ensure_ascii=False), encoding="utf-8")

    def write_sources(self, symbols):
        for sub in gate.SCAN_ROOTS:
            (self.root / sub).mkdir(parents=True, exist_ok=True)
        for i, sym in enumerate(symbols):
            (self.root / "bone-ai" / f"Wired{i}.java").write_text(
                f"public class {sym} {{}}\n", encoding="utf-8"
            )
        (self.root / "bone-apps" / "Plain.java").write_text(
            "public class Plain {}\n", encoding="utf-8"
        )

    def errors(self):
        return gate.check(self.root)

    def assert_clean(self):
        self.assertEqual(self.errors(), [])

    def assert_rule(self, rule):
        errs = self.errors()
        self.assertTrue(
            any(e.startswith(rule + ":") for e in errs),
            f"期望精确命中 {rule}，实际 {errs}",
        )

    # ---------- 正向钉子：合规态必须放行（防「永远拒绝」蒙混） ----------
    def test_compliant_repository_passes(self):
        self.assert_clean()

    # ---------- E1 冻结集清单 ----------
    def test_e1_missing_manifest_is_red(self):
        self.manifest_path.unlink()
        self.assert_rule("E1")

    def test_e1_invalid_manifest_is_red(self):
        d = engine.skeleton_manifest(); d["frozenHash"] = "0" * 64
        self.write_manifest(d)
        self.assert_rule("E1")

    # ---------- E2 判据自身有效性（独立哨兵） ----------
    def test_e2_degenerate_engine_is_caught(self):
        """把引擎改成恒绿（self_test 恒真 + verify_manifest 恒空）⇒ 门禁必须仍判红。

        这是本单测最关键的一条：若 E2 只信引擎自证，退化引擎会让所有 V 判据
        静默失效，而 CI 依旧全绿。
        """
        (self.eval_dir / "frozen_set.py").write_text(
            "def skeleton_manifest():\n"
            "    return {'status': 'skeleton', 'cases': [], 'collected': 0, 'frozenHash': 'x'}\n"
            "def verify_manifest(doc):\n"
            "    return []\n"
            "def self_test():\n"
            "    return True, []\n"
            "SLICE_DIMENSIONS = " + repr(tuple(EXPECTED_DIMS)) + "\n",
            encoding="utf-8",
        )
        self.assert_rule("E2")

    def test_e2_broken_self_test_is_caught(self):
        src = ENGINE_PATH.read_text(encoding="utf-8")
        src = src.replace("    return (not failures), failures", "    return (False, ['injected'])", 1)
        (self.eval_dir / "frozen_set.py").write_text(src, encoding="utf-8")
        self.assert_rule("E2")

    # ---------- E3 指标字典结构 ----------
    def test_e3_missing_metrics_file_is_red(self):
        self.metrics_path.unlink()
        self.assert_rule("E3")

    def test_e3_deleting_a_metric_is_red(self):
        d = _metrics_doc(); d["metrics"].pop()
        self.write_metrics(d)
        self.assert_rule("E3")

    def test_e3_missing_baseline_metric_is_red(self):
        d = _metrics_doc(); d["metrics"][0]["id"] = "renamed"
        self.write_metrics(d)
        self.assert_rule("E3")

    def test_e3_missing_field_is_red(self):
        d = _metrics_doc(); del d["metrics"][0]["definition"]
        self.write_metrics(d)
        self.assert_rule("E3")

    # ---------- E4 切片维度单一真源 ----------
    def test_e4_dimension_drift_is_red(self):
        d = _metrics_doc(); d["sliceDimensions"] = EXPECTED_DIMS[:-1]
        self.write_metrics(d)
        self.assert_rule("E4")

    def test_e4_metric_missing_slice_is_red(self):
        d = _metrics_doc(); d["metrics"][0]["slices"] = EXPECTED_DIMS[:-1]
        self.write_metrics(d)
        self.assert_rule("E4")

    # ---------- E5 状态诚实性 / 防文档抢跑 ----------
    def test_e5_skeleton_cannot_claim_wired(self):
        d = _metrics_doc()
        d["metrics"][0].update(status="wired", sourceSymbol=WIRED_SYMBOLS[0], blockingReason=None)
        self.write_metrics(d)
        self.assert_rule("E5")

    def test_e5_declared_requires_blocking_reason(self):
        d = _metrics_doc(); d["metrics"][0]["blockingReason"] = ""
        self.write_metrics(d)
        self.assert_rule("E5")

    def test_e5_declared_cannot_have_source_symbol(self):
        d = _metrics_doc(); d["metrics"][0]["sourceSymbol"] = WIRED_SYMBOLS[0]
        self.write_metrics(d)
        self.assert_rule("E5")

    # ---------- E6 wired 符号源码可达 ----------
    def test_e6_ghost_wired_symbol_is_red(self):
        d = _metrics_doc(); d["wiredObservability"][0]["sourceSymbol"] = "GhostMetrics"
        self.write_metrics(d)
        self.assert_rule("E6")

    def test_e6_deleted_source_declaration_is_red(self):
        """删掉源码里的埋点类 ⇒ 门禁红（对应 G0-1「删任一埋点 ⇒ 指标消失」）。"""
        for p in (self.root / "bone-ai").glob("Wired*.java"):
            p.unlink()
        self.write_sources([])
        self.assert_rule("E6")

    # ---------- E7 防假绿：扫描落空不得恒绿 ----------
    def test_e7_zero_java_sources_is_red(self):
        for p in self.root.rglob("*.java"):
            p.unlink()
        self.assert_rule("E7")

    # ---------- E8 wiredObservability ----------
    def test_e8_empty_wired_list_is_red(self):
        d = _metrics_doc(); d["wiredObservability"] = []
        self.write_metrics(d)
        self.assert_rule("E8")

    def test_e8_duplicate_ids_are_red(self):
        d = _metrics_doc(); d["wiredObservability"][1]["id"] = d["wiredObservability"][0]["id"]
        self.write_metrics(d)
        self.assert_rule("E8")

    def test_e8_non_wired_status_is_red(self):
        d = _metrics_doc(); d["wiredObservability"][0]["status"] = "declared"
        self.write_metrics(d)
        self.assert_rule("E8")


if __name__ == "__main__":
    unittest.main()
