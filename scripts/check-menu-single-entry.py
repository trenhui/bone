#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-menu-single-entry.py  (G35)

判据：菜单信息架构三条硬规则 ——

  规则 1（单入口）：**不得**存在「容器根路径」形态的菜单 path（形如 `/metadata`，
           没有 `#hash`）。这类菜单的落地页**恒等于**该微应用 `<Route path="/">`
           指向的页面；只要该微应用已有任一显式 `path#hash` 入口，它就是纯噪音。
  规则 2（不 dead-link）：菜单 path 的落点必须合法 —— 要么是 shell 自身路由
           （`/` `/profile`），要么是 9 个容器前缀之一（`/metadata` 等）配合 `#hash`。
           落在集合外的（如 `/dashboard` `/tenant` `/qa-board`）点击必被
           `<Route path="*" element={<Navigate to="/" replace/>}/>` 静默送回首页。
  规则 3（按钮前缀联动）：迁移脚本**改名菜单**时，若该菜单有按钮子节点（type=2），
           同一脚本必须存在按钮名前缀联动 `REPLACE(name, '<旧名>-', '<新名>-')`。
           按钮命名约定 `<分组>-<菜单>-<动作>`（0024 种子第 143-144 行注释），
           菜单改名后按钮不联动 ⇒ 权限管理页新旧词汇混排，授权时看不清归属。

为什么需要这道门禁（判别力来源，非装饰）
--------------------------------------
2026-10-09 用户实测截图：左侧同时出现顶级「元数据建模」`/metadata` 与
「业务建模 → 建模工作台」`/metadata#/apps`，两个入口点进去**是同一个页面**。

机制（Playwright 实测，非推断）：
  1. shell `<Route path="/metadata/*">` 渲染 qiankun 容器（bone-shell/src/App.tsx:1221）；
  2. metadata-app 用 HashRouter，`App.tsx` 的 `<Route path="/">` 是
     `<Navigate to="/apps" replace/>`；
  3. ⇒ 访问 `/metadata`（无 hash）时 location.hash 自动变 `#/apps`，落地「建模工作台」。

规则 3：0039 把「建模工作台/实体/运行时数据」改名为「模型设计/业务对象/业务数据」，
其按钮前缀联动段在目标库**未落地**（2026-10-13 实测：存活按钮仍叫
「业务建模-实体-新增/发布」「业务建模-运行时数据-写入」，菜单却已叫新名）。
0039 脚本本身写全了联动语句——「写了」不等于「跑了」，而 0036 的前车之鉴是
「脚本里就漏了联动」。本规则把 0039 注释里的教训变成机器判据：**改名语句与
REPLACE 联动必须同脚本成对出现**，漏写的那一类在 CI 即时拦截。

★ 关键设计：判**净效果**，不判字面出现（v1 曾有假阳性，见下）
--------------------------------------------------------------
朴素实现「扫到 '/metadata' 字面量就报错」是错的：`0030_menu_single_entry.sql` 里
出现的 `'/metadata'` 恰恰是**要删掉它**的软删条件，报错等于把修复脚本判成违规。
因此本门禁按 **SQL 语句**分类，只对「让菜单存活」的语句（INSERT / 不带 `SET deleted`
的 UPDATE）判违规；`SET deleted = 1` 的软删语句是**修复手段**，其 path 是删除目标、
不是违规。注释行先剥离（迁移脚本头部大量注释引用 path 做背景说明）。

规则 3 的 ref 状态跟踪：菜单名跨迁移演进（0024 种子「模型管理」→ 0027「实体」→
0039「业务对象」），只按种子名判断会漏判链式改名。故从 0024 种子解析
(ref_key → 当前名, ref_key → 有无按钮子节点)，按文件名顺序应用每个改名/软删，
在**改动发生时点**判定「该菜单当时有无按钮」。无按钮菜单（如 建模工作台→模型设计）
不要求联动——给它编一条 REPLACE 反而是噪音。软删菜单时其按钮一并退役（0027 范式），
后续改名同样不要求联动。

