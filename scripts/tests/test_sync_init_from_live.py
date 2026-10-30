"""表征测试：scripts/sync-init-from-live.py 的纯函数部分（normalize / splice）。

这个脚本守的是「bone-init.sql 的迁移并入区块 == 活库结构」。它的失效方式都很安静：
  · normalize 漏剥 AUTO_INCREMENT → 基线带上数据状态，每次重建都让 diff 抖动；
  · splice 不幂等 → 每跑一次就在文件末尾再贴一遍区块，init 里出现重复 CREATE TABLE；
  · 定界符损坏时静默覆盖 → 手工编辑过的内容被无声吞掉，且没人知道。

区块内容来自活库，不能在这里造；但**写入文件的那一步**必须被锁死。

运行：``python3 scripts/tests/test_sync_init_from_live.py -v``
"""

from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path

_SCRIPTS = Path(__file__).resolve().parent.parent
_SPEC = importlib.util.spec_from_file_location(
    "sync_init_from_live", _SCRIPTS / "sync-init-from-live.py")
sync = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(sync)


class NormalizeTest(unittest.TestCase):
    def test_剥离自增计数器(self):
        """AUTO_INCREMENT=58 是数据状态，不是结构：带进基线就会让每次重建都产生假 diff。"""
        ddl = ("CREATE TABLE `t` (\n  `id` int NOT NULL AUTO_INCREMENT,\n  PRIMARY KEY (`id`)\n"
               ") ENGINE=InnoDB AUTO_INCREMENT=58 DEFAULT CHARSET=utf8mb4")
        out = sync.normalize(ddl)
        self.assertNotIn("AUTO_INCREMENT=58", out)
        self.assertIn("AUTO_INCREMENT", out, "列定义里的 AUTO_INCREMENT 必须保留")

    def test_无自增时原样返回(self):
        ddl = "CREATE TABLE `t` (\n  `id` int NOT NULL\n) ENGINE=InnoDB"
        self.assertEqual(sync.normalize(ddl), ddl)

    def test_剥离后不留尾随空格(self):
        ddl = "CREATE TABLE `t` (\n  `id` int\n) ENGINE=InnoDB AUTO_INCREMENT=1  \n"
        self.assertEqual(sync.normalize(ddl).rstrip(), sync.normalize(ddl))


class SpliceTest(unittest.TestCase):
    def setUp(self):
        self.block = f"{sync.BEGIN}\nCREATE TABLE `a` (`id` int);\n{sync.END}"

    def test_首次追加到末尾(self):
        text = "-- 头部\nCREATE TABLE `z` (`id` int);\n"
        out = sync.splice(text, self.block)
        self.assertTrue(out.startswith("-- 头部"))
        self.assertIn("CREATE TABLE `z`", out)
        self.assertIn(sync.BEGIN, out)
        self.assertTrue(out.rstrip().endswith(sync.END))

    def test_幂等_重复拼接不会出现两个区块(self):
        text = "-- 头部\n"
        once = sync.splice(text, self.block)
        twice = sync.splice(once, self.block)
        self.assertEqual(once, twice, "splice 必须幂等，否则每跑一次就多贴一个区块")
        self.assertEqual(twice.count(sync.BEGIN), 1)

    def test_替换区块时区块外内容不变(self):
        text = f"-- 头部\nCREATE TABLE `z` (`id` int);\n{sync.BEGIN}\n旧内容\n{sync.END}\n-- 尾部\n"
        out = sync.splice(text, self.block)
        self.assertIn("-- 头部", out)
        self.assertIn("-- 尾部", out)
        self.assertNotIn("旧内容", out)
        self.assertIn("CREATE TABLE `a`", out)

    def test_定界符损坏必须报错而不是静默覆盖(self):
        """只有 BEGIN 没有 END：若静默截断，后面手工内容会被无声吞掉。"""
        text = f"-- 头部\n{sync.BEGIN}\n半截内容\n"
        with self.assertRaises(SystemExit):
            sync.splice(text, self.block)

    def test_区块内含表名可被统计(self):
        out = sync.splice("-- x\n", self.block)
        self.assertIn("CREATE TABLE `a`", out)


if __name__ == "__main__":
    unittest.main(verbosity=2)
