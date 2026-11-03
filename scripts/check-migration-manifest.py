#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-migration-manifest.py  (G38 · 迁移清单完整性)

判据：`scripts/migration/` 的每个文件都必须在 `migration-manifest.json` 登记，
      且登记项与磁盘、与文件内容三者一致。

为什么需要这一步
----------------
`scripts/migration/` 的文件名序号是**变更号**，不是执行序：
  · 同一变更的文档/校验附件会复用同号（0008_README、0020_RUNBOOK、0052_verify）；
  · 更糟的是**两个不同变更用了同一个号**——0052 被 `mdm_entity_workflow` 与
    `rename_expense_claim_to_reimbursement` 共用，0067/0068/0069/0070 同理。

于是「该跑哪些、什么顺序」只能靠人按文件名排序猜。猜错的代价不是报错而是**静默少跑**：
0072 的头部就记着一次实证——理赔 6 张表「代码已上线、DDL 没跟上」，缺表时
`ClaimCaseTrackRecorder` 打 ERROR 后吞异常，审计流水全丢且页面照常返回。
同类还有 0074：权限码未注册，菜单可见但端点全 403。

所以本门禁把「顺序」变成显式数据（manifest 的 `order`），把「重号」变成必须登记的判断
（`ordinalConflicts`），并钉死一条规则：**新增脚本只能追加在末尾**。这样「漏跑/乱序」
从「无人察觉」变成「提交即红」。

十条判定
--------
  M1 存在即登记   目录里的文件未登记 → 红（新增迁移无法绕过清单）；子目录一律判红——
                 本门禁只扫本层文件（os.listdir + isfile），把一个 .sql 放进子目录即可
                 **完全隐身**（2026-10-30 实测：子目录里的 0001_zz.sql 未被发现，门禁仍报绿）。
                 「看不见的文件」比「登记错的文件」危险得多，故直接禁止子目录。
  M2 登记即存在   清单里的文件已不存在 → 红（改名/删除必须同步清单）
  M3 order 合法   仅 .sql 有 order，且为 1..N 连续无重复；非 .sql 的 order 必须为 null
  M4 顺序冻结     按 order 排开必须与文件名字典序一致；不一致须在 outOfLexOrder 逐项声明理由
  M5 kind 与内容一致  verify 不得含写语句；ddl 必须含 DDL；dml 必须含 DML；mixed 两者皆有；
                     doc 必须是 .md；tool 必须不是 .md/.sql
  M6 重号组登记  实际重号组（4 位前缀，≥2 个文件）必须与 ordinalConflicts 完全一致（双向）
  M7 summary 有效 非空、≥8 字符、非占位符
  M8 requires    被依赖项必须存在，且 order 严格更小
  M9 元数据自洽  journalTable / baseline / frozenAt / appliesTo 要么整体声明、要么整体缺省
                 （后者仅供最小化夹具）；声明了就必须有效，且 journalTable 必须与
                 scripts/migrate.py 的 JOURNAL 常量一致——两处各写一遍就是下一次漂移的种子。
  M10 retired    下线登记：文件必须已不在磁盘、且未出现在 entries，理由非占位。
                 没有这条，「把迁移并入 bone-init.sql 后归档」会让已应用环境永久报「账本残留」。
  M11 入库       登记的每个文件必须已被 git 跟踪（进入索引）。
                 M2 只比「工作树里有没有这个文件」——它管的是**当前 checkout**。
                 但清单是**要提交进仓库的产物**：如果某人在自己工作树里写了迁移脚本、
                 同时把清单条目提交了（清单在 HEAD、脚本只在工作树），M2 在他的机器上完全绿，
                 而**任何干净 clone 都缺这个脚本**——执行序里凭空少一步，或者更糟：
                 该步骤的 DDL 内容永久丢失，谁也不知道少了什么。
                 2026-11-03 实测：`0100_synap_claim_calculation_version_chain.sql`（171 行 DDL）
                 被清单登记为 order=106，却只存在于并行会话的工作树，从未进入任何分支。
                 干净 clone 直接卡死在 M2 ⇒ 该迁移在所有新环境永久丢失。
                 故本判据把「文件在不在索引里」纳入门禁：未跟踪即红，提交前必须 git add。
                 用 `git ls-files`（而非 `git log`）判定，因为工作树里的新文件在索引中，
                 `git ls-files` 能区分「已 add 未 commit」与「完全没 add」——前者允许（正在提交中），
                 后者判红。

