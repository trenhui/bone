#!/usr/bin/env python3
"""DDL 重复列声明检测：同一张表的同一列，不得在两个地方被**无条件**声明。

背景（2026-11-03 实测）
----------------------
`bone-apps/synap-claim/src/main/resources/db/migration/V21__...sql` 曾无条件
`ALTER TABLE t_claim_mapping_miss ADD COLUMN resolved_by ...`，而 V20 的建表语句
**已经包含**该列。两处叠加 ⇒ `ERROR 1060 Duplicate column name 'resolved_by'`：
任何按 V20→V21 顺序执行的环境都会在 V21 中止。

为什么它能潜伏这么久
--------------------
1. `db/migration` 是**参考态**（无 runner），不会被自动执行 ⇒ 缺陷不会自己暴露；
2. 仓库里**没有任何门禁**检查「同表同列被重复声明」——现有门禁管的是脚本清单
   （G38）、init 覆盖（G39）、种子（G40）、拓扑图（G41），没有一条看 DDL 内容。

判据 D1
-------
同一 (表, 列) 在**≥2 处**被声明，且其中**至少一处是无守卫的 `ADD COLUMN`** ⇒ 判红。

判据 D2（表级）
--------------
同一张表被**两处以上「裸 CREATE TABLE」**（无 `IF NOT EXISTS`）声明 ⇒ 判红：
第二个 CREATE 必报 `ERROR 1050 Table already exists`。

D2 排除「重建模式」：同文件内先 `DROP TABLE` 再 `CREATE TABLE` 是合法的表重建，
只要该文件对这张表有 `DROP TABLE` 保护就不判红。

为什么只抓「无守卫的 ADD」而不抓「重复」本身
--------------------------------------------
「同一列被提到两次」**不等于**缺陷——幂等范式正是靠"提到两次但第二次是 no-op"
实现增量兼容：

  · `CREATE TABLE IF NOT EXISTS` 重复 —— 第二次 no-op，安全（0047/0058 同款）；
  · `ADD COLUMN` + 同脚本 information_schema 探测 —— 列已存在时 `DO 0`，安全
    （0046 建表已含 created_by，仍用探测补列给存量库；0085/0090/0097/0098 同范式）；
  · `ADD COLUMN IF NOT EXISTS` —— 安全。

只有**裸 `ALTER TABLE ... ADD COLUMN`**（无探测、无 IF NOT EXISTS）撞上另一处
声明时，才会真的执行第二次 ⇒ `ERROR 1060 Duplicate column name`。这正是 V21 的形态。

「无守卫（必炸 ERROR 1060）」
  · 裸 `ALTER TABLE tbl ADD COLUMN x ...`（无探测、无 IF NOT EXISTS）。

「无守卫（必炸 ERROR 1050，见 D2）」
  · 裸 `CREATE TABLE tbl (...)`（无 IF NOT EXISTS）。

未覆盖的相邻形态（已知边界，本判据不抓）
----------------------------------------
· **跨文件**的裸 CREATE 撞车（ERROR 1050）：需要按迁移执行序做时序分析——生效通道的
  顺序在 `migration-manifest.json` 里、参考态只有 V 编号；跨文件 DROP+CREATE 重建
  与「谁先谁后」耦合，误报风险高于收益，故不纳入（实测存量跨文件裸 CREATE 冲突
  为 0 处，纳入也无现存收益）。
· 跨通道重复：生效通道建的列与 init 全量基线同名属**正常**（增量 + 全量并存），
  故 init 不参与本判据。

扫描范围
--------
· 生效通道 `scripts/migration/*.sql`（会真的被执行）；
· 参考态 `bone-apps/*/src/main/resources/db/migration/*.sql`（设计真源：下游按它
  落地 Flyway/Liquibase 时会把重复声明一起带走）。

防假绿
------
目录扫不到任何 `.sql` ⇒ 判失败而非通过——否则"路径写错/目录改名"会让门禁恒绿。
该判定放在 `run_check()` 内（而非 `main()`），否则单元测试覆盖不到它。

用法
----
    python3 scripts/check-ddl-duplicate-column.py [--root <repo-root>]
"""

import argparse
import os
import re
import sys

