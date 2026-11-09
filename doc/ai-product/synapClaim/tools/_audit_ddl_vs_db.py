#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""§9.5 设计态 DDL 与活库 information_schema 的列级对账（report-only）。

定位与边界（重要，别把它接进 CI）：
  * _check_ddl.py 读不到数据库，它的 PASS 只证明「文档内部自洽」：
    D1–D6 是 SQL 语法/结构判据，D7 是 §9.1/§9.0/§9.5 三个文档集合对账。
  * 本脚本补的是另一维：文档里的 CREATE TABLE 与活库实际列是否一致。
    它依赖活库连接，CI 环境无 DB；做成必过门禁会让「没起数据库」变成构建失败，
    与「误报比漏报更致命」同族。因此定位为人工可复跑的体检，不进 §六 7 道门禁。

fail-closed：DB 不可达时明确报错退出（exit 2），绝不静默输出「无差异」。
ratchet：实跑按「含业务列差异的表集合」对存量基线做只减不增判定，
        新增差异表 exit 1（提示漏登记 §9.0（5）），补齐的基线项提示删除。
        用集合而非计数，防止「A 补齐、B 新增、总数不变」的静默漂移。
对账：  实算的 5/7/14 必须与 §9.0（4）汇总表声明的一致，不一致 exit 1。
        此前只打印不回读，文档被手改成 6/7/13 也没人发现（2026-11-09 补）。

用法：
    BONE_DB_PASSWORD=xxx python3 tools/_audit_ddl_vs_db.py
    python3 tools/_audit_ddl_vs_db.py --self-test     # 只测解析逻辑，不连库

不硬编码表名：表清单从 §9.5 现读；物理名按 name / t_name / t_adj_name 归一化。
"""
import io
import os
import re
import shutil
import subprocess
import sys

import _manifest as M

DDL_DOC = M.path("contract.landing")
AUDIT_COLS = {
    "tenant_id", "created_by", "created_at", "updated_by", "updated_at",
    "version", "deleted",
}
CREATE_RE = re.compile(r"CREATE\s+TABLE\s+`?([a-z_0-9]+)", re.I)
# 刻意不加 re.I：本仓 DDL 的列名全小写，而 PRIMARY / UNIQUE / KEY / CONSTRAINT
# 等索引子句一律大写。加 re.I 会把「PRIMARY」「UNIQUE」当成列名，
# 使每张表都凭空多出 2–3 个差异 —— 误报会让体检结论整体失真。
NON_COLUMN = {"PRIMARY", "UNIQUE", "KEY", "CONSTRAINT", "FOREIGN", "INDEX", "CHECK", "FULLTEXT"}
COL_RE = re.compile(r"^\s+([a-z_0-9]+)\s+[A-Z]")
HEAD_95 = "### 9.5"
HEAD_10 = "## 10. "

# 存量基线（ratchet，只减不增）：2026-11-07 首次全量体检时含业务列差异的 14 张表。
# 为什么存「集合」而不是「数量 14」：数量相等但换了表（A 补齐、B 新增）会静默通过，
# 与 D7 从「总数 = 36」改集合对账是同一条教训。
# 规则：新增差异表 → 判红（本次改动又漏登记）；基线里的表补齐 → 提示从这里删掉。
# 与 §七 _check_doc_structure.py 的 BASELINE 是同一套做法。
BASELINE_BUSINESS_DIFF = {
    "adj_calculation", "adj_calculation_line", "adj_liability", "appeal_record",
    "claim_case", "claim_decision_execution", "claim_evidence", "claim_flow_config",
    "claim_inspection", "claim_item", "quota_account", "quota_transaction",
    "settle_funding_event", "settle_order",
}


def ratchet(names):
    """返回 (新增差异表, 已补齐的基线项)。纯函数，便于 --self-test 反证。"""
    now = set(names)
    return sorted(now - BASELINE_BUSINESS_DIFF), sorted(BASELINE_BUSINESS_DIFF - now)


def parse_ddl(text):
    """解析 §9.5 的 CREATE TABLE 块 → {表名: 列集合}。"""
    lines = text.splitlines()
    a = next(i for i, l in enumerate(lines) if l.startswith(HEAD_95))
    b = next(i for i in range(a + 1, len(lines)) if lines[i].startswith(HEAD_10))
    out, cur = {}, None
    for line in lines[a + 1:b]:
        m = CREATE_RE.match(line.strip())
        if m:
            cur = m.group(1)
            out[cur] = set()
            continue
        if cur is None:
            continue
        if line.startswith(") ENGINE="):
            cur = None
            continue
        c = COL_RE.match(line)
        if c and c.group(1).upper() not in NON_COLUMN:
            out[cur].add(c.group(1))
    return out


def normalize(name):
    """逻辑名 → 活库可能的物理名（本仓 t_ 前缀不统一，§9.0 已登记）。"""
    return [name, "t_" + name, "t_adj_" + name, "adj_" + name]


def classify(doc_cols, db_cols):
    """返回 (差集, 是否仅审计列差异)。"""
    diff = (doc_cols - db_cols) | (db_cols - doc_cols)
    return diff, bool(diff) and diff <= AUDIT_COLS


# §9.0（4）汇总表的三行标签。判据取**语义标签**而非行序：
# 汇总表将来插入新行（如「活库无同名表」）时，按行序取值会静默错位。
SUMMARY_LABELS = ("列完全一致", "仅审计列差异", "含业务列差异")


def _section(lines, head):
    """截取以 head 开头的小节，到下一个 ### / ## 为止。"""
    a = next(i for i, l in enumerate(lines) if l.startswith(head))
    b = next((i for i in range(a + 1, len(lines))
              if lines[i].startswith("### ") or lines[i].startswith("## ")), len(lines))
    return a, b


