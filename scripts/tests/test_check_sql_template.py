#!/usr/bin/env python3
"""check-sql-template.py 的三条硬约束自测。

判据背景见 scripts/check-sql-template.py 的模块 docstring：SDK 会改写模板文本
（首行前缀判语句类型 / 锚点替换成裸谓词 / 压平成一行），三处都会让「编译期与启动期
都正常」的模板在**执行期**炸成 1064 或 500。

本测试用最小夹具覆盖三条约束的每个分支，并特别覆盖两类**不该红**的形态：

  · `'a--b'` 这类字符串字面量里的 `--` 不是注释（误报会让人把判据关掉）；
  · `sqlFragment/` 与 `src/test/` 不在运行时路径上（范围误扩会把片段判成模板）。

另有一条「扫描数为 0 也必须判红」的用例：目录改名的结果是**所有模板都合规**，
判据若把「没扫到」当绿灯，等于把门禁泡成永远绿。
"""

import contextlib
import importlib.util
import io
import os
import shutil
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

SCRIPT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "check-sql-template.py",
)
_spec = importlib.util.spec_from_file_location("check_sql_template", SCRIPT)
mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(mod)

SQL_ROOT = "bone-apps/demo/src/main/resources/sql/com/bone/demo/Repo"


class SqlTemplateCheckTest(unittest.TestCase):
    def setUp(self):
        self.root = tempfile.mkdtemp(prefix="sql-template-")
        self._orig_repo = mod.REPO
        mod.REPO = Path(self.root)

    def tearDown(self):
        mod.REPO = self._orig_repo
        shutil.rmtree(self.root, ignore_errors=True)

    def _write(self, rel, body):
        path = Path(self.root, *rel.split("/"))
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(body, encoding="utf-8")
        return path

    def _problems(self, body, name="findById.sql", rel=SQL_ROOT):
        return mod.check(self._write(rel + "/" + name, body))

    # ---- 正常路径 -------------------------------------------------------
    def test_anchor_after_trailing_and_passes(self):
        body = "SELECT id\nFROM t_demo\nWHERE deleted = 0 AND\n  /*bone:tenant*/\nORDER BY id\n"

        self.assertEqual(self._problems(body), [])

    def test_anchor_on_same_line_after_and_passes(self):
        body = "SELECT id\nFROM t_demo\nWHERE id = :id AND /*bone:tenant*/\n"

        self.assertEqual(self._problems(body), [])

    def test_string_literal_with_double_dash_is_not_a_comment(self):
        body = "SELECT 'a--b' AS note\nFROM t_demo\nWHERE deleted = 0 AND\n  /*bone:tenant*/\n"

        self.assertEqual(self._problems(body), [])

    # ---- A 首行关键字 ---------------------------------------------------
    def test_leading_line_comment_is_reported(self):
        body = "-- 说明写在这里\nSELECT id FROM t_demo\n"

        problems = self._problems(body)

        self.assertTrue(any("首行" in p for p in problems), problems)

    def test_blank_lines_before_keyword_are_allowed(self):
        body = "\n\nSELECT id FROM t_demo\n"

        self.assertEqual(self._problems(body), [])

    # ---- B 禁止行注释 ---------------------------------------------------
    def test_trailing_comment_is_reported(self):
        body = "SELECT id FROM t_demo\nWHERE deleted = 0 AND -- 只看未删\n  /*bone:tenant*/\n"

        problems = self._problems(body)

        self.assertTrue(any("行内" in p for p in problems), problems)

    # ---- C 锚点前置 AND -------------------------------------------------
    def test_anchor_without_and_on_same_line_is_reported(self):
        body = "SELECT id FROM t_demo\nWHERE deleted = 0 /*bone:tenant*/\n"

        problems = self._problems(body)

        self.assertTrue(any("锚点前缺 AND" in p for p in problems), problems)

    def test_anchor_without_and_on_previous_line_is_reported(self):
        body = "SELECT id FROM t_demo\nWHERE deleted = 0\n  /*bone:tenant*/\n"

        problems = self._problems(body)

        self.assertTrue(any("锚点前缺 AND" in p for p in problems), problems)

    def test_anchor_after_where_without_other_predicate_passes(self):
        body = "SELECT id FROM t_demo\nWHERE /*bone:tenant*/\n"

        self.assertEqual(self._problems(body), [])

    # ---- 扫描范围 -------------------------------------------------------
    def test_fragment_directory_is_out_of_scope(self):
        self._write(
            SQL_ROOT + "/sqlFragment/columns.sql",
            "-- 片段允许注释\nid, name\n",
        )

        self.assertEqual(mod.templates(), [])

    def test_test_sources_are_out_of_scope(self):
        self._write("bone-apps/demo/src/test/resources/sql/com/bone/demo/Repo/x.sql", "-- 夹具\n")

        self.assertEqual(mod.templates(), [])

    def test_main_template_is_scanned(self):
        path = self._write(SQL_ROOT + "/findById.sql", "SELECT id FROM t_demo\n")

        self.assertEqual(mod.templates(), [path])

    def test_zero_scan_targets_fail_closed(self):
        # 目录改名/路径漂移会让 templates() 返回空列表——若 main 把 0 当绿灯，
        # 这条门禁就变成永远通过。这里钉住「0 个扫描对象」必须非 0 退出。
        self.assertEqual(mod.templates(), [])

        with mock.patch.object(sys, "argv", ["check-sql-template.py"]):
            with contextlib.redirect_stdout(io.StringIO()) as out:
                rc = mod.main()

        self.assertEqual(rc, 1)
        self.assertIn("扫描 0 个", out.getvalue())

    def test_empty_scan_can_be_explicitly_allowed(self):
        # 裁剪检出（纯前端仓）没有后端模块是合法状态，须能显式放行；
        # 但放行必须是**写出来的决定**，不能是默认行为。
        with mock.patch.object(sys, "argv", ["check-sql-template.py", "--allow-empty"]):
            with contextlib.redirect_stdout(io.StringIO()):
                rc = mod.main()

        self.assertEqual(rc, 0)


if __name__ == "__main__":
    unittest.main()
