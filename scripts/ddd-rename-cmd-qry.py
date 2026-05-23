#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Bone-DDD 命名规范工具：将 `*Cmd` / `*Qry` 类（含文件名与所有引用）批量重命名为
`*Command` / `*Query`，并修正子包路径（`...command.cmd.` / `...query.qry.` 中的
**子包名 `cmd` / `qry` 可保留**作为短目录名，与《Bone-DDD》§14.1 一致）。

仅改动 *Cmd / *Qry **类名**和文件名；不动子包 `cmd/`、`qry/`。

用法：
    python3 scripts/ddd-rename-cmd-qry.py <module-root>            # 仅 dry-run
    python3 scripts/ddd-rename-cmd-qry.py <module-root> --apply    # 真正执行

多模块顺序：
    python3 scripts/ddd-rename-cmd-qry.py bone-platform/bone-iam --apply
    python3 scripts/ddd-rename-cmd-qry.py bone-platform/bone-masterdata --apply
    ...

注意：
- 只重命名 **以 `Cmd` 或 `Qry` 结尾** 的顶级标识符；变量后缀 `cmd` / `qry` **不动**。
- 仅扫描 src/main/java 与 src/test/java 下的 .java 文件。
- 跳过非应用模块（无 src/main/java 直接退出）。
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path
from typing import Dict, List, Tuple

EXCLUDE_DIRS = {"target", "build", "out", ".idea", "node_modules", "archunit_store"}


def collect_classes(root: Path) -> Dict[str, str]:
    """扫描根目录下所有 .java，返回 {OldClassName: NewClassName}。"""
    renames: Dict[str, str] = {}
    for path in iter_java(root):
        stem = path.stem
        if stem.endswith("Cmd"):
            renames[stem] = stem[:-3] + "Command"
        elif stem.endswith("Qry"):
            renames[stem] = stem[:-3] + "Query"
    return renames


def iter_java(root: Path):
    for path in root.rglob("*.java"):
        if any(part in EXCLUDE_DIRS for part in path.parts):
            continue
        yield path


def rewrite_content(text: str, renames: Dict[str, str]) -> Tuple[str, int]:
    """替换 *所有* 出现的旧类名为新类名，单词边界匹配，避免误伤。"""
    count = 0
    for old, new in renames.items():
        pattern = re.compile(rf"\b{re.escape(old)}\b")
        new_text, n = pattern.subn(new, text)
        if n:
            count += n
            text = new_text
    return text, count


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("module_root", help="模块根目录（含 src/main/java）")
    parser.add_argument("--apply", action="store_true", help="真正执行；否则 dry-run")
    args = parser.parse_args()

    root = Path(args.module_root).resolve()
    if not (root / "src" / "main" / "java").exists():
        print(f"[skip] {root} 无 src/main/java，跳过", file=sys.stderr)
        sys.exit(0)

    renames = collect_classes(root)
    if not renames:
        print(f"[ok] {root.name}: 无 *Cmd / *Qry 类需要迁移")
        return

    print(f"\n=== {root.name}：将重命名 {len(renames)} 个类 ===")
    for old, new in sorted(renames.items()):
        print(f"  {old}  ->  {new}")

    if not args.apply:
        print("\n(dry-run，未应用；加 --apply 真正执行)")
        return

    java_files: List[Path] = list(iter_java(root))
    total_changes = 0
    touched_files = 0
    for path in java_files:
        text = path.read_text(encoding="utf-8")
        new_text, n = rewrite_content(text, renames)
        if n:
            path.write_text(new_text, encoding="utf-8")
            total_changes += n
            touched_files += 1

    print(f"[content] 已更新 {touched_files} 个文件，{total_changes} 处替换")

    file_renames = 0
    for path in iter_java(root):
        if path.stem in renames:
            new_path = path.with_name(renames[path.stem] + ".java")
            if new_path.exists():
                print(f"[warn] 目标文件已存在，跳过：{new_path}", file=sys.stderr)
                continue
            path.rename(new_path)
            file_renames += 1
    print(f"[rename] 已重命名 {file_renames} 个 .java 文件")


if __name__ == "__main__":
    main()
