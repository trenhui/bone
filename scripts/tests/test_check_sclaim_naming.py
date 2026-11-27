#!/usr/bin/env python3
"""check-sclaim-naming.py 的 R3 单测（G1–G5 命名规范机检）。

新增门禁必须有单测（scripts/README.md §9 / R3CheckerTestRatchet）：判据回归时
本地与 CI 都会安静放行。本测试 importlib 加载真实脚本，不复制判据：
  1. 真实仓库当前形态合规（check(ROOT) 为空）——证明门禁不会误伤正常代码；
  2. 脚本自带 self_test() 的全套负向探针通过——证明每条判据都能变红；
  3. 独立构造一个 G4 负向变异（/api/sclaim）确实判红——防止 self_test 恒绿。
"""

import importlib.util
import os
import tempfile
import unittest
from pathlib import Path

SCRIPTS = Path(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SCRIPT = SCRIPTS / "check-sclaim-naming.py"
_spec = importlib.util.spec_from_file_location("check_sclaim_naming", SCRIPT)
mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(mod)


class SclaimNamingTest(unittest.TestCase):
    def test_real_tree_is_compliant(self):
        """真实仓库不应触发任何命名违规（否则门禁会挡住无关提交）。"""
        self.assertEqual(mod.check(mod.ROOT), [])

    def test_self_test_negative_probes_all_red(self):
        """脚本自带负向探针必须全部判红——任一恒绿即门禁不可信。"""
        self.assertEqual(mod.self_test(), 0)

    def test_custom_g4_mutation_is_detected(self):
        """独立构造 /api/sclaim 违规（非 self_test 用例），确认 G4 判据能变红。"""
        with tempfile.TemporaryDirectory(prefix="sclaim-g4-") as td:
            root = Path(td)
            mod._build_fixture(root)
            svc = root / "bone-frontend/apps/bone-claim-app/src/services/leak.ts"
            svc.write_text("fetch('/api/sclaim/claims')", encoding="utf-8")
            errors = mod.check(root)
            self.assertTrue(
                any(e.startswith("G4:") for e in errors),
                f"G4 未判红：{errors}",
            )


if __name__ == "__main__":
    unittest.main()