def declared_counts(text):
    """从 §9.0（4）汇总表取 {标签: 张数}；缺任一类返回 None（不按缺失放行）。

    2026-11-09 补：本脚本此前只**打印**实算的 5/7/14，从不回读文档——
    等于体检报告和文档各说一套，谁都没错。文档数字一旦手改成 6/7/13，
    报告仍会打印"含业务列差异 14 张"，没人发现两处已经不一致。
    """
    lines = text.splitlines()
    a, b = _section(lines, "### 9.0")
    got = {}
    for line in lines[a:b]:
        if not line.startswith("|"):
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) < 2:
            continue
        label = cells[0].strip("* ")
        if label not in SUMMARY_LABELS:
            continue
        m = re.fullmatch(r"\**(\d+)\**", cells[1].replace(" ", ""))
        if m:
            got[label] = int(m.group(1))
    return got if len(got) == len(SUMMARY_LABELS) else None


def mysql_bin():
    found = shutil.which("mysql")
    if found:
        return found
    for p in ("/usr/local/mysql/bin/mysql", "/opt/homebrew/bin/mysql", "/usr/bin/mysql"):
        if os.path.isfile(p):
            return p
    return None


def read_db():
    """读 bone 库全部列名；失败即抛，不允许静默返回空集合。"""
    exe = mysql_bin()
    if not exe:
        raise RuntimeError("找不到 mysql 客户端，无法做列级对账")

    host = os.environ.get("BONE_DB_HOST", "127.0.0.1")
    port = os.environ.get("BONE_DB_PORT", "3306")
    user = os.environ.get("BONE_DB_USER", "root")
    schema = os.environ.get("BONE_DB_SCHEMA", "bone")
    env = dict(os.environ)
    pwd = os.environ.get("BONE_DB_PASSWORD") or os.environ.get("MYSQL_PWD")
    if pwd:
        env["MYSQL_PWD"] = pwd  # 密码不进命令行参数，避免 ps 泄露

    sql = ("SELECT table_name, column_name FROM information_schema.columns "
           "WHERE table_schema='%s'" % schema.replace("'", ""))
    proc = subprocess.run(
        [exe, "-h", host, "-P", port, "-u" + user, "-N", "-e", sql],
        capture_output=True, text=True, env=env, timeout=30,
    )
    if proc.returncode != 0:
        raise RuntimeError(
            "活库连接失败（%s:%s/%s）：%s\n"
            "请设置 BONE_DB_PASSWORD（或 MYSQL_PWD / ~/.my.cnf）后重跑；"
            "DB 不可达时本脚本拒绝输出「无差异」。" % (host, port, schema, proc.stderr.strip()[:300])
        )
    out = {}
    for line in proc.stdout.splitlines():
        parts = line.split("\t")
        if len(parts) == 2:
            out.setdefault(parts[0], set()).add(parts[1])
    if not out:
        raise RuntimeError("活库返回 0 张表：schema 名或权限有误，拒绝按空集合判绿")
    return out


