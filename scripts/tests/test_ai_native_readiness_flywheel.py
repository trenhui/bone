#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""AI 原生 L3 就绪度复核器：飞轮下游判据的表征测试（防假绿回归钉子）。

2026-11-06 循环复核发现：初版 FLYWHEEL_DOWNSTREAM 只要生产代码里出现
`AiConfidenceCalibration.` 就判「已接线」，于是一行
`import ...AiConfidenceCalibration.CalibrationSample;` 也能让它翻绿 ——
与「有类没接线 / 配了不生效」同型的假绿。修复后判据改为三要素真实调用：

  1. 校准实现类存在；
  2. 盲标样本源 `BlindLabelCalibrationSamples.from(` 被生产代码调用；
  3. 校准计算 `AiConfidenceCalibration.evaluate(`（或 ECE/Brier/challenger）被生产代码调用。

import 行、javadoc `{@link}`、注释、单测引用一律不算接线。

本测试用临时夹具注入变造后的 src/main 目录，不改动真仓库任何文件；每条判据
都有合规正例，防「永远拒绝」式实现蒙混过关；扫描根缺失必须 fail-closed。
"""
from __future__ import annotations

import contextlib
import importlib.util
import io
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
GATE = ROOT / "doc" / "ai-product" / "synapClaim" / "tools" / "_ai_native_readiness.py"

CALIB_IMPL = (
    "package com.bone.claim.domain.model.evaluation;\n"
    "public class AiConfidenceCalibration {\n"
    "  public static Object evaluate(Object a, Object b, Object c, int d, int e) { return null; }\n"
    "}\n"
)


def _load():
    spec = importlib.util.spec_from_file_location("ai_native_readiness_under_test", GATE)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


M = _load()

# 合规正例用的证据束：字段与 /ai-provider-probe 的真实响应同源，测试里一律
# 用 dict(...) 拷贝后再变造，避免跨用例共享可变对象。
GOOD_PROBE_EVIDENCE = {
    "httpStatus": 200, "model": "deepseek-chat", "provenance": "PROVIDER",
    "promptTokens": 12, "completionTokens": 8, "requestId": "chatcmpl-9f2a7c1d",
}


class FlywheelCriterionTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.java = Path(self.tmp.name) / "com" / "bone" / "claim"
        self.java.mkdir(parents=True)
        (self.java / "AiConfidenceCalibration.java").write_text(CALIB_IMPL, encoding="utf-8")
        self.addCleanup(self.tmp.cleanup)

    def _write_consumer(self, body: str) -> None:
        (self.java / "Consumer.java").write_text(body, encoding="utf-8")

    def _facts(self):
        with patch.dict(M.PATHS, {"java_main": Path(self.tmp.name)}):
            return M.load_facts()

    @staticmethod
    def _flywheel(facts):
        return next(g for g in M.evaluate(facts) if g["id"] == "FLYWHEEL_DOWNSTREAM")

    def test_import_only_is_not_wired(self):
        """本轮修复的核心：仅 import 数据类不得翻绿。"""
        self._write_consumer(
            "package x;\n"
            "import com.bone.claim.domain.model.evaluation.AiConfidenceCalibration.CalibrationSample;\n"
            "class Consumer { CalibrationSample s; }\n"
        )
        f = self._facts()
        self.assertFalse(f["calibration_compute_wired"])
        g = self._flywheel(f)
        self.assertEqual(g["status"], M.BLOCKED)
        self.assertEqual(g["reason"], "CALIBRATION_COMPUTE_NOT_WIRED")

    def test_javadoc_link_is_not_wired(self):
        """javadoc {@link} 与注释提及同样不算真实调用。"""
        self._write_consumer(
            "package x;\n"
            "/** 见 {@link AiConfidenceCalibration#evaluate} 的说明 */\n"
            "// AiConfidenceCalibration.evaluate(x)\n"
            "class Consumer {}\n"
        )
        self.assertFalse(self._facts()["calibration_compute_wired"])

    def test_real_evaluate_call_is_wired(self):
        """合规正例：真实方法调用必须被识别（防判据永远拒绝）。"""
        self._write_consumer(
            "package x;\n"
            "class Consumer {\n"
            "  void run() { AiConfidenceCalibration.evaluate(List.of(), a, b, 1, 1); }\n"
            "}\n"
        )
        self.assertTrue(self._facts()["calibration_compute_wired"])

    def test_sample_source_is_required(self):
        """有计算调用但缺盲标样本源，仍须按缺口精确归因。"""
        self._write_consumer(
            "package x;\n"
            "class Consumer { void run() { AiConfidenceCalibration.evaluate(List.of(), a, b, 1, 1); } }\n"
        )
        g = self._flywheel(self._facts())
        self.assertEqual(g["status"], M.BLOCKED)
        self.assertEqual(g["reason"], "SAMPLE_SOURCE_NOT_WIRED")

    def test_three_elements_all_present(self):
        """合规正例：三要素齐备才 SATISFIED。"""
        self._write_consumer(
            "package x;\n"
            "class Consumer {\n"
            "  void run() {\n"
            "    var m = BlindLabelCalibrationSamples.from(tasks, mapper);\n"
            "    var r = AiConfidenceCalibration.evaluate(m.samples(), a, b, 1, 1);\n"
            "  }\n"
            "}\n"
        )
        g = self._flywheel(self._facts())
        self.assertEqual(g["status"], M.SATISFIED)
        self.assertEqual(g["reason"], "ADMITTED")

    def test_missing_scan_root_fails_closed(self):
        """扫描根路径漂移时不得恒绿。"""
        with patch.dict(M.PATHS, {"java_main": Path(self.tmp.name) / "gone"}):
            f = M.load_facts()
        self.assertFalse(f["calibration_class_present"])
        self.assertEqual(self._flywheel(f)["status"], M.BLOCKED)

    def test_self_test_probes_all_flip(self):
        """复核器自带的负向探针必须全部有判别力。"""
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertTrue(M.self_test(M.load_facts()))


class RuntimeModelCoverageTest(unittest.TestCase):
    """RUNTIME_MODEL_COVERAGE 四条件：provider 配置 + 探活 REACHABLE +
    证据束自洽 + 运行期参与率非零。

    防三类局部假绿：① 只刷参与率（provider 未配）；② AI技术方案 §11.7 的
    「配了 ≠ 通了」——provider 已配、参与率非零，但探活失败或压根没测过；
    ③ 第 11 轮 GAP 的「三要素同源可伪造」——环境变量 / 裸判词 / 参与率都能
    手写，故还须同一次真实调用返回的证据束自洽。
    """

    @staticmethod
    def _coverage(facts):
        return next(g for g in M.evaluate(facts) if g["id"] == "RUNTIME_MODEL_COVERAGE")

    @staticmethod
    def _mutate(**kw):
        f = M.load_facts()
        return dict(f, **kw)

    @staticmethod
    def _runtime(**overrides):
        return {"nodesScanned": 88, "aiParticipated": 44,
                "participationRate": 0.5, "capabilityMissing": 0,
                "probeVerdict": "REACHABLE",
                "probeEvidence": dict(GOOD_PROBE_EVIDENCE), **overrides}

    def _configured(self, **overrides):
        return self._mutate(provider_env="http://localhost:8000/v1",
                            e2e_runtime=self._runtime(**overrides))

    def test_forged_participation_without_provider_stays_blocked(self):
        """连探活判词与参与率一起伪造、provider 未配：必须仍 BLOCKED 且归因精确。"""
        f = self._mutate(e2e_runtime=self._runtime())
        f["provider_env"] = ""
        g = self._coverage(f)
        self.assertEqual(g["status"], M.BLOCKED)
        self.assertEqual(g["reason"], "PROVIDER_NOT_CONFIGURED")
        self.assertIn("providerConfigured=False", g["evidence"])

    def test_four_conditions_all_present_is_satisfied(self):
        """合规正例：四条件齐备，判据须可达 SATISFIED（防永远拒绝）。"""
        g = self._coverage(self._configured())
        self.assertEqual(g["status"], M.SATISFIED)
        self.assertEqual(g["reason"], "ADMITTED")
        self.assertIn("providerConfigured=True", g["evidence"])
        self.assertIn("probeVerdict=REACHABLE", g["evidence"])
        self.assertIn("probeEvidence=ADMITTED", g["evidence"])

    def test_probe_not_reachable_with_provider_stays_blocked(self):
        """§11.7「配了 ≠ 通了」：探活非 REACHABLE 时，参与率非零也不得翻绿。"""
        for verdict in ("NOT_CONFIGURED", "MOCK_NOT_ALLOWED", "OUTPUT_CONTRACT_VIOLATION",
                        "PROVIDER_TIMEOUT", "GATEWAY_ERROR"):
            with self.subTest(verdict=verdict):
                f = self._mutate(provider_env="http://localhost:8000/v1",
                                 e2e_runtime=self._runtime(probeVerdict=verdict))
                g = self._coverage(f)
                self.assertEqual(g["status"], M.BLOCKED)
                self.assertEqual(g["reason"], "PROBE_NOT_REACHABLE")
                self.assertIn(f"probeVerdict={verdict}", g["evidence"])

    def test_missing_probe_verdict_fails_closed(self):
        """产物无探活判词（未测过）不得当成 REACHABLE。"""
        rt = self._runtime()
        rt.pop("probeVerdict")
        f = self._mutate(provider_env="http://localhost:8000/v1", e2e_runtime=rt)
        g = self._coverage(f)
        self.assertEqual(g["status"], M.BLOCKED)
        self.assertEqual(g["reason"], "PROBE_NOT_REACHABLE")
        self.assertIn("probeVerdict=MISSING", g["evidence"])

    def test_provider_configured_but_zero_participation_is_blocked(self):
        """provider 已配、探活可达，但参与率为零，仍按 MODEL_PARTICIPATION_ZERO 阻断。"""
        f = self._mutate(provider_env="http://localhost:8000/v1",
                         e2e_runtime=self._runtime(aiParticipated=0, participationRate=0))
        g = self._coverage(f)
        self.assertEqual(g["status"], M.BLOCKED)
        self.assertEqual(g["reason"], "MODEL_PARTICIPATION_ZERO")

    def test_missing_e2e_evidence_is_runtime_only(self):
        """无 E2E 产物时优先级最高，不得因 provider 条件降级为 BLOCKED。"""
        f = self._mutate(e2e_runtime=None)
        g = self._coverage(f)
        self.assertEqual(g["status"], M.RUNTIME_ONLY)
        self.assertEqual(g["reason"], "RUNTIME_EVIDENCE_REQUIRED")

    def test_missing_probe_evidence_stays_blocked(self):
        """三要素全伪造但无证据束：GAP 的核心假绿通道必须堵住。"""
        g = self._coverage(self._configured(probeEvidence=None))
        self.assertEqual(g["status"], M.BLOCKED)
        self.assertEqual(g["reason"], "PROBE_EVIDENCE_REQUIRED")

    def test_zero_completion_tokens_stays_blocked(self):
        """空返回不算跑过模型：completionTokens=0 的「自洽伪造」须判红。"""
        g = self._coverage(self._configured(
            probeEvidence=dict(GOOD_PROBE_EVIDENCE, completionTokens=0)))
        self.assertEqual(g["status"], M.BLOCKED)
        self.assertEqual(g["reason"], "PROBE_EVIDENCE_EMPTY_COMPLETION")

    def test_stub_provenance_stays_blocked(self):
        """替身响应不得计入：provenance != PROVIDER 须判红。"""
        g = self._coverage(self._configured(
            probeEvidence=dict(GOOD_PROBE_EVIDENCE, provenance="STUB")))
        self.assertEqual(g["status"], M.BLOCKED)
        self.assertEqual(g["reason"], "PROBE_EVIDENCE_STUB_NOT_ALLOWED")

    def test_placeholder_request_id_stays_blocked(self):
        """占位 requestId 不得充当供应商侧调用凭证。"""
        g = self._coverage(self._configured(
            probeEvidence=dict(GOOD_PROBE_EVIDENCE, requestId="unknown")))
        self.assertEqual(g["status"], M.BLOCKED)
        self.assertEqual(g["reason"], "PROBE_EVIDENCE_REQUEST_ID_INVALID")

    def test_http_not_ok_stays_blocked(self):
        """非 200 的探活响应不得与 REACHABLE 判词并存。"""
        g = self._coverage(self._configured(
            probeEvidence=dict(GOOD_PROBE_EVIDENCE, httpStatus=502)))
        self.assertEqual(g["status"], M.BLOCKED)
        self.assertEqual(g["reason"], "PROBE_EVIDENCE_HTTP_NOT_OK")

    def _degraded_self_test(self, old: str, new: str) -> bool:
        """变造源码在独立命名空间执行，返回其 self_test 是否仍通过。"""
        src = GATE.read_text(encoding="utf-8")
        degraded = src.replace(old, new)
        self.assertNotEqual(degraded, src)
        ns: dict = {"__name__": "readiness_degraded", "__file__": str(GATE)}
        exec(compile(degraded, str(GATE), "exec"), ns)  # noqa: S102
        with contextlib.redirect_stdout(io.StringIO()):
            return ns["self_test"](ns["load_facts"]())

    _ALL = "all_conditions = provider_configured and coverage and probe_reachable and evidence_ok"

    def test_dropping_provider_condition_is_detectable(self):
        """摘除 provider 条件时，--self-test 的负向探针必须判红（可证伪）。"""
        bad = self._degraded_self_test(
            self._ALL, "all_conditions = coverage and probe_reachable and evidence_ok")
        self.assertFalse(bad, "摘除 provider 条件后，防假绿负向探针必须判红")

    def test_dropping_probe_condition_is_detectable(self):
        """摘除探活条件时，--self-test 的负向探针必须判红（可证伪）。"""
        bad = self._degraded_self_test(
            self._ALL, "all_conditions = provider_configured and coverage and evidence_ok")
        self.assertFalse(bad, "摘除探活条件后，防假绿负向探针必须判红")

    def test_dropping_evidence_condition_is_detectable(self):
        """摘除证据束条件时，--self-test 的伪造鲁棒性探针必须判红（GAP 验收 4）。"""
        bad = self._degraded_self_test(
            self._ALL, "all_conditions = provider_configured and coverage and probe_reachable")
        self.assertFalse(bad, "摘除证据束条件后，伪造鲁棒性探针必须判红")

    def test_evidence_validator_fail_closed_is_detectable(self):
        """把「无证据束」分支退化成恒真时，伪造鲁棒性探针必须判红。"""
        bad = self._degraded_self_test(
            '    return False, "PROBE_EVIDENCE_REQUIRED"',
            '    return True, "ADMITTED"')
        self.assertFalse(bad, "证据束缺失分支退化后，--self-test 必须判红")


class MaturityLevelTest(unittest.TestCase):
    """成熟度档位描述运行态：配置态全齐但模型从未上场，不得判 L1/L2。

    第 11 轮修复的假绿面：原 `current_level()` 只看 provider 与 parse_mode 两个
    配置态判据，「配好环境变量 + 打开 llm」即可判 L2，哪怕 E2E 证明模型没上场。
    """

    @staticmethod
    def _rt(**overrides):
        return {"nodesScanned": 88, "aiParticipated": 44,
                "participationRate": 0.5, "capabilityMissing": 0,
                "probeVerdict": "REACHABLE",
                "probeEvidence": dict(GOOD_PROBE_EVIDENCE), **overrides}

    @classmethod
    def _facts(cls, **overrides):
        f = M.load_facts()
        f.update({"provider_env": "http://localhost:8000/v1",
                  "parse_mode_env": "llm", "e2e_runtime": cls._rt()})
        f.update(overrides)
        return f

    def _level(self, **overrides):
        return M.current_level(M.evaluate(self._facts(**overrides)))

    def test_config_only_with_failed_probe_cannot_reach_l2(self):
        """provider + llm 已配，但探活非 REACHABLE：回落 L0 且带原因码。"""
        level = self._level(e2e_runtime=self._rt(probeVerdict="GATEWAY_ERROR"))
        self.assertTrue(level.startswith("L0"), level)
        self.assertIn("PROBE_NOT_REACHABLE", level)

    def test_no_runtime_evidence_cannot_reach_l2(self):
        """provider + llm 已配，但根本没有 E2E 产物：回落 L0。"""
        level = self._level(e2e_runtime=None)
        self.assertTrue(level.startswith("L0"), level)
        self.assertIn("RUNTIME_EVIDENCE_REQUIRED", level)

    def test_zero_participation_cannot_reach_l2(self):
        """探活可达但模型从未在案件链路上场：回落 L0。"""
        level = self._level(e2e_runtime=self._rt(aiParticipated=0, participationRate=0))
        self.assertTrue(level.startswith("L0"), level)
        self.assertIn("MODEL_PARTICIPATION_ZERO", level)

    def test_forged_three_elements_without_evidence_is_l0(self):
        """裸判词 + 参与率 + provider 三样齐全但缺证据束：档位仍须 L0。"""
        level = self._level(e2e_runtime=self._rt(probeEvidence=None))
        self.assertTrue(level.startswith("L0"), level)
        self.assertIn("PROBE_EVIDENCE_REQUIRED", level)

    def test_runtime_evidence_but_perception_not_driven_is_l1(self):
        """四条件齐全但感知仍走确定性文本层：AI 辅助、可摘除，判 L1。"""
        level = self._level(parse_mode_env="text")
        self.assertTrue(level.startswith("L1"), level)

    def test_full_evidence_reaches_l2(self):
        """合规正例：四条件 + 感知模型驱动，档位须可达 L2（防永远拒绝）。"""
        level = self._level()
        self.assertTrue(level.startswith("L2"), level)

    def test_provider_missing_is_l0_without_runtime_reason(self):
        """未配 provider 时优先报全链路降级，不把运行期原因码混进档位。"""
        level = self._level(provider_env="")
        self.assertIn("AI 全链路降级", level)
        self.assertNotIn("配置态就绪", level)

    def test_degraded_level_is_detectable(self):
        """把档位退化成只看配置态时，本类断言必须能抓到假绿（可证伪）。"""
        src = GATE.read_text(encoding="utf-8")
        degraded = src.replace('if coverage.get("status") != SATISFIED:', "if False:")
        self.assertNotEqual(degraded, src)
        ns: dict = {"__name__": "readiness_degraded", "__file__": str(GATE)}
        exec(compile(degraded, str(GATE), "exec"), ns)  # noqa: S102
        facts = self._facts(e2e_runtime=self._rt(probeVerdict="GATEWAY_ERROR"))
        self.assertTrue(ns["current_level"](ns["evaluate"](facts)).startswith("L2"),
                        "退化实现应判 L2，否则本类用例没有判别力")

    def test_degraded_level_makes_self_test_fail(self):
        """把档位退化成只看配置态时，--self-test 的新增档位探针必须判红。"""
        src = GATE.read_text(encoding="utf-8")
        degraded = src.replace('if coverage.get("status") != SATISFIED:', "if False:")
        self.assertNotEqual(degraded, src)
        ns: dict = {"__name__": "readiness_degraded", "__file__": str(GATE)}
        exec(compile(degraded, str(GATE), "exec"), ns)  # noqa: S102
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertFalse(ns["self_test"](ns["load_facts"]()),
                             "退化实现下 --self-test 必须判红，否则档位探针恒真")


if __name__ == "__main__":
    unittest.main(verbosity=2)