# 不是列定义的行首关键字（CREATE TABLE 体与 ALTER ADD 目标共用）
NOT_A_COLUMN = re.compile(
    r"^(UNIQUE|KEY|INDEX|CONSTRAINT|PRIMARY|FOREIGN|CHECK|FULLTEXT|SPATIAL)\b", re.I
)
# CREATE TABLE ... ( ... ) 的表名与是否 IF NOT EXISTS
CREATE_RE = re.compile(
    r"CREATE\s+TABLE\s+(?P<ine>IF\s+NOT\s+EXISTS\s+)?`?(?P<tbl>\w+)`?\s*\((?P<body>.*?)\n\)\s*"
    r"(?:ENGINE|;)",
    re.S | re.I,
)
# ALTER TABLE tbl <rest until ;>
ALTER_RE = re.compile(r"ALTER\s+TABLE\s+`?(?P<tbl>\w+)`?\s+(?P<rest>.*?);", re.S | re.I)
# ADD [COLUMN] col
ADD_RE = re.compile(r"ADD\s+(?:COLUMN\s+)?`?(?P<col>\w+)`?\s+[\w(]", re.I)
# DROP TABLE [IF EXISTS] tbl —— 表重建的保护伞：D2 判据据此豁免「先 DROP 后 CREATE」
DROP_TABLE_RE = re.compile(r"DROP\s+(?:TEMPORARY\s+)?TABLE\s+(?:IF\s+EXISTS\s+)?`?(?P<tbl>\w+)`?", re.I)
# information_schema 探测（大小写不敏感，允许 TABLE_SCHEMA/TABLE_NAME 任意大小写）
GUARD_TABLE_RE = re.compile(r"TABLE_NAME\s*=\s*'(?P<tbl>\w+)'", re.I)
GUARD_COLUMN_RE = re.compile(r"COLUMN_NAME\s*=\s*'(?P<col>\w+)'", re.I)

# 存量豁免：已知的历史重复声明（表.列）。格式 (通道, "表.列") -> 理由。
#
# 纪律（与 init-coverage-baseline 同款）：
#   · 豁免必须写清「为什么现在不炸 / 怎么治」，理由不足 20 字 ⇒ 判红；
#   · 豁免项若已不再命中（缺陷被别的脚本消掉了）⇒ 判红「豁免已失效，请删除」，
#     否则豁免表会变成只增不减的永久白名单，最终谁都不敢删。
KNOWN_EXEMPTIONS = {
    (
        "生效通道 scripts/migration",
        "meta_entity_ui_schema.updated_by",
    ): (
        "0046 的动态 DDL 一次 ADD 两列（created_by + updated_by）却只探测 created_by ⇒ 半幂等："
        "存量库「只缺 updated_by」永远补不上、「缺 created_by 但有 updated_by」报 ERROR 1060。"
        "不回改 0046：migrate.py 按 sha256 记 journal，改已应用脚本会让所有已应用环境报 drift。"
        "治本：0110_fix_meta_entity_ui_schema_audit_columns.sql 逐列独立探测补齐（已实机验证三种存量形态）。"
    ),
}


def norm(name):
    return name.strip().strip("`").lower()


def strip_comment_lines(text):
    """去掉整行 `--` 注释（行内注释不动，避免误删字符串里的内容）。"""
    return "\n".join(l for l in text.split("\n") if not l.strip().startswith("--"))


def parse_create_table(body):
    """返回 [(表.列, 是否 IF NOT EXISTS)]。

    用 finditer 而非 search：单个脚本常建多张表（0058 有 14 张），search 只取第一张
    ⇒ 其余表的列声明**从未被扫描**（门禁大面积假绿）。
    """
    out = []
    for m in CREATE_RE.finditer(body):
        ine = bool(m.group("ine"))
        tbl = norm(m.group("tbl"))
        for raw in m.group("body").split("\n"):
            line = raw.strip().rstrip(",")
            if not line or NOT_A_COLUMN.match(line):
                continue
            cm = re.match(r"`?(\w+)`?\s+\S", line)
            if cm:
                out.append((f"{tbl}.{norm(cm.group(1))}", ine))
    return out


def parse_alter_adds(body):
    """返回 [(表, 列, 是否有 IF NOT EXISTS 守卫, 片段)]。"""
    out = []
    for m in ALTER_RE.finditer(body):
        tbl = norm(m.group("tbl"))
        rest = m.group("rest")
        for am in ADD_RE.finditer(rest):
            col = norm(am.group("col"))
            if NOT_A_COLUMN.match(am.group("col")):
                continue
            # 该 ADD 是否自带 IF NOT EXISTS（ADD COLUMN IF NOT EXISTS x）
            window = rest[max(0, am.start() - 40): am.end()]
            guarded = bool(re.search(r"IF\s+NOT\s+EXISTS", window, re.I))
            out.append((tbl, col, guarded, m.group(0)))
    return out