def self_test():
    """纯解析逻辑探针，不连库。"""
    sample = """### 9.5 核心表 DDL
CREATE TABLE foo (
  id BIGINT NOT NULL,
  pay_percent DECIMAL(6,2),
  tenant_id BIGINT,
  PRIMARY KEY (id)
) ENGINE=InnoDB;
CREATE TABLE bar (
  id BIGINT NOT NULL,
  x INT,
  PRIMARY KEY (id)
) ENGINE=InnoDB;
## 10. 接口契约
"""
    d = parse_ddl(sample)
    assert d == {"foo": {"id", "pay_percent", "tenant_id"}, "bar": {"id", "x"}}, d
    diff, only_audit = classify({"id", "pay_percent", "tenant_id", "updated_at"},
                                {"id", "pay_percent", "tenant_id"})
    assert diff == {"updated_at"} and only_audit, (diff, only_audit)
    diff2, only_audit2 = classify({"id", "pay_percent"}, {"id", "restrict_scope"})
    assert not only_audit2 and "restrict_scope" in diff2
    assert normalize("claim_case")[1] == "t_claim_case"

    base = sorted(BASELINE_BUSINESS_DIFF)
    assert ratchet(base) == ([], []), "基线自身必须全绿"
    new, _ = ratchet(base + ["a_newly_drifted_table"])
    assert new == ["a_newly_drifted_table"], new
    # 数量不变但换了一张表：集合判据必须仍能抓到，这是不用「计数 = 14」的理由
    swapped = base[:-1] + ["a_newly_drifted_table"]
    new2, stale2 = ratchet(swapped)
    assert new2 == ["a_newly_drifted_table"] and stale2 == [base[-1]], (new2, stale2)
    _, stale3 = ratchet(base[:-1])
    assert stale3 == [base[-1]]

    # §9.0（4）汇总表解析：标签取值、缺项 fail-closed、与行序无关
    fixture = """### 9.0 物理落地对齐
| 结论 | 张数 | 说明 |
|---|---:|---|
| 活库无同名表 | **2** | 新增行插在中间 |
| 列完全一致 | **5** | x |
| 仅审计列差异 | **7** | x |
| **含业务列差异** | **14** | x |
### 9.1 表清单
"""
    assert declared_counts(fixture) == {"列完全一致": 5, "仅审计列差异": 7,
                                       "含业务列差异": 14}, declared_counts(fixture)
    # 少一类 → None（不按缺失当 0），否则"文档删掉一行"会静默通过
    assert declared_counts(fixture.replace("| **含业务列差异** | **14** | x |\n", "")) is None

    print("=== _audit_ddl_vs_db.py --self-test ===")
    print("  ✅ §9.5 块解析（多表 / 围栏收尾 / 到 §10 截止）")
    print("  ✅ 审计列差异单独归类，业务列差异不误判为约定")
    print("  ✅ 物理名归一化（t_ 前缀不统一）")
    print("  ✅ ratchet 基线自洽 / 新增差异判红 / 补齐转 stale")
    print("  ✅ 数量不变但换表仍被抓（集合判据优于计数）")
    print("  ✅ §9.0（4）汇总按标签取值 / 插行不错位 / 缺项 fail-closed")
    print("  7 通过 / 0 失败")
    return 0


