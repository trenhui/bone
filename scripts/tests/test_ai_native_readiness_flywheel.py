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


if __name__ == "__main__":
    unittest.main(verbosity=2)
