"""表征测试：scripts/seed-bootstrap.py 的校验判据 + migrate.py 的 apply 互斥锁。

两处都是「安静失效」型：
  · 种子校验若把「行数不足」放行，新环境就是空壳——有 146 张表、0 条菜单，登录后侧栏空白，
    而所有门禁都绿（它们只看结构）。
  · 互斥锁若形同虚设，两个终端同时 apply 会交叉写账本与 DDL，且结果看起来都「成功」。
    本文件用一个真实子进程持锁来验证互斥，而不是在同进程里自我欺骗（同进程 flock 不互斥）。

运行：``python3 scripts/tests/test_seed_and_lock.py -v``
"""

from __future__ import annotations

import importlib.util
import json
import os
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path
from unittest import mock

_SCRIPTS = Path(__file__).resolve().parent.parent


def _load(name: str, filename: str):
    spec = importlib.util.spec_from_file_location(name, _SCRIPTS / filename)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


seed = _load("seed_bootstrap", "seed-bootstrap.py")
migrate = _load("migrate_for_lock", "migrate.py")


class FakeDb:
    def __init__(self, counts):
        self.counts = counts

    def count(self, table, where="1=1", db=None):
        return self.counts.get(table, 0)


class SeedVerifyTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="seedtest-"))
        self.manifest = self.tmp / "seed-manifest.json"
        doc = {"tables": {
            "iam_menu": {"rows": 288, "where": "1=1", "why": "菜单树"},
            "iam_permission": {"rows": 126, "where": "1=1", "why": "权限点"},
        }}
        self.manifest.write_text(json.dumps(doc), encoding="utf-8")
        self._orig = seed.OUT_MANIFEST
        seed.OUT_MANIFEST = self.manifest

    def tearDown(self):
        seed.OUT_MANIFEST = self._orig

    def test_行数齐备通过(self):
        with mock.patch.object(seed, "Db", lambda *a, **k: FakeDb({"iam_menu": 288, "iam_permission": 126})):
            self.assertEqual(seed.verify("x"), 0)

    def test_行数不足判红(self):
        """灌了 init 没灌 seed 就是这个症状：表在、数据空。"""
        with mock.patch.object(seed, "Db", lambda *a, **k: FakeDb({"iam_menu": 0, "iam_permission": 126})):
            self.assertEqual(seed.verify("x"), 1)

    def test_多出一些行不算失败(self):
        """业务会继续加菜单，种子只是下限。"""
        with mock.patch.object(seed, "Db", lambda *a, **k: FakeDb({"iam_menu": 999, "iam_permission": 126})):
            self.assertEqual(seed.verify("x"), 0)

    def test_空清单必须判红(self):
        """清单被清空 = 没有任何表被校验 = 恒过；这正是空壳最容易被放行的形态。"""
        self.manifest.write_text(json.dumps({"tables": {}}), encoding="utf-8")
        with mock.patch.object(seed, "Db", lambda *a, **k: FakeDb({"iam_menu": 288})):
            self.assertEqual(seed.verify("x"), 1)


class SeedFreshnessTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="seedfresh-"))
        self.manifest = self.tmp / "seed-manifest.json"
        self.manifest.write_text(json.dumps({"tables": {"iam_menu": {"rows": 288}}}), encoding="utf-8")
        self._orig_out = seed.OUT_MANIFEST
        self._orig_specs = seed.SEED_SPECS
        seed.OUT_MANIFEST = self.manifest
        seed.SEED_SPECS = [("iam_menu", "1=1", "菜单树")]

    def tearDown(self):
        seed.OUT_MANIFEST = self._orig_out
        seed.SEED_SPECS = self._orig_specs

    def test_活库变了而种子没重生成应判红(self):
        with mock.patch.object(seed, "Db", lambda *a, **k: FakeDb({"iam_menu": 300})):
            self.assertEqual(seed.check_fresh(), 1)

    def test_一致时通过(self):
        with mock.patch.object(seed, "Db", lambda *a, **k: FakeDb({"iam_menu": 288})):
            self.assertEqual(seed.check_fresh(), 0)


class ApplyLockTest(unittest.TestCase):
    def test_返回值为None时锁仍被持有(self):
        """本函数第一版就在这里失效：调用方丢弃返回值 → CPython 回收 file 对象 → flock 跟着释放。
        锁看起来加了，实际没加，而任何单进程测试都发现不了。"""
        migrate._LOCK_FH = None
        migrate.acquire_lock("pytest-refkeep-%d" % os.getpid())
        self.assertIsNotNone(migrate._LOCK_FH, "必须由模块级强引用兜住，否则丢弃返回值即失锁")

    def _start_holder(self, name: str, sleep_s: int = 30) -> tuple[subprocess.Popen, Path]:
        """起一个真实子进程持锁。

        同步**不能靠 stdout 管道**：实测 readline() 会一直阻塞到子进程退出（等到 EOF 才拿到内容），
        于是主进程以为「已持锁」时子进程其实已跑完、锁早释放了——测试会假绿成「锁没生效」。
        这里用 ready 文件握手，主进程确认文件出现后才知道锁真的被另一个进程拿着。
        """
        ready = Path(tempfile.gettempdir()) / f"{name}.ready"
        ready.unlink(missing_ok=True)
        proc = subprocess.Popen(
            [sys.executable, "-c",
             "import importlib.util,sys,time,pathlib\n"
             f'spec=importlib.util.spec_from_file_location("m", {str(_SCRIPTS / "migrate.py")!r})\n'
             "m=importlib.util.module_from_spec(spec); spec.loader.exec_module(m)\n"
             "m.acquire_lock(sys.argv[1])\n"
             "pathlib.Path(sys.argv[2]).write_text('held')\n"
             "time.sleep(float(sys.argv[3]))\n",
             name, str(ready), str(sleep_s)],
            stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True,
        )
        for _ in range(200):  # 最多等 10s
            if ready.exists():
                return proc, ready
            if proc.poll() is not None:
                raise AssertionError("持锁子进程提前退出：" + (proc.stderr.read() if proc.stderr else ""))
            time.sleep(0.05)
        proc.kill()
        raise AssertionError("持锁子进程 10s 内未就绪")

    def test_另一进程持锁时必须拒绝(self):
        name = "pytest-mutex-%d" % os.getpid()
        proc, ready = self._start_holder(name)
        try:
            with self.assertRaises(SystemExit) as ctx:
                migrate.acquire_lock(name)
            self.assertIn("另一个 migrate.py", str(ctx.exception.code))
        finally:
            proc.terminate()
            proc.wait(timeout=10)
            ready.unlink(missing_ok=True)

    def test_持锁进程退出后可以重新获取(self):
        name = "pytest-release-%d" % os.getpid()
        proc, ready = self._start_holder(name, sleep_s=0)
        proc.wait(timeout=10)
        ready.unlink(missing_ok=True)
        migrate.acquire_lock(name)  # 前一个持有者已退出，必须能拿到
        self.assertIsNotNone(migrate._LOCK_FH)


if __name__ == "__main__":
    unittest.main(verbosity=2)
