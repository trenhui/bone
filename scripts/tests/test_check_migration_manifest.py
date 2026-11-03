"""表征测试：锁定 check-migration-manifest.py 的八条判据（M1–M8）。

门禁脚本文件名含连字符，不能用 import 按名导入，故用 importlib 按字符串路径加载，
再用 `--migration-dir/--manifest` 指向临时目录构造最小化场景，断言 main() 退出码。

为什么要测门禁自身
------------------
这份清单的全部价值在于「新增迁移无法绕过它」。若判据被改坏（比如 M1 的比对方向写反，
或 M6 只比组号不比文件集合），门禁会一路绿着，而顺序与重号重新回到无人知晓的状态——
这正是它要治的病。故每个用例都是一个「构造该缺陷 ⇒ 必须退 1」的负向场景。

运行：``python3 scripts/tests/test_check_migration_manifest.py -v``
"""

from __future__ import annotations

import contextlib
import importlib.util
import io
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parent.parent
_GATE_PATH = str(SCRIPTS_DIR / "check-migration-manifest.py")
_SPEC = importlib.util.spec_from_file_location("check_migration_manifest", _GATE_PATH)
gate = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(gate)

DDL_SQL = "CREATE TABLE t (id BIGINT);\n"
DML_SQL = "UPDATE t SET a = 1;\n"
READ_SQL = "SELECT 1;\n"


class MigrationManifestGateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.root = Path(tempfile.mkdtemp(prefix="mig-manifest-"))
        self.mig = self.root / "migration"
        self.mig.mkdir()
        self.manifest = self.root / "migration-manifest.json"

    def tearDown(self) -> None:
        shutil.rmtree(self.root, ignore_errors=True)

    # ---- 场景构造 ----
    def write(self, name: str, content: str = DDL_SQL) -> None:
        (self.mig / name).write_text(content, encoding="utf-8")

    def manifest_of(self, entries, conflicts=None, extra=None) -> dict:
        doc = {
            "entries": entries,
            "executableCount": sum(1 for e in entries if isinstance(e.get("order"), int)),
            "ordinalConflicts": conflicts or [],
        }
        doc.update(extra or {})
        self.manifest.write_text(json.dumps(doc, ensure_ascii=False), encoding="utf-8")
        return doc

    def run_gate(self) -> int:
        argv = ["check-migration-manifest.py", "--root", str(self.root),
                "--migration-dir", str(self.mig), "--manifest", str(self.manifest)]
        buf = io.StringIO()
        old = sys.argv
        try:
            sys.argv = argv
            with contextlib.redirect_stdout(buf), contextlib.redirect_stderr(buf):
                return gate.main()
        finally:
            sys.argv = old

    def base(self):
        """两个合规脚本 + 一个文档附件的合规最小集。"""
        self.write("0001_a.sql", DDL_SQL)
        self.write("0002_b.sql", DML_SQL)
        self.write("0002_README.md", "# b\n")
        return [
            {"order": 1, "file": "0001_a.sql", "kind": "ddl", "summary": "建表 t（id 主键）"},
            {"order": 2, "file": "0002_b.sql", "kind": "dml", "summary": "回填字段 a 的默认值"},
            {"order": None, "file": "0002_README.md", "kind": "doc", "summary": "0002 的执行路线说明文档"},
        ]

    # ---- 用例 ----
    def test_合规最小集应通过(self):
        self.manifest_of(self.base(), conflicts=[
            {"ordinal": "0002", "files": ["0002_README.md", "0002_b.sql"], "verdict": "同变更附件：README 为说明文档"}
        ])
        self.assertEqual(self.run_gate(), 0)

    def test_M1_存在即登记(self):
        entries = self.base()
        self.write("0003_c.sql", DDL_SQL)  # 磁盘多出未登记脚本
        self.manifest_of(entries, conflicts=[{"ordinal": "0002", "files": ["0002_README.md", "0002_b.sql"],
                                              "verdict": "同变更附件：README 为说明文档"}])
        self.assertEqual(self.run_gate(), 1)

    def test_M2_登记即存在(self):
        entries = self.base()
        entries.append({"order": 3, "file": "0099_gone.sql", "kind": "ddl", "summary": "已被删除的历史脚本条目"})
        self.manifest_of(entries, conflicts=[{"ordinal": "0002", "files": ["0002_README.md", "0002_b.sql"],
                                              "verdict": "同变更附件：README 为说明文档"}])
        self.assertEqual(self.run_gate(), 1)

    def test_M3_order重复(self):
        entries = self.base()
        entries[1]["order"] = 1
        self.manifest_of(entries, conflicts=[{"ordinal": "0002", "files": ["0002_README.md", "0002_b.sql"],
                                              "verdict": "同变更附件：README 为说明文档"}])
        self.assertEqual(self.run_gate(), 1)

    def test_M4_order与词典序分叉未声明(self):
        entries = self.base()
        entries[0]["order"], entries[1]["order"] = 2, 1  # 逆序但不声明理由
        self.manifest_of(entries, conflicts=[{"ordinal": "0002", "files": ["0002_README.md", "0002_b.sql"],
                                              "verdict": "同变更附件：README 为说明文档"}])
        self.assertEqual(self.run_gate(), 1)

    def test_M4_声明理由后放行(self):
        entries = self.base()
        entries[0]["order"], entries[1]["order"] = 2, 1
        self.manifest_of(entries, conflicts=[{"ordinal": "0002", "files": ["0002_README.md", "0002_b.sql"],
                                              "verdict": "同变更附件：README 为说明文档"}],
                         extra={"outOfLexOrder": [
                             {"file": "0001_a.sql", "reason": "必须晚于 0002 执行，因为依赖其建好的索引"},
                             {"file": "0002_b.sql", "reason": "必须早于 0001 执行，因为要先把数据补齐"},
                         ]})
        self.assertEqual(self.run_gate(), 0)

    def test_M5_kind与内容不符(self):
        entries = self.base()
        entries[0]["kind"] = "verify"  # 明明含 CREATE TABLE
        self.manifest_of(entries, conflicts=[{"ordinal": "0002", "files": ["0002_README.md", "0002_b.sql"],
                                              "verdict": "同变更附件：README 为说明文档"}])
        self.assertEqual(self.run_gate(), 1)

    def test_M5_动态DDL不得被判成只读(self):
        """条件 DDL（SET @ddl + PREPARE/EXECUTE）剥离字面量后只剩 PREPARE，必须仍判为 ddl。"""
        dyn = ("SET @ddl := IF(1 = 1, 'ALTER TABLE t ADD COLUMN a INT', 'SELECT 1');\n"
               "PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;\n")
        self.write("0001_a.sql", dyn)
        self.write("0002_b.sql", DML_SQL)
        self.manifest_of([
            {"order": 1, "file": "0001_a.sql", "kind": "ddl", "summary": "条件补列 a（探测后 PREPARE）"},
            {"order": 2, "file": "0002_b.sql", "kind": "dml", "summary": "回填字段 a 的默认值"},
        ])
        self.assertEqual(self.run_gate(), 0)

    def test_M6_重号未登记(self):
        entries = self.base()
        self.manifest_of(entries)  # 故意不登记 0002 重号组
        self.assertEqual(self.run_gate(), 1)

    def test_M6_登记组与文件集合不符(self):
        self.write("0001_a.sql", DDL_SQL)
        self.write("0002_b.sql", DML_SQL)
        self.write("0002_c.sql", DML_SQL)
        self.manifest_of([
            {"order": 1, "file": "0001_a.sql", "kind": "ddl", "summary": "建表 t（id 主键）"},
            {"order": 2, "file": "0002_b.sql", "kind": "dml", "summary": "回填字段 a 的默认值"},
            {"order": 3, "file": "0002_c.sql", "kind": "dml", "summary": "回填字段 c 的默认值"},
        ], conflicts=[{"ordinal": "0002", "files": ["0002_b.sql"], "verdict": "登记漏了 0002_c，必须被判红"}])
        self.assertEqual(self.run_gate(), 1)

    def test_M7_summary占位(self):
        entries = self.base()
        entries[1]["summary"] = "TODO"
        self.manifest_of(entries, conflicts=[{"ordinal": "0002", "files": ["0002_README.md", "0002_b.sql"],
                                              "verdict": "同变更附件：README 为说明文档"}])
        self.assertEqual(self.run_gate(), 1)

    def test_M8_requires逆序(self):
        entries = self.base()
        entries[1]["requires"] = ["0001_a.sql"]  # order=2 依赖 order=1，合规
        self.manifest_of(entries, conflicts=[{"ordinal": "0002", "files": ["0002_README.md", "0002_b.sql"],
                                              "verdict": "同变更附件：README 为说明文档"}])
        self.assertEqual(self.run_gate(), 0, "先验合规方向应放行")

        entries[0]["order"], entries[1]["order"] = 2, 1
        self.manifest_of(entries, conflicts=[{"ordinal": "0002", "files": ["0002_README.md", "0002_b.sql"],
                                              "verdict": "同变更附件：README 为说明文档"}],
                         extra={"outOfLexOrder": [
                             {"file": "0001_a.sql", "reason": "必须晚于 0002 执行，因为依赖其建好的索引"},
                             {"file": "0002_b.sql", "reason": "必须早于 0001 执行，因为要先把数据补齐"},
                         ]})
        self.assertEqual(self.run_gate(), 1, "依赖项排在被依赖项之后必须判红")

    # ---- M1 子目录 / M9 元数据 / M10 retired（2026-10-30 补）----
    def test_M1_子目录里的迁移不得隐身(self):
        """实证过的洞：gate 用 os.listdir + isfile 只扫本层，子目录里的 .sql 完全不被发现。"""
        self.manifest_of(self.base(), conflicts=[
            {"ordinal": "0002", "files": ["0002_README.md", "0002_b.sql"], "verdict": "同变更附件：README 为说明文档"}
        ])
        (self.mig / "archive").mkdir()
        (self.mig / "archive" / "0099_zz.sql").write_text(DDL_SQL, encoding="utf-8")
        self.assertEqual(self.run_gate(), 1, "子目录里的脚本对门禁隐身，必须直接禁止子目录")

    def test_M9_账本表名与migrate_不一致(self):
        """账本表名在 manifest 与 migrate.py 各写一遍 = 下一次漂移；必须钉死一致。"""
        (self.root / "bone-init.sql").write_text("-- 全量基线\n", encoding="utf-8")
        (self.root / "migrate.py").write_text('JOURNAL = "schema_migration_journal"\n', encoding="utf-8")
        entries = self.base()
        meta = {"journalTable": "migration_journal_typo", "baseline": "bone-init.sql",
                "frozenAt": "2026-10-30", "appliesTo": "store-incremental"}
        self.manifest_of(entries, conflicts=[
            {"ordinal": "0002", "files": ["0002_README.md", "0002_b.sql"], "verdict": "同变更附件：README 为说明文档"}
        ], extra=meta)
        self.assertEqual(self.run_gate(), 1)

        meta["journalTable"] = "schema_migration_journal"
        self.manifest_of(entries, conflicts=[
            {"ordinal": "0002", "files": ["0002_README.md", "0002_b.sql"], "verdict": "同变更附件：README 为说明文档"}
        ], extra=meta)
        self.assertEqual(self.run_gate(), 0, "表名对齐后应放行")

    def test_M9_元数据只声明一半(self):
        """只声明一部分必须判红：否则「删掉某个键」就成了绕过检查的手法。"""
        self.manifest_of(self.base(), conflicts=[
            {"ordinal": "0002", "files": ["0002_README.md", "0002_b.sql"], "verdict": "同变更附件：README 为说明文档"}
        ], extra={"journalTable": "schema_migration_journal"})
        self.assertEqual(self.run_gate(), 1)

    def test_M10_retired_文件仍在磁盘(self):
        entries = self.base()
        self.manifest_of(entries, conflicts=[
            {"ordinal": "0002", "files": ["0002_README.md", "0002_b.sql"], "verdict": "同变更附件：README 为说明文档"}
        ], extra={"retired": [{"file": "0001_a.sql", "since": "2026-10-30",
                               "reason": "已并入 bone-init.sql，存量环境均已越过"}]})
        self.assertEqual(self.run_gate(), 1, "文件还在就是没下线")

    def test_M10_retired_合规(self):
        entries = self.base()
        self.manifest_of(entries, conflicts=[
            {"ordinal": "0002", "files": ["0002_README.md", "0002_b.sql"], "verdict": "同变更附件：README 为说明文档"}
        ], extra={"retired": [{"file": "0000_old.sql", "since": "2026-10-30",
                               "reason": "已并入 bone-init.sql，存量环境均已越过该版本"}]})
        self.assertEqual(self.run_gate(), 0)


