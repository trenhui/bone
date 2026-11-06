#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""飞轮下游强判据机检（doc/ai-product/synapClaim/tools/_check_ai_native_calibration.py）
自身的表征测试。

为什么需要它：该机检是库外文档族自带的第 6 道脚本，治理规约的「跑全套」是人工命令，
不属于「每次提交都拦」。接进 [42/59] 门禁自身单测后，判据回归（被改成恒绿/恒红、
锚点被删、扫描失效）会在 pre-commit 被拦，而不是等某个人记得手动跑。

设计要点（沿用本仓假绿教训）：
  1. 每条判据精确归因：断言违规前缀命中 C1/C2/C3/C4/C5，不接受「返回非空就算抓到」。
  2. 每条判据都有合规正例，防「永远拒绝」式实现蒙混过关。
  3. 用临时夹具注入变造后的目录，不改动真仓库任何文件。
  4. 扫描失效闸：实现类/单测整个目录缺失时必须判红，防路径漂移后恒绿。
"""
from __future__ import annotations

import importlib.util
import shutil
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GATE_PATH = (
    ROOT / "doc/ai-product/synapClaim/tools/_check_ai_native_calibration.py"
)
MAIN_REL = Path("bone-apps/synap-claim/src/main/java/com/bone/claim/domain/model/evaluation")
TEST_REL = Path("bone-apps/synap-claim/src/test/java/com/bone/claim/domain/model/evaluation")


def _load():
    spec = importlib.util.spec_from_file_location("bone_calib_gate_under_test", GATE_PATH)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


gate = _load()


class CalibrationGateFixture(unittest.TestCase):
    """每个用例在临时目录里构造一份完整合规快照，再只变造被考察的那一处。"""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        (self.root / MAIN_REL).mkdir(parents=True)
        (self.root / TEST_REL).mkdir(parents=True)
        for p in gate.MAIN.glob("*.java"):
            shutil.copy(p, self.root / MAIN_REL / p.name)
        for p in gate.TEST.glob("*.java"):
            shutil.copy(p, self.root / TEST_REL / p.name)

    def tearDown(self):
        self.tmp.cleanup()

    def _violations(self):
        return gate.violations(self.root)

    def _prefixes(self):
        return {v.split()[0] for v in self._violations()}

    def test_合规快照必须零违规(self):
        self.assertEqual(self._violations(), [])

    def test_C1_实现类缺失必须判红且精确归因(self):
        (self.root / MAIN_REL / "AiModelCard.java").unlink()
        self.assertIn("C1", self._prefixes())
        self.assertTrue(any("实现类缺失" in v for v in self._violations()))

    def test_C1_实现符号被掏空必须判红(self):
        (self.root / MAIN_REL / "AiConfidenceCalibration.java").write_text(
            "package com.bone.claim.domain.model.evaluation;\n"
            "public final class AiConfidenceCalibration {}\n",
            encoding="utf-8",
        )
        self.assertIn("C1", self._prefixes())
        self.assertTrue(any("判据符号缺失" in v for v in self._violations()))

    def test_C2_单测缺失必须判红(self):
        (self.root / TEST_REL / "AiModelCardTest.java").unlink()
        self.assertIn("C2", self._prefixes())

    def test_C2_单测用例数被砍到下界以下必须判红(self):
        path = self.root / TEST_REL / "AiModelCardTest.java"
        text = path.read_text(encoding="utf-8")
        # 只留 1 个 @Test（远低于下界 6）；只砍 1 个仍应合规，另由恒红用例守住。
        first = text.find("@Test")
        head, tail = text[: first + len("@Test")], text[first + len("@Test") :]
        path.write_text(head + tail.replace("@Test", "@Ignored"), encoding="utf-8")
        self.assertIn("C2", self._prefixes())
        self.assertTrue(any("用例数" in v for v in self._violations()))

    def test_C3_防假绿锚点被删必须判红(self):
        path = self.root / MAIN_REL / "AiConfidenceCalibration.java"
        text = path.read_text(encoding="utf-8")
        path.write_text(text.replace("UNTRUSTED_EVIDENCE", "TRUSTED_ANYWAY"), encoding="utf-8")
        self.assertIn("C3", self._prefixes())

    def test_C4_反向锚点被删必须判红(self):
        path = self.root / TEST_REL / "AiConfidenceCalibrationTest.java"
        text = path.read_text(encoding="utf-8")
        path.write_text(text.replace("WEAK_FINAL_STATUS", "GOLD_LABEL"), encoding="utf-8")
        self.assertIn("C4", self._prefixes())

    def test_C5_纯领域内核引入Spring依赖必须判红(self):
        path = self.root / MAIN_REL / "AiConfidenceCalibration.java"
        text = path.read_text(encoding="utf-8")
        path.write_text(
            "import org.springframework.stereotype.Service;\n" + text,
            encoding="utf-8",
        )
        self.assertIn("C5", self._prefixes())

    def test_扫描失效闸_整个实现目录为空必须判红(self):
        for p in (self.root / MAIN_REL).glob("*.java"):
            p.unlink()
        prefixes = self._prefixes()
        self.assertIn("C1", prefixes)
        self.assertIn("C3", prefixes)

    def test_门禁不得恒红_合规快照必须能过(self):
        # 与上面各负向用例互补：若实现被改成「一律返回违规」，本例转红。
        self.assertFalse(self._violations())

    def test_自检负向探针自身有效(self):
        self.assertTrue(gate.self_test())


if __name__ == "__main__":
    unittest.main()