变异验证（必做，否则门禁只是装饰）
----------------------------------
  规则 1/2：
  新增一条存活语句 `INSERT INTO iam_menu (...) VALUES (..., '/metadata', ...)`
  ⇒ 红；换成 `/metadata#/apps` ⇒ 恢复绿。
  新增 `'/dashboard'` ⇒ 红（规则 2）。
  规则 3：
  复制 0039 的改名语句 `UPDATE iam_menu SET name = '业务对象' ... name IN ('实体','模型管理')`
  到临时脚本但删去其 REPLACE 联动 ⇒ 红（实体→业务对象 命中 m_meta_ent 有按钮）；
  补回 `REPLACE(name, '业务建模-实体-', '业务建模-业务对象-')` ⇒ 恢复绿。
  对无按钮菜单（建模工作台→模型设计）删去联动 ⇒ 仍绿（不编造要求）。

用法：
  python3 scripts/check-menu-single-entry.py [--root <repo-root>]
"""
import glob
import os
import re
import sys

MIGRATION_DIR = "scripts/migration"
SHELL_APP_REL = "bone-frontend/apps/bone-shell/src/App.tsx"
SEED_FILE = "0024_seed_iam_menu_tree.sql"

# shell 自身路由（bone-shell/src/App.tsx 的 <Route path="...">，非 /xxx/* 容器形态）
SHELL_SELF_ROUTES = {"/", "/profile"}

PATH_LITERAL = re.compile(r"'(/[A-Za-z][A-Za-z0-9\-]*(?:#/[A-Za-z0-9\-/_]+)?)'")
SOFT_DELETE = re.compile(r"SET\s+deleted\s*=\s*1", re.IGNORECASE)

# ---- 规则 3（按钮前缀联动）判据 ----
# 0024 种子 plan 元组：(id, ref_key, parent_ref, name, path, icon, order, permission, type)
# path/icon 均可为 NULL（目录与按钮行大量 NULL），permission 亦可为 NULL。
SEED_TUPLE = re.compile(
    r"\(\s*\d+\s*,\s*'([a-z0-9_]+)'\s*,\s*(?:NULL|'([a-z0-9_]*)')\s*,\s*'([^']*)'\s*,"
    r"\s*(?:NULL|'[^']*')\s*,\s*(?:NULL|'[^']*')\s*,\s*\d+\s*,"
    r"\s*(?:NULL|'[^']*')\s*,\s*(\d)\s*\)"
)
RENAME_SET = re.compile(r"SET\s+name\s*=\s*'([^']+)'")
RENAME_WHERE_EQ = re.compile(r"\bname\s*=\s*'([^']+)'")
RENAME_WHERE_IN = re.compile(r"\bname\s+IN\s*\(([^)]+)\)", re.IGNORECASE)
REPLACE_CALL = re.compile(
    r"REPLACE\(\s*name\s*,\s*'([^']+)'\s*,\s*'([^']+)'\s*\)", re.IGNORECASE
)
WHERE_SPLIT = re.compile(r"\bWHERE\b", re.IGNORECASE)


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


def parse_seed_menu_state(root):
    """
    从 0024 种子解析初始菜单状态：(ref_key → 种子名, ref_key → 是否有按钮子节点)。

    只认 plan 元组行（单行五字段定位），不猜多行 VALUES——种子即真源，解析不了就返回空
    （规则 3 退化为「无按钮菜单」全跳过），绝不基于猜测判违规。
    """
    path = os.path.join(root, MIGRATION_DIR, SEED_FILE)
    names, has_buttons = {}, set()
    if not os.path.isfile(path):
        return names, has_buttons
    with open(path, "r", encoding="utf-8") as fh:
        for line in fh:
            m = SEED_TUPLE.search(line)
            if not m:
                continue
            ref, parent_ref, _name, node_type = m.group(1), m.group(2), m.group(3), int(m.group(4))
            if node_type == 2:
                if parent_ref:
                    has_buttons.add(parent_ref)
            else:
                names[ref] = _name
    return names, has_buttons


def scan_menu_renames(root):
    """
    按文件名顺序扫描迁移脚本，跟踪菜单名演进，返回 (改名事件, 每文件 REPLACE 对)。

    改名事件：(文件, 行号, 旧名, 新名, 该菜单当时有无按钮子节点)。
    REPLACE 对：[(源前缀, 目标前缀)]，用于校验「改名 ⇔ 按钮联动」成对。
    """
    names, has_buttons = parse_seed_menu_state(root)
    events, replaces = [], {}
    for rel, lineno, stmt in iter_sql_statements(root):
        if "iam_menu" not in stmt:
            continue
        upper = stmt.upper()
        for m in REPLACE_CALL.finditer(stmt):
            replaces.setdefault(rel, []).append((m.group(1), m.group(2)))
        if "UPDATE" not in upper:
            continue
        parts = WHERE_SPLIT.split(stmt)
        where_part = parts[1] if len(parts) > 1 else ""
        set_m = RENAME_SET.search(stmt)
        if set_m and where_part:
            new_name = set_m.group(1)
            old_names = [m.group(1) for m in RENAME_WHERE_EQ.finditer(where_part)]
            in_m = RENAME_WHERE_IN.search(where_part)
            if in_m:
                old_names += re.findall(r"'([^']+)'", in_m.group(1))
            for old in old_names:
                matched = [r for r, n in names.items() if n == old]
                # 多个 ref 同名（如「代码生成」既是目录也是菜单）时保守判定：任一有按钮即要求联动
                had_buttons = any(r in has_buttons for r in matched)
                events.append((rel, lineno, old, new_name, had_buttons))
                for r in matched:
                    names[r] = new_name
        if SOFT_DELETE.search(stmt) and where_part:
            # 菜单软删 ⇒ 其按钮子节点一并退役（0027 范式），后续改名不再要求联动
            del_names = [m.group(1) for m in RENAME_WHERE_EQ.finditer(where_part)]
            in_m = RENAME_WHERE_IN.search(where_part)
            if in_m:
                del_names += re.findall(r"'([^']+)'", in_m.group(1))
            for old in del_names:
                for r, n in list(names.items()):
                    if n == old:
                        has_buttons.discard(r)
    return events, replaces


def check_button_prefix_linkage(root):
    """规则 3：有按钮的菜单改名必须同脚本带 REPLACE 前缀联动。返回违规列表。"""
    events, replaces = scan_menu_renames(root)
    violations = []
    for rel, lineno, old, new, had_buttons in events:
        if not had_buttons:
            continue
        linked = any(
            src.endswith(old + "-") and dst.endswith(new + "-")
            for src, dst in replaces.get(rel, [])
        )
        if not linked:
            violations.append((
                rel, lineno, old, new,
                f"菜单改名「{old} → {new}」未在同脚本联动按钮前缀："
                f"该菜单有按钮子节点（命名约定 `<分组>-<菜单>-<动作>`），"
                f"缺少 REPLACE(name, '...{old}-', '...{new}-')",
            ))
    return violations


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

    # 规则 3：菜单改名 ⇔ 按钮前缀联动成对
    prefix_violations = check_button_prefix_linkage(root)
    for rel, ln, old, new, reason in prefix_violations:
        print(f"[G35] ❌ {rel}:{ln} [规则3 按钮前缀联动] {reason}")

    if violations or prefix_violations:
        for rel, ln, p, rule, reason in violations:
            print(f"[G35] ❌ {rel}:{ln} [{rule}] {reason}")
        print(
            f"[G35] 发现 {len(violations) + len(prefix_violations)} 处信息架构违规。\n"
            f"  · 规则1：删掉裸容器根路径菜单，保留 `path#hash` 形态的显式入口（元数据域入口 = `/metadata#/apps`）；\n"
            f"  · 规则2：死链菜单须经迁移**软删**（`SET deleted = 1`），不要留「点了跳首页」的菜单；\n"
            f"  · 规则3：菜单改名须同脚本联动按钮前缀 REPLACE(name, '<旧>-', '<新>-')"
            f"（0036 漏联动、0039 联动未落地两件事故的机器判据）；\n"
            f"  · 参考实现：`scripts/migration/0030_menu_single_entry.sql`、"
            f"`scripts/migration/0039_ia_business_modeling_rename.sql`。"
        )
        sys.exit(1)

    print(
        f"[G35] ✅ 菜单入口唯一性 + 按钮前缀联动通过"
        f"（校验 {scanned} 条存活语句 / {len(hashed_paths)} 个显式入口；"
        f"软删目标 {len(soft_deleted)} 个；0 个裸容器入口 / 0 个死链 / "
        f"{len(prefix_violations)} 个未联动改名）"
    )


if __name__ == "__main__":
    main()