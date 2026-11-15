#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""scripts/ 编目与门禁纪律的机器守卫（编目铁律 + 单测棘轮）。

背景
----
2026-11-11 全目录诊断实测：`scripts/` 根平铺 132 个文件，**没有总索引**。后果不是「不好看」，
而是三条具体的失效：

1. **可发现性缺失 ⇒ 重复造轮子**。agent 拿到目录只能 `ls` 后逐个读 docstring，
   于是「这个能力可能已经有了」无法判断。`scripts/e2e/` 整合前正是 34/59 脚本查不到，
   同款问题在根目录重演。
2. **一次性脚本混居可复用库**。`_probe_*.js` / `tmp-*` 这类「针对某次发布的快速实证」
   住在库里，会让后来者误以为它们是权威入口（与 `e2e/` 第一轮删 `diag-*` 同一形态）。
3. **门禁自身无人看护**。59 个 `check-*.py` 里只有 17 个有单测；没有单测的门禁一旦判据回归，
   本地与 CI 都会安静地放行——守卫坏了比没有守卫更危险。（2026-11-12 已补 11 个，
   剩余缺口见 `MISSING_TEST_BASELINE`。）

本测试把这三条从文档口号变成会红的判据：

- R1 编目铁律：`scripts/` 根与分域目录（`ci/` `dev/` `lib/` `frontend/` `ops/` `prompts/` `sdk/`）下
  每个手写脚本都必须出现在 `scripts/README.md`；`archive/` 下每个文件必须出现在 `archive/README.md`。
- R2 命名纪律：根目录不得出现 `_*` / `tmp-*` 前缀（作者自己标记为「临时/私有」的脚本，
  要么毕业进正式入口、要么进 `archive/`）。
- R3 单测棘轮：缺单测的门禁集合**只可收缩**——存量缺口冻结在 `MISSING_TEST_BASELINE`，
  任何新增 `check-*.py` 必须同批带来 `scripts/tests/test_<名下划线>.py`，删一个存量缺口也必须同步下调基线。