防假绿自检
----------
清单为空、目录扫不到文件、或有 .sql 却没解析出任何 kind，均判失败而非通过——
免得「空集合恒过」把门禁变成摆设。

用法：
  python3 scripts/check-migration-manifest.py [--root <repo-root>]
      [--migration-dir <dir>] [--manifest <json>]     # 后两项供变异测试
"""
import argparse
import json
import os
import re
import subprocess
import sys

MANIFEST_REL = os.path.join("scripts", "migration", "migration-manifest.json")
MIGRATION_REL = os.path.join("scripts", "migration")

DDL_RX = re.compile(
    r"(CREATE\s+TABLE|ALTER\s+TABLE|CREATE\s+(?:UNIQUE\s+)?INDEX|DROP\s+TABLE"
    r"|CREATE\s+(?:DEFINER=\S+\s+)?PROCEDURE|TRUNCATE\s+TABLE)",
    re.I,
)
# MySQL 8 没有 CREATE INDEX / ADD COLUMN 的 IF [NOT] EXISTS，本仓的条件 DDL 惯用写法是
#   SET @ddl := IF(<探查 information_schema>, 'ALTER TABLE ...', 'SELECT 1');
#   PREPARE stmt FROM @ddl; EXECUTE stmt;
# 剥离字面量后 DDL 关键词只剩在字符串里，因此必须把这一惯用式本身认作 DDL，
# 否则「动态补列」类脚本会被误判成 verify（只读）——那是最危险的方向：
# 一个真改结构的脚本被当成只读，复核时就会被跳过。
DYNAMIC_DDL_RX = re.compile(r"PREPARE\s+\w+\s+FROM", re.I)
DYNAMIC_EXEC_RX = re.compile(r"EXECUTE\s+\w+", re.I)
DML_RX = re.compile(r"(^\s*INSERT\s+INTO|^\s*UPDATE\s+\w|^\s*DELETE\s+FROM)", re.I | re.M)
PLACEHOLDER = {"", "-", "--", "todo", "tbd", "待补充", "无", "n/a", "na"}


def find_repo_root():
    out = os.popen("git rev-parse --show-toplevel 2>/dev/null").read().strip()
    if out:
        return out
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def strip_sql_noise(text):
    """剥注释与字符串字面量：注释里常引用被注释掉的 DDL，字面量里常嵌动态 DDL。"""
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    text = re.sub(r"--[^\n]*", " ", text)
    text = re.sub(r"'[^'\n]*'", "''", text)
    text = re.sub(r'"[^"\n]*"', '""', text)
    return text


def git_tracked_files(root, migration_rel):
    """返回该目录下**已被 git 索引跟踪**的文件名集合；无法判定时返回 None（跳过而非误红）。

    为什么用 `git ls-files` 而不是 `git log -- <path>`：
      · `ls-files` 给出的是**索引**状态，能区分「已 add 未 commit」与「完全没 add」；
        前者是提交过程中的正常中间态，允许；后者是丢失风险，必须拦。
      · `log` 只看提交历史，看不到暂存区，会把「正在提交」误判成「没提交」。

    **必须清掉 GIT_INDEX_FILE（实测踩坑）**：`git commit --only` 会构造一个**临时索引**
    （通过 GIT_INDEX_FILE 指向），它只含本次提交涉及的文件。此时在本进程里跑 `git ls-files`，
    读到的就是那个临时索引——于是「真实索引里已跟踪但本次没提交的文件」会被误判成
    「完全没 add」，M11 在**每一次 pre-commit 里都会误红**（实测：`git commit --only` 提交时
    门禁自身用例 7 红 1 错，而手动跑全绿）。
    故这里显式剥离 GIT_INDEX_FILE / GIT_DIR 等环境变量，让查询回到真实索引。
    """
    env = {k: v for k, v in os.environ.items()
           if k not in ("GIT_INDEX_FILE", "GIT_DIR", "GIT_WORK_TREE")}
    try:
        out = subprocess.run(
            ["git", "ls-files", "-z", "--", os.path.relpath(migration_rel, root)],
            cwd=root, capture_output=True, text=True, timeout=30, env=env,
        )
    except (OSError, subprocess.SubprocessError):
        return None
    if out.returncode != 0:
        return None
    return {os.path.basename(p) for p in out.stdout.split("\0") if p}


def kind_of(path):
    """按内容判 kind，与清单声明比对（M5）。"""
    if path.endswith(".md"):
        return "doc"
    if not path.endswith(".sql"):
        return "tool"
    text = strip_sql_noise(open(os.path.join(MIGRATION_REL, path), encoding="utf-8", errors="ignore").read())
    has_ddl = bool(DDL_RX.search(text))
    if not has_ddl and DYNAMIC_DDL_RX.search(text) and DYNAMIC_EXEC_RX.search(text):
        has_ddl = True  # 条件 DDL 惯用式（见文件头 DYNAMIC_DDL_RX 注释）
    has_dml = bool(DML_RX.search(text))
    if not has_ddl and not has_dml:
        return "verify"
    if has_ddl and has_dml:
        return "mixed"
    return "ddl" if has_ddl else "dml"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root")
    ap.add_argument("--migration-dir")
    ap.add_argument("--manifest")
    ap.add_argument("--skip-tracked", action="store_true",
                    help="跳过 M11（登记项是否已被 git 跟踪）——供无 git 仓库的最小化夹具使用")
    args = ap.parse_args()

    global MIGRATION_REL
    root = args.root or find_repo_root()
    MIGRATION_REL = args.migration_dir or os.path.join(root, MIGRATION_REL)
    manifest_path = args.manifest or os.path.join(root, MANIFEST_REL)

    if not os.path.isdir(MIGRATION_REL):
        print(f"❌ 迁移目录不存在：{MIGRATION_REL}")
        return 1
    if not os.path.exists(manifest_path):
        print(f"❌ 清单不存在：{manifest_path}（本门禁要求清单是唯一真源，不接受缺省放行）")
        return 1

    doc = json.loads(open(manifest_path, encoding="utf-8").read())
    entries = doc.get("entries") or []
    on_disk = sorted(f for f in os.listdir(MIGRATION_REL) if os.path.isfile(os.path.join(MIGRATION_REL, f)))
    if not entries or not on_disk:
        print(f"❌ 自检失败：清单 {len(entries)} 项 / 目录 {len(on_disk)} 个文件——至少一侧为空，无法判定")
        return 1

    errors = []
    registered = [e["file"] for e in entries]
    # 清单自身不是迁移项；除它之外任何文件都必须登记（无其他豁免，避免白名单重开「什么都能漏」的口子）。
    index_name = os.path.basename(manifest_path)

    # ---- M1 存在即登记 ----
    for f in on_disk:
        if f == index_name:
            continue
        if f not in registered:
            errors.append(f"M1 `{f}` 存在于 scripts/migration 但未登记（新增迁移必须同时登记清单，否则执行顺序无人知晓）")

    # 子目录：本门禁只扫本层文件，子目录里的 .sql 完全隐身（实证见文件头 M1）。禁止而不是递归，
    # 是因为「递归也要登记」会诱使人把迁移塞进子目录当归档，而归档的正确做法是 M10 的 retired。
    for d in sorted(os.listdir(MIGRATION_REL)):
        full = os.path.join(MIGRATION_REL, d)
        if os.path.isdir(full) and d != "__pycache__" and not d.startswith("."):
            errors.append(
                f"M1 `{d}/` 是子目录：本门禁只扫本层文件，其中的迁移会**完全不生效且不被发现**。"
                "迁移脚本只能平铺；已并入 bone-init.sql 的历史脚本请移出本目录并在 retired 登记。"
            )

    # ---- M2 登记即存在 ----
    for f in registered:
        if f not in on_disk:
            errors.append(f"M2 清单登记的 `{f}` 已不存在（改名/删除必须同步清单）")

    # ---- M3 order 合法 ----
    sql_orders = []
    for e in entries:
        is_sql = e["file"].endswith(".sql")
        if is_sql:
            if not isinstance(e.get("order"), int):
                errors.append(f"M3 `{e['file']}` 是 SQL 但 order 不是整数")
            else:
                sql_orders.append(e["order"])
        elif e.get("order") is not None:
            errors.append(f"M3 `{e['file']}` 不是 SQL，order 必须为 null（执行序只定义于可执行脚本）")
    if sql_orders:
        expect = list(range(1, len(sql_orders) + 1))
        if sorted(sql_orders) != expect:
            dup = sorted({o for o in sql_orders if sql_orders.count(o) > 1})
            miss = [o for o in expect if o not in sql_orders]
            errors.append(f"M3 order 不是 1..{len(sql_orders)} 连续：重复 {dup or '无'} / 缺 {miss or '无'}")
    else:
        errors.append("M3 解析不到任何 SQL 的 order（清单可能已失效）")
    if doc.get("executableCount") != len(sql_orders):
        errors.append(f"M3 executableCount={doc.get('executableCount')} 与实际 SQL 数 {len(sql_orders)} 不符")

    # ---- M4 顺序冻结：order 序列必须等于词典序 ----
    by_order = [e["file"] for e in sorted((x for x in entries if isinstance(x.get("order"), int)), key=lambda x: x["order"])]
    by_name = sorted(f for f in by_order)
    if by_order != by_name:
        declared = {d.get("file"): d.get("reason", "") for d in (doc.get("outOfLexOrder") or [])}
        moved = [f for f, g in zip(by_order, by_name) if f != g]
        for f in moved:
            if f not in declared or len(str(declared[f]).strip()) < 8:
                errors.append(
                    f"M4 `{f}` 的 order 与文件名词典序不一致，且未在 outOfLexOrder 声明理由"
                    "（新增脚本只能追加在末尾，插入中间会让已应用环境与新环境顺序分叉）"
                )

    # ---- M5 kind 与内容一致 ----
    for e in entries:
        if e["file"] not in on_disk:
            continue
        actual = kind_of(e["file"])
        claimed = e.get("kind")
        if claimed != actual:
            errors.append(f"M5 `{e['file']}` 声明 kind={claimed}，按内容应为 {actual}")
        if actual == "verify" and claimed == "verify":
            text = strip_sql_noise(open(os.path.join(MIGRATION_REL, e["file"]), encoding="utf-8", errors="ignore").read())
            if re.search(r"^\s*(INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|TRUNCATE)\b", text, re.I | re.M):
                errors.append(f"M5 `{e['file']}` 声明 verify（只读），但含写语句")

    # ---- M6 重号组登记（双向） ----
    groups = {}
    for f in on_disk:
        m = re.match(r"^(\d{4})_", f)
        if m:
            groups.setdefault(m.group(1), []).append(f)
    actual_groups = {g: sorted(fs) for g, fs in groups.items() if len(fs) > 1}
    declared_groups = {c["ordinal"]: sorted(c.get("files") or []) for c in (doc.get("ordinalConflicts") or [])}
    for g in sorted(set(actual_groups) - set(declared_groups)):
        errors.append(f"M6 序号 {g} 有 {len(actual_groups[g])} 个文件共用，但未在 ordinalConflicts 登记：{actual_groups[g]}")
    for g in sorted(set(declared_groups) - set(actual_groups)):
        errors.append(f"M6 ordinalConflicts 登记了 {g}，但实际不存在重号组（存量必须随文件变动同步收缩）")
    for g in sorted(set(actual_groups) & set(declared_groups)):
        if actual_groups[g] != declared_groups[g]:
            errors.append(f"M6 序号 {g} 的文件集合与登记不符：实际 {actual_groups[g]} / 登记 {declared_groups[g]}")
    for g in sorted(declared_groups):
        c = next(x for x in doc["ordinalConflicts"] if x["ordinal"] == g)
        if len(str(c.get("verdict", "")).strip()) < 10:
            errors.append(f"M6 ordinalConflicts {g} 缺 verdict（重号必须给出「谁先谁后、为什么可以共存」的判断）")

    # ---- M7 summary 有效 ----
    for e in entries:
        s = str(e.get("summary", "")).strip()
        if s.lower() in PLACEHOLDER or len(s) < 8:
            errors.append(f"M7 `{e['file']}` 的 summary 无效（空/占位/过短）")

    # ---- M8 requires ----
    order_of = {e["file"]: e.get("order") for e in entries}
    for e in entries:
        for req in e.get("requires") or []:
            if req not in order_of:
                errors.append(f"M8 `{e['file']}` 依赖的 `{req}` 未登记")
            elif order_of[req] is None:
                errors.append(f"M8 `{e['file']}` 依赖的 `{req}` 不是可执行脚本")
            elif e.get("order") is not None and order_of[req] >= e["order"]:
                errors.append(f"M8 `{e['file']}`(order={e['order']}) 必须晚于依赖 `{req}`(order={order_of[req]})")

    # ---- M9 元数据自洽 ----
    # 四个键要么整体声明（真实清单），要么整体缺省（最小化夹具）。整体缺省允许存在，
    # 是因为夹具只关心判据本身；但只要声明了一个就必须齐全——否则「删掉某个键」就成了绕过检查的手法。
    META = ("journalTable", "baseline", "frozenAt", "appliesTo")
    declared = [k for k in META if k in doc]
    if declared:
        missing = [k for k in META if k not in doc]
        if missing:
            errors.append(f"M9 元数据不完整：声明了 {declared}，但缺 {missing}（元数据要么齐全、要么整体缺省）")
        elif not re.match(r"^\d{4}-\d{2}-\d{2}$", str(doc["frozenAt"])):
            errors.append(f"M9 frozenAt=`{doc['frozenAt']}` 不是 YYYY-MM-DD")
        elif not str(doc["appliesTo"]).strip():
            errors.append("M9 appliesTo 为空（必须写清这份顺序适用于哪种环境）")
        elif not os.path.exists(os.path.join(root, str(doc["baseline"]))):
            errors.append(f"M9 baseline=`{doc['baseline']}` 指向的文件不存在（全量基线才是新环境的唯一入口）")
        else:
            # 账本表名在 manifest 与 migrate.py 各写一遍 = 下一次漂移的种子，这里钉死一致性。
            mig_py = os.path.join(os.path.dirname(MIGRATION_REL.rstrip(os.sep)), "migrate.py")
            if os.path.exists(mig_py):
                m = re.search(r'^JOURNAL\s*=\s*"([^"]+)"', open(mig_py, encoding="utf-8").read(), re.M)
                if not m:
                    errors.append("M9 无法从 scripts/migrate.py 解析出 JOURNAL 常量（账本表名的真源）")
                elif m.group(1) != doc["journalTable"]:
                    errors.append(
                        f"M9 journalTable=`{doc['journalTable']}` 与 scripts/migrate.py 的 "
                        f"JOURNAL=`{m.group(1)}` 不一致（两处各写一遍，迟早漂移）"
                    )

    # ---- M10 retired ----
    # 迁移并入 bone-init.sql 后需要一条「正式下线」的路：否则文件一删，已应用环境就永久报
    # 「账本残留」，而永久红的门禁等于没有门禁。
    retired = doc.get("retired") or []
    seen_retired = set()
    for r in retired:
        f = str(r.get("file") or "").strip()
        if not f:
            errors.append("M10 retired 条目缺 file")
            continue
        if f in seen_retired:
            errors.append(f"M10 retired 里 `{f}` 重复登记")
        seen_retired.add(f)
        if f in registered:
            errors.append(f"M10 `{f}` 同时在 entries 与 retired（文件还在，就不算下线）")
        if f in on_disk:
            errors.append(f"M10 `{f}` 在 retired 登记为下线，但文件仍在目录里（应作为正常 entry 登记）")
        if len(str(r.get("reason") or "").strip()) < 8:
            errors.append(f"M10 retired `{f}` 缺理由（≥8 字：为什么可以下线、凭什么已并入基线）")

    # ---- M11 登记项必须已被 git 跟踪 ----
    # 判据只查「是否已进索引」，不查「是否已 commit」：提交过程中索引先于 HEAD 更新，
    # 允许「已 add 未 commit」的中间态（那正是提交者此刻应有的状态），只拦「完全没 add」。
    if not args.skip_tracked:
        tracked = git_tracked_files(root, MIGRATION_REL)
        if tracked is None:
            print("⚠️  跳过 M11：无法读取 git 索引（非 git 仓库或 git 不可用）")
        else:
            for f in registered:
                if f in on_disk and f not in tracked:
                    errors.append(
                        f"M11 `{f}` 已在清单登记、文件也在工作树，但**未被 git 跟踪**——"
                        "提交后干净 clone 将缺这个文件 ⇒ 执行序里凭空少一步，该迁移的 DDL 内容永久丢失。"
                        "请 git add 后再提交（清单与脚本必须同批入库）"
                    )

    if errors:
        print(f"❌ 迁移清单校验失败：{len(errors)} 项\n")
        for x in errors:
            print("  · " + x)
        print(
            "\n清单是 scripts/migration 的顺序唯一真源："
            f"{manifest_path}\n"
            "新增脚本请追加到 entries 末尾并给 order = 当前最大 + 1；重号必须同步 ordinalConflicts；"
            "脚本只能平铺（子目录对门禁隐身）；并入 bone-init.sql 后下线请登记 retired。"
        )
        return 1

    kinds = {}
    for e in entries:
        kinds[e["kind"]] = kinds.get(e["kind"], 0) + 1
    print(
        "OK: 迁移清单校验通过（{} 个文件 / {} 个可执行脚本 / order 1..{} 连续 / "
        "重号组 {} 个已登记 / 已下线 {} / kind {}）".format(
            len(entries), len(sql_orders), len(sql_orders), len(actual_groups), len(retired),
            " ".join(f"{k}={v}" for k, v in sorted(kinds.items())),
        )
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
