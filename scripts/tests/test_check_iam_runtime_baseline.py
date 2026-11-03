#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""check-iam-runtime-baseline.py 的门禁自测（防门禁回归）。

判据背景见脚本 docstring：`bone-init.sql` 是新环境唯一入口，它的 IAM 运行时数据段
必须能让系统登进去且有权限；行数下限按业务闭环定。

本测试覆盖每个分支，特别覆盖三类「不该红」的形态——
它们是误报的主要来源，误报会让门禁被整体关闭，等于没有门禁。
"""

import importlib.util
import json
import os
import shutil
import tempfile
import unittest

SCRIPT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "check-iam-runtime-baseline.py",
)
_spec = importlib.util.spec_from_file_location("check_iam_runtime_baseline", SCRIPT)
mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(mod)

TABLES = ["iam_account", "iam_tenant", "iam_role", "iam_account_role", "iam_role_permission"]


def _init_sql(rows_per_table=None):
    """造一份最小 init：每张表 1 行。"""
    rows_per_table = rows_per_table or {}
    parts = []
    for t in TABLES:
        n = rows_per_table.get(t, 1)
        vals = ",".join(
            f"({i + 1},0,'x{i}','h','e','r',1,0)" for i in range(n)
        )
        parts.append(f"INSERT INTO {t} (id,tenant_id,username,password_hash,email,real_name,status,is_admin) VALUES {vals};")
    return "\n".join(parts) + "\n"


class IamRuntimeBaselineTest(unittest.TestCase):
    def setUp(self):
        self.root = tempfile.mkdtemp(prefix="iam-baseline-")
        self.init_path = os.path.join(self.root, "bone-init.sql")
        self.baseline_path = os.path.join(self.root, "baseline.json")
        with open(self.init_path, "w", encoding="utf-8") as fh:
            fh.write(_init_sql())
        with open(self.baseline_path, "w", encoding="utf-8") as fh:
            json.dump(
                {"tables": {t: {"min_rows": 1, "why": "x"} for t in TABLES}},
                fh,
            )

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def errors(self):
        with open(self.baseline_path, encoding="utf-8") as fh:
            baseline = json.load(fh)
        return mod.check_init(self.init_path, baseline)[0]

    def write_init(self, body):
        with open(self.init_path, "w", encoding="utf-8") as fh:
            fh.write(body)

    def write_baseline(self, doc):
        with open(self.baseline_path, "w", encoding="utf-8") as fh:
            json.dump(doc, fh)

    # ---------- 该红的 ----------

    def test_某表数据缺失必须判红(self):
        """缺 iam_account_role ⇒ 账号登进去是零权限。"""
        body = "\n".join(
            l for l in _init_sql().splitlines() if not l.startswith("INSERT INTO iam_account_role")
        )
        self.write_init(body + "\n")
        errs = self.errors()
        self.assertTrue(any("iam_account_role" in e for e in errs), errs)

    def test_全部表缺失必须判红(self):
        self.write_init("-- 空 init\n")
        errs = self.errors()
        self.assertTrue(len(errs) >= len(TABLES), errs)

    def test_init文件不存在必须判红(self):
        os.remove(self.init_path)
        errs = self.errors()
        self.assertTrue(any("找不到" in e for e in errs), errs)

    def test_基线缺该表必须判红(self):
        """基线里没有该表 ⇒ 该表失去校验 ⇒ 必须判红（否则恒过）。"""
        self.write_baseline({"tables": {"iam_account": {"min_rows": 1, "why": "x"}}})
        errs = self.errors()
        self.assertTrue(any("iam_role" in e and "基线文件里没有" in e for e in errs), errs)

    def test_基线与代码下界不一致必须判红(self):
        """baseline 记 5、代码记 1 ⇒ 改代码即可绕过校验 ⇒ 必须判红。"""
        doc = {"tables": {t: {"min_rows": 5, "why": "x"} for t in TABLES}}
        self.write_baseline(doc)
        errs = self.errors()
        self.assertTrue(any("下界不一致" in e for e in errs), errs)

    def test_行数低于下界必须判红(self):
        """下界提到 3 而 init 只有 1 行 ⇒ 判红。

        注意必须**同时**改代码里的 MIN_ROWS 与 baseline.json（两者一致性是另一条判据），
        只改 baseline 会先撞上「下界不一致」而不是「低于下界」。
        """
        orig = dict(mod.MIN_ROWS)
        try:
            mod.MIN_ROWS.update({t: 3 for t in TABLES})
            self.write_baseline(
                {"tables": {t: {"min_rows": 3, "why": "x"} for t in TABLES}}
            )
            errs = self.errors()
            self.assertTrue(any("低于下界" in e for e in errs), errs)
        finally:
            mod.MIN_ROWS.clear()
            mod.MIN_ROWS.update(orig)

    # ---------- 不该红的（误报防护） ----------

    def test_足量数据不应判红(self):
        self.assertEqual(self.errors(), [])

    def test_行数高于下界不应判红(self):
        self.write_init(_init_sql({t: 5 for t in TABLES}))
        self.assertEqual(self.errors(), [])

    def test_注释里的INSERT不应被采集(self):
        """注释里的 INSERT 字样不该被当成数据。

        关键场景是**冒充缺失表的数据**：真 init 里没有 iam_account_role，
        注释里却写着一行 INSERT —— 若不去注释，该表会被算成「有 1 行」而下界通过，
        门禁就失去了判别力（删掉真实数据 + 留一行注释即可骗过）。
        """
        body = "\n".join(
            l for l in _init_sql().splitlines() if not l.startswith("INSERT INTO iam_account_role")
        )
        body = "-- INSERT INTO iam_account_role (id) VALUES (1);\n" + body + "\n"
        self.write_init(body)
        errs = self.errors()
        self.assertTrue(any("iam_account_role" in e for e in errs), errs)

    def test_字符串里的逗号与括号不应打乱元组切分(self):
        """'a,b(c)' 这种值不该让元组计数错位。"""
        body = (
            "INSERT INTO iam_account (id,tenant_id,username,password_hash,email,real_name,status,is_admin) "
            "VALUES (1,0,'admin','h','a,b(c)','r',1,0);\n"
        )
        for t in TABLES:
            if t == "iam_account":
                continue
            body += (
                f"INSERT INTO {t} (id,tenant_id,username,password_hash,email,real_name,status,is_admin) "
                f"VALUES (1,0,'x','h','e','r',1,0);\n"
            )
        self.write_init(body)
        self.assertEqual(self.errors(), [])

    def test_多条INSERT语句应累加(self):
        """同一张表分多条 INSERT 灌 ⇒ 行数应累加（不能只看第一条）。"""
        body = "INSERT INTO iam_account (id,tenant_id,username,password_hash,email,real_name,status,is_admin) VALUES (1,0,'a','h','e','r',1,0);\n"
        body += "INSERT INTO iam_account (id,tenant_id,username,password_hash,email,real_name,status,is_admin) VALUES (2,0,'b','h','e','r',1,0);\n"
        for t in TABLES:
            if t == "iam_account":
                continue
            body += (
                f"INSERT INTO {t} (id,tenant_id,username,password_hash,email,real_name,status,is_admin) "
                f"VALUES (1,0,'x','h','e','r',1,0);\n"
            )
        self.write_init(body)
        self.write_baseline(
            {"tables": {t: {"min_rows": 1, "why": "x"} for t in TABLES}}
        )
        self.assertEqual(self.errors(), [])


if __name__ == "__main__":
    unittest.main()
