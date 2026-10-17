"""表征测试：锁定 check-archunit-freeze-baseline.py 的判据（R1/R2/R3）。

做法：门禁脚本文件名含连字符，无法用 import 按名导入；故用 importlib 按
**字符串路径**加载为模块（Python 3.14 的 SourceFileLoader 对 Path 位置在
get_data 上异常，必须用 str），再 monkeypatch 其模块级 ``REPO`` 与 ``tracked_files``，
在临时目录构造最小化模块，断言 ``main()`` 的退出码与诊断文案。
**不改门禁脚本本身**（避免动 CI 门禁逻辑）——只验证其当前契约不被回归。

运行：``python3 scripts/tests/test_check_archunit_freeze_baseline.py -v``
"""

from __future__ import annotations

import contextlib
import importlib.util
import io
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parent.parent

# 文件名含连字符，不能 import；且必须用 str 路径（Path 位置在 Py3.14 的
# SourceFileLoader.get_data 上会报 FileNotFoundError）。
_GATE_PATH = str(SCRIPTS_DIR / "check-archunit-freeze-baseline.py")
_SPEC = importlib.util.spec_from_file_location("check_archunit_freeze_baseline", _GATE_PATH)
gate = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(gate)


class FreezeBaselineGateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.root = Path(tempfile.mkdtemp(prefix="freeze-gate-"))
        self._orig_repo = gate.REPO
        self._orig_tracked = gate.tracked_files
        gate.REPO = self.root
        # 默认：磁盘上的 properties 都被视为已跟踪；各用例按需覆盖
        self.tracked_override: set[str] = set()
        gate.tracked_files = lambda: self.tracked_override

    def tearDown(self) -> None:
        gate.REPO = self._orig_repo
        gate.tracked_files = self._orig_tracked
        import shutil

        shutil.rmtree(self.root, ignore_errors=True)

    # ---- 构造辅助 ----

    def _make_module(self, name: str) -> Path:
        mod = self.root / name
        mod.mkdir(parents=True, exist_ok=True)
        (mod / "pom.xml").write_text("<project/>", encoding="utf-8")
        tdir = mod / "src" / "test" / "java" / "com" / "x"
        tdir.mkdir(parents=True)
        (tdir / "FooArchitectureTest.java").write_text(
            "class FooArchitectureTest {}", encoding="utf-8"
        )
        return mod

    def _write_props(self, mod: Path, content: str | None) -> None:
        if content is None:
            return
        rdir = mod / "src" / "test" / "resources"
        rdir.mkdir(parents=True, exist_ok=True)
        (rdir / "archunit.properties").write_text(content, encoding="utf-8")

    def _run(self) -> tuple[int, str]:
        saved = sys.argv
        sys.argv = ["check-archunit-freeze-baseline.py", "--check"]
        buf = io.StringIO()
        try:
            with contextlib.redirect_stdout(buf):
                rc = gate.main()
        finally:
            sys.argv = saved
        return rc, buf.getvalue()

    # ---- read_props 纯函数 ----

    def test_read_props_parses_kv(self) -> None:
        f = self.root / "p.properties"
        f.write_text(
            "freeze.store.default.allowStoreUpdate=false\n"
            "freeze.store.default.allowStoreCreation=false\n",
            encoding="utf-8",
        )
        d = gate.read_props(f)
        self.assertEqual(d.get("freeze.store.default.allowStoreUpdate"), "false")
        self.assertEqual(d.get("freeze.store.default.allowStoreCreation"), "false")

    def test_read_props_allow_update_true(self) -> None:
        f = self.root / "p.properties"
        f.write_text("freeze.store.default.allowStoreUpdate=true\n", encoding="utf-8")
        d = gate.read_props(f)
        self.assertEqual(d.get("freeze.store.default.allowStoreUpdate"), "true")

    # ---- main() 端到端判据 ----

    def test_missing_props_fails(self) -> None:
        mod = self._make_module("modA")
        self._write_props(mod, None)  # 故意不创建
        rc, out = self._run()
        self.assertEqual(rc, 1)
        self.assertIn("不存在", out)

    def test_present_but_untracked_fails(self) -> None:
        mod = self._make_module("modA")
        self._write_props(
            mod,
            "freeze.store.default.allowStoreUpdate=false\n"
            "freeze.store.default.allowStoreCreation=false\n",
        )
        self.tracked_override = set()  # 文件在磁盘但未被 git 跟踪
        rc, out = self._run()
        self.assertEqual(rc, 1)
        self.assertIn("未被 git 跟踪", out)

    def test_present_and_tracked_passes(self) -> None:
        mod = self._make_module("modA")
        self._write_props(
            mod,
            "freeze.store.default.allowStoreUpdate=false\n"
            "freeze.store.default.allowStoreCreation=false\n",
        )
        self.tracked_override = {"modA/src/test/resources/archunit.properties"}
        rc, out = self._run()
        self.assertEqual(rc, 0)
        self.assertIn("齐备", out)

    def test_allow_update_true_fails(self) -> None:
        mod = self._make_module("modA")
        self._write_props(
            mod,
            "freeze.store.default.allowStoreUpdate=true\n"
            "freeze.store.default.allowStoreCreation=false\n",
        )
        self.tracked_override = {"modA/src/test/resources/archunit.properties"}
        rc, out = self._run()
        self.assertEqual(rc, 1)
        self.assertIn("allowStoreUpdate", out)


if __name__ == "__main__":
    unittest.main()
