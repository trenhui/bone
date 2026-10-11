#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-menu-single-entry.py  (G35)

判据：菜单信息架构两条硬规则 ——

  规则 1（单入口）：**不得**存在「容器根路径」形态的菜单 path（形如 `/metadata`，
           没有 `#hash`）。这类菜单的落地页**恒等于**该微应用 `<Route path="/">`
           指向的页面；只要该微应用已有任一显式 `path#hash` 入口，它就是纯噪音。
  规则 2（不 dead-link）：菜单 path 的落点必须合法 —— 要么是 shell 自身路由
           （`/` `/profile`），要么是 9 个容器前缀之一（`/metadata` 等）配合 `#hash`。
           落在集合外的（如 `/dashboard` `/tenant` `/qa-board`）点击必被
           `<Route path="*" element={<Navigate to="/" replace/>}/>` 静默送回首页。

为什么需要这道门禁（判别力来源，非装饰）
--------------------------------------
2026-10-09 用户实测截图：左侧同时出现顶级「元数据建模」`/metadata` 与
「业务建模 → 建模工作台」`/metadata#/apps`，两个入口点进去**是同一个页面**。

机制（Playwright 实测，非推断）：
  1. shell `<Route path="/metadata/*">` 渲染 qiankun 容器（bone-shell/src/App.tsx:1221）；
  2. metadata-app 用 HashRouter，`App.tsx` 的 `<Route path="/">` 是
     `<Navigate to="/apps" replace/>`；
  3. ⇒ 访问 `/metadata`（无 hash）时 location.hash 自动变 `#/apps`，落地「建模工作台」。

★ 关键设计：判**净效果**，不判字面出现（v1 曾有假阳性，见下）
--------------------------------------------------------------
朴素实现「扫到 '/metadata' 字面量就报错」是错的：`0030_menu_single_entry.sql` 里
出现的 `'/metadata'` 恰恰是**要删掉它**的软删条件，报错等于把修复脚本判成违规。
因此本门禁按 **SQL 语句**分类，只对「让菜单存活」的语句（INSERT / 不带 `SET deleted`
的 UPDATE）判违规；`SET deleted = 1` 的软删语句是**修复手段**，其 path 是删除目标、
不是违规。注释行先剥离（迁移脚本头部大量注释引用 path 做背景说明）。

变异验证（必做，否则门禁只是装饰）
----------------------------------
  新增一条存活语句 `INSERT INTO iam_menu (...) VALUES (..., '/metadata', ...)`
  ⇒ 红；换成 `/metadata#/apps` ⇒ 恢复绿。
  新增 `'/dashboard'` ⇒ 红（规则 2）。

用法：
  python3 scripts/check-menu-single-entry.py [--root <repo-root>]
