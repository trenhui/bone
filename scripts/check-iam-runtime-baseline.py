#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""check-iam-runtime-baseline.py  (G43 · IAM 运行时数据下限)

判据：`bone-init.sql` 必须携带**能让系统登进去**的那几行运行时数据，
      且行数不低于下限。

背景（2026-11-03 实测）
----------------------
本仓有两套数据通道，职责不同：

  · `bone-init.sql` —— 结构 + **IAM 运行时数据**（账号 / 租户 / 角色 / 授权绑定）。
    新环境灌完它就该能登录。
  · `scripts/seed/bootstrap/10_bootstrap_seed.sql` —— 菜单 / 权限点 / 字典
    （`seed-manifest.json` 已对这 4 张表做下限校验，见 `seed-bootstrap.py --verify`）。

问题：IAM 运行时那 5 张表（`iam_account` / `iam_tenant` / `iam_role` /
`iam_account_role` / `iam_role_permission`）**在 init 里，但没有任何门禁看它们**。
实测 dev 库这 5 张表一度全部 0 行（结构在、数据空），而当时：
  · G39（init 覆盖）只比**列**，不数**行** ⇒ 绿；
  · G40（菜单 permission）只比种子里的码 ⇒ 与账号无关 ⇒ 绿；
  · seed-manifest 只覆盖 4 张种子表，不含这 5 张 ⇒ 绿。
结果是 dev 环境**登不进去**，而所有门禁全绿 —— 与「配了不生效」同一类静默失效。

为什么判「init」而不是判「活库」
--------------------------------
活库行数会随业务正常波动（加账号、加角色），拿它当下限会天天误报。
init 是**全量基线**，它的数据段就是"新环境的最小可用集"——把它钉住才有判据。
同时提供 `--db` 模式做活库对照（只报不红，供人看）。

下界怎么定
----------
按**业务闭环**定，不按"当前有多少"定：
  · `iam_account` ≥ 1 —— 至少一个可登录账号；
  · `iam_tenant` ≥ 1 —— 至少一个租户（账号的 tenant_id 指向它）；
  · `iam_role` ≥ 1 —— 至少一个角色；
  · `iam_account_role` ≥ 1 —— 至少一条账号-角色绑定（**没有它就登进去也是零权限**）；
  · `iam_role_permission` ≥ 1 —— 至少一条角色-权限绑定（**没有它就是白板**）。

防假绿
------
· 解析不到 `bone-init.sql` ⇒ 判红；
· 某张表在 init 里找不到 INSERT ⇒ 判红（不是"没数据就过"）；
· 解析出行数但下限为 0 ⇒ 判红（下限为 0 等于没校验）。

基线纪律
--------
`scripts/iam-runtime-baseline.json` 记录**当前下界**；它只可**上移**（下界提高 ⇒ 更严），
下界被降低须先改代码里的 `MIN_ROWS` 并在此登记理由——否则「把下限调小」会退化成
"永远绿"。本门禁校验 JSON 与代码常量一致。

用法：
  python3 scripts/check-iam-runtime-baseline.py                 # 静态解析 init（默认）
  python3 scripts/check-iam-runtime-baseline.py --db bone       # 活库对照（只报）
  python3 scripts/check-iam-runtime-baseline.py --baseline <json>
