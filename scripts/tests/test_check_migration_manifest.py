"""表征测试：锁定 check-migration-manifest.py 的八条判据（M1–M8）。

门禁脚本文件名含连字符，不能用 import 按名导入，故用 importlib 按字符串路径加载，
再用 `--migration-dir/--manifest` 指向临时目录构造最小化场景，断言 main() 退出码。

为什么要测门禁自身
------------------
这份清单的全部价值在于「新增迁移无法绕过它」。若判据被改坏（比如 M1 的比对方向写反，
或 M6 只比组号不比文件集合），门禁会一路绿着，而顺序与重号重新回到无人知晓的状态——
这正是它要治的病。故每个用例都是一个「构造该缺陷 ⇒ 必须退 1」的负向场景。

运行：``python3 scripts/tests/test_check_migration_manifest.py -v``
"""

from __future__ import annotations

import contextlib
import importlib.util
import io
import json
import os
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parent.parent
_GATE_PATH = str(SCRIPTS_DIR / "check-migration-manifest.py")
_SPEC = importlib.util.spec_from_file_location("check_migration_manifest", _GATE_PATH)
gate = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(gate)

DDL_SQL = "CREATE TABLE t (id BIGINT);\n"
DML_SQL = "UPDATE t SET a = 1;\n"
READ_SQL = "SELECT 1;\n"


class MigrationManifestGateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.root = Path(tempfile.mkdtemp(prefix="mig-manifest-"))
        self.mig = self.root / "migration"
        self.mig.mkdir()
        self.manifest = self.root / "migration-manifest.json"

    def tearDown(self) -> None:
        shutil.rmtree(self.root, ignore_errors=True)

    # ---- 场景构造 ----
    def write(self, name: str, content: str = DDL_SQL) -> None:
        (self.mig / name).write_text(content, encoding="utf-8")

    def manifest_of(self, entries, conflicts=None, extra=None) -> dict:
        doc = {
            "entries": entries,
            "executableCount": sum(1 for e in entries if isinstance(e.get("order"), int)),
            "ordinalConflicts": conflicts or [],
        }
        doc.update(extra or {})
        self.manifest.write_text(json.dumps(doc, ensure_ascii=False), encoding="utf-8")
        return doc

    def run_gate(self) -> int:
        argv = ["check-migration-manifest.py", "--migration-dir", str(self.mig), "--manifest", str(self.manifest)]
        buf = io.StringIO()
        old = sys.argv
        try:
            sys.argv = argv
            with contextlib.redirect_stdout(buf), contextlib.redirect_stderr(buf):
                return gate.main()
        finally:
            sys.argv = old

    def base(self):
        """两个合规脚本 + 一个文档附件的合规最小集。"""
        self.write("0001_a.sql", DDL_SQL)
        self.write("0002_b.sql", DML_SQL)
        self.write("0002_README.md", "# b\n")
        return [
            {"order": 1, "file": "0001_a.sql", "kind": "ddl", "summary": "建表 t（id 主键）"},
            {"order": 2, "file": "0002_b.sql", "kind": "dml", "summary": "回填字段 a 的默认值"},
            {"order": None, "file": "0002_README.md", "kind": "doc", "summary": "0002 的执行路线说明文档"},
        ]

    # ---- 用例 ----
    def test_合规最小集应通过(self):
        self.manifest_of(self.base(), conflicts=[
            {"ordinal": "0002", "files": ["0002_README.md", "0002_b.sql"], "verdict": "同变更附件：README 为说明文档"}
        ])
        self.assertEqual(self.run_gate(), 0)

    def test_M1_存在即登记(self):
        entries = self.base()
        self.write("0003_c.sql", DDL_SQL)  # 磁盘多出未登记脚本
        self.manifest_of(entries, conflicts=[{"ordinal": "0002", "files": ["0002_README.md", "0002_b.sql"],
                                              "verdict": "同变更附件：README 为说明文档"}])
        self.assertEqual(self.run_gate(), 1)

    def test_M2_登记即存在(self):
        entries = self.base()
        entries.append({"order": 3, "file": "0099_gone.sql", "kind": "ddl", "summary": "已被删除的历史脚本条目"})
        self.manifest_of(entries, conflicts=[{"ordinal": "0002", "files": ["0002_README.md", "0002_b.sql"],
                                              "verdict": "同变更附件：README 为说明文档"}])
        self.assertEqual(self.run_gate(), 1)

    def test_M3_order重复(self):
        entries = self.base()
        entries[1]["order"] = 1
        self.manifest_of(entries, conflicts=[{"ordinal": "0002", "files": ["0002_README.md", "0002_b.sql"],
                                              "verdict": "同变更附件：README 为说明文档"}])
        self.assertEqual(self.run_gate(), 1)

    def test_M4_order与词典序分叉未声明(self):
        entries = self.base()
        entries[0]["order"], entries[1]["order"] = 2, 1  # 逆序但不声明理由
        self.manifest_of(entries, conflicts=[{"ordinal": "0002", "files": ["0002_README.md", "0002_b.sql"],
                                              "verdict": "同变更附件：README 为说明文档"}])
        self.assertEqual(self.run_gate(), 1)

    def test_M4_声明理由后放行(self):
        entries = self.base()
        entries[0]["order"], entries[1]["order"] = 2, 1
        self.manifest_of(entries, conflicts=[{"ordinal": "0002", "files": ["0002_README.md", "0002_b.sql"],
                                              "verdict": "同变更附件：README 为说明文档"}],
                         extra={"outOfLexOrder": [
                             {"file": "0001_a.sql", "reason": "必须晚于 0002 执行，因为依赖其建好的索引"},
                             {"file": "0002_b.sql", "reason": "必须早于 0001 执行，因为要先把数据补齐"},
                         ]})
        self.assertEqual(self.run_gate(), 0)

    def test_M5_kind与内容不符(self):
        entries = self.base()
        entries[0]["kind"] = "verify"  # 明明含 CREATE TABLE
        self.manifest_of(entries, conflicts=[{"ordinal": "0002", "files": ["0002_README.md", "0002_b.sql"],
                                              "verdict": "同变更附件：README 为说明文档"}])
        self.assertEqual(self.run_gate(), 1)

    def test_M5_动态DDL不得被判成只读(self):
        """条件 DDL（SET @ddl + PREPARE/EXECUTE）剥离字面量后只剩 PREPARE，必须仍判为 ddl。"""
        dyn = ("SET @ddl := IF(1 = 1, 'ALTER TABLE t ADD COLUMN a INT', 'SELECT 1');\n"
               "PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;\n")
        self.write("0001_a.sql", dyn)
        self.write("0002_b.sql", DML_SQL)
        self.manifest_of([
            {"order": 1, "file": "0001_a.sql", "kind": "ddl", "summary": "条件补列 a（探测后 PREPARE）"},
            {"order": 2, "file": "0002_b.sql", "kind": "dml", "summary": "回填字段 a 的默认值"},
        ])
        self.assertEqual(self.run_gate(), 0)

    def test_M6_重号未登记(self):
        entries = self.base()
        self.manifest_of(entries)  # 故意不登记 0002 重号组
        self.assertEqual(self.run_gate(), 1)

    def test_M6_登记组与文件集合不符(self):
        self.write("0001_a.sql", DDL_SQL)
        self.write("0002_b.sql", DML_SQL)
        self.write("0002_c.sql", DML_SQL)
        self.manifest_of([
            {"order": 1, "file": "0001_a.sql", "kind": "ddl", "summary": "建表 t（id 主键）"},
            {"order": 2, "file": "0002_b.sql", "kind": "dml", "summary": "回填字段 a 的默认值"},
            {"order": 3, "file": "0002_c.sql", "kind": "dml", "summary": "回填字段 c 的默认值"},
        ], conflicts=[{"ordinal": "0002", "files": ["0002_b.sql"], "verdict": "登记漏了 0002_c，必须被判红"}])
        self.assertEqual(self.run_gate(), 1)

    def test_M7_summary占位(self):
        entries = self.base()
        entries[1]["summary"] = "TODO"
        self.manifest_of(entries, conflicts=[{"ordinal": "0002", "files": ["0002_README.md", "0002_b.sql"],
                                              "verdict": "同变更附件：README 为说明文档"}])
        self.assertEqual(self.run_gate(), 1)

    def test_M8_requires逆序(self):
        entries = self.base()
        entries[1]["requires"] = ["0001_a.sql"]  # order=2 依赖 order=1，合规
        self.manifest_of(entries, conflicts=[{"ordinal": "0002", "files": ["0002_README.md", "0002_b.sql"],
                                              "verdict": "同变更附件：README 为说明文档"}])
        self.assertEqual(self.run_gate(), 0, "先验合规方向应放行")

        entries[0]["order"], entries[1]["order"] = 2, 1
        self.manifest_of(entries, conflicts=[{"ordinal": "0002", "files": ["0002_README.md", "0002_b.sql"],
                                              "verdict": "同变更附件：README 为说明文档"}],
                         extra={"outOfLexOrder": [
                             {"file": "0001_a.sql", "reason": "必须晚于 0002 执行，因为依赖其建好的索引"},
                             {"file": "0002_b.sql", "reason": "必须早于 0001 执行，因为要先把数据补齐"},
                         ]})
        self.assertEqual(self.run_gate(), 1, "依赖项排在被依赖项之后必须判红")


if __name__ == "__main__":
    unittest.main(verbosity=2)
