#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-seed-menu-permission.py  (G40 · 菜单 permission 必须存在对应权限点)

判据：bootstrap 种子里 `iam_menu.permission` 的每一个**非空**取值，都必须在
      `iam_permission.code` 集合内。否则该菜单在运行时被 `MenuApplicationService#current`
      按 `scopes.contains(menu.permission)` 静默剪掉——**菜单不报错、接口可见、名字也对，
      但侧栏里就是没有它**。这是与 G37「图标静默降级」同一类的**配了不生效**静默失效，
      只是后果更严重（整个菜单消失 vs 只是图标不对）。

为什么是静态解析而非连库
------------------------
种子由 mysqldump `--complete-insert` 生成，列名写在语句里，可离线可靠解析；
这样 check.sh（不保证有本地 MySQL）也能跑。CI 里另有 `--db` 模式做权威 join 复核。

防假绿自检
----------
· 解析不到 iam_menu / iam_permission 任一段 → 判红（不是「没数据就过」，那是 G39 踩过的坑）。
· 菜单段解析出 0 行 → 判红（种子空了，本门禁失去意义）。
· 匹配一条都不做断言 = 恒过，故必须确有 menu.permission 非空才放行。

用法：
  python3 scripts/check-seed-menu-permission.py [--seed <10_bootstrap_seed.sql>]
  python3 scripts/check-seed-menu-permission.py --db <db>     # 权威 join 复核（需 MySQL）
