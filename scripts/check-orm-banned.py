#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""门禁：HC-001 —— 禁止引入 MyBatis-Plus / JPA / Hibernate / MyBatis。

背景
----
`gate-state.json` 中 HC-001 的实测状态是 **Manual**：判据只挂在本地
`scripts/check.sh` 的 ``[5/28]``（import 扫描）与 ``[7/28]``（pom 扫描），
**CI 侧无载体**。实测全仓当前**零ORM import、零 ORM 依赖坐标**（完全合规），
但"当前合规"与"引入就阻断"是两件事—— 前者靠没人引入，后者靠机制。

本脚本把判据补齐并**同时覆盖 import 与 pom 两个面**，供 CI 与本地共用
（避免"两处同一命令、参数却不一致"的漂移）。

判定面
------
* **R1 import 面**：`org.apache.ibatis` / `javax.persistence` / `jakarta.persistence` /
  `org.hibernate` / `com.baomidou` 的 import 语句。
* **R2 pom 面**：任何 artifactId / groupId 含 `mybatis` / `ibatis` / `hibernate` /
  `jpa` 的坐标。
  —— **比原 check.sh 的 4 个固定坐标更全**：原判据漏掉
  ``mybatis-spring-boot-starter``、``tk.mybatis:mapper``、``ibatis-*-spring-boot-starter``、
  ``spring-boot-starter-data-jpa`` 的部分命名变体等常见写法。

刻意的排除（非豁免，而是"不同质的依赖"）
----------------------------------------
* ``bone-metadata-sdk`` 里的 ``SqlTemplateType.MYBATIS`` 等**枚举字面量**：
  它是"生成的 SQL 模板风格"这一业务概念（允许用户写 MyBatis 风格 XML 模板，
  由 SDK 自己解析执行），**不是引入 MyBatis 框架**。故只对 ``import`` 与
  ``artifactId`` 判禁，不对字符串字面量判禁。
* ``.semgrep/rules/bone-orm-ban.yml`` 与 ``AggregatePureUnitTestGuard``
  里的 ``mybatis`` 字样：前者是**禁令规则本身**，后者是"禁止使用
  ``@MybatisTest`` 注解"的守卫清单 —— 命中它们等于禁令自我实现，必须放行。

用法::

    python3 scripts/check-orm-banned.py# 阻断
    python3 scripts/check-orm-banned.py --report-only# 恒 exit 0
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]

# R1：禁止的 import 前缀
BANNED_IMPORT_PREFIXES = [
    "org.apache.ibatis",
    "javax.persistence",
    "jakarta.persistence",
    "org.hibernate",
    "com.baomidou",
]

# R1 正则（git grep 用）
IMPORT_PATTERN = r"import\s+(" + "|".join(re.escape(p) for p in BANNED_IMPORT_PREFIXES) + ")"

# R2：pom 中禁止的坐标关键词（覆盖各种命名变体）
POM_KEYWORDS = ["mybatis", "ibatis", "hibernate", "spring-boot-starter-data-jpa"]

# 允许命中禁令规则/ 守卫清单自身的路径
SELF_EXEMPT_PATHS = [
    ".semgrep/rules/bone-orm-ban.yml",
    "scripts/check-orm-banned.py",
    "bone-framework/bone-architecture-test/",
]


def git_grep(pattern: str, pathspec: str) -> tuple[list[str], bool]:
    """返回 (命中行列表, 命令是否成功)。"""
    r = subprocess.run(
        ["git", "grep", "--untracked", "-nE", pattern, "--", pathspec],
        cwd=REPO,
        capture_output=True,
        text=True,
    )
    # exit 0=有命中, 1=无命中, >1=错误
    if r.returncode > 1:
        print(r.stderr.strip(), file=sys.stderr)
        return [], False
    return [l for l in r.stdout.splitlines() if l.strip()], True


