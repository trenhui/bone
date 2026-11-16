#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""check-menu-single-entry.py（G35 · 菜单信息架构三条硬规则）的判据单测。

门禁判据：
  规则 1（单入口）容器根路径菜单（`/metadata` 无 #hash）是噪音；
  规则 2（落点合法）菜单 path 必须是 shell 自身路由或容器前缀，否则点击被
          `<Route path="*">` 静默送回首页；
  规则 3（按钮前缀联动）有按钮子节点的菜单改名时，同一 SQL 文件必须带
          `REPLACE(name, '<旧>-', '<新>-')`。

本测试锁定：
  · `strip_sql_comments`：剥离 `--` 行注释，但不误伤字符串里的 `--`；
  · `iter_sql_statements`：按 `;` / `DELIMITER $$` 切语句，字符串内分号不切；
  · `parse_seed_menu_state`：从 0024 plan 元组解析 ref→名、ref→有无按钮子节点；
  · `scan_menu_renames`：跨文件跟踪改名事件与每文件 REPLACE 对；
  · `check_button_prefix_linkage`：有按钮未联动 ⇒ 精确报旧名/新名；无按钮改名、
    已软删菜单改名 ⇒ 不要求联动（不编造要求）；
  · `parse_shell_containers`：从 shell App.tsx 提取 `path="/xxx/*"` 前缀，缺失时走兜底；
  · `PATH_LITERAL` / `SHELL_SELF_ROUTES` / `SOFT_DELETE` 的常量契约。

未覆盖：`main()` 的规则 1/2 分类编排（存活 INSERT/UPDATE 遍历、软删语句跳过、
与 `hashed_paths` 比对）——按测试纪律不调用 main()（会扫全仓）。main 的输入
（常量、PATH_LITERAL、语句切分、容器集合）均已直接覆盖。
"""

import importlib.util
import os
import shutil
import tempfile
import unittest

SCRIPT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "check-menu-single-entry.py",
)
_spec = importlib.util.spec_from_file_location("check_menu_single_entry", SCRIPT)
mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(mod)


SEED_FIXTURE = """INSERT INTO iam_menu (id, ref, parent_ref, name, path, icon, order_no, permission, type) VALUES
  (1000, 'm_meta', NULL, '元数据建模', '/metadata', 'DatabaseOutlined', 10, 'metadata:view', 1),
  (1001, 'm_meta_ent', 'm_meta', '实体', '/metadata#/entities', 'TableOutlined', 20, 'metadata:entity', 1),
  (1002, 'b_ent_add', 'm_meta_ent', '业务建模-实体-新增', NULL, NULL, 30, NULL, 2),
  (1003, 'm_dir', 'm_meta', '目录节点', NULL, NULL, 40, NULL, 0);
