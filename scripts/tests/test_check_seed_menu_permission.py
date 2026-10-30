#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""G40（菜单 permission 必须存在对应权限点）表征测试。

用临时种子 SQL 验证判据：
  · 菜单 permission ⊆ 权限点 code  → 绿
  · 任一菜单引用不存在的 code        → 红（静默失效的菜单）
  · 解析不到 iam_permission 段       → 红（门禁失去对象，防假绿）
  · 菜单段 0 行                      → 红
这些用例刻意做变异验证：把「一致」改成「不一致」必须让绿变红。
"""
import importlib.util
import os
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
SEED_DIR = REPO / "scripts" / "seed" / "bootstrap"

spec = importlib.util.spec_from_file_location(
    "check_seed_menu_permission", REPO / "scripts" / "check-seed-menu-permission.py")
g40 = importlib.util.module_from_spec(spec)
sys.modules["check_seed_menu_permission"] = g40
spec.loader.exec_module(g40)


def _seed_sql(perm_rows, menu_rows, with_perm_header=True):
    """拼一个最小种子 SQL。perm_rows / menu_rows 为值元组列表。"""
    perm_vals = ", ".join("('%s','%s')" % (c, n) for c, n in perm_rows)
    menu_vals = ", ".join("('%s','%s','%s')" % (n, p, ic) for n, p, ic in menu_rows)
    parts = [
        "REPLACE INTO `iam_permission` (`code`, `name`) VALUES %s;" % perm_vals,
        "REPLACE INTO `iam_menu` (`name`, `permission`, `icon`) VALUES %s;" % menu_vals,
    ]
    return "\n".join(parts)


class SeedMenuPermissionTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp(prefix="g40-")
        self.seed = Path(self.tmp) / "10_bootstrap_seed.sql"

    def tearDown(self):
        import shutil
        shutil.rmtree(self.tmp, ignore_errors=True)

    def _write(self, sql):
        self.seed.write_text(sql, encoding="utf-8")

    def test_一致时绿(self):
        self._write(_seed_sql(
            [("claim:read", "读"), ("claim:write", "写")],
            [("理赔列表", "claim:read", "FileTextOutlined"),
             ("理赔新建", "claim:write", "FormOutlined")]))
        self.assertEqual(g40.check_static(str(self.seed)), 0)

    def test_孤儿菜单必须红(self):
        """菜单引用了不存在的权限码 → 运行时被按 scope 静默剪掉。"""
        self._write(_seed_sql(
            [("claim:read", "读")],
            [("理赔列表", "claim:read", "FileTextOutlined"),
             ("幽灵菜单", "claim:phantom", "FileTextOutlined")]))
        self.assertEqual(g40.check_static(str(self.seed)), 1)

    def test_empty_permission_skipped(self):
        """目录型菜单（permission 为空）不应要求权限点。"""
        self._write(_seed_sql(
            [("claim:read", "读")],
            [("系统管理", "", "AppstoreOutlined"),
             ("理赔列表", "claim:read", "FileTextOutlined")]))
        self.assertEqual(g40.check_static(str(self.seed)), 0)

    def test_解析不到权限段必须红(self):
        self._write(_seed_sql([], [("理赔列表", "claim:read", "FileTextOutlined")]))
        self.assertEqual(g40.check_static(str(self.seed)), 1)

    def test_菜单段为零必须红(self):
        self._write(_seed_sql([("claim:read", "读")], []))
        self.assertEqual(g40.check_static(str(self.seed)), 1)

    def test_扩展插入多行也能解析(self):
        """mysqldump 默认 extended insert：一条 REPLACE 含多行，必须正确切分。"""
        sql = (
            "REPLACE INTO `iam_permission` (`code`, `name`) "
            "VALUES ('a:read','读'),('a:write','写'),('b:read','读');\n"
            "REPLACE INTO `iam_menu` (`name`, `permission`, `icon`) "
            "VALUES ('A列表','a:read','FileTextOutlined'),"
            "('A新建','a:write','FormOutlined'),"
            "('B列表','b:read','AppstoreOutlined');\n"
        )
        self._write(sql)
        self.assertEqual(g40.check_static(str(self.seed)), 0)


if __name__ == "__main__":
    unittest.main()
