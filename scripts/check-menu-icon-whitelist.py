#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-menu-icon-whitelist.py  (G37 · 菜单图标白名单)

判据：`scripts/migration/*.sql` 与 `bone-init.sql` 里写进 `iam_menu.icon` 的图标名，
      必须已登记在 `bone-frontend/apps/bone-shell/src/App.tsx` 的 `MENU_ICON_MAP`。

为什么需要这一步
----------------
`MENU_ICON_MAP` 是**白名单映射**而不是动态取（`require('@ant-design/icons')[name]`）——
动态取会把整个图标包打进产物且逃过类型检查（见 App.tsx 该常量的注释）。代价是：
数据里写了一个没登记的图标名，既不报错也不告警，只会被 `resolveMenuIcon` 的
`|| DEFAULT_MENU_ICON` 静默降级。表现是「菜单种下发、接口可见、名字也对，但图标不对」，
跟 G32「点了跳别处」、G35「点了静默回首页」是同一类**配了不生效**的静默失效。

2026-10-30 实证：0078（初审工作台）初版写的是 `SolutionOutlined`，不在 38 项白名单内。
该脚本在落地前拦下并改为 `ProfileOutlined`。此前该维度零门禁。

扫描口径
--------
· 只取**单引号字面量**（`'ProfileOutlined'`），并先剥掉 SQL 注释。
  注释里引用图标名约定用反引号（`` `SolutionOutlined` ``），故不会误伤
  ——「注释里解释为什么不用某个图标」正是需要保留的上下文，不该被门禁逼着删。
· 同时接受 Outlined / Filled / TwoTone 三种 antd 后缀，避免只认 Outlined 而漏掉
  `CheckCircleFilled` 这类写法（漏认 = 静默放行）。

防假绿自检
----------
解析不到图标清单、扫不到任何 SQL、或一个字面量都没匹配到，均判失败而非通过：
宁可红着让人来看解析逻辑，也不要绿着假装校验过了（这是 `_check_numbers.py` 的
`BASE='.'` 曾把 FORBIDDEN 判恒过的教训）。

用法：
  python3 scripts/check-menu-icon-whitelist.py [--root <repo-root>]
      [--app <App.tsx>] [--migration-dir <dir>] [--init <bone-init.sql>]   # 后四项供变异测试
"""
import os
import re
import sys
from glob import glob

APP_REL = os.path.join("bone-frontend", "apps", "bone-shell", "src", "App.tsx")
MIGRATION_REL = os.path.join("scripts", "migration")
INIT_REL = "bone-init.sql"

ICON_LITERAL = re.compile(r"'([A-Za-z0-9_]+(?:Outlined|Filled|TwoTone))'")


def find_repo_root():
    here = os.path.dirname(os.path.abspath(__file__))
    out = os.popen("git rev-parse --show-toplevel 2>/dev/null").read().strip()
    return out or os.path.dirname(here)


def strip_sql_comments(text):
    """剥掉 `/* ... */` 与 `-- ...` 注释，避免注释里的图标名被当成种子值。"""
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"--[^\n]*", "", text)


def whitelist_keys(app_path):
    """从 App.tsx 的 MENU_ICON_MAP 取出已登记的图标名集合。"""
    with open(app_path, "r", encoding="utf-8") as fh:
        src = fh.read()
    m = re.search(
        r"const MENU_ICON_MAP[^=]*=\s*\{(.*?)\n\};", src, flags=re.S
    )
    if not m:
        return None
    return set(re.findall(r"^\s+([A-Za-z0-9_]+)\s*:", m.group(1), flags=re.M))


def collect_usages(paths):
    """返回 {icon: [文件相对名, ...]}，只统计单引号字面量。"""
    found = {}
    for p in paths:
        try:
            with open(p, "r", encoding="utf-8", errors="ignore") as fh:
                body = strip_sql_comments(fh.read())
        except OSError:
            continue
        for name in ICON_LITERAL.findall(body):
            found.setdefault(name, []).append(os.path.basename(p))
    return found


def main():
    root = None
    app_override = None
    mig_override = None
    init_override = None
    args = sys.argv[1:]
    i = 0
    while i < len(args):
        a = args[i]
        if a == "--root" and i + 1 < len(args):
            root = args[i + 1]; i += 2; continue
        if a == "--app" and i + 1 < len(args):
            app_override = args[i + 1]; i += 2; continue
        if a == "--migration-dir" and i + 1 < len(args):
            mig_override = args[i + 1]; i += 2; continue
        if a == "--init" and i + 1 < len(args):
            init_override = args[i + 1]; i += 2; continue
        i += 1

    if root is None:
        root = find_repo_root()

    app_path = app_override or os.path.join(root, APP_REL)
    if not os.path.isfile(app_path):
        print(f"[G37] ❌ 找不到 shell 入口：{app_path}")
        return 1

    keys = whitelist_keys(app_path)
    if not keys:
        print(
            "[G37] ❌ 未能从 MENU_ICON_MAP 解析出任何图标名。\n"
            "      解析逻辑可能已漂移（常量改名 / 结构变化）——这是本门禁的假绿口子，"
            "必须先修脚本再看结论。"
        )
        return 1

    mig_dir = mig_override or os.path.join(root, MIGRATION_REL)
    init_path = init_override or os.path.join(root, INIT_REL)
    sql_files = sorted(glob(os.path.join(mig_dir, "*.sql")))
    if os.path.isfile(init_path):
        sql_files.append(init_path)
    if not sql_files:
        print(f"[G37] ❌ 未扫描到任何 SQL 文件（{mig_dir} / {init_path}），无法校验")
        return 1

    usages = collect_usages(sql_files)
    if not usages:
        print(
            f"[G37] ❌ 扫描 {len(sql_files)} 个 SQL 却未匹配到任何图标字面量。\n"
            "      要么种子真的一份都没有（那本门禁已失去意义），要么匹配规则漂移。"
        )
        return 1

    unknown = {k: v for k, v in usages.items() if k not in keys}
    if unknown:
        print(f"[G37] ❌ 菜单种子使用了未登记的图标名（{len(unknown)} 个）：")
        for name, files in sorted(unknown.items()):
            print(f"      {name}  ←  {', '.join(sorted(set(files)))}")
        print(
            "      `MENU_ICON_MAP` 是白名单，未登记的图标会被静默降级成默认图标（不报错）。\n"
            "      二选一：改用已登记的图标（数据侧，轻），或在 App.tsx 的 MENU_ICON_MAP 里登记"
            "（前端侧，需重新构建）。"
        )
        return 1

    print(
        f"[G37] ✅ 菜单图标全部已登记（{len(usages)} 个图标 / 白名单 {len(keys)} 项 / "
        f"扫描 {len(sql_files)} 个 SQL）"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