"""


def _write(root, rel, body):
    path = os.path.join(root, *rel.split("/"))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(body)
    return path


class SqlHelpersTest(unittest.TestCase):
    """纯解析函数与模块级正则/常量契约。"""

    def setUp(self):
        self.root = tempfile.mkdtemp(prefix="menu-single-entry-")

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def test_strip_sql_comments_keeps_string_content(self):
        line = "UPDATE iam_menu SET name = 'a--b' WHERE id = 1; -- '/dashboard' 背景"

        stripped = mod.strip_sql_comments(line)

        self.assertIn("'a--b'", stripped)
        self.assertNotIn("/dashboard", stripped)

    def test_strip_sql_comments_full_line(self):
        self.assertEqual(mod.strip_sql_comments("-- 仅为背景说明 '/metadata'\n"), "")

    def test_path_literal_matches_bare_and_hashed_forms(self):
        found = mod.PATH_LITERAL.findall(
            "'/metadata' '/dashboard' '/metadata#/apps' \"/double/\""
        )

        self.assertEqual(found, ["/metadata", "/dashboard", "/metadata#/apps"])

    def test_shell_self_routes_and_soft_delete_contract(self):
        self.assertEqual(mod.SHELL_SELF_ROUTES, {"/", "/profile"})
        # 大小写不敏感：迁移脚本里有 SET deleted = 1 / set deleted=1 两种写法
        self.assertIsNotNone(mod.SOFT_DELETE.search("SET   deleted = 1"))
        self.assertIsNotNone(mod.SOFT_DELETE.search("set deleted=1"))


class StatementAndSeedTest(unittest.TestCase):
    def setUp(self):
        self.root = tempfile.mkdtemp(prefix="menu-single-entry-")

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def test_iter_sql_statements_splits_and_strips_comments(self):
        _write(
            self.root,
            "scripts/migration/0001_a.sql",
            "-- 头部注释 '/dashboard'\n"
            "INSERT INTO iam_menu (name) VALUES ('a;b');\n"
            "\n"
            "UPDATE iam_menu SET deleted = 1 WHERE name = 'z';\n",
        )

        statements = [stmt for _, _, stmt in mod.iter_sql_statements(self.root)]

        self.assertEqual(len(statements), 2)
        # 字符串内分号不切句；注释被剥离
        self.assertIn("'a;b'", statements[0])
        self.assertIn("SET deleted = 1", statements[1])
        self.assertFalse(any("/dashboard" in s for s in statements))

    def test_iter_sql_statements_honours_delimiter(self):
        _write(
            self.root,
            "scripts/migration/0002_proc.sql",
            "DELIMITER $$\n"
            "CREATE PROCEDURE p()\n"
            "BEGIN\n"
            "  UPDATE iam_menu SET name = 'x';\n"
            "END$$\n",
        )

        statements = [stmt for _, _, stmt in mod.iter_sql_statements(self.root)]

        self.assertEqual(len(statements), 1)
        self.assertIn("UPDATE iam_menu", statements[0])
        self.assertNotIn("DELIMITER", statements[0])

    def test_parse_seed_menu_state(self):
        _write(
            self.root,
            "scripts/migration/0024_seed_iam_menu_tree.sql",
            SEED_FIXTURE,
        )

        names, has_buttons = mod.parse_seed_menu_state(self.root)

        self.assertEqual(
            names,
            {"m_meta": "元数据建模", "m_meta_ent": "实体", "m_dir": "目录节点"},
        )
        # 只有 type=2 的子节点才把父 ref 标记为「有按钮」
        self.assertEqual(has_buttons, {"m_meta_ent"})

    def test_parse_seed_menu_state_missing_file_is_empty(self):
        self.assertEqual(mod.parse_seed_menu_state(self.root), ({}, set()))

    def test_parse_shell_containers_extracts_prefixed_routes(self):
        app = _write(
            self.root,
            "bone-frontend/apps/bone-shell/src/App.tsx",
            '<Route path="/metadata/*" element={<MicroApp />} />\n'
            '<Route path="/iam/*" element={<MicroApp />} />\n'
            '<Route path="/profile" element={<Profile />} />\n',
        )

        self.assertTrue(os.path.isfile(app))
        self.assertEqual(mod.parse_shell_containers(self.root), {"/metadata", "/iam"})

    def test_parse_shell_containers_falls_back_when_file_missing(self):
        containers = mod.parse_shell_containers(self.root)

        self.assertIn("/metadata", containers)
        self.assertIn("/iam", containers)


class RenameLinkageTest(unittest.TestCase):
    def setUp(self):
        self.root = tempfile.mkdtemp(prefix="menu-single-entry-")
        _write(
            self.root,
            "scripts/migration/0024_seed_iam_menu_tree.sql",
            SEED_FIXTURE,
        )

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def _migration(self, name, body):
        _write(self.root, "scripts/migration/" + name, body)

    def test_scan_menu_renames_tracks_event(self):
        self._migration(
            "0039_rename.sql",
            "UPDATE iam_menu SET name = '业务对象' WHERE name IN ('实体');\n",
        )

        events, replaces = mod.scan_menu_renames(self.root)

        self.assertEqual([(e[2], e[3], e[4]) for e in events], [("实体", "业务对象", True)])
        self.assertEqual(replaces, {})

    def test_rename_with_buttons_without_linkage_is_violation(self):
        self._migration(
            "0039_rename.sql",
            "UPDATE iam_menu SET name = '业务对象' WHERE name IN ('实体');\n",
        )

        violations = mod.check_button_prefix_linkage(self.root)

        self.assertEqual(len(violations), 1)
        _, _, old, new, reason = violations[0]
        self.assertEqual((old, new), ("实体", "业务对象"))
        self.assertIn("未在同脚本联动按钮前缀", reason)
        self.assertIn("REPLACE(name, '...实体-'", reason)

    def test_rename_with_buttons_and_linkage_passes(self):
        self._migration(
            "0039_rename.sql",
            "UPDATE iam_menu SET name = '业务对象' WHERE name IN ('实体');\n"
            "UPDATE iam_menu SET name = REPLACE(name, '业务建模-实体-', '业务建模-业务对象-')\n"
            "  WHERE name LIKE '业务建模-实体-%';\n",
        )

        events, replaces = mod.scan_menu_renames(self.root)

        self.assertEqual([(e[2], e[3], e[4]) for e in events], [("实体", "业务对象", True)])
        self.assertIn(
            ("业务建模-实体-", "业务建模-业务对象-"), replaces["0039_rename.sql"]
        )
        self.assertEqual(mod.check_button_prefix_linkage(self.root), [])

    def test_rename_menu_without_buttons_requires_no_linkage(self):
        # 建模工作台→模型设计 这类无按钮菜单不应被编造联动要求
        self._migration(
            "0039_rename.sql",
            "UPDATE iam_menu SET name = '目录新名' WHERE name IN ('目录节点');\n",
        )

        events, _ = mod.scan_menu_renames(self.root)

        self.assertEqual([(e[2], e[3], e[4]) for e in events], [("目录节点", "目录新名", False)])
        self.assertEqual(mod.check_button_prefix_linkage(self.root), [])

    def test_soft_deleted_menu_rename_requires_no_linkage(self):
        # 0027 范式：软删菜单连同按钮一起退役，后续改名不再要求联动
        self._migration(
            "0027_soft_delete.sql",
            "UPDATE iam_menu SET deleted = 1 WHERE name IN ('实体');\n",
        )
        self._migration(
            "0039_rename.sql",
            "UPDATE iam_menu SET name = '业务对象' WHERE name IN ('实体');\n",
        )

        violations = mod.check_button_prefix_linkage(self.root)

        self.assertEqual(violations, [])


if __name__ == "__main__":
    unittest.main(verbosity=2)