def main():
    if "--self-test" in sys.argv:
        return self_test()
    try:
        db = read_db()
    except (RuntimeError, subprocess.TimeoutExpired) as e:
        print("=== §9.5 DDL ↔ 活库列级对账 ===")
        print("【FAIL】" + str(e))
        return 2

    text = io.open(DDL_DOC, encoding="utf-8").read()
    doc = parse_ddl(text)
    same, audit_only, business, missing = [], [], [], []
    for name, cols in sorted(doc.items()):
        hit = next((c for c in normalize(name) if c in db), None)
        if hit is None:
            missing.append(name)
            continue
        diff, only_audit = classify(cols, db[hit])
        if not diff:
            same.append(name)
        elif only_audit:
            audit_only.append(name)
        else:
            business.append((name, hit, len(diff - AUDIT_COLS),
                             sorted(diff - AUDIT_COLS)))

    print("=== §9.5 DDL ↔ 活库列级对账（report-only）===")
    print("§9.5 表 %d 张 / 活库表 %d 张" % (len(doc), len(db)))
    print("  列完全一致     %d 张：%s" % (len(same), ", ".join(same) or "—"))
    print("  仅审计列差异   %d 张（§9.2 注入矩阵已知约定，非缺陷）" % len(audit_only))
    print("  含业务列差异   %d 张" % len(business))
    print("  活库无同名表   %d 张：%s" % (len(missing), ", ".join(missing) or "—"))
    print()
    for name, hit, n, cols in sorted(business, key=lambda x: -x[2]):
        print("  %-28s → %-32s %2d 处：%s" % (name, hit, n, ", ".join(cols[:6])))
    print()
    new, stale = ratchet([n for n, _, _, _ in business])
    if new:
        print("【FAIL】新增业务列差异（不在 2026-11-07 存量基线内）：%s" % ", ".join(new))
        print("        按《文档治理规约》§六之二：本次迁移改动漏了 §9.0（5）台账登记，先补再合并。")
    if stale:
        print("【STALE】基线内已补齐，可从 _audit_ddl_vs_db.py 的 BASELINE_BUSINESS_DIFF 删除：%s"
              % ", ".join(stale))

    # 文档声明 vs 实算：同一事实的三份拷贝（§9.0（4）汇总 / 本次实算 / ratchet 基线）
    # 必须互相相等，否则体检报告的绿只是"脚本和历史基线一致"，不代表文档在说同一件事。
    mismatch = []
    declared = declared_counts(text)
    if declared is None:
        mismatch.append("未能从 §9.0（4）汇总表解析出三类张数（格式已改？不得按缺失放行）")
    else:
        actual = {"列完全一致": len(same), "仅审计列差异": len(audit_only),
                  "含业务列差异": len(business)}
        for k in SUMMARY_LABELS:
            if declared[k] != actual[k]:
                mismatch.append("「%s」文档写 %d 张，本次实算 %d 张" % (k, declared[k], actual[k]))
    if mismatch:
        print()
        print("【FAIL】§9.0（4）汇总数字与本次实算不一致：")
        for m in mismatch:
            print("        " + m)
        print("        同步改文档数字与 §9.0（5）台账；否则报告与文档各说一套，两边都\"没错\"。")

    if not new and not stale and not mismatch:
        print("【OK】业务列差异表集合与存量基线一致（ratchet 未增长）；"
              "§9.0（4）汇总数字与实算一致。")

    print()
    print("【结论】§9.5 是设计态 DDL，不是活库现状；物理真源见 V*.sql / bone-init.sql。")
    print("         本脚本不进 CI 门禁；_check_ddl.py 的绿只代表文档内部自洽。")
    print("         业务维度↔目标列↔活库的三方对照见《总体技术方案》§5.2.1。")
    return 1 if (new or mismatch) else 0


if __name__ == "__main__":
    sys.exit(main())