"""
import argparse
import json
import os
import re
import subprocess
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
INIT_REL = os.path.join("bone-init.sql")
BASELINE_REL = os.path.join("scripts", "iam-runtime-baseline.json")

# 每张表的业务下界（理由见上）。改这里必须同步改 baseline.json，否则本门禁判红。
MIN_ROWS = {
    "iam_account": 1,
    "iam_tenant": 1,
    "iam_role": 1,
    "iam_account_role": 1,
    "iam_role_permission": 1,
}

# 每个下界对应的「为什么非它不可」——写进 baseline.json 供人复核。
WHY = {
    "iam_account": "至少一个可登录账号（admin）；没有它新环境连登录页都过不去",
    "iam_tenant": "至少一个租户；账号的 tenant_id 指向它，缺了账号无法归属租户",
    "iam_role": "至少一个角色；没有角色就没有任何可授予的权限集合",
    "iam_account_role": "至少一条账号-角色绑定；没有它账号登进去也是零权限（静默失败）",
    "iam_role_permission": "至少一条角色-权限绑定；没有它角色是白板，登录后侧栏全空",
}


def _split_values(text):
    """按顶层逗号切 VALUES 里的元组文本（跳过字符串内的逗号与括号）。"""
    out = []
    depth = 0
    in_str = False
    buf = []
    i = 0
    while i < len(text):
        c = text[i]
        if in_str:
            buf.append(c)
            if c == "\\":
                if i + 1 < len(text):
                    buf.append(text[i + 1])
                    i += 2
                    continue
            elif c == "'":
                in_str = False
            i += 1
            continue
        if c == "'":
            in_str = True
            buf.append(c)
        elif c == "(":
            depth += 1
            if depth == 1:
                buf = []
            else:
                buf.append(c)
        elif c == ")":
            depth -= 1
            if depth == 0:
                out.append("".join(buf))
            else:
                buf.append(c)
        elif c == "," and depth == 1:
            buf.append(c)
        else:
            if depth >= 1:
                buf.append(c)
        i += 1
    return out


def parse_init_rows(init_path):
    """返回 {表: [VALUES 里的元组字符串, ...]}；表不存在 ⇒ 该表不进字典。"""
    with open(init_path, encoding="utf-8", errors="ignore") as fh:
        body = fh.read()
    # 去掉整行注释，避免注释里的 INSERT 字样被误采
    body = "\n".join(l for l in body.split("\n") if not l.strip().startswith("--"))
    rows = {}
    pat = re.compile(
        r"INSERT\s+INTO\s+`?(?P<tbl>\w+)`?\s*\([^)]*\)\s*VALUES\s*(?P<vals>.*?);\s*(?:\n|$)",
        re.S | re.I,
    )
    for m in pat.finditer(body):
        tbl = m.group("tbl").lower()
        rows.setdefault(tbl, []).extend(_split_values(m.group("vals")))
    return rows


def check_init(init_path, baseline):
    """静态判定 init 的 IAM 运行时数据。返回 (errors, details)。"""
    errors = []
    details = []
    if not os.path.isfile(init_path):
        return [f"找不到 {init_path} —— init 是新环境唯一入口，缺了就没法判"], details
    rows = parse_init_rows(init_path)
    for table, min_rows in sorted(MIN_ROWS.items()):
        n = len(rows.get(table, []))
        recorded = baseline.get("tables", {}).get(table, {}).get("min_rows")
        if recorded is None:
            errors.append(
                f"基线文件里没有 `{table}` 的下界 —— 该表失去校验，必须补登记（否则本门禁对它恒过）"
            )
            continue
        if recorded != min_rows:
            errors.append(
                f"`{table}` 的下界不一致：baseline.json 记 {recorded}，代码 MIN_ROWS 记 {min_rows}。"
                f"两者必须一致，否则会出现「改代码绕过校验」。"
            )
            continue
        if n < min_rows:
            errors.append(
                f"`{table}` 在 {os.path.basename(init_path)} 里只有 {n} 行，"
                f"低于下界 {min_rows} —— 新环境灌完 init 后{WHY[table]}。"
                f"修法：把该表的 INSERT 补回 init（数据在 scripts/seed/ 或历史 init 里）。"
            )
        details.append((table, n, min_rows))
    return errors, details


def check_db(db_name):
    """活库对照：只报不红（活库行数会正常波动，不当下限）。"""
    client = None
    for cand in ("mysql", "mariadb"):
        # PATH 里通常没有 mysql（macOS 常见于 /usr/local/mysql/bin）；
        # 补上这几个常见安装位置，否则「有库却查不了」会被误报成「跳过」。
        candidates = [os.path.join(p, cand) for p in os.environ.get("PATH", "").split(os.pathsep)]
        candidates += [
            os.path.join(d, cand)
            for d in ("/usr/local/mysql/bin", "/opt/homebrew/bin", "/opt/homebrew/opt/mysql-client/bin")
        ]
        for exe in candidates:
            if os.path.isfile(exe) and os.access(exe, os.X_OK):
                client = exe
                break
        if client:
            break
    if not client:
        print("ℹ️  未找到 mysql 客户端，跳过活库对照（非违规）")
        return 0
    sql = " UNION ALL ".join(
        f"SELECT '{t}', COUNT(*) FROM `{t}`" for t in sorted(MIN_ROWS)
    )
    env = dict(os.environ)
    env["MYSQL_PWD"] = os.environ.get("MYSQL_PWD", "mysql123")
    try:
        r = subprocess.run(
            [client, "-h127.0.0.1", "-uroot", "--default-character-set=utf8mb4", "-N", "-B", "-e", sql, db_name],
            capture_output=True, text=True, env=env, timeout=30,
        )
    except (OSError, subprocess.SubprocessError) as exc:
        print(f"ℹ️  活库对照失败（{exc}），跳过（非违规）")
        return 0
    if r.returncode != 0:
        print(f"ℹ️  活库对照失败（{r.stderr.strip()[:160]}），跳过（非违规）")
        return 0
    print(f"ℹ️  活库 {db_name} 的 IAM 运行时数据（对照用，不作判据）：")
    for line in r.stdout.strip().splitlines():
        parts = line.split("\t")
        if len(parts) == 2:
            t, n = parts[0], parts[1]
            mark = "" if int(n) >= MIN_ROWS.get(t, 0) else "  ← 低于下界"
            print(f"    {t}: {n} 行（下界 {MIN_ROWS.get(t, '?')}）{mark}")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=REPO)
    ap.add_argument("--init", default=None)
    ap.add_argument("--baseline", default=None)
    ap.add_argument("--db", default=None, help="额外做活库对照（只报不红）")
    args = ap.parse_args()
    root = os.path.abspath(args.root)
    init_path = args.init or os.path.join(root, INIT_REL)
    baseline_path = args.baseline or os.path.join(root, BASELINE_REL)

    if not os.path.isfile(baseline_path):
        print(f"❌ 找不到基线文件 {baseline_path} —— 没有下界清单等于没有校验，必须判失败")
        return 1
    try:
        baseline = json.loads(open(baseline_path, encoding="utf-8").read())
    except (OSError, ValueError) as exc:
        print(f"❌ 基线文件解析失败：{exc}")
        return 1

    errors, details = check_init(init_path, baseline)

    if errors:
        print(f"❌ IAM 运行时数据基线：{len(errors)} 项不达标\n")
        for e in errors:
            print("  · " + e)
        return 1

    print(
        "OK: IAM 运行时数据基线通过（"
        + "、".join(f"{t} {n}/{m}" for t, n, m in details)
        + "）"
    )
    if args.db:
        check_db(args.db)
    return 0


if __name__ == "__main__":
    sys.exit(main())
