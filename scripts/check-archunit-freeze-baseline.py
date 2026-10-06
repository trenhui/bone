#!/usr/bin/env python3
"""ArchUnit 冻结基线的**开关**必须存在且进库（P1-5 的防复发门禁）。

背景：真存在的问题不是「9 个模块忘了配」，而是下面这条链路
─────────────────────────────────────────────────────────────────────
  `.gitignore` 的 `*.properties`（第 139 行）吞掉 `archunit.properties`
  ⇒ 新配的文件根本进不了库
  ⇒ clean checkout 上该文件不存在
  ⇒ ArchUnit 回落 `freeze.store.default.allowStoreUpdate=true`
  ⇒ **第一次跑测试就把新增违规写进基线、此后永久豁免**
  ⇒ 门禁照跑、照绿，判断已经失效。
`bone-architecture-test` 是被 10 个模块复用的共享库，所以只要一个模块漏配，
该模块的架构冻结就是假的 —— 且它在 CI 上永远不报错，因为"跑通了"。

判据（每条都能被反例证伪）
------------------------
  R1 每个含 `*ArchitectureTest.java` 的模块，必须有
     `src/test/resources/archunit.properties`，且 `freeze.store.default.allowStoreUpdate=false`
     （写成 `true` 或整行缺失都算失败：默认 true 正是要防的那个状态）。
  R2 该文件必须**已被 git 跟踪**。只检查"文件在磁盘上"会漏掉最致命的那一类失效——
     被 .gitignore 吞掉 ⇒ 你本地看得到、CI 干净 checkout 上不存在。
  R3 `allowStoreCreation` 同样必须为 false（允许新建基线 = 允许第一把火把存量违规合法化）。

机器可读的结论都是现场跑出来的，不写死任何数字；本门禁只判机制，不判内容。
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent

ALLOW_UPDATE_KEY = "freeze.store.default.allowStoreUpdate"
ALLOW_CREATION_KEY = "freeze.store.default.allowStoreCreation"

KV_RE = re.compile(r"^\s*(?P<key>[^#=\s][^=]*?)\s*=\s*(?P<val>[^#\s]+)\s*$")


def tracked_files() -> set[str]:
    """git 已跟踪的文件清单（相对仓库根，posix 风格）。"""
    try:
        out = subprocess.run(
            ["git", "ls-files", "-z"],
            cwd=REPO,
            capture_output=True,
            text=True,
            timeout=30,
        )
    except (OSError, subprocess.SubprocessError) as exc:  # pragma: no cover
        print(f"⚠ 无法执行 git ls-files：{exc}（R2 无法判定）")
        return set()
    if out.returncode != 0:
        print(f"⚠ git ls-files 失败：{out.stderr.strip()[:200]}")
        return set()
    return {p for p in out.stdout.split("\0") if p}


def find_modules_with_arch_tests() -> list[Path]:
    """含 *ArchitectureTest.java 的模块根（向上找到含 pom.xml 的目录）。"""
    roots: set[Path] = set()
    for test in REPO.rglob("*ArchitectureTest.java"):
        if "node_modules" in test.parts or "/target/" in test.as_posix():
            continue
        for parent in [test.parent, *test.parent.parents]:
            if (parent / "pom.xml").is_file():
                roots.add(parent)
                break
    return sorted(roots)


def read_props(path: Path) -> dict[str, str]:
    props: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8", errors="ignore").splitlines():
        m = KV_RE.match(line)
        if m:
            props[m.group("key").strip()] = m.group("val").strip()
    return props


def main() -> int:
    parser = argparse.ArgumentParser(description="ArchUnit 冻结基线开关检查")
    parser.add_argument("--check", action="store_true", help="CI 门禁模式：有违规即非零退出")
    parser.add_argument("--baseline", action="store_true", help="把当前缺失刷进基线（本地收敛用）")
    args = parser.parse_args()

    if args.baseline:
        print("--baseline 尚未实现：本判据的口径是「开关必须存在」，"
              "没有可收缩的存量概念。缺失请直接补 src/test/resources/archunit.properties。")
        return 1

    modules = find_modules_with_arch_tests()
    tracked = tracked_files()
    violations: list[str] = []

    print(f"ArchUnit 冻结基线开关检查（发现 {len(modules)} 个含 ArchitectureTest 的模块）")

    for mod in modules:
        rel_mod = mod.relative_to(REPO).as_posix()
        rel_props = f"{rel_mod}/src/test/resources/archunit.properties"
        path = REPO / rel_props

        if not path.is_file():
            violations.append(
                f"{rel_props}: 不存在（缺失 ⇒ ArchUnit 回落 allowStoreUpdate=true，"
                "新增违规会被首次运行写进基线并永久豁免）"
            )
            continue

        props = read_props(path)
        val = props.get(ALLOW_UPDATE_KEY)
        if val != "false":
            violations.append(
                f"{rel_props}: {ALLOW_UPDATE_KEY} = {val!r}（必须是字面量 false；"
                f"true 表示允许基线自增，等于新增违规不阻断）"
            )
        if props.get(ALLOW_CREATION_KEY, "false") != "false":
            violations.append(
                f"{rel_props}: {ALLOW_CREATION_KEY} = {props.get(ALLOW_CREATION_KEY)!r}"
                f"（必须 false：允许新建基线 = 把存量违规合法化）"
            )
        if rel_props not in tracked:
            violations.append(
                f"{rel_props}: 文件在磁盘上但**未被 git 跟踪**（多半被 .gitignore 的 "
                f"`*.properties` 吞掉）⇒ clean checkout 上不存在，开关等于没配"
            )

    if violations:
        print("\n❌ 冻结基线开关违规：")
        for v in violations:
            print(f"  - {v}")
        print("\n处置：补 <模块>/src/test/resources/archunit.properties，内容两行——")
        print("  freeze.store.default.allowStoreUpdate=false")
        print("  freeze.store.default.allowStoreCreation=false")
        print("并确认未被 .gitignore 吞掉（本门禁 R2 会复核跟踪状态）。")
        return 1 if args.check else 0

    print(f"\n✅ {len(modules)} 个模块的冻结基线开关齐备且已纳入版本控制。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
