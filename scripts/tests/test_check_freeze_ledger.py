"""表征测试：锁定 check-freeze-ledger.py 的 R4 到期 + 复审判据（G-2 到期机制落地）。

做法：importlib 按字符串路径加载门禁脚本（文件名含连字符，无法 import），
monkeypatch 其模块级 ``LEDGER`` / ``REPO`` / ``ARCHUNIT_STORE_DIRS`` 到临时目录，
构造最小化台账条目，断言 ``main()`` 的退出码与诊断文案。
**不改门禁脚本本身**——只验证其当前契约不被回归。

R4 契约：
  * ``classification: exception`` 必须带 ``expires``（ISO 日期）且未过期，否则 CI 硬失败；
  * ``debt`` / ``planned-adjustment`` 的 ``expires`` 过期仅告警，不阻断。

注：R4 依赖 PyYAML 解析 ``expires``（脚本主路径）；无 yaml 时退化为逐行解析不抽取
expires，故 R4 相关用例在无 yaml 环境跳过。

运行：``python3 scripts/tests/test_check_freeze_ledger.py -v``
"""

from __future__ import annotations

import contextlib
import importlib.util
import io
import re
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parent.parent

# 文件名含连字符，不能 import；且必须用 str 路径（Path 位置在 Py3.14 的
# SourceFileLoader.get_data 上会报 FileNotFoundError）。
_GATE_PATH = str(SCRIPTS_DIR / "check-freeze-ledger.py")
_SPEC = importlib.util.spec_from_file_location("check_freeze_ledger", _GATE_PATH)
gate = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(gate)


def _make_ledger(tmp: Path, body: str) -> None:
    (tmp / "freeze-ledger.yaml").write_text(
        "# test ledger\nledger:\n" + body, encoding="utf-8"
    )
    # 为每个 store: <rel> 创建非空 store 文件（落在 tmp 下，REPO 已 monkeypatch 到 tmp）
    for rel in re.findall(r"store:\s*(\S+)", body):
        f = tmp / rel
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_text("stored violation line\n", encoding="utf-8")


@unittest.skipUnless(
    gate._HAVE_YAML, "无 PyYAML 时脚本退化为逐行解析，不抽取 expires，R4 不可测"
)
class FreezeLedgerR4Test(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = Path(tempfile.mkdtemp(prefix="freeze-ledger-"))
        self._orig_ledger = gate.LEDGER
        self._orig_repo = gate.REPO
        self._orig_stores = gate.ARCHUNIT_STORE_DIRS
        gate.LEDGER = self.tmp / "freeze-ledger.yaml"
        gate.REPO = self.tmp
        gate.ARCHUNIT_STORE_DIRS = []  # 反向扫描不产出未登记条目

    def tearDown(self) -> None:
        gate.LEDGER = self._orig_ledger
        gate.REPO = self._orig_repo
        gate.ARCHUNIT_STORE_DIRS = self._orig_stores
        import shutil

        shutil.rmtree(self.tmp, ignore_errors=True)

    def _run(self, body: str) -> tuple[int, str]:
        _make_ledger(self.tmp, body)
        saved = sys.argv
        sys.argv = ["check-freeze-ledger.py", "--check"]
        buf = io.StringIO()
        try:
            with contextlib.redirect_stdout(buf):
                rc = gate.main()
        finally:
            sys.argv = saved
        return rc, buf.getvalue()

    # 一条 R1/R2 齐备的 debt 条目模板，仅 classification 可替换
    _VALID = (
        "  - rule: r1\n"
        "    module: modA\n"
        "    store: modA/archunit_store/aaaaaaaa-0000-0000-0000-000000000001\n"
        "    classification: {cls}\n"
        "    violations: 1\n"
        "    items:\n      - \"x -> y\"\n"
        "    owner: platform-arch\n"
        "    removalCondition: 改为合规实现并移除冻结\n"
        "    lastRenewal: null\n"
    )

    def test_debt_without_expires_passes(self) -> None:
        rc, _ = self._run(self._VALID.format(cls="debt"))
        self.assertEqual(rc, 0)

    def test_exception_without_expires_fails(self) -> None:
        rc, out = self._run(self._VALID.format(cls="exception"))
        self.assertEqual(rc, 1)
        self.assertIn("必须带 expires", out)

    def test_exception_expired_fails(self) -> None:
        body = self._VALID.format(cls="exception") + "    expires: 2020-01-01\n"
        rc, out = self._run(body)
        self.assertEqual(rc, 1)
        self.assertIn("例外已到期", out)

    def test_exception_valid_passes(self) -> None:
        body = self._VALID.format(cls="exception") + "    expires: 2099-01-01\n"
        rc, _ = self._run(body)
        self.assertEqual(rc, 0)

    def test_debt_expired_warns_not_fails(self) -> None:
        body = self._VALID.format(cls="debt") + "    expires: 2020-01-01\n"
        rc, out = self._run(body)
        self.assertEqual(rc, 0)
        self.assertIn("已逾复审日期", out)


if __name__ == "__main__":
    unittest.main()