为什么不直接要求「59 个全部有单测」：存量缺口是历史债务，一次性补齐既不可靠（无实跑验证的
补测等于假绿），也会让本门禁在落地当天就是红的。棘轮是仓库里已被验证过的收敛方式
（`check-archunit-freeze-baseline.py`、`e2e/selftest.py` 的 `INLINE_LOGIN_BASELINE` 同源）。
"""

import os
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
REPO = SCRIPTS.parent
README = SCRIPTS / "README.md"
ARCHIVE_README = SCRIPTS / "archive" / "README.md"

# 编目范围：只收「人写的可执行脚本」。以下目录有意排除，理由随行注明。
CATALOG_DIRS = [
    "",          # scripts/ 根
    "ci",
    "dev",
    "lib",
    "frontend",
    "ops",
    "prompts",
    "sdk",
    "registry",
    "hooks",
]
EXCLUDED_DIRS = {
    # 生成物 / 运行时目录：不是手写资产
    "tmp", "logs", "__pycache__", "node_modules",
    # migration/ 的顺序与完整性由 migration-manifest.json + check-migration-manifest.py 托管，
    # 在总索引里逐条重复 131 个文件名只会制造第二套易漂移的清单。
    "migration",
    # seed/uat/build/ 是生成产物；seed/ 的入口在 §4 已编目
    "seed",
    # e2e/ 与 e2e/ui/ 有各自 README（同样的编目铁律），由 run.sh 与 selftest 托管
    "e2e",
    # archive/ 单独校验；tests/ 由 R3 棘轮托管
    "archive",
    "tests",
}
SCRIPT_SUFFIXES = {".py", ".sh", ".js", ".cjs", ".mjs"}

# 缺单测的门禁冻结基线（2026-11-11 首次实测 59 个 check-*.py / 17 个有单测 / 42 个缺口；
# 2026-11-12 补 11 个 → 28 个有单测 / 31 个缺口）。
# 只可收缩：某条补齐单测后，必须从本集合删除；新增 check-*.py 必须同批带单测，
# 不得把新脚本塞进本集合（那等于把棘轮泡成永远绿）。
MISSING_TEST_BASELINE = frozenset({
    "check-application-constructs",
    "check-approver-reference-not-exposed",
    "check-autoconfig-registration",
    "check-controller-authorization",
    "check-controller-authorization-url-bypass",
    "check-cross-service-exception-mapping",
    "check-ddd-doc-drift",
    "check-ddd-gate-state",
    "check-ddl-doc-sync",
    "check-ddl-required-columns",
    "check-deterministic-cache",
    "check-doc-code-symbols",
    "check-frontend-response-contract",
    "check-frontend-route-param",
    "check-id-annotation",
    "check-openapi-generation-readiness",
    "check-openapi-int64-contract",
    "check-orm-banned",
    "check-orphan-tables",
    "check-sdk-contract-surface",
    "check-sdk-persistence",
    "check-security-fail-open-default",
    "check-slice-truncation",
    "check-soft-delete-declaration",
    "check-synapclaim-architecture",
    "check-synapclaim-module-boundaries",
    "check-tenant-deletion-coverage",
    "check-tenant-entity-declaration",
    "check-three-entry-boundaries",
    "check-ui-schema-samples",
    "check-ui-schema-sync",
})


def _normalize(stem: str) -> str:
    """脚本名 → 单测名约定的键：`check-ddl-dup.py` ⇒ `check_ddl_dup`。

    之所以先归一再去空格错，是为了让「文件名改了但单测没跟着改」也能被识别成缺口，
    而不是被字符串精确匹配悄悄放过。
    """
    return stem.replace("-", "_")


def _iter_catalogued_scripts(base: Path):
    """遍历待编目脚本，边遍历边剪枝（`tmp/npmcache` 这类缓存目录有上万文件，不能进）。

    用 os.walk 的 dirnames 原地剪枝，而不是 rglob 事后过滤——后者仍会把大目录走完。
    """
    for dirpath, dirnames, filenames in os.walk(base):
        dirnames[:] = sorted(
            d for d in dirnames if d not in EXCLUDED_DIRS and not d.startswith(".")
        )
        for name in sorted(filenames):
            if Path(name).suffix in SCRIPT_SUFFIXES:
                yield Path(dirpath) / name


class R1CatalogTest(unittest.TestCase):
    """R1：目录里每个手写脚本都必须可查。"""

    def setUp(self):
        self.assertTrue(README.is_file(), f"缺少总索引：{README}")
        self.text = README.read_text(encoding="utf-8")

    def test_root_and_subdir_scripts_are_indexed(self):
        # 精确登记：必须是反引号包裹的「相对路径」或「文件名」token。
        # 旧版用 `path.name in README`，文件名只要恰好是别的文本的子串就算编目
        # （例如 `gate.py` 被 `example_gate.py` 命中），是典型的假绿判据。
        # 根目录递归与子目录遍历会重复命中同一文件，故用 set 去重。
        missing = set()
        for rel in CATALOG_DIRS:
            base = SCRIPTS / rel if rel else SCRIPTS
            for path in _iter_catalogued_scripts(base):
                rel_path = str(path.relative_to(SCRIPTS))
                if f"`{rel_path}`" not in self.text and f"`{path.name}`" not in self.text:
                    missing.add(str(path.relative_to(REPO)))
        self.assertEqual(
            sorted(missing), [],
            "以下脚本未登记在 scripts/README.md（编目铁律：查不到 = 不存在 = 会被重复造轮子）。\n"
            "请按 §9 判断归属并补登记；若已废弃，移入 scripts/archive/ 并登记 archive/README.md。\n  "
            + "\n  ".join(sorted(missing)),
        )

    def test_readme_has_no_stale_checker_entries(self):
        """反向对账：README 里登记的 check-*.py 必须真实存在，避免清单悄悄腐烂。

        库外判据（`registry/external.json` 登记）也允许出现在 README 中；
        排除它们是为了不把「提到库外脚本」误判成悬空条目，判据本身不放宽。
        """
        import json
        import re
        ext_path = SCRIPTS / "registry" / "external.json"
        external_names = set()
        if ext_path.is_file():
            external_names = {
                Path(str(e.get("path", ""))).name
                for e in json.loads(ext_path.read_text(encoding="utf-8")).get("entries", [])
            }
        stale = sorted({
            name for name in re.findall(r"`(check-[a-z0-9-]+\.py)`", self.text)
            if not (SCRIPTS / name).is_file() and name not in external_names
        })
        self.assertEqual(
            stale, [],
            "README 登记了已不存在的判据脚本（旧版 R1 只做单向检查，查不出悬空条目）：%s" % stale,
        )

    def test_archive_files_are_indexed(self):
        self.assertTrue(ARCHIVE_README.is_file(), f"缺少归档说明：{ARCHIVE_README}")
        archive_text = ARCHIVE_README.read_text(encoding="utf-8")
        missing = [
            p.name
            for p in sorted((SCRIPTS / "archive").iterdir())
            if p.is_file() and p.suffix in SCRIPT_SUFFIXES and p.name not in archive_text
        ]
        self.assertEqual(missing, [], f"归档文件未在 archive/README.md 登记：{missing}")

    def test_readme_states_the_iron_law(self):
        """索引本身要写明铁律，否则下一个人不知道维护它是义务。"""
        self.assertIn("每个手写脚本必须能在本文件查到", self.text)


class R2NamingTest(unittest.TestCase):
    """R2：根目录禁止一次性脚本的命名前缀。"""

    def test_no_private_or_tmp_prefixed_scripts_at_root(self):
        offenders = [
            p.name
            for p in sorted(SCRIPTS.iterdir())
            if p.is_file()
            and p.suffix in SCRIPT_SUFFIXES
            and (p.name.startswith("_") or p.name.startswith("tmp-") or p.name.startswith("tmp_"))
        ]
        self.assertEqual(
            offenders, [],
            "根目录出现一次性/私有前缀脚本：%s\n"
            "处置二选一：① 确认是常驻能力 ⇒ 去掉前缀、接入活入口并登记 README；"
            "② 一次性 ⇒ 移入 scripts/archive/ 并在 archive/README.md 登记。" % offenders,
        )


class R3CheckerTestRatchet(unittest.TestCase):
    """R3：缺单测的门禁集合只可收缩。"""

    def _missing_tests(self):
        missing = set()
        for path in sorted(SCRIPTS.glob("check-*.py")):
            stem = path.stem
            test_path = SCRIPTS / "tests" / f"test_{_normalize(stem)}.py"
            if not test_path.is_file():
                missing.add(stem)
        return missing

    def test_no_new_checker_without_test(self):
        missing = self._missing_tests()
        new_gaps = sorted(missing - MISSING_TEST_BASELINE)
        self.assertEqual(
            new_gaps, [],
            "新增门禁缺单测（判据回归时本地与 CI 都会安静放行）：%s\n"
            "请补 scripts/tests/test_<名下划线>.py（照抄 test_check_gate_liveness.py："
            "importlib 加载真实脚本 + 临时夹具）。" % new_gaps,
        )

    def test_baseline_shrinks_when_gap_is_closed(self):
        """补了单测就必须同步下调基线，否则棘轮会悄悄变松（与 freeze-baseline 同源纪律）。"""
        missing = self._missing_tests()
        stale = sorted(MISSING_TEST_BASELINE - missing)
        self.assertEqual(
            stale, [],
            "以下门禁已有单测但基线仍登记为缺口，请从 test_scripts_catalog.MISSING_TEST_BASELINE 删除：%s"
            % stale,
        )

    def test_checked_scripts_exist(self):
        """基线里登记的脚本必须真实存在（防「脚本删了、基线还留着」把棘轮架空）。"""
        gone = sorted(name for name in MISSING_TEST_BASELINE if not (SCRIPTS / f"{name}.py").is_file())
        self.assertEqual(gone, [], f"基线登记的脚本已不存在，请同步清理：{gone}")


if __name__ == "__main__":
    unittest.main(verbosity=2)
