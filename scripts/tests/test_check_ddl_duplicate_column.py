#!/usr/bin/env python3
"""check-ddl-duplicate-column.py 的门禁自测（防门禁回归）。

判据背景见 scripts/check-ddl-duplicate-column.py 的模块 docstring：
同一 (表, 列) 被两处声明，且其中至少一处是**无条件 ADD COLUMN** ⇒ 必炸 ERROR 1060。

本测试用**最小化夹具**（临时目录 + 手写 .sql）覆盖判据的每个分支，
特别覆盖三类「不该红」的形态（守卫 ADD / IF NOT EXISTS CREATE / 跨通道同名）——
它们是误报的主要来源，误报会让门禁被整体关闭，等于没有门禁。
"""

import importlib.util
import os
import shutil
import tempfile
import unittest

SCRIPT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "check-ddl-duplicate-column.py",
)
_spec = importlib.util.spec_from_file_location("check_ddl_dup", SCRIPT)
mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(mod)

EFF = ("scripts", "migration")
REF = ("bone-apps", "app", "src", "main", "resources", "db", "migration")


def _write(root, rel_dir, name, body):
    d = os.path.join(root, *rel_dir)
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, name), "w", encoding="utf-8") as fh:
        fh.write(body)


class DuplicateColumnGateTest(unittest.TestCase):
    def setUp(self):
        self.root = tempfile.mkdtemp(prefix="ddl-dup-")
        # 默认清空豁免表：夹具里不会真的出现仓库那一条，
        # 否则「豁免已失效」判定会让每个用例都红（豁免是相对当前仓库判定的）。
        self._orig_exemptions = mod.KNOWN_EXEMPTIONS
        mod.KNOWN_EXEMPTIONS = {}

    def tearDown(self):
        mod.KNOWN_EXEMPTIONS = self._orig_exemptions
        shutil.rmtree(self.root, ignore_errors=True)

    def errors(self):
        return mod.run_check(self.root)[0]

    # ---------- 该红的 ----------

    def test_裸ADD撞已有建表必须判红(self):
        """V21 同款形态：建表已含该列，另一个脚本又无条件 ADD ⇒ 1060。"""
        _write(self.root, REF, "V1__init.sql", "CREATE TABLE t_x (\n  id BIGINT NOT NULL,\n  c1 VARCHAR(8) NOT NULL DEFAULT ''\n) ENGINE=InnoDB;\n")
        _write(self.root, REF, "V2__add.sql", "ALTER TABLE t_x ADD COLUMN c1 VARCHAR(8) NOT NULL DEFAULT '';\n")
        errs = self.errors()
        self.assertTrue(any("t_x.c1" in e for e in errs), errs)

    def test_两个脚本各自裸ADD同一列必须判红(self):
        _write(self.root, EFF, "0001_a.sql", "ALTER TABLE t_y ADD COLUMN c2 BIGINT NULL;\n")
        _write(self.root, EFF, "0002_b.sql", "ALTER TABLE t_y ADD COLUMN c2 BIGINT NULL;\n")
        errs = self.errors()
        self.assertTrue(any("t_y.c2" in e for e in errs), errs)

    def test_同文件内裸ADD撞自己的建表必须判红(self):
        """0046 同款：建表含 created_by/updated_by，后面又 ADD 同一列。"""
        _write(
            self.root,
            EFF,
            "0003_self.sql",
            "CREATE TABLE meta_entity_ui_schema (\n"
            "  id BIGINT NOT NULL,\n"
            "  created_by BIGINT NULL,\n"
            "  updated_by BIGINT NULL,\n"
            "  PRIMARY KEY (id)\n"
            ") ENGINE=InnoDB;\n"
            "ALTER TABLE meta_entity_ui_schema ADD COLUMN updated_by BIGINT NULL;\n",
        )
        errs = self.errors()
        self.assertTrue(any("meta_entity_ui_schema.updated_by" in e for e in errs), errs)

    def test_扫不到任何sql必须判红(self):
        """防假绿：路径失效 ⇒ 门禁恒绿 ⇒ 必须判失败。

        该闸必须在 run_check() 内（而非 main()），否则单元测试覆盖不到它，
        变异验证也证明不了它有判别力。
        """
        errors, _exempted, total_files, _keys = mod.run_check(self.root)
        self.assertEqual(total_files, 0)
        self.assertTrue(any("D0" in e for e in errors), errors)

    # ---------- 不该红的（误报防护） ----------

    def test_有information_schema守卫的ADD撞建表不应判红(self):
        """0046/0085/0090 同款：探测 + 动态 DDL，列已存在时是 no-op。"""
        _write(
            self.root,
            EFF,
            "0004_guarded.sql",
            "CREATE TABLE meta_entity_ui_schema (\n"
            "  id BIGINT NOT NULL,\n"
            "  created_by BIGINT NULL,\n"
            "  PRIMARY KEY (id)\n"
            ") ENGINE=InnoDB;\n"
            "SET @ddl := (SELECT IF(COUNT(*) = 0,\n"
            "  'ALTER TABLE meta_entity_ui_schema ADD COLUMN created_by BIGINT NULL',\n"
            "  'DO 0') FROM information_schema.COLUMNS\n"
            "  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meta_entity_ui_schema'\n"
            "    AND COLUMN_NAME = 'created_by');\n"
            "PREPARE stmt FROM @ddl;\nEXECUTE stmt;\nDEALLOCATE PREPARE stmt;\n",
        )
        self.assertEqual(self.errors(), [])

    def test_IF_NOT_EXISTS的建表撞建表不应判红(self):
        """0047/0058 同款：第二次建表是 no-op。"""
        _write(self.root, EFF, "0005_a.sql", "CREATE TABLE IF NOT EXISTS t_z (\n  id BIGINT NOT NULL,\n  c3 VARCHAR(8) NOT NULL DEFAULT ''\n) ENGINE=InnoDB;\n")
        _write(self.root, EFF, "0006_b.sql", "CREATE TABLE IF NOT EXISTS t_z (\n  id BIGINT NOT NULL,\n  c3 VARCHAR(8) NOT NULL DEFAULT ''\n) ENGINE=InnoDB;\n")
        self.assertEqual(self.errors(), [])

    def test_跨通道同名不算重复(self):
        """参考态与生效通道是同一批变更的两种形态，跨通道同名是设计使然。"""
        _write(self.root, EFF, "0007_eff.sql", "ALTER TABLE t_adj_liability ADD COLUMN invoice_deduct DECIMAL(12,2) NOT NULL DEFAULT 0;\n")
        _write(self.root, REF, "V12__ref.sql", "ALTER TABLE t_adj_liability ADD COLUMN invoice_deduct DECIMAL(12,2) NOT NULL DEFAULT 0;\n")
        self.assertEqual(self.errors(), [])

    def test_无重复应当通过(self):
        _write(self.root, EFF, "0008_ok.sql", "CREATE TABLE IF NOT EXISTS t_ok (\n  id BIGINT NOT NULL,\n  c4 VARCHAR(8) NOT NULL DEFAULT ''\n) ENGINE=InnoDB;\n")
        self.assertEqual(self.errors(), [])

    # ---------- D2：表级，同表被两处以上裸 CREATE（ERROR 1050） ----------

    def test_同文件裸CREATE撞裸CREATE必须判红(self):
        """同文件里同一张表裸 CREATE 两次 ⇒ 第二个必报 ERROR 1050。"""
        _write(
            self.root,
            EFF,
            "0010_dup_create.sql",
            "CREATE TABLE t_dup (\n  id BIGINT NOT NULL\n) ENGINE=InnoDB;\n"
            "CREATE TABLE t_dup (\n  id BIGINT NOT NULL\n) ENGINE=InnoDB;\n",
        )
        errs = self.errors()
        self.assertTrue(any("D2" in e and "t_dup" in e for e in errs), errs)

    def test_同文件裸CREATE撞裸CREATE有DROP保护不应判红(self):
        """先 DROP TABLE IF EXISTS 再 CREATE = 显式重建，合法。"""
        _write(
            self.root,
            EFF,
            "0011_rebuild.sql",
            "DROP TABLE IF EXISTS t_rebuild;\n"
            "CREATE TABLE t_rebuild (\n  id BIGINT NOT NULL\n) ENGINE=InnoDB;\n"
            "DROP TABLE IF EXISTS t_rebuild;\n"
            "CREATE TABLE t_rebuild (\n  id BIGINT NOT NULL\n) ENGINE=InnoDB;\n",
        )
        self.assertEqual(self.errors(), [])

    def test_两次IF_NOT_EXISTS建表不应判红(self):
        """CREATE TABLE IF NOT EXISTS 重复 ⇒ 第二次 no-op（0047/0058 同款）。"""
        _write(
            self.root,
            EFF,
            "0012_ine.sql",
            "CREATE TABLE IF NOT EXISTS t_ine (\n  id BIGINT NOT NULL\n) ENGINE=InnoDB;\n"
            "CREATE TABLE IF NOT EXISTS t_ine (\n  id BIGINT NOT NULL\n) ENGINE=InnoDB;\n",
        )
        self.assertEqual(self.errors(), [])

    def test_跨文件裸CREATE撞车不在D2范围(self):
        """D2 只查同文件内：跨文件需按迁移执行序做时序分析（重建合法），不纳入。"""
        _write(self.root, EFF, "0013_a.sql", "CREATE TABLE t_cross (\n  id BIGINT NOT NULL\n) ENGINE=InnoDB;\n")
        _write(self.root, EFF, "0014_b.sql", "CREATE TABLE t_cross (\n  id BIGINT NOT NULL\n) ENGINE=InnoDB;\n")
        self.assertEqual(self.errors(), [])

    # ---------- 「只声明一次」不应判红（判别的下界） ----------

    def test_单个裸ADD只声明一次不应判红(self):
        """D1 要求同一列被声明 ≥2 次：单个裸 ADD 是正常的新增列，不该红。"""
        _write(self.root, EFF, "0015_single_add.sql", "ALTER TABLE t_single ADD COLUMN c1 BIGINT NULL;\n")
        self.assertEqual(self.errors(), [])

    def test_单次裸CREATE不应判红(self):
        """D2 要求同表被裸 CREATE ≥2 次：单次建表是正常的，不该红。"""
        _write(self.root, EFF, "0016_single_create.sql", "CREATE TABLE t_once (\n  id BIGINT NOT NULL\n) ENGINE=InnoDB;\n")
        self.assertEqual(self.errors(), [])

    def test_裸CREATE撞IF_NOT_EXISTS_CREATE不应判红(self):
        """D1 只抓「无条件 ADD COLUMN」；CREATE 侧不参与无守卫判定。

        裸 CREATE + IF NOT EXISTS CREATE：若表不存在，第一个建表、第二个 no-op；
        若表已存在，第一个撞 1050（由 D2 在「裸 CREATE ≥2 次」时覆盖，此处不重复红）。
        """
        _write(
            self.root,
            EFF,
            "0017_mixed.sql",
            "CREATE TABLE t_mixed (\n  id BIGINT NOT NULL\n) ENGINE=InnoDB;\n"
            "CREATE TABLE IF NOT EXISTS t_mixed (\n  id BIGINT NOT NULL\n) ENGINE=InnoDB;\n",
        )
        self.assertEqual(self.errors(), [])

    # ---------- 豁免纪律 ----------

    def test_豁免命中应放行且可见(self):
        mod.KNOWN_EXEMPTIONS = {
            ("参考态 db/migration", "t_x.c1"): "存量重复声明，理由写足二十字以上以便通过豁免理由长度检查。" * 1
        }
        _write(self.root, REF, "V1__init.sql", "CREATE TABLE t_x (\n  id BIGINT NOT NULL,\n  c1 VARCHAR(8) NOT NULL DEFAULT ''\n) ENGINE=InnoDB;\n")
        _write(self.root, REF, "V2__add.sql", "ALTER TABLE t_x ADD COLUMN c1 VARCHAR(8) NOT NULL DEFAULT '';\n")
        errors, exempted, _tf, _k = mod.run_check(self.root)
        self.assertEqual(errors, [])
        self.assertEqual(len(exempted), 1)

    def test_豁免已失效必须判红(self):
        """豁免只可收缩：不再命中的豁免必须删掉，否则白名单只增不减。"""
        mod.KNOWN_EXEMPTIONS = {
            ("生效通道 scripts/migration", "t_gone.c9"): "这条豁免在夹具里不会命中，属于已失效豁免，必须被判红要求删除。" * 1
        }
        _write(self.root, EFF, "0009_ok.sql", "CREATE TABLE IF NOT EXISTS t_ok (\n  id BIGINT NOT NULL\n) ENGINE=InnoDB;\n")
        errors = self.errors()
        self.assertTrue(any("已不再命中" in e for e in errors), errors)

    def test_豁免理由过短必须判红(self):
        """「不炸的理由 + 怎么治」写不清 ⇒ 豁免无效，否则会长成无主白名单。"""
        mod.KNOWN_EXEMPTIONS = {("参考态 db/migration", "t_x.c1"): "太短"}
        _write(self.root, REF, "V1__init.sql", "CREATE TABLE t_x (\n  id BIGINT NOT NULL,\n  c1 VARCHAR(8) NOT NULL DEFAULT ''\n) ENGINE=InnoDB;\n")
        _write(self.root, REF, "V2__add.sql", "ALTER TABLE t_x ADD COLUMN c1 VARCHAR(8) NOT NULL DEFAULT '';\n")
        errors = self.errors()
        self.assertTrue(any("理由不足" in e for e in errors), errors)


if __name__ == "__main__":
    unittest.main()
