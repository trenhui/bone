#!/usr/bin/env python3
"""校验自动配置注册文件与源码一致（Boot 3 唯一机制）。

为什么需要这个门禁：
- Spring Boot 2.7 引入 `META-INF/spring/<全限定名>.AutoConfiguration.imports`，
  3.0 **移除**了 `spring.factories` 里 `EnableAutoConfiguration` 键的支持。
  ⇒ 写在旧式文件里的自动配置类**永远不会被加载，且没有任何报错**（静默失效）。
- 2026-10-07 实测两个真实案例：
  · `bone-metadata-engine-starter` 把两个自动配置类只写在旧式文件里 ⇒ 整个 starter
    长期「建好却从未被使用」，而编译与单测全绿。
  · `bone-metadata-sdk` 旧式文件列的 3 个类与新式文件的 3 个**完全不同**，
    其中 `QueryAutoConfiguration` 在源码里**已不存在**（改名后未同步注册文件）。
  ⇒ 这类漂移必须机器化检查，不能靠人记。

检查三件事：
1. **死条目**：注册文件里列的类在源码中不存在 ⇒ 改名/删除后忘了同步。
2. **漏注册**：源码里带 @AutoConfiguration（或作为自动配置写在 .imports 里），
   但没出现在 .imports 中 ⇒ 类写了却永远不生效。
3. **旧式误用**：`spring.factories` 里出现 `EnableAutoConfiguration` 键 ⇒ Boot 3 会忽略它。
   本工程允许保留该文件（兼容 Boot 2.x），但**必须为空**。

退出码：0 = 通过；1 = 有违规。
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
SKIP_DIRS = {"target", "node_modules", ".git", ".idea", "dist", "build"}

IMPORTS_RESOURCE = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
LEGACY_RESOURCE = "META-INF/spring.factories"
LEGACY_KEY = "org.springframework.boot.autoconfigure.EnableAutoConfiguration"

# 允许在 .imports 里出现、但不属于本仓库源码的类（第三方/框架类）
ALLOW_NON_PROJECT = set()


def iter_java_files():
    for root, dirs, files in os.walk(REPO):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for name in files:
            if name.endswith(".java"):
                yield Path(root) / name


def collect_imports_entries():
    """返回 [(相对路径, [注册的全限定类名...])]，只含真正带 .imports 的模块。

    ⚠️实现注意：资源路径是**多级**的（`META-INF/spring/xxx.imports`），
    它不会出现在某个目录的 `os.walk` files 列表里（那里只有文件名），
    必须对每个 `src/main/resources` 根直接拼路径判断。
    """
    results = []
    for resources_root in iter_resource_roots():
        path = resources_root / IMPORTS_RESOURCE
        if not path.exists():
            continue
        rel = path.relative_to(REPO).as_posix()
        classes = []
        for line in path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line and not line.startswith("#"):
                classes.append(line)
        results.append((rel, classes))
    return sorted(results)


def iter_resource_roots():
    """遍历所有 src/main/resources 目录。"""
    seen = []
    for root, dirs, files in os.walk(REPO):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        if Path(root).name == "resources" and "main" in Path(root).parts:
            seen.append(Path(root))
    return sorted(seen)


def collect_project_classes():
    """全仓库 Java 全限定类名集合。"""
    names = set()
    for f in iter_java_files():
        try:
            text = f.read_text(encoding="utf-8")
        except OSError:
            continue
        pkg = re.search(r"^package\s+([\w\.]+)\s*;", text, re.M)
        if not pkg:
            continue
        for m in re.finditer(r"^(?:public\s+)?(?:final\s+|abstract\s+)?(?:class|interface|enum|record)\s+(\w+)", text, re.M):
            names.add(f"{pkg.group(1)}.{m.group(1)}")
    return names


def find_legacy_autoconfig_keys():
    """返回 [(相对路径, 行号)]，指 spring.factories 里仍出现 EnableAutoConfiguration 键的位置。"""
    hits = []
    for resources_root in iter_resource_roots():
        path = resources_root / LEGACY_RESOURCE
        if not path.exists():
            continue
        rel = path.relative_to(REPO).as_posix()
        for i, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if LEGACY_KEY in line and not line.strip().startswith("#"):
                hits.append((rel, i))
    return hits


def main() -> int:
    parser = argparse.ArgumentParser(description="校验自动配置注册文件（Boot 3 .imports 机制）与源码一致")
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args()

    entries = collect_imports_entries()
    project_classes = collect_project_classes()

    if args.verbose:
        print(f"发现 {len(entries)} 个 {IMPORTS_RESOURCE}，仓库类总数 {len(project_classes)}")

    problems = []

    # ── 0. 末尾换行：缺了会让"追加一行"粘到上一行末尾，注册类名静默拼错 ──
    #实测踩过：某 .imports 无末尾换行，追加 QueryAutoConfiguration 后变成
    #   "…InterceptorAutoConfigurationcom.bone…QueryAutoConfiguration"
    # 整个条目变成一个不存在的类名 ⇒ 门禁报错才被发现（若没有门禁就是静默失效）。
    for resources_root in iter_resource_roots():
        path = resources_root / IMPORTS_RESOURCE
        if not path.exists():
            continue
        raw = path.read_bytes()
        if raw and not raw.endswith(b"\n"):
            problems.append(
                (
                    "no-trailing-newline",
                    path.relative_to(REPO).as_posix(),
                    "文件末尾无换行",
                    "追加注册类名时会与上一行粘连成一个不存在的类名",
                )
            )

    # ── 1. 死条目：注册了但源码里不存在 ──
    for rel, classes in entries:
        for cls in classes:
            if cls in ALLOW_NON_PROJECT:
                continue
            if cls not in project_classes:
                problems.append(("dead-entry", rel, cls, "注册文件列了该类，但源码中不存在（改名/删除后未同步）"))

    # ── 2. 旧式误用：spring.factories 仍有 EnableAutoConfiguration 键 ──
    for rel, line in find_legacy_autoconfig_keys():
        problems.append(("legacy-key", rel, f"line {line}", f"{LEGACY_KEY} 在 Boot 3 下会被静默忽略（应登记到 .imports）"))

    if problems:
        kind_label = {
            "dead-entry": "注册文件里的类在源码中不存在",
            "legacy-key": "spring.factories 仍用旧式自动配置注册键（Boot 3 会忽略）",
            "no-trailing-newline": "注册文件末尾无换行（追加会粘连）",
        }
        for kind, rel, what, why in problems:
            print(f"  [{kind_label[kind]}] {rel}: {what} — {why}", file=sys.stderr)
        print(f"自动配置注册检查失败：{len(problems)} 项", file=sys.stderr)
        return 1

    total = sum(len(c) for _, c in entries)
    print(f"OK: 自动配置注册一致（{len(entries)} 个 .imports / 共 {total} 个类，无死条目；无旧式注册键）")
    return 0


if __name__ == "__main__":
    sys.exit(main())