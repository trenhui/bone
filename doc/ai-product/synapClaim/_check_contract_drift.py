#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""契约漂移自检：Rulebook / 接口契约 与 工程落地版的一致性。

为什么需要：`_check_xref.py` 只守「章节引用可解析」，而**规则 ID、对外枚举、错误码
集合**这三类跨文档硬契约此前全靠人工比对——已实际发生过同一规则 ID 在
Rulebook 与落地版指两条不同规则、对外枚举值只在交付包出现的情况。人工比对
不可持续，故本脚本把这三类契约变成可重复执行的门禁。

校验项
------
  C1 规则 ID 落地覆盖：工程落地版引用的每个 `R-XXX-NN` 必须在 Rulebook（04）
     中有定义行（防「引用了不存在的规则」与「一号两义」）。
  C2 规则 ID 定义唯一：同一 ID 在 Rulebook 中若出现多行且名称不同 ⇒ 一号两义。
  C3 对外枚举落地存在：接口契约（05）声明的每个枚举值必须在工程落地版出现。
  C4 错误码集合一致：接口契约（05）与工程落地版的错误码集合必须完全相同。
  C5 对外枚举反向唯一（新增）：工程落地版自造的对外枚举值不得逃逸——
     落地版 §10.5 OpenAPI 响应 example / §10.7 事件载荷中的 ALL_CAPS 值，若既非
     内部状态码（§6.1 数字码）、非节点类型、也未在接口契约（05）登记，即
     「自造对外枚举」（曾发生 status=ACCEPTED，C3 单向比对抓不到）。
  C6 表清单 ↔ DDL 落点（新增，弱检查）：§9.1 表清单登记的表必须在 §9.5 DDL 中
     出现；DDL 中出现的表未登记则提示（防「声明了但无落点」的空洞）。
  C7 分层真源规则 ID 覆盖（新增）：`design/` 与 `verification/` 引用的每个
     `R-XXX-NN` 必须在 Rulebook 有定义行。
  C8 分层真源表名对位（新增，弱检查）：上述目录出现的业务表名须在工程落地版
     出现过，防合并后的设计真源自造表名。

**扫描范围为何是这三个根文件 + 两个子目录**：`reviews/` 是有意豁免——它是冻结的
过程记录（某时点的取证），不是当前设计；要求它随契约演进而修改，等于篡改历史。

**C7/C8 的由来**（记一笔，免得后人误删）：文档按「设计/验证/过程记录」分层后，
`design/理算引擎设计.md`、`design/风控与支付通道设计.md`、`verification/理算引擎
验证套件.md` 已升为理算与风控主题的唯一真源，但它们落在原有门禁之外——引用一个
不存在的规则 ID 不会被任何人拦住。分层若不同步扩门禁，等于把门禁变成摆设。