class MigrationManifestTrackedGateTest(unittest.TestCase):
    """M11：登记项必须已被 git 跟踪。

    为什么单独一个测试类
    ------------------
    M11 判的是「文件在不在 git 索引里」，必须有一个**真实的 git 仓库**才能构造。
    上面那个类用 `tempfile.mkdtemp()` 造裸目录（不是 git 仓库），M11 在那里只能跳过——
    所以本类自己 `git init` 一个真仓库，并逐条验证三种形态：

      · 文件已 add（未 commit）→ 放行（提交过程中的正常中间态）
      · 文件完全没 add（只在工作树）→ 判红（这就是 0100 丢失缺陷的形态）
      · 文件被登记但 git 不可用 → 跳过而非误红（无 git 环境的最小化夹具仍能跑）
    """

    def setUp(self) -> None:
        self.root = Path(tempfile.mkdtemp(prefix="mig-manifest-git-"))
        self.mig = self.root / "scripts" / "migration"
        self.mig.mkdir(parents=True)
        self.manifest = self.mig / "migration-manifest.json"
        self.git("init", "-q")
        self.git("config", "user.email", "gate@test.local")
        self.git("config", "user.name", "gate")

    def tearDown(self) -> None:
        shutil.rmtree(self.root, ignore_errors=True)

    def git(self, *args: str, env_index: str | None = None) -> str:
        """跑 git 命令。

        默认**剥离 GIT_INDEX_FILE / GIT_DIR / GIT_WORK_TREE**：本测试可能运行在
        `git commit` 的 pre-commit 环境里，而那套环境带着 commit 用的**临时索引**
        （只含本次提交涉及的文件）与 worktree 相关的 GIT_DIR/GIT_WORK_TREE。
        若不剥离，夹具里的 `git commit` / `git add` 会写到**临时索引**上——
        表现为 `git commit` 返回 1、或 `git add` 之后 `ls-files` 仍查不到文件。
        """
        env = {k: v for k, v in os.environ.items()
               if k not in ("GIT_INDEX_FILE", "GIT_DIR", "GIT_WORK_TREE")}
        if env_index is not None:
            env["GIT_INDEX_FILE"] = env_index
        return subprocess.run(
            ["git", *args], cwd=self.root, capture_output=True, text=True, check=True, env=env
        ).stdout

    def write(self, name: str, content: str = DDL_SQL) -> None:
        (self.mig / name).write_text(content, encoding="utf-8")

    def manifest_of(self, entries, conflicts=None) -> None:
        doc = {
            "entries": entries,
            "executableCount": sum(1 for e in entries if isinstance(e.get("order"), int)),
            "ordinalConflicts": conflicts or [],
        }
        self.manifest.write_text(json.dumps(doc, ensure_ascii=False), encoding="utf-8")

    def run_gate(self) -> int:
        argv = ["check-migration-manifest.py", "--root", str(self.root),
                "--migration-dir", str(self.mig), "--manifest", str(self.manifest)]
        buf = io.StringIO()
        old = sys.argv
        try:
            sys.argv = argv
            with contextlib.redirect_stdout(buf), contextlib.redirect_stderr(buf):
                return gate.main()
        finally:
            sys.argv = old

    def one_entry(self):
        return [{"order": 1, "file": "0001_a.sql", "kind": "ddl", "summary": "建表 t（id 主键）"}]

    def test_M11_文件未入索引必须判红(self):
        """0100 丢失缺陷的形态：清单已登记、文件在工作树，但从未 git add。"""
        self.write("0001_a.sql")
        self.manifest_of(self.one_entry())
        # 注意：刻意**不** git add
        self.assertEqual(self.run_gate(), 1, "工作树有文件但未入索引 ⇒ 必须判红")

    def test_M11_已入索引未提交应放行(self):
        """「已 add 未 commit」是提交过程中的正常中间态，不能误红。"""
        self.write("0001_a.sql")
        self.manifest_of(self.one_entry())
        self.git("add", "-f", "scripts/migration/0001_a.sql")
        self.assertEqual(self.run_gate(), 0, "已 add 即放行")

    def test_M11_已提交应放行(self):
        self.write("0001_a.sql")
        self.manifest_of(self.one_entry())
        self.git("add", "-f", "scripts/migration/0001_a.sql", "scripts/migration/migration-manifest.json")
        self.git("commit", "-q", "-m", "init")
        self.assertEqual(self.run_gate(), 0)

    def test_M11_跳过开关应放行(self):
        """无 git 能力的最小化夹具（裸目录）必须仍能跑门禁——跳过而非误红。"""
        self.root2 = Path(tempfile.mkdtemp(prefix="mig-manifest-nogit-"))
        try:
            mig2 = self.root2 / "migration"
            mig2.mkdir()
            man2 = self.root2 / "migration-manifest.json"
            (mig2 / "0001_a.sql").write_text(DDL_SQL, encoding="utf-8")
            man2.write_text(json.dumps({
                "entries": self.one_entry(),
                "executableCount": 1,
                "ordinalConflicts": [],
            }, ensure_ascii=False), encoding="utf-8")
            argv = ["check-migration-manifest.py", "--root", str(self.root2),
                    "--migration-dir", str(mig2), "--manifest", str(man2), "--skip-tracked"]
            buf = io.StringIO()
            old = sys.argv
            try:
                sys.argv = argv
                with contextlib.redirect_stdout(buf), contextlib.redirect_stderr(buf):
                    rc = gate.main()
            finally:
                sys.argv = old
            self.assertEqual(rc, 0)
        finally:
            shutil.rmtree(self.root2, ignore_errors=True)

    def test_M11_临时索引环境不得误判(self):
        """**回归用例**：`git commit --only` 会构造临时索引（GIT_INDEX_FILE）。

        那个索引只含**本次提交涉及**的文件。若门禁在 pre-commit 里直接读它，就会把
        「真实索引里已跟踪、但本次没提交的文件」误判成「完全没 add」⇒ M11 在每一次
        pre-commit 里都误红（实测：手动跑全绿，`git commit --only` 时门禁自身用例
        7 红 1 错——且误红的是**原有**用例，说明是判据本身坏了）。

        本用例构造同样的环境：GIT_INDEX_FILE 指向一个只含其它文件的临时索引，
        断言门禁仍按**真实索引**判定（已 add 的文件不该被误判成未跟踪）。
        """
        self.write("0001_a.sql")
        self.manifest_of(self.one_entry())
        self.git("add", "-f", "scripts/migration/0001_a.sql")

        # 构造一个「只含无关文件」的临时索引，模拟 commit --only 的中间态
        other = self.root / "other.sql"
        other.write_text("-- unrelated\n", encoding="utf-8")
        self.git("add", "-f", "other.sql")
        tmp_index = self.root / "tmp-index"
        # 用 --empty 而不是 read-tree HEAD：夹具仓库此刻可能还没有任何 commit，
        # 而这里要的正是「临时索引里没有目标文件」这一形态。
        self.git("read-tree", "--empty", env_index=str(tmp_index))

        old = os.environ.get("GIT_INDEX_FILE")
        os.environ["GIT_INDEX_FILE"] = str(tmp_index)
        try:
            self.assertEqual(
                self.run_gate(), 0,
                "GIT_INDEX_FILE 指向临时索引时，不得把真实索引里已跟踪的文件误判成未跟踪")
        finally:
            if old is None:
                os.environ.pop("GIT_INDEX_FILE", None)
            else:
                os.environ["GIT_INDEX_FILE"] = old


if __name__ == "__main__":
    unittest.main(verbosity=2)