def guard_set(body):
    """脚本内 information_schema 探测覆盖到的 (表, 列) 集合。"""
    guards = set()
    tables = {norm(t) for t in GUARD_TABLE_RE.findall(body)}
    cols = {norm(c) for c in GUARD_COLUMN_RE.findall(body)}
    for t in tables:
        for c in cols:
            guards.add((t, c))
    return guards


def collect(root):
    """扫描两个通道，返回 (通道: {文件: 声明列表}, 通道: {文件: 守卫集合}, 通道: {文件: 建表信息})。

    两个通道**分别索引**：生效通道（scripts/migration）与参考态（db/migration）是
    同一批变更的两种形态——参考态是设计源、生效通道是本项目的单一执行序。
    跨通道的同名列是**设计使然**（参考态 V12 与生效通道 0085 讲的是同一件事），
    把它们放在一起比会把"正确的对照"判成缺陷。故只在**通道内**比较。

    第三个返回值记录**表级**信息（D2 判据要用）：每个文件里哪些表被裸 CREATE、
    以及该文件有没有对这张表 DROP（重建保护）。
    """
    channels = {"生效通道 scripts/migration": [], "参考态 db/migration": []}
    mig = os.path.join(root, "scripts", "migration")
    if os.path.isdir(mig):
        channels["生效通道 scripts/migration"] += [
            os.path.join(mig, n)
            for n in sorted(os.listdir(mig))
            if n.endswith(".sql") and os.path.isfile(os.path.join(mig, n))
        ]
    base = os.path.join(root, "bone-apps")
    if os.path.isdir(base):
        for app in sorted(os.listdir(base)):
            d = os.path.join(base, app, "src", "main", "resources", "db", "migration")
            if os.path.isdir(d):
                channels["参考态 db/migration"] += [
                    os.path.join(d, n)
                    for n in sorted(os.listdir(d))
                    if n.endswith(".sql") and os.path.isfile(os.path.join(d, n))
                ]
    decls = {}
    guards = {}
    tables = {}
    for chan, paths in channels.items():
        decls[chan] = {}
        guards[chan] = {}
        tables[chan] = {}
        for path in paths:
            try:
                with open(path, encoding="utf-8", errors="ignore") as fh:
                    body = strip_comment_lines(fh.read())
            except OSError:
                continue
            items = []
            for key, ine in parse_create_table(body):
                items.append((key, ine, "create"))
            for tbl, col, guarded, _frag in parse_alter_adds(body):
                items.append((f"{tbl}.{col}", guarded, "alter"))
            decls[chan][path] = items
            guards[chan][path] = guard_set(body)
            # 表级信息（D2 用）：裸 CREATE 的表 + 本文件 DROP 过的表（重建保护）
            bare_creates = []
            for mm in CREATE_RE.finditer(body):
                if not mm.group("ine"):
                    bare_creates.append(norm(mm.group("tbl")))
            dropped = {norm(d.group("tbl")) for d in DROP_TABLE_RE.finditer(body)}
            tables[chan][path] = (bare_creates, dropped)
    return channels, decls, guards, tables


