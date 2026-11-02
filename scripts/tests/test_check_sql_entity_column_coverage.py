"""表征测试：锁定 check-sql-entity-column-coverage.py 的判据（R1/R2/R3 + 豁免 + 棘轮）。

背景：这条门禁存在的唯一目的是拦住「实体加了 @Column、手写 SQL 模板没跟着加」——
该形态已复发两次（`fee_code` 让费用类别规则整条失效；`triage_reason` 让风控闸从未生效）。
判据一旦悄悄失效，就会退化成"全绿但什么都没查"。

做法：门禁脚本文件名含连字符，无法按名 import；用 importlib 按 **str 路径** 加载为模块，
再 monkeypatch 其模块级 ``REPO`` / ``BASELINE``，在临时目录构造最小仓库，断言 ``main()``
的退出码与诊断文案。**不改门禁脚本本身**——只验证其当前契约不被回归。

运行：``python3 scripts/tests/test_check_sql_entity_column_coverage.py -v``
"""

from __future__ import annotations

import contextlib
import importlib.util
import io
import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parent.parent

# 文件名含连字符，不能 import；必须用 str 路径（Py3.14 的 SourceFileLoader 对 Path 有 get_data 问题）。
_GATE_PATH = str(SCRIPTS_DIR / "check-sql-entity-column-coverage.py")
_SPEC = importlib.util.spec_from_file_location("check_sql_entity_column_coverage", _GATE_PATH)
gate = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(gate)

ENTITY = """package com.x.domain.model;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;

@Table("t_foo")
public class Foo {
  @Column(name = "foo_no")
  private String fooNo;

  @Column(name = "amount")
  private java.math.BigDecimal amount;
}
"""

REPO_IFACE = """package com.x.domain.repository;
import com.x.domain.model.Foo;
import java.util.List;

public interface FooRepository {
  Foo findById(long id);

  List<Foo> findByNo(String no);

  List<FooProjection> findProjected(String no);
}
"""


def _sql(*cols: str, table: str = "t_foo") -> str:
    body = ",\n       ".join(cols)
    return f"SELECT\n       {body}\nFROM {table}\nWHERE deleted = 0\n"


class SqlEntityColumnGateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.root = Path(tempfile.mkdtemp(prefix="g42-gate-"))
        self._orig_repo = gate.REPO
        self._orig_baseline = gate.BASELINE
        gate.REPO = self.root
        gate.BASELINE = self.root / "doc/architecture/sql-entity-column-baseline.json"

        module = self.root / "m"
        self.iface = module / "src/main/java/com/x/domain/repository/FooRepository.java"
        self.iface.parent.mkdir(parents=True, exist_ok=True)
        self.iface.write_text(REPO_IFACE, encoding="utf-8")

        entity = module / "src/main/java/com/x/domain/model/Foo.java"
        entity.parent.mkdir(parents=True, exist_ok=True)
        entity.write_text(ENTITY, encoding="utf-8")

        self.sql_root = module / "src/main/resources/sql/com/x/domain/repository/FooRepository"
        self.sql_root.mkdir(parents=True, exist_ok=True)

    def tearDown(self) -> None:
        gate.REPO = self._orig_repo
        gate.BASELINE = self._orig_baseline
        shutil.rmtree(self.root, ignore_errors=True)

    # ---- 辅助 ----

    def write_sql(self, name: str, text: str) -> None:
        (self.sql_root / f"{name}.sql").write_text(text, encoding="utf-8")

    def write_baseline(self, templates: dict | None = None, orphans: list | None = None) -> None:
        gate.BASELINE.parent.mkdir(parents=True, exist_ok=True)
        gate.BASELINE.write_text(
            json.dumps(
                {"templates": templates or {}, "orphans": orphans or []}, ensure_ascii=False
            ),
            encoding="utf-8",
        )

    def run_gate(self, *args: str) -> tuple[int, str]:
        out = io.StringIO()
        argv = sys.argv
        sys.argv = ["gate", *args]
        try:
            with contextlib.redirect_stdout(out):
                code = gate.main()
        finally:
            sys.argv = argv
        return code, out.getvalue()

    # ---- R1 读整实体必须全列 ----

    def test_full_columns_pass(self) -> None:
        self.write_sql("findById", _sql("id", "foo_no", "amount"))
        self.write_baseline()
        code, out = self.run_gate("--check")
        self.assertEqual(code, 0, out)
        self.assertIn("受检模板 1 个", out)

    def test_missing_column_blocks(self) -> None:
        """核心判据：漏一列即红，且文案要点名是哪一列。"""
        self.write_sql("findById", _sql("id", "foo_no"))
        self.write_baseline()
        code, out = self.run_gate("--check")
        self.assertEqual(code, 1, out)
        self.assertIn("新增缺列", out)
        self.assertIn("amount", out)

    def test_alias_prefix_is_understood(self) -> None:
        """`f.amount AS amount` 属于已选该列，不得误报。"""
        text = "SELECT f.id, f.foo_no, f.amount AS amount\nFROM t_foo f WHERE f.deleted = 0\n"
        self.write_sql("findById", text)
        self.write_baseline()
        code, out = self.run_gate("--check")
        self.assertEqual(code, 0, out)

    def test_select_star_is_exempt(self) -> None:
        self.write_sql("findById", "SELECT * FROM t_foo WHERE deleted = 0\n")
        self.write_baseline()
        code, out = self.run_gate("--check")
        self.assertEqual(code, 0, out)

    # ---- R2 读整实体必须选主键 ----

    def test_missing_primary_key_blocks(self) -> None:
        self.write_sql("findById", _sql("foo_no", "amount"))
        self.write_baseline()
        code, out = self.run_gate("--check")
        self.assertEqual(code, 1, out)
        self.assertIn("id", out)

    # ---- 豁免：返回投影 / 非 SELECT ----

    def test_projection_return_type_is_exempt(self) -> None:
        """返回投影类的方法不读整实体，缺列不构成缺陷。"""
        self.write_sql("findProjected", _sql("foo_no"))
        self.write_baseline()
        code, out = self.run_gate("--check")
        self.assertEqual(code, 0, out)
        self.assertIn("受检模板 0 个", out)

    def test_update_template_is_skipped(self) -> None:
        self.write_sql("findById", _sql("id", "foo_no", "amount"))
        (self.sql_root / "touchFoo.sql").write_text(
            "UPDATE t_foo SET foo_no = :no WHERE id = :id\n", encoding="utf-8"
        )
        self.write_baseline()
        code, out = self.run_gate("--check")
        self.assertEqual(code, 0, out)

    # ---- R3 孤儿模板 ----

    def test_orphan_template_blocks(self) -> None:
        (self.sql_root / "findGhost.sql").write_text(_sql("id"), encoding="utf-8")
        self.write_baseline()
        code, out = self.run_gate("--check")
        self.assertEqual(code, 1, out)
        self.assertIn("新增孤儿模板", out)

    # ---- 基线棘轮：只可收缩 ----

    def test_baseline_absorbs_known_gap(self) -> None:
        self.write_sql("findById", _sql("id", "foo_no"))
        self.write_baseline(
            templates={
                "m/src/main/resources/sql/com/x/domain/repository/FooRepository/findById.sql": {
                    "entity": "Foo",
                    "table": "t_foo",
                    "missing": ["amount"],
                }
            }
        )
        code, out = self.run_gate("--check")
        self.assertEqual(code, 0, out)

    def test_baseline_gap_widening_blocks(self) -> None:
        """已知缺 1 列，现在缺 2 列 —— 缺口扩大必须红。"""
        self.write_sql("findById", _sql("foo_no"))
        self.write_baseline(
            templates={
                "m/src/main/resources/sql/com/x/domain/repository/FooRepository/findById.sql": {
                    "entity": "Foo",
                    "table": "t_foo",
                    "missing": ["amount"],
                }
            }
        )
        code, out = self.run_gate("--check")
        self.assertEqual(code, 1, out)
        self.assertIn("缺口扩大", out)

    def test_baseline_entry_stale_blocks(self) -> None:
        """缺口已补上但条目没删 —— 视为过宽豁免，必须红（只可收缩的另一半）。"""
        self.write_sql("findById", _sql("id", "foo_no", "amount"))
        self.write_baseline(
            templates={
                "m/src/main/resources/sql/com/x/domain/repository/FooRepository/findById.sql": {
                    "entity": "Foo",
                    "table": "t_foo",
                    "missing": ["amount"],
                }
            }
        )
        code, out = self.run_gate("--check")
        self.assertEqual(code, 1, out)
        self.assertIn("已失效", out)

    def test_baseline_entry_for_deleted_template_blocks(self) -> None:
        self.write_sql("findById", _sql("id", "foo_no", "amount"))
        self.write_baseline(
            templates={
                "m/src/main/resources/sql/com/x/domain/repository/FooRepository/gone.sql": {
                    "entity": "Foo",
                    "table": "t_foo",
                    "missing": ["amount"],
                }
            }
        )
        code, out = self.run_gate("--check")
        self.assertEqual(code, 1, out)
        self.assertIn("已不存在", out)

    def test_baseline_orphan_removed_blocks(self) -> None:
        self.write_baseline(
            templates={},
            orphans=["m/src/main/resources/sql/com/x/domain/repository/FooRepository/findGhost.sql"],
        )
        code, out = self.run_gate("--check")
        self.assertEqual(code, 1, out)
        self.assertIn("基线孤儿模板已消失", out)


if __name__ == "__main__":
    unittest.main()