# 源码扫描根目录
SOURCE_ROOTS = [
    "bone-platform",
    "bone-engine",
    "bone-blueprint",
    "bone-framework",
    "bone-tool",
]


def walk_source_files(suffix: str) -> list[Path]:
    """遍历源码文件（跳过 target / node_modules / .git）。

    ★ **为什么不能用 `git grep --untracked`（2026-10-06 实测踩坑）**：
    本仓 `.gitignore` 里有 `!**/src/main/**` 这条**反向忽略**规则，
    而 `git grep --untracked` 会尊重 ignore 规则 ⇒
    **未跟踪的 `src/main/**` 文件被整片跳过**。
    实测：新建一个含 `import org.apache.ibatis` 的未跟踪文件放在
    `bone-blueprint/src/main/java/...`，`git grep` 退出码=1（零命中），
    即"刚写完的违规代码在提交前完全扫不到"。
    这与 check.sh [5/28] 的既有实现是同一个盲区，故本脚本改用文件系统遍历兜住。
    """
    import os

    out: list[Path] = []
    for root in SOURCE_ROOTS:
        base = REPO / root
        if not base.exists():
            continue
        for dirpath, dirnames, filenames in os.walk(base):
            dp = Path(dirpath)
            dirnames[:] = [
                d for d in dirnames if d not in ("target", "node_modules", ".git", "dist", "build")
            ]
            for fn in filenames:
                if fn.endswith(suffix):
                    out.append(dp / fn)
    return out


def is_self_exempt(path: str) -> bool:
    return any(p in path for p in SELF_EXEMPT_PATHS)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--report-only", action="store_true")
    args = ap.parse_args()

    violations: list[tuple[str, str]] = []

    # R1 import 面：文件系统遍历（不能用 git grep，理由见 walk_source_files docstring）
    import_line_re = re.compile(IMPORT_PATTERN)
    for path in walk_source_files(".java"):
        rel = str(path.relative_to(REPO))
        if is_self_exempt(rel):
            continue
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        for i, line in enumerate(text.splitlines(), 1):
            if import_line_re.search(line):
                violations.append(("R1 import", f"{rel}:{i}:{line.strip()}"))

    # R2 pom 面：同样走文件系统（未跟踪的 pom 也要扫到）
    pom_re = re.compile(
        r"<(artifactId|groupId)>[^<]*(" + "|".join(POM_KEYWORDS) + r")[^<]*</(artifactId|groupId)>"
    )
    for path in walk_source_files(".xml"):
        if path.name != "pom.xml":
            continue
        rel = str(path.relative_to(REPO))
        if is_self_exempt(rel):
            continue
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        for i, line in enumerate(text.splitlines(), 1):
            if pom_re.search(line):
                violations.append(("R2 pom", f"{rel}:{i}:{line.strip()}"))

    print("=" * 72)
    print("HC-001 禁用 ORM 框架门禁")
    print(f"  import 面禁止前缀: {', '.join(BANNED_IMPORT_PREFIXES)}")
    print(f"  pom 面禁止关键词: {', '.join(POM_KEYWORDS)}")
    print(f"  扫描方式: 文件系统遍历（git grep --untracked 会漏 src/main 下的未跟踪文件）")
    print(f"  违规命中: {len(violations)}")
    print("=" * 72)

    if violations:
        for face, line in violations[:30]:
            print(f"❌ [{face}] {line}")
        if len(violations) > 30:
            print(f"   ...另有 {len(violations) - 30} 条")
        print("\n本工程持久化唯一入口是 bone-metadata-sdk（@EnableSqlRepositories）；")
        print("如需 SQL 模板请用 SqlTemplateType 而非引入 MyBatis/JPA/Hibernate 框架。")
        if not args.report_only:
            return 1

    if not violations:
        print("✅ 未引入任何被禁 ORM（import 面与 pom 面均为零命中）")
    return 0


if __name__ == "__main__":
    sys.exit(main())