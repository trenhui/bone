#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""check-deterministic-cache.py 迁移到 gatekit 后的 TDD 护体测试。

文件名刻意带 _gatekit 后缀：与并行会话正在推进的 tests/test_check_*.py 战役区分，
避免同名覆盖；验证逻辑与战役用例同构（真实仓库绿 / self-test 有牙 / 注入判红 /
清洁树放行）。
"""

import argparse
import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]  # scripts/tests -> scripts
sys.path.insert(0, str(REPO / "lib"))       # 让 gatekit 作为顶层包可导入

GATE_PATH = REPO / "check-deterministic-cache.py"


def _load_gate():
    spec = importlib.util.spec_from_file_location("dc_gate_migrated", str(GATE_PATH))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


class GateModuleTest(unittest.TestCase):
    def setUp(self):
        self.gate = _load_gate()

    def test_real_repo_green(self):
        """真实仓库当前无违规 => main() 返回 0（与迁移前基线一致）。"""
        self.assertEqual(self.gate.main(), 0)

    def test_main_isolated_from_process_argv(self):
        """回归护栏：进程 argv 含 unittest 参数时 main() 仍返回 0，不得 SystemExit(2)。

        旧实现 main() -> run() 默认读 sys.argv[1:]，门禁自身单测在 discover 模式下
        会把 'discover -s scripts/tests' 喂给 argparse，解析失败直接退出 2，
        表现为 [42/59] 门禁自身单测 errors=1。本例在旧实现下必红。
        """
        original = sys.argv
        sys.argv = ["unittest", "discover", "-s", "scripts/tests", "-v",
                    "--not-a-gate-flag"]
        try:
            self.assertEqual(self.gate.main(), 0)
        finally:
            sys.argv = original

    def test_self_test_has_teeth(self):
        """--self-test 用独立 Report 做阳性/阴性对照，结束时 ok=True => exit 0。"""
        r = self.gate.Report()
        self.gate.self_test(r)
        self.assertTrue(r.ok, f"self-test 应全绿：{r._failures}")

    def test_injected_violation_detected(self):
        """注入 R4 死配置 => judge 判红（证明迁移未削弱判定）。"""
        d = Path(tempfile.mkdtemp())
        base = ("bone-engine/bone-metadata-engine/"
                "bone-metadata-engine-runtime/src/main/java/com/bone/metadata/engine/runtime")
        rt = d / base
        rt.mkdir(parents=True)
        (rt / "MetadataEngine.java").write_text(
            "class M { int maxRetries = 5; }\n", encoding="utf-8")
        r = self.gate.Report()
        self.gate.judge(r, argparse.Namespace(), repo=d, runtime_main=rt)
        self.assertFalse(r.ok, "注入 maxRetries 未被判红 => 判定削弱")
        self.assertIn("maxRetries", r._failures[0])
        self.assertIn("R4 死配置 maxRetries", r._failures[0])

    def test_clean_tree_passes(self):
        """只含允许的确定性键缓存 => 放行（无新增误报）。"""
        d = Path(tempfile.mkdtemp())
        base = ("bone-engine/bone-metadata-engine/"
                "bone-metadata-engine-runtime/src/main/java/com/bone/metadata/engine/runtime")
        rt = d / base
        rt.mkdir(parents=True)
        (rt / "MetadataEngine.java").write_text(
            "class M { private final Map<String,Object> propertyAccessorCache = new HashMap<>(); }\n",
            encoding="utf-8")
        r = self.gate.Report()
        self.gate.judge(r, argparse.Namespace(), repo=d, runtime_main=rt)
        self.assertTrue(r.ok, f"清洁树被误报：{r._failures}")

    def test_comment_lines_not_flagged(self):
        """注释行（// · /* · * 开头）不触发匹配。"""
        d = Path(tempfile.mkdtemp())
        base = ("bone-engine/bone-metadata-engine/"
                "bone-metadata-engine-runtime/src/main/java/com/bone/metadata/engine/runtime")
        rt = d / base
        rt.mkdir(parents=True)
        (rt / "MetadataEngine.java").write_text(
            "// 历史：曾用 maxRetries 但已摘除\nclass M {}\n", encoding="utf-8")
        r = self.gate.Report()
        self.gate.judge(r, argparse.Namespace(), repo=d, runtime_main=rt)
        self.assertTrue(r.ok, f"注释行误触发：{r._failures}")


if __name__ == "__main__":
    unittest.main(verbosity=2)