退出码：发现硬性漂移 -> 1；仅提示（--report） -> 0。
"""
import glob
import os
import re
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))

RULEBOOK = os.path.join(ROOT, "delivery/04-业务规则书（Rulebook）-门诊小额快赔MVP全集.md")
CONTRACT = os.path.join(ROOT, "delivery/05-接口契约与数据字典（MVP）.md")
LANDING = os.path.join(ROOT, "synapClaim 智能理赔平台-工程详细设计（落地版）.md")

RULE_ID = re.compile(r"\|\s*(R-[A-Z]+-\d+)\s*\|")
ANY_RULE_ID = re.compile(r"R-[A-Z]+-\d+")
ENUM_ROW = re.compile(r"\|\s*([a-z_]+)\s*\|\s*ENUM\s*\|([^|]*)\|")
HTTP_CODES = re.compile(r"`(\d{3})`")


def read(path):
    if not os.path.exists(path):
        print(f"❌ 缺少文件：{os.path.relpath(path, ROOT)}")
        sys.exit(2)
    with open(path, encoding="utf-8") as fh:
        return fh.read()


def main() -> int:
    report_only = "--report" in sys.argv
    book = read(RULEBOOK)
    contract = read(CONTRACT)
    landing = read(LANDING)

    hard, soft = [], []

    # --- Rulebook 侧：ID -> 名称（行内第 2 列） ---
    defined = {}
    for line in book.splitlines():
        m = re.match(r"\|\s*(R-[A-Z]+-\d+)\s*\|([^|]*)\|", line)
        if m:
            rid, name = m.group(1), m.group(2).strip()
            defined.setdefault(rid, set()).add(name)

    # C2 同一 ID 多名称 => 一号两义
    for rid, names in defined.items():
        if len(names) > 1:
            hard.append(f"[C2] 规则 ID 一号两义：{rid} 在 Rulebook 中有多个名称 {sorted(names)}")

    # C1 落地版引用的 ID 必须已定义
    used = set(ANY_RULE_ID.findall(landing))
    for rid in sorted(used - set(defined)):
        hard.append(f"[C1] 工程落地版引用了 Rulebook 未定义的规则 ID：{rid}")
    # 落地版多以「R-XXX-01~04」或规则族汇总引用，逐条 ID 未出现属正常；
    # 仅当整个规则族（连族名）都未出现时才提示。
    landing_families = set(re.findall(r"R-[A-Z]+", landing))
    for rid in sorted(set(defined) - used):
        family = rid.rsplit("-", 1)[0]
        if family not in landing_families:
            soft.append(f"[C1] Rulebook 规则族未在工程落地版出现：{family}（含 {rid} 等）")

    # C3 接口契约声明的枚举值必须在落地版出现
    for field, raw in ENUM_ROW.findall(contract):
        values = [v.strip() for v in re.split(r"[/、,，]", raw)]
        for v in values:
            if not v or not re.fullmatch(r"[A-Z][A-Z0-9_]*", v):
                continue  # 「等」「见 PRD §x」等非枚举值
            if v not in landing:
                hard.append(f"[C3] 接口契约声明的枚举值在工程落地版未出现：{field} = {v}")

    # C4 错误码集合一致
    contract_codes = set()
    for line in contract.splitlines():
        m = re.match(r"\|\s*(\d{3}(?:/\d{3})*)\s*\|", line)
        if m:
            contract_codes.update(m.group(1).split("/"))
    landing_codes = {c for c in HTTP_CODES.findall(landing) if not c.startswith("2")}
    for code in sorted(contract_codes - landing_codes):
        hard.append(f"[C4] 错误码 {code} 只在接口契约出现，工程落地版未定义")
    for code in sorted(landing_codes - contract_codes):
        soft.append(f"[C4] 错误码 {code} 只在工程落地版出现，接口契约未登记")

    # C5 对外枚举反向唯一：落地版对外通道自造的 ALL_CAPS 值必须已在 05 登记
    #    白名单：节点类型 / 决策结果 / 内部状态码族名（非对外枚举）/ 通用技术词
    enum_whitelist = {
        # 节点类型与决策结果（05 契约已登记）
        "OCR", "RULE", "LLM", "DECISION", "AUTO_APPROVE", "SUGGEST_REJECT",
        "MANUAL_REQUIRED", "NEED_MORE_EVIDENCE", "APPROVED", "REJECTED",
        "REQUEST_MORE_EVIDENCE",
        # 案件对外两值 + 资金阶段（05 已登记）
        "PENDING_EVIDENCE", "REVIEWING", "SOFT_RESERVED", "HARD_RESERVED",
        "REVIEW_FREEZE", "PAID", "RELEASED", "FAILED", "REFUNDED",
        # 枚举列名 / DDL 结构词（非对外枚举值）
        "ENUM", "JSON", "TINYINT", "DATETIME", "VARCHAR", "DECIMAL", "CHAR",
        "BIGINT", "INT", "PRIMARY", "UNIQUE", "KEY", "NULL", "DEFAULT",
        "COMMENT", "ENGINE", "INNODB", "CHARSET", "NOT", "ON", "CREATE",
        "TABLE", "INDEX", "REFERENCES", "CASCADE", "IF", "EXISTS", "ADD",
        "MODIFY", "DROP", "SELECT", "INSERT", "UPDATE", "DELETE", "FROM",
        "WHERE", "AND", "OR", "SET", "VALUES", "INTO", "TRUE", "FALSE",
        # 内部状态码族（§6.1 数字码体系，不对外）
        "DRAFT", "INSPECTION", "AUDITING", "SUBMITTING", "PRE_EXAM",
        "SIGNING", "INIT", "FINISH",
        # 决策执行内部状态（claim_decision_execution.status，不对外）
        "RUNNING", "SUCCESS", "FALLBACK",
    }
    out_channel = []
    # §10.5 OpenAPI：`example: XXX` / `example: 'XXX'`
    out_channel += re.findall(r"example:\s*'?([A-Z][A-Z0-9_]{2,})'?", landing)
    # §10.7 事件表：status=XXX / decisionResult=XXX 等载荷
    out_channel += re.findall(r"(?:status|decisionResult|decision)=([A-Z][A-Z0-9_]{2,})", landing)
    for v in sorted(set(out_channel)):
        if v in enum_whitelist or v in contract:
            continue
        hard.append(f"[C5] 工程落地版对外通道出现自造枚举值：{v}（未在 05 契约登记，也不在已知白名单）")

    # C6 表清单 ↔ DDL 落点（弱检查）
    #  §9.1 表清单行形态为 `| `表名` | 聚合 | 说明 | 对位表 |`，只取第一列；
    #  同一行可能并列多表（`a` / `b`）；对位列（ss_claim / ia_liability 等遗留表）
    #  与字段名（idempotency_key 等）不得误判为表名，故仅截取 §9.1～§9.2 区间。
    section_91 = landing.split("### 9.2")[0].split("### 9.1")[-1]
    listed_tables = set()
    for line in section_91.splitlines():
        m = re.match(r"\|\s*`([a-z][a-z0-9_`/ ]+)`\s*\|", line)
        if m:
            for t in m.group(1).split("/"):
                t = t.strip().strip("`").strip()
                if re.fullmatch(r"[a-z][a-z0-9_]+", t):
                    listed_tables.add(t)
    ddl_tables = set(re.findall(r"CREATE TABLE (\w+)", landing))
    for t in sorted(ddl_tables - listed_tables):
        soft.append(f"[C6] DDL 表未在 §9.1 表清单登记：{t}")
    for t in sorted(listed_tables - ddl_tables):
        soft.append(f"[C6] §9.1 登记的表在 §9.5 DDL 中无 CREATE TABLE：{t}")

    # C7/C8 分层真源（design/ verification/）纳入同一门禁
    #  根目录三份文件之外，合并后的设计/验证真源同样承载规则 ID 与表名契约，
    #  不纳管就会出现「真源写错、门禁全绿」。
    layer_files = sorted(
        glob.glob(os.path.join(ROOT, "design", "*.md"))
        + glob.glob(os.path.join(ROOT, "verification", "*.md"))
    )
    landing_tables = set(re.findall(r"\b(?:claim|tenant|ai)_[a-z_]+\b", landing))
    for path in layer_files:
        rel = os.path.relpath(path, ROOT)
        text = read(path)
        for rid in sorted(set(ANY_RULE_ID.findall(text)) - set(defined)):
            hard.append(f"[C7] {rel} 引用了 Rulebook 未定义的规则 ID：{rid}")
        for t in sorted(set(re.findall(r"\b(?:claim|tenant|ai)_[a-z_]+\b", text)) - landing_tables):
            soft.append(f"[C8] {rel} 出现工程落地版未登记的表名：{t}")

    for line in hard:
        print(f"❌ {line}")
    for line in soft:
        print(f"⚠️  {line}")
    if hard:
        print(f"\n发现 {len(hard)} 处契约漂移（硬性）。")
        return 0 if report_only else 1
    print(f"✅ 契约漂移检查通过（规则 ID / 对外枚举双向 / 错误码集合 / 表清单对位 / 分层真源 C7-C8 一致）。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