"""
import argparse
import os
import re
import subprocess
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SEED_REL = os.path.join("scripts", "seed", "bootstrap", "10_bootstrap_seed.sql")

REPLACE_RE = re.compile(r"REPLACE INTO `([^`]+)`\s*\(([^)]*)\)\s*VALUES\s*", re.I)


def _unquote(tok: str):
    """把 mysqldump 的值 token 还原成字面量；NULL → None。"""
    t = tok.strip()
    if t.upper() == "NULL":
        return None
    if len(t) >= 2 and t[0] == "'" and t[-1] == "'":
        inner = t[1:-1]
        inner = inner.replace("''", "'").replace("\\'", "'").replace('\\"', '"')
        return inner
    return t


def _split_rows(stmt: str):
    """解析一条 REPLACE INTO 语句为多行 [(col, raw_value), ...]。

    状态机：尊重单引号字符串（含 \\' 与 '' 转义）与括号深度。
    · 列值分隔逗号出现在**元组内部**（depth==1）；元组之间的逗号在 depth==0，忽略。
    · 元组自身的 `(` / `)` 不计入值（只在 depth 跨 0 时切换），避免括号漏进首尾值。
    """
    m = REPLACE_RE.match(stmt)
    if not m:
        return None
    cols = [c.strip().strip("`") for c in m.group(2).split(",")]
    body = stmt[m.end():]
    rows = []
    i, n = 0, len(body)
    cur = ""
    in_str = False
    esc = False
    vals = []
    depth = 0
    while i < n:
        ch = body[i]
        if in_str:
            if esc:
                cur += ch
                esc = False
            elif ch == "\\":
                cur += ch
                esc = True
            elif ch == "'":
                cur += ch
                in_str = False
            else:
                cur += ch
        else:
            if ch == "'":
                cur += ch
                in_str = True
            elif ch == "(":
                depth += 1  # 元组开始，不进值
            elif ch == ")":
                depth -= 1
                if depth == 0:
                    vals.append(cur)
                    cur = ""
                    if len(vals) == len(cols):
                        rows.append(list(zip(cols, vals)))
                    vals = []
                # 元组结束的 ) 不进值
            elif ch == "," and depth == 1:
                vals.append(cur)
                cur = ""
            elif ch == "," and depth == 0:
                pass  # 元组之间的分隔逗号，忽略（否则会漏进下一行首值导致列错位）
            elif ch == ";":
                break
            else:
                cur += ch
        i += 1
    return rows


def parse_seed(path: str):
    text = open(path, encoding="utf-8", errors="ignore").read()
    perm_codes = set()
    menu_perms = []  # (name, permission)
    menu_rows = 0
    perm_rows = 0
    for stmt in re.findall(r"REPLACE INTO `[^;]*;", text, flags=re.S):
        rows = _split_rows(stmt)
        if rows is None:
            continue
        table = REPLACE_RE.match(stmt).group(1)
        if table == "iam_permission":
            for r in rows:
                d = dict((c, _unquote(v)) for c, v in r)
                if d.get("code") is not None:
                    perm_codes.add(d["code"])
                    perm_rows += 1
        elif table == "iam_menu":
            for r in rows:
                d = dict((c, _unquote(v)) for c, v in r)
                if d.get("permission") not in (None, ""):
                    menu_perms.append((d.get("name"), d["permission"]))
                    menu_rows += 1
    return perm_codes, menu_perms, menu_rows, perm_rows


def check_static(seed_path: str) -> int:
    if not os.path.isfile(seed_path):
        print(f"[G40] ❌ 找不到种子文件：{seed_path}")
        return 1
    perm_codes, menu_perms, menu_rows, perm_rows = parse_seed(seed_path)
    if perm_rows == 0:
        print(f"[G40] ❌ 种子里解析不到 iam_permission 行——门禁失去校验对象，必须判红。"
              " 重新生成：python3 scripts/seed-bootstrap.py")
        return 1
    if menu_rows == 0:
        print(f"[G40] ❌ 种子里解析不到任何带 permission 的菜单行——菜单段可能为空，"
              "整棵菜单树会消失，必须判红。")
        return 1

    orphans = [(name, p) for name, p in menu_perms if p not in perm_codes]
    if orphans:
        print(f"[G40] ❌ {len(orphans)} 个菜单的 permission 在权限点里找不到对应 code"
              f"（菜单将在运行时被按 scope 静默剪掉）：")
        for name, p in sorted(set(orphans)):
            print(f"      menu=`{name}`  permission=`{p}`")
        print("      修复：把该权限点补进 iam_permission 种子，或改正菜单的 permission 码。")
        return 1

    print(f"[G40] ✅ 菜单 permission 全部有对应权限点"
          f"（{menu_rows} 个带权限菜单 / {perm_rows} 个权限点 / 0 孤儿）")
    return 0


def check_db(db: str) -> int:
    cli = os.environ.get("MYSQL_CLIENT") or (
        "/usr/local/mysql/bin/mysql" if os.path.exists("/usr/local/mysql/bin/mysql") else "mysql")
    conn = ["-h" + os.environ.get("BONE_DB_HOST", "127.0.0.1"),
            "-P" + os.environ.get("BONE_DB_PORT", "3306"),
            "-u" + os.environ.get("BONE_DB_USER", "root"),
            "-p" + os.environ.get("BONE_DB_PASSWORD", "mysql123")]
    q = ("SELECT m.name, m.permission FROM iam_menu m "
         "WHERE m.deleted = 0 AND m.permission IS NOT NULL AND m.permission <> '' "
         "AND NOT EXISTS (SELECT 1 FROM iam_permission p "
         "WHERE p.code = m.permission AND p.deleted = 0)")
    out = subprocess.run([cli, *conn, "-N", "-B", db, "-e", q],
                         capture_output=True, text=True)
    if out.returncode != 0:
        print("[G40] ❌ 数据库复核失败：" + out.stderr.strip().splitlines()[-1:])
        return 1
    rows = [l for l in out.stdout.splitlines() if l.strip()]
    if rows:
        print(f"[G40] ❌ 数据库里 {len(rows)} 个菜单的 permission 无对应权限点：")
        for l in rows[:20]:
            name, perm = l.split("\t", 1)
            print(f"      menu=`{name}`  permission=`{perm}`")
        return 1
    print(f"[G40] ✅ 数据库复核通过（{db} 无孤儿菜单 permission）")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--seed", help=f"种子 SQL（默认 {SEED_REL}）")
    ap.add_argument("--db", help="连库做权威 join 复核（默认走静态解析）")
    args = ap.parse_args()
    if args.db:
        return check_db(args.db)
    seed = args.seed or os.path.join(REPO, SEED_REL)
    return check_static(seed)


if __name__ == "__main__":
    sys.exit(main())
