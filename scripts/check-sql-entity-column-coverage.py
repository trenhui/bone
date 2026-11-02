#!/usr/bin/env python3
"""G42 仓储 SQL 模板 ↔ 实体列覆盖：读回来的实体不许缺列。

**为什么需要这条门禁**（2026-11-02 实证，同一缺陷形态已复发两次）：

bone-metadata-sdk 的仓储有两套写法，其中「抽象 finder + `resources/sql/<包路径>/<Repo>/<方法名>.sql`」
的列清单是**手写**的，而写入侧（INSERT/UPDATE）的列由实体元数据自动生成。两侧不同源，于是：

- 写：实体加了 `@Column(name="fee_code")` ⇒ INSERT 自动带上新列，**写得进**；
- 读：手写模板没加那一列 ⇒ `SmartRowMapper` 读不到 ⇒ 字段恒 `null`，**读不出**；
- 更糟：读到 `null` 的实体被整实体 `save()` 写回（初审回填核定金额就是这种），
  会把刚写进去的值覆盖成空串——即「建得进、读不出、再被写回抹掉」。数据静默丢失，
  没有任何异常，单测也抓不到（夹具走内存仓储，根本不经过 SQL 模板）。

实测后果（本轮修复前）：
1. `ClaimItemRepository/findByClaimId.sql` 漏 `fee_code`/`department` ⇒ 费用类别规则整条链路失效，
   理算退回责任统一比例静默错算；`department` 全表 713 行只剩 2 行还有值（已不可恢复）。
2. `DecisionExecutionRepository/*`（3 个模板）漏 `triage_reason` ⇒ 快捷赔付风险闸读到的永远是
   `null`，「风控命中不得一键放款」这道闸**从未生效**。
3. `QuotaAccountRepository/findTransactions.sql` 漏 `biz_date` ⇒ 日累计限额判断里
   `txn.getBizDate() == null` 恒真，日限额累计被整段跳过。
4. `SettlementRepository/findByBizDate.sql` 漏 `review_first_approver` ⇒ 对账不变量 5
   「大额双人复核留痕」把每笔合法大额结算都判成留痕缺失。

**判据**（只在语义确定时判红，避免用误报换 `--no-verify`）：

- R1 读整实体必须全列：仓储方法的返回类型若是本仓 `@Table` 实体，则其 SQL 模板的 SELECT
  列必须覆盖该实体全部显式 `@Column`。
- R2 读整实体必须选主键 `id`：否则读回来的对象 `id == null`，后续 `save()` 会当新增再插一行。
- R3 孤儿模板：模板文件在、接口里没有同名方法（`@Sql` 内联 SQL 的方法不算）⇒ 该查询能力
  实际不存在，读代码的人却以为有。存量登记基线，新增零容忍。

**豁免**（有明确理由，不登记、不计数）：

- 非 SELECT（UPDATE / DELETE 模板）；
- `SELECT *` / `<alias>.*`（列清单不由人维护）；
- 返回类型不是本仓实体（`*Projection` / `*Ref` / `Map` / 标量 / `Long`）；
- 模板对应不到接口文件（跨模块/生成代码）。

`*Projection` 之所以豁免：投影类字段与 SQL 逐列对齐、且只用于展示或只读计算，
不存在「读回来再整实体写回」的路径；一旦某个投影被当成实体用，R1 会因为返回类型是
`@Table` 实体而自动把它纳入。

**基线只可收缩**（`doc/architecture/sql-entity-column-baseline.json`）：存量缺口登记后，
缺口变大 / 新增缺口 / 条目已失效（补上了或文件没了）三种都判红。只判前两种会让白名单
越写越宽——同一个失效模式已在 i18n 白名单、ddl-required-columns 基线上各修过一次。

用法:
  python3 scripts/check-sql-entity-column-coverage.py            # 报告（不阻断）
  python3 scripts/check-sql-entity-column-coverage.py --check    # 门禁
  python3 scripts/check-sql-entity-column-coverage.py --baseline # 把当前缺口刷进基线
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
BASELINE = REPO / "doc/architecture/sql-entity-column-baseline.json"

SKIP_DIRS = {
    "node_modules",
    "target",
    "dist",
    "build",
    "out",
    ".git",
    ".idea",
    ".gradle",
    ".workbuddy",
    "coverage",
}

TABLE_ANN = re.compile(r'@Table\(\s*(?:value\s*=\s*)?"([^"]+)"')
COL_NAME = re.compile(r'@Column\([^)]*?name\s*=\s*"([^"]+)"')
CLASS_DECL = re.compile(r"(?m)^(?:public\s+)?(?:abstract\s+)?class\s+(\w+)")

# FROM 后紧跟的保留字不可能是表别名
NOT_ALIAS = {
    "WHERE", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "CROSS", "ON", "AND", "OR",
    "ORDER", "GROUP", "HAVING", "LIMIT", "UNION", "SET", "VALUES", "FOR", "USE", "AS",
}


def iter_sources(sub: str, suffix: str):
    """遍历 <模块>/src/main/<sub> 下的文件，跳过构建产物与前端依赖目录。"""
    for dirpath, dirnames, filenames in os.walk(REPO):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        marker = os.sep + os.path.join("src", "main", sub) + os.sep
        if marker not in dirpath + os.sep:
            continue
        for fn in filenames:
            if fn.endswith(suffix):
                yield Path(dirpath) / fn


def entity_columns() -> tuple[dict[str, set[str]], dict[str, str]]:
    """返回 ({表名: 显式 @Column 列集}, {实体简单类名: 表名})。

    只收显式 `@Column`：基类（`AbstractEntity`/`TenantAbstractEntity`）的 id/created_at/
    deleted 等字段由 SDK 隐式映射，没有 `@Column`，本判据不检查它们（漏判方向偏保守）。
    同一张表被多个实体映射时取并集——宁可多要求一列，也不让任一实体读回缺列。
    """
    by_table: dict[str, set[str]] = {}
    class_to_table: dict[str, str] = {}
    for path in iter_sources("java", ".java"):
        src = path.read_text(encoding="utf-8", errors="ignore")
        m = TABLE_ANN.search(src)
        if not m:
            continue
        table = m.group(1)
        by_table.setdefault(table, set()).update(COL_NAME.findall(src))
        cls = CLASS_DECL.search(src)
        if cls:
            class_to_table[cls.group(1)] = table
    return by_table, class_to_table


def strip_comments(sql: str) -> str:
    """剥离 `--` 行注释与 `/* */` 块注释（含 SDK 的 /*bone:tenant*/ 占位符）。

    注释里出现 `FROM` / 逗号会让列解析错位，且占位符本身不携带列信息。
    """
    sql = re.sub(r"/\*.*?\*/", " ", sql, flags=re.S)
    return re.sub(r"(?m)--[^\n]*", " ", sql)


def split_top(text: str) -> list[str]:
    """按顶层逗号切分（括号内的逗号属于函数参数）。"""
    parts: list[str] = []
    depth = 0
    cur: list[str] = []
    for ch in text:
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append("".join(cur))
            cur = []
        else:
            cur.append(ch)
    if cur:
        parts.append("".join(cur))
    return parts


def parse_select(sql: str) -> tuple[str, str, str | None] | None:
    """返回 (select 列表文本, 主表名, 主表别名)；非 SELECT 或解析不出返回 None。"""
    m = re.search(r"\bSELECT\b", sql, re.I)
    if not m:
        return None
    depth, i = 0, m.end()
    while i < len(sql):
        ch = sql[i]
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        elif depth == 0 and re.match(r"(?i)\bFROM\b", sql[i : i + 6]):
            break
        i += 1
    body, rest = sql[m.end() : i], sql[i:]
    fm = re.match(r"(?is)\bFROM\b\s+`?(\w+)`?(?:\s+(?:AS\s+)?(\w+))?", rest)
    if not fm:
        return None
    alias = fm.group(2)
    if alias and alias.upper() in NOT_ALIAS:
        alias = None
    return body, fm.group(1), alias


def selected_columns(body: str, alias: str | None) -> tuple[set[str], bool]:
    """返回 (SELECT 引用到的列, 是否被 `*` 通配覆盖)。"""
    text = re.sub(r"^\s*(?:DISTINCT\s+)?", "", body, flags=re.I)
    cols: set[str] = set()
    star = False
    for raw in split_top(text):
        # 去掉 AS 别名：`a.claim_id AS claim_id` -> `a.claim_id`
        item = re.split(r"(?i)\s+AS\s+", raw.strip())[0].strip()
        if not item:
            continue
        if item == "*":
            star = True
            continue
        if re.fullmatch(r"`?\w+`?\.\*", item):
            if alias is None or item.split(".")[0].strip("`") == alias:
                star = True
            continue
        # 带表前缀：`c.status` / `SUM(c.amount)`；有别名时只认主表前缀，避免把 join 表的列算进来
        for q in re.finditer(r"`?(\w+)`?\.`?(\w+)`?", item):
            if alias is None or q.group(1) == alias:
                cols.add(q.group(2))
        # 裸列（无别名表前缀）：`id`、`status`
        bare = re.fullmatch(r"`?(\w+)`?", item)
        if bare:
            cols.add(bare.group(1))
    return cols, star


def repository_interface(sql_file: Path) -> Path | None:
    """由 `src/main/resources/sql/<pkg>/<Repo>/<method>.sql` 反推 `src/main/java/<pkg>/<Repo>.java`。"""
    rel = sql_file.relative_to(REPO).as_posix()
    module, sep, rest = rel.partition("src/main/resources/sql/")
    if not sep:
        return None
    pkg = rest.split("/")[:-2]  # 去掉 <Repo>/<method>.sql
    if not pkg:
        return None
    return REPO / module / "src/main/java" / Path(*pkg) / f"{sql_file.parent.name}.java"


def method_return(iface: Path, method: str) -> str | None:
    """从接口源码里取某方法的返回类型原文；找不到返回 None。"""
    if not iface.exists():
        return None
    text = iface.read_text(encoding="utf-8", errors="ignore")
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    text = re.sub(r"(?m)//[^\n]*", " ", text)
    # 归一化空白，让跨行签名也能匹配；`>` 前面不插空格以免拆散泛型
    flat = re.sub(r"\s+", " ", text)
    m = re.search(r"([\w$.]+(?:\s*<[^;{}()]*?>)?(?:\[\])?)\s+" + re.escape(method) + r"\s*\(", flat)
    return m.group(1).strip() if m else None


def core_type(ret: str) -> str:
    """`List<Foo>` / `Optional<Foo>` -> `Foo`。"""
    ret = ret.strip()
    m = re.fullmatch(r"(?:List|Set|Optional|Collection|Iterable)\s*<\s*(.+?)\s*>", ret)
    return m.group(1).strip() if m else ret


def scan() -> tuple[list[dict], list[str], int]:
    """返回 (违规清单, 孤儿模板清单, 受检模板数)。"""
    by_table, class_to_table = entity_columns()
    problems: list[dict] = []
    orphans: list[str] = []
    checked = 0

    for sql_file in sorted(iter_sources("resources", ".sql")):
        rel = sql_file.relative_to(REPO).as_posix()
        if f"resources/sql/" not in rel:
            continue
        sql = strip_comments(sql_file.read_text(encoding="utf-8", errors="ignore"))
        parsed = parse_select(sql)
        if not parsed:
            continue  # R1/R2 只管 SELECT；UPDATE/DELETE 模板不产生实体
        body, table, alias = parsed
        if table not in by_table:
            continue  # 表没有实体映射（纯报表/聚合查询），不在本判据范围

        iface = repository_interface(sql_file)
        ret = method_return(iface, sql_file.stem) if iface else None
        if ret is None:
            iface_exists = iface is not None and iface.exists()
            if iface_exists:
                orphans.append(rel)
            continue
        entity = core_type(ret)
        if entity not in class_to_table:
            continue  # 投影 / 标量 / Map：不读整实体

        checked += 1
        cols, star = selected_columns(body, alias)
        if star:
            continue
        missing = sorted(by_table[class_to_table[entity]] - cols)
        if missing:
            problems.append(
                {
                    "template": rel,
                    "entity": entity,
                    "table": class_to_table[entity],
                    "missing": missing,
                }
            )
        if "id" not in cols:
            problems.append(
                {
                    "template": rel,
                    "entity": entity,
                    "table": class_to_table[entity],
                    "missing": ["id"],
                }
            )
    return problems, orphans, checked


def load_baseline() -> dict:
    if not BASELINE.exists():
        return {"templates": {}, "orphans": []}
    return json.loads(BASELINE.read_text(encoding="utf-8"))


def aggregate(problems: list[dict]) -> dict[str, dict]:
    """同一模板可能同时命中「缺列」与「缺主键」，按模板合并成一条。"""
    out: dict[str, dict] = {}
    for p in problems:
        item = out.setdefault(
            p["template"], {"entity": p["entity"], "table": p["table"], "missing": set()}
        )
        item["missing"] |= set(p["missing"])
    return out


def write_baseline(problems: list[dict], orphans: list[str]) -> None:
    doc = {
        "_comment": (
            "G42 仓储 SQL 列覆盖存量缺口（唯一真源）。新增缺口零容忍；此表内条目只可收缩——"
            "缺口补上、模板删除、或条目不成立时，条目必须同步移除，否则 --check 判红。"
            "登记理由只能写可复核的事实（哪个消费方读到它、不修的实际后果），不要写「待评估」。"
        ),
        "templates": {
            rel: {
                "entity": item["entity"],
                "table": item["table"],
                "missing": sorted(item["missing"]),
            }
            for rel, item in sorted(aggregate(problems).items())
        },
        "orphans": sorted(orphans),
    }
    BASELINE.parent.mkdir(parents=True, exist_ok=True)
    BASELINE.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="新增缺口 / 缺口扩大 / 条目失效均失败")
    ap.add_argument("--baseline", action="store_true", help="把当前缺口刷进基线")
    args = ap.parse_args()

    problems, orphans, checked = scan()

    if args.baseline:
        write_baseline(problems, orphans)
        print(
            "OK: 已写入基线 {} 条缺口 / {} 个孤儿模板 → {}".format(
                len(problems), len(orphans), BASELINE.relative_to(REPO)
            )
        )
        return 0

    if not args.check:
        print("G42 仓储 SQL 列覆盖报告（受检模板 {} 个：返回类型为本仓 @Table 实体）".format(checked))
        if not problems and not orphans:
            print("OK: 无缺口")
            return 0
        for rel, item in sorted(aggregate(problems).items()):
            print(
                "  缺列 {:<26} {}@{} 缺 {}".format(
                    Path(rel).parent.name + "/" + Path(rel).stem,
                    item["entity"],
                    item["table"],
                    "/".join(sorted(item["missing"])),
                )
            )
            print("        {}".format(rel))
        for o in orphans:
            print("  孤儿模板（接口无同名方法） {}".format(o))
        print("  说明：读整实体缺列 ⇒ 字段恒 null；若随后整实体 save() 会把该列写回覆盖。")
        return 0

    baseline = load_baseline()
    known = baseline.get("templates", {})
    known_orphans = set(baseline.get("orphans", []))
    errors: list[str] = []

    current = aggregate(problems)
    for rel, item in sorted(current.items()):
        cols = "/".join(sorted(item["missing"]))
        # 主键与普通列后果不同：缺普通列是「读到 null 再写回覆盖」，缺 id 是 SaveKind 判定成新增
        effect = (
            "未选主键：读回的实体 id 为 null，后续 save() 会当新增再插一行"
            if "id" in item["missing"]
            else "读回实体的该字段恒为 null，整实体 save() 还会把列覆盖成空值"
        )
        if rel not in known:
            errors.append(
                "新增缺列：{}（{}@{}）缺 {} ——{}".format(
                    rel, item["entity"], item["table"], cols, effect
                )
            )
            continue
        worse = sorted(item["missing"] - set(known[rel].get("missing", [])))
        if worse:
            errors.append(
                "基线内缺口扩大：{} 新增缺 {}（基线只可收缩）".format(rel, "/".join(worse))
            )
    for rel in sorted(set(known) - set(current)):
        if (REPO / rel).exists():
            errors.append(
                "基线条目已失效：{} 缺口已补上，请从基线移除（只可收缩）".format(rel)
            )
        else:
            errors.append("基线条目指向的模板已不存在：{}，请从基线移除".format(rel))

    current_orphans = set(orphans)
    for o in sorted(current_orphans - known_orphans):
        errors.append(
            "新增孤儿模板：{} ——接口里没有同名方法，这份 SQL 永远不会被执行".format(o)
        )
    for o in sorted(known_orphans - current_orphans):
        errors.append("基线孤儿模板已消失：{}，请从基线移除（只可收缩）".format(o))

    if errors:
        print("G42 仓储 SQL 列覆盖检查失败：")
        for e in errors:
            print("  - {}".format(e))
        print("  修法：补齐模板 SELECT 列；或把该 finder 的返回类型改成投影类（则不再要求全列）。")
        return 1

    print(
        "OK: G42 仓储 SQL 列覆盖通过（受检模板 {} 个，新增缺口 0，基线内 {} 条存量缺口未扩大，"
        "孤儿模板 {} 个仍在基线）".format(checked, len(known), len(known_orphans))
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
