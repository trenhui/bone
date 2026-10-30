"""表征测试：锁定 scripts/migrate.py 的账本比对逻辑（diff_state）。

为什么要把比对逻辑拆成纯函数再测
--------------------------------
账本的判据（哪条算「没跑过」、哪条算「被改过」、哪条只是「顺序变了」）全部落在一个
函数里，而它原先只被 CI 的 MySQL 冒烟间接覆盖——那意味着一旦「重编号」被误判成
「脚本被偷改」，本地跑门禁是发现不了的。2026-10-30 的实证正是这样：

    把账本里 order>=60 的记账整体 +1（等价于清单里插入一个新脚本后重编号），
    旧实现（按 order 认身份）报出 24 条「校验和不符（已应用后脚本被改动）」，
    而磁盘上一个字节都没改；--apply 还会据此拒绝执行并提示「请还原该文件」。

所以这里断言两条方向相反的性质：
  · 内容一致、只是 order 变了 ⇒ 只能进 order_shift，**不得**进 drift（否则误报成改脚本）；
  · 内容真的变了 ⇒ 必须进 drift（否则「谁改了已发的迁移」永远查不出来）。

运行：``python3 scripts/tests/test_migrate_journal.py -v``
"""

from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path

_SCRIPTS = Path(__file__).resolve().parent.parent
_SPEC = importlib.util.spec_from_file_location("migrate", _SCRIPTS / "migrate.py")
migrate = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(migrate)

SHA = {f: "h_" + f for f in ("0001_a.sql", "0002_b.sql", "0003_c.sql")}


def sha_of(f):
    return SHA.get(f, "h_?")


def entry(order, file, **kw):
    e = {"order": order, "file": file, "kind": "ddl", "summary": "s" * 10}
    e.update(kw)
    return e


def row(order, file, sha=None, success=True, source="apply"):
    return {"order": order, "sha": sha or sha_of(file), "success": success, "source": source}


class DiffStateTest(unittest.TestCase):
    def test_全部一致(self):
        items = [entry(1, "0001_a.sql"), entry(2, "0002_b.sql")]
        journal = {"0001_a.sql": row(1, "0001_a.sql"), "0002_b.sql": row(2, "0002_b.sql")}
        s = migrate.diff_state(items, journal, sha_of)
        self.assertEqual(len(s["ok"]), 2)
        for k in ("pending", "failed", "drift", "order_shift", "orphan", "retired"):
            self.assertEqual(s[k], [], k)

    def test_重编号只能报order变更不得报内容被改(self):
        """回归：清单插入新脚本后整体重编号，账本按 file 认身份，不能误报 drift。"""
        items = [entry(1, "0001_a.sql"), entry(3, "0002_b.sql")]  # 0002 从 2 挪到 3
        journal = {"0001_a.sql": row(1, "0001_a.sql"), "0002_b.sql": row(2, "0002_b.sql")}
        s = migrate.diff_state(items, journal, sha_of)
        self.assertEqual(s["drift"], [], "内容没变就不得报「已发脚本被改动」")
        self.assertEqual([f["file"] for f, _ in s["order_shift"]], ["0002_b.sql"])
        self.assertEqual(len(s["ok"]), 2, "重编号不影响「已应用」的判定")
        self.assertEqual(s["pending"], [])

    def test_内容真的变了必须报drift(self):
        items = [entry(1, "0001_a.sql")]
        journal = {"0001_a.sql": row(1, "0001_a.sql", sha="0" * 12)}
        s = migrate.diff_state(items, journal, sha_of)
        self.assertEqual(len(s["drift"]), 1)
        self.assertEqual(s["ok"], [])

    def test_执行失败与从未执行必须分开(self):
        """failed 的库可能处于半截状态，处置与 pending 完全不同（不能混成一个计数）。"""
        items = [entry(1, "0001_a.sql"), entry(2, "0002_b.sql"), entry(3, "0003_c.sql")]
        journal = {
            "0001_a.sql": row(1, "0001_a.sql"),
            "0002_b.sql": row(2, "0002_b.sql", success=False),
            # 0003_c.sql 不在账本里 = 从未执行
        }
        s = migrate.diff_state(items, journal, sha_of)
        self.assertEqual([f["file"] for f, _ in s["failed"]], ["0002_b.sql"])
        self.assertEqual([f["file"] for f in s["pending"]], ["0003_c.sql"])
        self.assertEqual(len(s["ok"]), 1)

    def test_retired不算法本残留_未登记才算(self):
        items = [entry(1, "0001_a.sql")]
        journal = {
            "0001_a.sql": row(1, "0001_a.sql"),
            "0000_old.sql": row(0, "0000_old.sql"),
            "9999_stranger.sql": row(99, "9999_stranger.sql"),
        }
        s = migrate.diff_state(items, journal, sha_of, retired=["0000_old.sql"])
        self.assertEqual([f for f, _ in s["retired"]], ["0000_old.sql"])
        self.assertEqual([f for f, _ in s["orphan"]], ["9999_stranger.sql"],
                         "既不在清单也不在 retired 的账本行必须报残留（别处来的脚本）")

    def test_baseline条数单独计数(self):
        """baseline 登记与真跑过必须可分辨：前者未经执行验证，状态里的绿不能把两者混为一谈。"""
        items = [entry(1, "0001_a.sql"), entry(2, "0002_b.sql")]
        journal = {
            "0001_a.sql": row(1, "0001_a.sql", source="baseline"),
            "0002_b.sql": row(2, "0002_b.sql", source="apply"),
        }
        s = migrate.diff_state(items, journal, sha_of)
        self.assertEqual(len(s["ok"]), 2)
        self.assertEqual(s["baseline_n"], 1)

    def test_失败行不计入baseline条数(self):
        items = [entry(1, "0001_a.sql")]
        journal = {"0001_a.sql": row(1, "0001_a.sql", success=False, source="baseline")}
        s = migrate.diff_state(items, journal, sha_of)
        self.assertEqual(s["baseline_n"], 0)


if __name__ == "__main__":
    unittest.main(verbosity=2)
