"""表征测试：锁定 check-migration-init-coverage.py（G39）的判据。

这个门禁守的是本仓最容易「静默不等价」的一处：`bone-init.sql`（新环境唯一入口）与
`scripts/migration/*.sql`（存量环境增量）必须指向同一结构。落一条迁移建表却没并进全量，
新环境就缺表，而缺表通常不报错——0072 头部的实证是「代码已上线、DDL 没跟上」，
`ClaimCaseTrackRecorder` 打 ERROR 后吞异常，审计流水全丢、页面照常 200。

所以每个用例都是「构造该缺陷 ⇒ 必须退 1」，另外两条是反向保护：
  · 基线允许存在（存量缺口需要单独评审），但**只可收缩**——已不需要的豁免必须删掉；
  · init 为空必须判红（空集合恒过是门禁最常见的假绿）。

运行：``python3 scripts/tests/test_check_migration_init_coverage.py -v``
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

_SCRIPTS = Path(__file__).resolve().parent.parent
_SPEC = importlib.util.spec_from_file_location(
    "check_migration_init_coverage", _SCRIPTS / "check-migration-init-coverage.py")
gate = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(gate)


class InitCoverageGateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.root = Path(tempfile.mkdtemp(prefix="init-cov-"))
        self.mig = self.root / "migration"
        self.mig.mkdir()
        self.init = self.root / "bone-init.sql"
        self.manifest = self.mig / "migration-manifest.json"
        self.baseline = self.mig / "init-coverage-baseline.json"

    def tearDown(self) -> None:
        shutil.rmtree(self.root, ignore_errors=True)

    def write_init(self, *tables: str) -> None:
        self.init.write_text("".join(f"CREATE TABLE {t} (\n  id BIGINT\n);\n" for t in tables), encoding="utf-8")

    def write_migration(self, name: str, *tables: str) -> None:
        (self.mig / name).write_text(
            "".join(f"CREATE TABLE IF NOT EXISTS `{t}` (\n  id BIGINT\n);\n" for t in tables), encoding="utf-8")

    def write_manifest(self, files) -> None:
        entries = [{"order": i + 1, "file": f, "kind": "ddl", "summary": "建表用于覆盖率测试"} for i, f in enumerate(files)]
        self.manifest.write_text(json.dumps({"entries": entries}, ensure_ascii=False), encoding="utf-8")

    def write_baseline(self, rows) -> None:
        self.baseline.write_text(json.dumps({"tables": rows}, ensure_ascii=False), encoding="utf-8")

    def run_gate(self) -> int:
        argv = ["check-migration-init-coverage.py", "--init", str(self.init),
                "--migration-dir", str(self.mig), "--manifest", str(self.manifest),
                "--baseline", str(self.baseline)]
        buf = io.StringIO()
        old = sys.argv
        try:
            sys.argv = argv
            with contextlib.redirect_stdout(buf), contextlib.redirect_stderr(buf):
                return gate.main()
        finally:
            sys.argv = old

    # ---- 用例 ----
    def test_全覆盖应通过(self):
        self.write_init("t_a", "t_b")
        self.write_migration("0001_x.sql", "t_a", "t_b")
        self.write_manifest(["0001_x.sql"])
        self.write_baseline([])
        self.assertEqual(self.run_gate(), 0)

    def test_建表未并入全量应判红(self):
        """核心判据：新环境灌 init 缺表，且不会报错，只能靠门禁发现。"""
        self.write_init("t_a")
        self.write_migration("0001_x.sql", "t_a", "t_missing")
        self.write_manifest(["0001_x.sql"])
        self.write_baseline([])
        self.assertEqual(self.run_gate(), 1)

    def test_基线登记后放行(self):
        self.write_init("t_a")
        self.write_migration("0001_x.sql", "t_a", "t_known_gap")
        self.write_manifest(["0001_x.sql"])
        self.write_baseline([{"table": "t_known_gap", "from": "0001_x.sql",
                              "reason": "存量缺口，并入全量属 L3 变更，待单独评审"}])
        self.assertEqual(self.run_gate(), 0)

    def test_基线只可收缩(self):
        """表已并入 init 后必须从基线删除，否则基线变成永久白名单。"""
        self.write_init("t_a", "t_known_gap")
        self.write_migration("0001_x.sql", "t_a", "t_known_gap")
        self.write_manifest(["0001_x.sql"])
        self.write_baseline([{"table": "t_known_gap", "from": "0001_x.sql",
                              "reason": "存量缺口，并入全量属 L3 变更，待单独评审"}])
        self.assertEqual(self.run_gate(), 1)

    def test_备份表不算结构(self):
        """脚本里的 bak_/tmp_ 是执行前的临时拷贝，不该要求并进全量。"""
        self.write_init("t_a")
        self.write_migration("0001_x.sql", "t_a", "bak_t_a_0001", "tmp_t_a_0001")
        self.write_manifest(["0001_x.sql"])
        self.write_baseline([])
        self.assertEqual(self.run_gate(), 0)

    def test_init为空必须判红(self):
        self.init.write_text("-- 空\n", encoding="utf-8")
        self.write_migration("0001_x.sql", "t_a")
        self.write_manifest(["0001_x.sql"])
        self.write_baseline([])
        self.assertEqual(self.run_gate(), 1, "空集合恒过是门禁最常见的假绿，必须判红")


if __name__ == "__main__":
    unittest.main(verbosity=2)
