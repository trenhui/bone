#!/usr/bin/env python3
"""新环境启动种子（bootstrap seed）：让「灌完 init 的库」不是一个空壳。

问题实证（2026-10-30 实测，把 bone-init.sql 灌进空库后计数）
------------------------------------------------------------
    iam_menu       0 行（dev 321）      → 登录后侧栏空白，菜单按 permission 过滤全落空
    sys_dict_type  0 行（dev 91）       → 所有下拉/字典枚举无值
    iam_permission 91 行（dev 146）     → 部分接口 @PreAuthorize 依赖的码不存在，403
    iam_account    0 行                 → 没人能登录
结构对了但数据没有，结果仍是「跑不起来」，而**没有任何门禁在看**：G39 只看表是否存在。

为什么种子单独一个入口，而不是全塞进 bone-init.sql
--------------------------------------------------
· init 是**结构**（schema），种子是**内容**（data）。混在一起会让「重建结构」与「重置数据」
  互相绑架：想清空业务数据重来，就得连着 DDL 一起重建；想改一行字典，diff 里会混进上百张表的 DDL。
· 业界的分野很清楚：Rails 用 schema.rb + seeds.rb，Django 用 migrations + fixtures/loaddata，
  Flyway 的 `R__` repeatable migration 专门跑参考数据。共同点是**结构可重建、种子可重放**。
· 种子必须**幂等可重放**：这里一律生成 REPLACE INTO（本库无外键，REPLACE 语义安全），
  重复执行结果一致，不会因「跑过两次」而炸唯一键。

导出源与过滤规则（显式、可审、可复现）
--------------------------------------
以本地 dev 库为源（它跑过全部增量，是「已知可用」的最接近形态），但**必须过滤联调残留**：
实测 dev 的 iam_menu 里混着 `E2E817117` / `新增测试` 这类 E2E 产物，整库照搬会把垃圾固化成基线。
过滤规则写死在 SEED_SPECS 的 where 里，生成时打印各表导出/排除行数，任何一条都可复核。

用法
----
  python3 scripts/seed-bootstrap.py                     # 从活库重新生成种子 SQL + 清单
  python3 scripts/seed-bootstrap.py --check             # 只校验已生成的种子是否与活库一致
  python3 scripts/seed-bootstrap.py --apply --db <db>   # 灌入指定库
  python3 scripts/seed-bootstrap.py --verify --db <db>  # 校验指定库的行数下限

环境变量：BONE_DB_HOST / BONE_DB_PORT / BONE_DB_USER / BONE_DB_PASSWORD / BONE_DB_NAME（源库）
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
OUT_DIR = REPO / "scripts" / "seed" / "bootstrap"
OUT_SQL = OUT_DIR / "10_bootstrap_seed.sql"
OUT_MANIFEST = OUT_DIR / "seed-manifest.json"

# (表, where 条件, 为什么它是启动必需)
# 过滤规则必须四张表**都**加 E2E 排除：2026-10-30 实测 dev 库里
#   iam_permission 28 行 / sys_dict_type 47 行 / sys_dict_item 58 行 / iam_menu 1 行
# 是 E2E 联调残留（码形如 e2e191721、E2E_DICT_354510、E2E_DI_...）。
# 只给 iam_menu 加过滤会让另外三张表的残渣固化进基线——上一版的真实事故。
# sys_dict_item 必须连带排除 type_code 命中的 E2E 类型（否则字典项指向一个被删的 E2E 类型）。
E2E_RE = "E2E|e2e"
SEED_SPECS = [
    ("iam_permission",
     "deleted = 0 AND code NOT REGEXP '%s' AND name NOT REGEXP '%s|测试'" % (E2E_RE, E2E_RE),
     "权限点：接口 @PreAuthorize 与菜单 permission 都按码匹配，缺码即 403 / 菜单被剪；排除 E2E 联调权限"),
    ("iam_menu",
     "tenant_id IN (0, 1001) AND deleted = 0 "
     "AND name NOT REGEXP '%s|测试' AND IFNULL(path, '') NOT REGEXP 'e2e|test' "
     "AND IFNULL(permission, '') NOT REGEXP '%s'" % (E2E_RE, E2E_RE),
     "菜单树：tenant 0 为平台模板、1001 为默认租户；已排除 E2E 联调残留菜单（含其脏 permission 码）"),
    ("sys_dict_type",
     "deleted = 0 AND code NOT REGEXP '%s' AND name NOT REGEXP '%s|测试'" % (E2E_RE, E2E_RE),
     "字典类型：下拉/枚举的取值域定义；排除 E2E 联调字典"),
    ("sys_dict_item",
     "deleted = 0 AND type_code NOT REGEXP '%s' "
     "AND code NOT REGEXP '%s' AND value NOT REGEXP '%s'" % (E2E_RE, E2E_RE, E2E_RE),
     "字典项：有类型无项等于没有字典；排除 E2E 类型及其项下所有项"),
]

SEED_TABLES = [spec[0] for spec in SEED_SPECS]


def _env(name: str, default: str) -> str:
    return os.environ.get(name, default)


def find_client(name: str) -> str | None:
    explicit = os.environ.get(f"{name.upper()}_CLIENT")
    if explicit and Path(explicit).exists():
        return explicit
    found = shutil.which(name)
    if found:
        return found
    for cand in (f"/usr/local/mysql/bin/{name}", f"/opt/homebrew/bin/{name}", f"/usr/bin/{name}"):
        if Path(cand).exists():
            return cand
    return None


class Db:
    def __init__(self, db: str | None = None) -> None:
        self.db = db or _env("BONE_DB_NAME", "bone")
        self.host = _env("BONE_DB_HOST", "127.0.0.1")
        self.port = _env("BONE_DB_PORT", "3306")
        self.user = _env("BONE_DB_USER", "root")
        self.password = _env("BONE_DB_PASSWORD", "mysql123")

    @property
    def conn(self) -> list[str]:
        return [f"-h{self.host}", f"-P{self.port}", f"-u{self.user}", f"-p{self.password}"]

    def count(self, table: str, where: str = "1=1", db: str | None = None) -> int:
        cli = find_client("mysql")
        if not cli:
            raise SystemExit("❌ 找不到 mysql 客户端（设 MYSQL_CLIENT 或装到 PATH）")
        out = subprocess.run(
            [cli, *self.conn, "-N", "-B", db or self.db, "-e", f"SELECT COUNT(*) FROM `{table}` WHERE {where}"],
            capture_output=True, text=True,
        )
        if out.returncode != 0:
            raise SystemExit(f"❌ 统计 {table} 失败：{out.stderr.strip().splitlines()[-1:] or out.stderr}")
        return int(out.stdout.strip() or 0)


def dump_table(db: Db, table: str, where: str) -> str:
    """mysqldump --no-create-info：只取数据。转义交给工具，不自己拼 SQL。"""
    cli = find_client("mysqldump")
    if not cli:
        raise SystemExit("❌ 找不到 mysqldump（设 MYSQLDUMP_CLIENT 或装到 PATH）")
    out = subprocess.run(
        [cli, *db.conn, "--no-create-info", "--skip-add-locks", "--skip-comments",
         "--no-tablespaces", "--complete-insert", "--skip-dump-date",
         f"--where={where}", db.db, table],
        capture_output=True, text=True,
    )
    if out.returncode != 0:
        raise SystemExit(f"❌ 导出 {table} 失败：{out.stderr.strip()}")
    body = out.stdout
    # 只保留 INSERT 语句；SET/LOCK 之类的会话语句对新库无意义。
    lines = [ln for ln in body.splitlines()
             if ln.startswith("INSERT INTO") or ln.startswith("--") and False]
    # 幂等：REPLACE INTO（本库无外键，重复执行结果一致）
    return "\n".join(ln.replace("INSERT INTO", "REPLACE INTO", 1) for ln in lines)


def orphan_menu_ids(db: Db) -> list[str]:
    """返回 dev 里 permission 指向不存在权限点的菜单 id（静默失效：普通租户永远看不到）。

    这层一致性是 G40（check-seed-menu-permission.py）的权威来源：生成种子时就把这些
    幽灵菜单排除，避免「新环境灌了种子却渲染不出菜单」的假象。根因（dev 缺对应权限点）
    是独立的数据缺陷，由 owner 在 dev 补权限点后再 regenerate 即可让菜单回归。
    """
    out = subprocess.run(
        [find_client("mysql"), *db.conn, "-N", "-B", db.db, "-e",
         "SELECT id FROM iam_menu WHERE deleted = 0 AND permission IS NOT NULL "
         "AND permission <> '' AND permission NOT IN "
         "(SELECT code FROM iam_permission WHERE deleted = 0)"],
        capture_output=True, text=True,
    )
    if out.returncode != 0:
        raise SystemExit(f"❌ 查孤儿菜单失败：{out.stderr.strip().splitlines()[-1:] or out.stderr}")
    return [ln.strip() for ln in out.stdout.splitlines() if ln.strip()]


def generate(source_db: str | None = None) -> int:
    db = Db(source_db)
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    orphans = orphan_menu_ids(db)
    parts = [
        "-- 10_bootstrap_seed.sql",
        "-- 新环境启动种子（结构之外的最小可用数据）。由 scripts/seed-bootstrap.py 生成，禁止手工编辑。",
        "-- 来源：本地 dev 库（跑过全部增量迁移）；已按 SEED_SPECS 的 where 过滤 E2E 联调残留，",
        "-- 并在生成时排除 permission 指向不存在权限点的幽灵菜单（G40 一致性）。",
        "-- 幂等：全部 REPLACE INTO，可重复执行。",
        "-- 用法：mysql -u root -p <db> < scripts/seed/bootstrap/10_bootstrap_seed.sql",
        "",
        "SET NAMES utf8mb4;",
        "",
    ]
    manifest = {"_comment": "bootstrap 种子的行数下限清单。由 scripts/seed-bootstrap.py 生成；"
                            "--verify 用它判断「灌完 init+seed 的库是否还是空壳」。",
                "generatedAt": None, "tables": {}}
    total = 0
    for table, where, why in SEED_SPECS:
        eff_where = where
        if table == "iam_menu" and orphans:
            # 把孤儿菜单从种子里排除：它们引用不存在的 permission，渲染不出来，
            # 塞进种子只会让新环境多一堆「种下了但永远看不到」的假数据。
            eff_where += " AND id NOT IN (%s)" % ", ".join(orphans)
        kept = db.count(table, eff_where)
        allrows = db.count(table)
        sql = dump_table(db, table, eff_where)
        # 注意：mysqldump 默认 extended insert，一条语句含多行——语句数 ≠ 行数，
        # 行数以 where 后的 COUNT 为准（语句数只用来判「解析是否拿到东西」）。
        rows = kept
        if kept and sql.count("REPLACE INTO") == 0:
            raise SystemExit(f"❌ {table} 应导出 {kept} 行，但 dump 里没有 REPLACE INTO 语句（解析已失效）")
        if kept == 0:
            print(f"  ⚠ {table:16s} where 命中 0 行——该表将不进种子，请确认过滤条件没有写错")
            continue
        # 审计：种子相对源库的排除 = E2E/测试残渣 + 孤儿菜单（仅 iam_menu）
        excluded = allrows - kept
        tag = ""
        if table == "iam_menu" and orphans:
            tag = f"，其中 {len(orphans)} 行为 permission 孤儿（G40 一致性排除）"
        total += rows
        manifest["tables"][table] = {"rows": rows, "source_rows": allrows,
                                     "excluded": excluded, "where": eff_where, "why": why}
        parts.append(f"-- ── {table}：导出 {rows} 行（源库 {allrows} 行，排除 {excluded}{tag}）")
        parts.append(f"--    {why}")
        parts.append(f"--    where: {eff_where}")
        parts.append(sql)
        parts.append("")
        print(f"  {table:16s} 导出 {rows:5d} / 源 {allrows:5d}（排除 {excluded}{tag}")

    if total == 0:
        raise SystemExit("❌ 一条种子都没导出——空种子会让 --verify 恒过，必须判红")

    from datetime import datetime
    manifest["generatedAt"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    OUT_SQL.write_text("\n".join(parts), encoding="utf-8")
    OUT_MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"\n已写入 {OUT_SQL.relative_to(REPO)}（{total} 行）+ {OUT_MANIFEST.name}")
    return 0


def apply_seed(target_db: str) -> int:
    if not OUT_SQL.exists():
        print(f"❌ 找不到 {OUT_SQL}，先跑 python3 scripts/seed-bootstrap.py 生成")
        return 1
    cli = find_client("mysql")
    db = Db(target_db)
    with OUT_SQL.open("rb") as fh:
        out = subprocess.run([cli, *db.conn, "--default-character-set=utf8mb4", target_db],
                             stdin=fh, capture_output=True)
    if out.returncode != 0:
        print("❌ 灌入失败：" + out.stderr.decode("utf-8", "ignore").strip()[:800])
        return 1
    print(f"✅ 已灌入 {target_db}")
    return verify(target_db)


def verify(target_db: str) -> int:
    if not OUT_MANIFEST.exists():
        print(f"❌ 找不到 {OUT_MANIFEST}")
        return 1
    doc = json.loads(OUT_MANIFEST.read_text(encoding="utf-8"))
    db = Db(target_db)
    tables = doc.get("tables") or {}
    if not tables:
        # 空清单 = 没有任何表被校验 = 恒过。这正是「有表没数据」的空壳最容易被放行的形态。
        print(f"❌ {OUT_MANIFEST.name} 里没有可校验的表——空清单会恒过，必须判红。"
              " 重新生成：python3 scripts/seed-bootstrap.py")
        return 1
    errors = []
    for table, meta in tables.items():
        try:
            actual = db.count(table)
        except SystemExit as exc:
            print(f"❌ 校验 {table} 失败：{exc}")
            return 1
        if actual < meta["rows"]:
            errors.append(f"{table}: {actual} < 期望下限 {meta['rows']}"
                          f"（灌完 init 没灌 seed 就是这个症状：结构在、数据空）")
    if errors:
        print(f"❌ 种子校验失败：{len(errors)} 项\n")
        for e in errors:
            print("  · " + e)
        return 1
    print("OK: 种子齐备（" + "、".join(f"{t} {m['rows']}" for t, m in doc["tables"].items()) + "）")
    return 0


def check_fresh() -> int:
    """校验已生成的种子与活库一致（防止 dev 加了菜单而种子没重生成）。"""
    if not OUT_MANIFEST.exists():
        print(f"❌ 找不到 {OUT_MANIFEST}")
        return 1
    doc = json.loads(OUT_MANIFEST.read_text(encoding="utf-8"))
    db = Db()
    stale = []
    for table, where, _ in SEED_SPECS:
        now = db.count(table, where)
        was = doc["tables"].get(table, {}).get("rows")
        if was is None:
            stale.append(f"{table}: 清单里没有它")
        elif now != was:
            stale.append(f"{table}: 活库 {now} 行 ≠ 种子 {was} 行（活库变了，种子没重生成）")
    if stale:
        print("❌ 种子已过期：\n" + "\n".join("  · " + s for s in stale)
              + "\n  修复：python3 scripts/seed-bootstrap.py")
        return 1
    print("OK: 种子与活库一致（" + "、".join(doc["tables"]) + "）")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true", help="把种子灌入 --db 指定的库")
    ap.add_argument("--verify", action="store_true", help="校验 --db 指定库的种子行数")
    ap.add_argument("--check", action="store_true", help="校验已生成种子是否与源库一致")
    ap.add_argument("--db", help="目标库（--apply/--verify）")
    ap.add_argument("--source-db", help="源库（默认 $BONE_DB_NAME 或 bone）")
    args = ap.parse_args()

    if args.apply:
        if not args.db:
            ap.error("--apply 需要 --db")
        return apply_seed(args.db)
    if args.verify:
        if not args.db:
            ap.error("--verify 需要 --db")
        return verify(args.db)
    if args.check:
        return check_fresh()
    return generate(args.source_db)


if __name__ == "__main__":
    sys.exit(main())
