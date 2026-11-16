#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""check-menu-icon-whitelist.py（G37 · 菜单图标白名单）的判据单测。

门禁判据：SQL 种子/迁移里写进 `iam_menu.icon` 的图标名（单引号字面量），
必须已登记在 shell `App.tsx` 的 `MENU_ICON_MAP` 白名单里；未登记会被
`resolveMenuIcon` 静默降级成默认图标。

本测试锁定：
  · `strip_sql_comments`：剥离 `/* */` 与 `--` 注释（注释里引用图标名不应误伤）；
  · `whitelist_keys`：从 MENU_ICON_MAP 提取已登记名；常量缺失时返回 None（防假绿）；
  · `collect_usages`：只认单引号字面量 + Outlined/Filled/TwoTone 后缀，记录来源文件；
  · 白名单比对语义（正向全登记 → 无 unknown，负向未登记 → 精确报出图标名与文件）；
  · 空输入边界。

未覆盖：`main()` 的 CLI 编排（参数解析、扫描目录拼接、退出码）——按测试纪律不调用
main()（会扫全仓）。上述纯函数已覆盖 main 的全部判据来源。
"""

import importlib.util
import os
import shutil
import tempfile
import unittest

SCRIPT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "check-menu-icon-whitelist.py",
)
_spec = importlib.util.spec_from_file_location("check_menu_icon_whitelist", SCRIPT)
mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(mod)


APP_FIXTURE = """const MENU_ICON_MAP: Record<string, JSX.Element> = {
  DashboardOutlined: <DashboardOutlined />,
  ProfileOutlined: <ProfileOutlined />,
  CheckCircleFilled: <CheckCircleFilled />,
};
"""


class MenuIconWhitelistTest(unittest.TestCase):
    def setUp(self):
        self.root = tempfile.mkdtemp(prefix="menu-icon-whitelist-")

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def _write(self, name, body):
        path = os.path.join(self.root, name)
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(body)
        return path

    def test_strip_sql_comments_removes_block_and_line_comments(self):
        body = (
            "/* 'CommentedOutlined' */\n"
            "INSERT INTO iam_menu VALUES ('RealOutlined'); -- 'TailFilled'\n"
        )

        stripped = mod.strip_sql_comments(body)

        self.assertNotIn("CommentedOutlined", stripped)
        self.assertNotIn("TailFilled", stripped)
        self.assertIn("'RealOutlined'", stripped)

    def test_whitelist_keys_extracts_registered_names(self):
        app = self._write("App.tsx", APP_FIXTURE)

        keys = mod.whitelist_keys(app)

        self.assertEqual(
            keys, {"DashboardOutlined", "ProfileOutlined", "CheckCircleFilled"}
        )

    def test_whitelist_keys_returns_none_when_map_absent(self):
        # 常量改名/结构漂移时返回 None，main 会判红而不是静默通过（防假绿）
        app = self._write("App.tsx", "export default function App() {}\n")

        self.assertIsNone(mod.whitelist_keys(app))

    def test_collect_usages_only_single_quoted_antd_names(self):
        sql = self._write(
            "0024_seed.sql",
            "INSERT INTO iam_menu (icon) VALUES ('DashboardOutlined'), ('CheckCircleTwoTone');\n"
            "INSERT INTO iam_menu (icon) VALUES ('Settings', \"DoubleQuotedOutlined\");\n",
        )

        usages = mod.collect_usages([sql])

        # 'Settings' 无 antd 后缀、双引号不是 SQL 单引号字面量 ⇒ 均不采集
        self.assertEqual(set(usages), {"DashboardOutlined", "CheckCircleTwoTone"})
        self.assertEqual(usages["DashboardOutlined"], ["0024_seed.sql"])

    def test_collect_usages_ignores_comment_literals(self):
        sql = self._write(
            "0050_comment.sql",
            "/* 'BlockCommentOutlined' */\n"
            "-- 'LineCommentFilled'\n"
            "UPDATE iam_menu SET icon = 'ProfileOutlined' WHERE id = 1;\n",
        )

        usages = mod.collect_usages([sql])

        self.assertEqual(set(usages), {"ProfileOutlined"})

    def test_whitelisted_usages_produce_no_unknown(self):
        app = self._write("App.tsx", APP_FIXTURE)
        sql = self._write(
            "seed.sql", "VALUES ('DashboardOutlined'), ('ProfileOutlined');"
        )

        keys = mod.whitelist_keys(app)
        unknown = {k: v for k, v in mod.collect_usages([sql]).items() if k not in keys}

        self.assertEqual(unknown, {})

    def test_unknown_icon_reported_with_name_and_file(self):
        # 2026-10-30 实证形态：0078 初版写 SolutionOutlined，不在白名单内
        app = self._write("App.tsx", APP_FIXTURE)
        sql = self._write(
            "0078_workbench.sql",
            "VALUES ('DashboardOutlined'), ('SolutionOutlined');",
        )

        keys = mod.whitelist_keys(app)
        unknown = {k: v for k, v in mod.collect_usages([sql]).items() if k not in keys}

        self.assertEqual(unknown, {"SolutionOutlined": ["0078_workbench.sql"]})

    def test_empty_inputs(self):
        self.assertEqual(mod.collect_usages([]), {})
        self.assertEqual(mod.strip_sql_comments(""), "")

    def test_icon_literal_accepts_three_antd_suffixes(self):
        # 只认 Outlined 会漏掉 Filled / TwoTone（漏认 = 静默放行）
        names = mod.ICON_LITERAL.findall(
            "'DashboardOutlined' 'CheckCircleFilled' 'ProfileTwoTone' 'Settings'"
        )

        self.assertEqual(
            names, ["DashboardOutlined", "CheckCircleFilled", "ProfileTwoTone"]
        )


if __name__ == "__main__":
    unittest.main(verbosity=2)