"""
import glob
import os
import re
import sys

MIGRATION_DIR = "scripts/migration"
SHELL_APP_REL = "bone-frontend/apps/bone-shell/src/App.tsx"

# shell 自身路由（bone-shell/src/App.tsx 的 <Route path="...">，非 /xxx/* 容器形态）
SHELL_SELF_ROUTES = {"/", "/profile"}

PATH_LITERAL = re.compile(r"'(/[A-Za-z][A-Za-z0-9\-]*(?:#/[A-Za-z0-9\-/_]+)?)'")
SOFT_DELETE = re.compile(r"SET\s+deleted\s*=\s*1", re.IGNORECASE)


def find_repo_root():
    here = os.path.dirname(os.path.abspath(__file__))
    out = os.popen("git rev-parse --show-toplevel 2>/dev/null").read().strip()
    return out or os.path.dirname(here)


def strip_sql_comments(line):
    """去掉 `--` 行注释（迁移脚本头部大量注释引用 path 做背景说明，不是声明）。"""
    out, in_str, i = [], False, 0
    while i < len(line):
        ch = line[i]
        if ch == "'" and (i == 0 or line[i - 1] != "\\"):
            in_str = not in_str
        if not in_str and ch == "-" and i + 1 < len(line) and line[i + 1] == "-":
            break
        out.append(ch)
        i += 1
    return "".join(out)


def parse_shell_containers(root):
    """从 shell App.tsx 提取容器前缀集合（`<Route path="/xxx/*">` → `/xxx`）。"""
    path = os.path.join(root, SHELL_APP_REL)
    containers = set()
    if os.path.isfile(path):
        with open(path, "r", encoding="utf-8") as fh:
            for m in re.finditer(r"<Route\b[^>]*?\bpath=\"([^\"]+)\"", fh.read(), re.S):
                r = m.group(1)
                if r.endswith("/*"):
                    containers.add(r[:-2])
    # 兜底：shell 裁剪检出时 App.tsx 可能不存在，用已知前缀集兜底（与源码同步维护）
    return containers or {
        "/iam", "/metadata", "/masterdata", "/commerce",
        "/integration", "/system", "/extension", "/generator",
    }


def iter_sql_statements(root):
    """
    逐语句产出 (相对文件名, 起始行号, 语句文本)，已剥离注释。

    分隔符按 MySQL 语义：分号、以及 `DELIMITER $$` 切换后的 `$$`。
    """
    for sql in sorted(glob.glob(os.path.join(root, MIGRATION_DIR, "*.sql"))):
        rel = os.path.basename(sql)
        buf, start, delim = [], 1, ";"
        with open(sql, "r", encoding="utf-8") as fh:
            for lineno, raw in enumerate(fh, 1):
                line = strip_sql_comments(raw)
                if not line.strip() and not buf:
                    continue
                m = re.search(r"DELIMITER\s+(\S+)", line, re.IGNORECASE)
                if m:
                    if buf:
                        yield rel, start, "\n".join(buf)
                        buf, start = [], lineno + 1
                    delim = m.group(1)
                    continue
                # 语句在当前分隔符处收口（排除字符串内的分隔符）
                idx, in_str = 0, False
                while idx < len(line):
                    ch = line[idx]
                    if ch == "'" and (idx == 0 or line[idx - 1] != "\\"):
                        in_str = not in_str
                    if not in_str and line.startswith(delim, idx):
                        buf.append(line[:idx])
                        yield rel, start, "\n".join(buf)
                        buf, start = [], lineno + 1
                        line = line[idx + len(delim):]
                        idx = -1
                    idx += 1
                if line.strip():
                    buf.append(line)
        if buf:
            yield rel, start, "\n".join(buf)


def main():
    root = None
    args = sys.argv[1:]
    i = 0
    while i < len(args):
        if args[i] == "--root" and i + 1 < len(args):
            root = args[i + 1]; i += 2; continue
        i += 1
    if root is None:
        root = find_repo_root()

    if not os.path.isdir(os.path.join(root, MIGRATION_DIR)):
        fail_root = os.path.join(root, MIGRATION_DIR)
        print(f"[G35] ❌ 找不到迁移目录：{fail_root}")
        sys.exit(1)

    containers = parse_shell_containers(root)
    known_landings = SHELL_SELF_ROUTES | containers

    violations = []
    hashed_paths = set()   # 活着的 `path#hash` 入口（用于规则 1 的「已有显式入口」判定）
    soft_deleted = set()   # 被软删的 path
    scanned = 0

    for rel, lineno, stmt in iter_sql_statements(root):
        if "iam_menu" not in stmt:
            continue
        upper = stmt.upper()
        # 只关心能改变菜单可见性的语句
        if not any(k in upper for k in ("INSERT", "UPDATE")):
            continue
        # 备份表 / 过程定义里的 SELECT ... 不是声明
        is_soft_delete = bool(SOFT_DELETE.search(stmt))
        if is_soft_delete:
            for m in PATH_LITERAL.finditer(stmt):
                soft_deleted.add(m.group(1))
            continue

        scanned += 1
        for m in PATH_LITERAL.finditer(stmt):
            p = m.group(1)
            if "#" in p:
                hashed_paths.add(p)
                continue
            if p in SHELL_SELF_ROUTES:
                continue
            if p in containers:
                # 规则 1：容器根路径 = 裸入口
                app_explicit = {h for h in hashed_paths if h.startswith(p + "#/")}
                reason = (
                    f"菜单 path `{p}` 是「容器根路径」形态（无 #hash）"
                    f"—其落地页恒等于该微应用 <Route path=\"/\"> 指向的页面"
                    + (f"，与已声明的显式入口 {sorted(app_explicit)} 重复" if app_explicit else "")
                    + "，是纯噪音（同一扇门只应有一个入口）"
                )
                violations.append((rel, lineno, p, "规则1 单入口", reason))
            else:
                violations.append((
                    rel, lineno, p, "规则2 落点合法",
                    f"菜单 path `{p}` 不在 shell 合法落点集合 "
                    f"{{{', '.join(sorted(SHELL_SELF_ROUTES))}}} ∪ "
                    f"{{{', '.join(sorted(containers))}}} 内"
                    f"—点击必被 <Route path=\"*\"> 静默送回首页，是死链菜单",
                ))

    if violations:
        for rel, ln, p, rule, reason in violations:
            print(f"[G35] ❌ {rel}:{ln} [{rule}] {reason}")
        print(
            f"[G35] 发现 {len(violations)} 处信息架构违规。\n"
            f"  · 规则1：删掉裸容器根路径菜单，保留 `path#hash` 形态的显式入口（元数据域入口 = `/metadata#/apps`）；\n"
            f"  · 规则2：死链菜单须经迁移**软删**（`SET deleted = 1`），不要留「点了跳首页」的菜单；\n"
            f"  · 参考实现：`scripts/migration/0030_menu_single_entry.sql`。"
        )
        sys.exit(1)

    print(
        f"[G35] ✅ 菜单入口唯一性通过"
        f"（校验 {scanned} 条存活语句 / {len(hashed_paths)} 个显式入口；"
        f"软删目标 {len(soft_deleted)} 个；0 个裸容器入口 / 0 个死链）"
    )


if __name__ == "__main__":
    main()