def run_check(root):
    """核心判定（与 IO 解耦，便于单测直接调用）。

    返回 (errors, exempted, total_files, total_keys)：
      errors    —— 必判红的条目列表；
      exempted  —— 命中豁免的存量重复声明 (通道, 表.列, 详情, 理由)；
      total_files —— 扫描到的 .sql 数；total_keys —— 识别到的 (表,列) 声明数。

    「扫不到任何 .sql ⇒ 判失败」的防假绿闸放在**这里**而不是 main()：
    放在 main() 里单元测试覆盖不到，变异验证也证明不了它有判别力。
    """
    channels, decls, guards, tables = collect(root)
    total_files = sum(len(v) for v in channels.values())

    errors = []
    exempted = []
    hit_keys = set()
    total_keys = 0

    if total_files == 0:
        errors.append(
            "D0 两个通道都扫不到任何 .sql —— 路径失效会让本门禁恒绿，必须判失败。"
            "预期目录：scripts/migration/ 与 bone-apps/*/src/main/resources/db/migration/"
        )

    for chan, chan_decls in decls.items():
        # ---- D1：列级，同一 (表, 列) 被声明多次且含无条件 ADD COLUMN ⇒ ERROR 1060 ----
        # 索引：(表, 列) -> [(文件, 声明方式, 是否为「无守卫 ADD」)]
        #
        # 第三位只对 alter 有意义：create 一律记 False——
        # 1060 (Duplicate column name) 只由 ADD COLUMN 触发；CREATE 的
        # IF NOT EXISTS 影响的是表级的 1050（见 D2），与列级判定无关。
        # （早先把 create 也算成 "safe" 是个**死分支**：D1 压根不看它，
        #   反而让人误以为 create 的 IF NOT EXISTS 会影响 D1。）
        index = {}
        for path, items in chan_decls.items():
            rel = os.path.relpath(path, root)
            g = guards[chan].get(path, set())
            for key, guarded, kind in items:
                if kind == "create":
                    index.setdefault(key, []).append((rel, kind, False))
                else:
                    tbl, col = key.split(".", 1)
                    unguarded = not (guarded or (tbl, col) in g)
                    index.setdefault(key, []).append((rel, kind, unguarded))
        total_keys += len(index)
        for tbl_col, places in sorted(index.items()):
            if len(places) < 2:
                continue
            unsafe_adds = [p for p in places if p[1] == "alter" and p[2]]
            if not unsafe_adds:
                continue
            hit_keys.add((chan, tbl_col))
            detail = "；".join(
                f"{rel}（{'ADD COLUMN' if kind == 'alter' else 'CREATE TABLE'}）"
                for rel, kind, _safe in places
            )
            reason = KNOWN_EXEMPTIONS.get((chan, tbl_col))
            if reason is not None:
                if len(reason.strip()) < 20:
                    errors.append(
                        f"[{chan}] 豁免条目 `{tbl_col}` 的理由不足 20 字（当前 {len(reason.strip())} 字）——"
                        f"「为什么不炸 + 怎么治」必须写清，否则豁免会变成无主的长名单。"
                    )
                else:
                    exempted.append((chan, tbl_col, detail, reason))
                continue
            errors.append(
                f"[{chan}] D1 `{tbl_col}` 被声明 {len(places)} 次，其中至少 1 次是**无条件 ADD COLUMN**："
                f"{detail}。执行时必报 ERROR 1060 Duplicate column name。修法二选一："
                f"①把无条件的那个改成幂等（IF NOT EXISTS 或 information_schema 探测 + 动态 DDL）；"
                f"②直接删掉重复声明（列已由另一处建立）。"
            )

        # ---- D2：表级，同一表被两处以上「裸 CREATE」声明 ⇒ ERROR 1050 ----
        # 只查**同文件内**：跨文件需要按迁移执行序做时序分析（重建合法），误报风险更高。
        for path, (bare_creates, dropped) in tables[chan].items():
            if not bare_creates:
                continue
            counts = {}
            for tbl in bare_creates:
                counts[tbl] = counts.get(tbl, 0) + 1
            rel = os.path.relpath(path, root)
            for tbl, cnt in sorted(counts.items()):
                if cnt < 2:
                    continue
                if tbl in dropped:
                    continue  # 先 DROP 后 CREATE = 表重建，合法
                hit_keys.add((chan, f"TABLE:{tbl}"))
                errors.append(
                    f"[{chan}] D2 表 `{tbl}` 在同一文件里被裸 CREATE 了 {cnt} 次"
                    f"（{rel}）且无 DROP TABLE 保护 ⇒ 第二个 CREATE 必报 "
                    f"ERROR 1050 Table already exists。修法："
                    f"①改成 CREATE TABLE IF NOT EXISTS（第二次即 no-op）；"
                    f"②或在该文件里先 DROP TABLE IF EXISTS `{tbl}` 再 CREATE（显式重建）。"
                )

    # 豁免只可收缩：豁免表里已不再命中的条目必须删掉，否则白名单只增不减。
    for chan, tbl_col in [k for k in KNOWN_EXEMPTIONS if k not in hit_keys]:
        errors.append(
            f"[{chan}] 豁免条目 `{tbl_col}` 已不再命中（缺陷已被别的脚本消掉）——"
            f"请从 KNOWN_EXEMPTIONS 中删除。豁免表只可收缩：留着会让它变成永久白名单。"
        )
    return errors, exempted, total_files, total_keys


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    args = ap.parse_args()
    root = os.path.abspath(args.root)

    errors, exempted, total_files, total_keys = run_check(root)

    if errors:
        print(f"❌ DDL 重复列检测：{len(errors)} 处必炸的重复声明")
        for e in errors:
            print(f"  · {e}")
        return 1
    for chan, tbl_col, detail, reason in exempted:
        print(f"⚠️ 已豁免的存量重复声明：[{chan}] `{tbl_col}`（{detail}）")
        print(f"    理由：{reason}")
    print(
        f"OK: DDL 重复列检测通过（扫描 {total_files} 个脚本 / {total_keys} 个 (表,列) 声明，"
        f"通道内无未豁免的无条件重复 / 存量豁免 {len(exempted)} 条）"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
