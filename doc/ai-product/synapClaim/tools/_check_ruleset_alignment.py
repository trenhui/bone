#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""规则 ID 命名空间对账：Rulebook 条款 ↔ claim-ruleset.json 执行项。

背景（2026-11-07 实测）：
  C7 只校验「L4/L6 引用的规则 ID 是否在 Rulebook 定义」，两边都定义就放行。
  但实测发现 `R-FRAUD-02` 在 Rulebook 是「金额异常检测」、在资产里跑的是
  `hasEvidence == false`——**同一个 ID、两套语义**，C7 完全查不出来。
  客户按规则书理解赔付逻辑，实际系统按另一套规则跑，属于比「ID 缺失」
  更危险的一类契约漂移。

策略（业界做法）：**不重命名已生效 ID**（日志/评测集/客户交付都在用，
改名是不可逆口径断裂），改为登记命名空间对照表 + 用门禁锁住它。

判据：
  A1 §11.1 登记的 ID 集合 == 资产 ID ∩ Rulebook 条款 ID（双向对账）
  A2 每行 conclusion / risk 与资产当前值逐行一致
  A3 「同名不同义」集合 == 存量基线（防静默改成「一致」或新增未登记撞名）
  A4 解析不出集合即 fail-closed，不按空集合恒过
  A5 资产 33 条全量落在 §11.1 ∪ §11.2（覆盖率缺口判红）
  A6 §11.3「仅本书有」的族级条数 == 实际条款数对账（含总条数）
  A7 §11.1 每行必须声明「条款落点」，且载体真实存在；`无落点` 集合锁基线

A7 的由来（2026-11-08 实测）：
  A3 只锁住「哪 10 个 ID 同名不同义」，但没回答更要命的一问——**条款语义到底由谁承载**。
  逐条取证后发现「冲突 ≠ 无落点」：10 条里 8 条的真实落点在出站端口
  （`InvoiceVerificationPort` / `ItemCatalogPort` / `GraphFraudPort` / `VisualForensicsPort`），
  只有 `R-FRAUD-02`（3σ / 保额 80%）、`R-FRAUD-03`（时间轴矛盾）全仓找不到承载——
  客户按规则书以为在风控，系统里根本没有这段代码。这类「条款空挂」比撞名更严重，
  且**无法从 ID 集合看出来**，只能靠逐条声明落点 + 机器校验载体存在。
"""
import io
import json
import os
import re
import sys

import _manifest as M

RULEBOOK = M.path("delivery.04")
ASSET = os.path.abspath(os.path.join(
    M.ROOT, "..", "..", "..", "bone-apps", "synap-claim",
    "src", "main", "resources", "claim-ruleset.json"))

SEC11 = "## 十一、执行资产 ID 命名空间对照"
ROW_RE = re.compile(
    r"^\|\s*`(R-[A-Z]+-\d+)`\s*\|[^|]*\|\s*`([A-Z_0-9]+)`\s*\|"
    r"\s*`(True|False)`\s*\|\s*\*{0,2}(一致|同名不同义)")
# §11.2「仅资产」逐条登记：ID | `CATEGORY` | `CONCLUSION` | `True|False` | expression
# conclusion 允许数字（实测 `OUT_OF_PHASE1_SCOPE` 含 1；首版 `[A-Z_]+`
# 把它静默漏掉，导致 15 条只解析出 14 条——与 BSD grep / `match()` 同族）。
ROW_ONLY_RE = re.compile(
    r"^\|\s*`(R-[A-Z]+-\d+)`\s*\|\s*`[A-Z_0-9]+`\s*\|\s*`([A-Z_0-9]+)`\s*\|"
    r"\s*`(True|False)`\s*\|")
CLAUSE_RE = re.compile(r"^\|\s*(R-[A-Z]+-\d+)\s*\|")
SEC112 = "### 11.2"
SEC113 = "### 11.3"

# 2026-11-07 首次对账时，两层共有但语义不同的 10 个 ID（ratchet 基线）。
CONFLICT_BASELINE = {
    "R-DIR-01", "R-FRAUD-02", "R-FRAUD-03", "R-FRAUD-04", "R-FRAUD-05",
    "R-FRAUD-07", "R-FRAUD-08", "R-FRAUD-09", "R-FRAUD-10", "R-FRAUD-11",
}

# §11.3 族级登记行：| `R-AREA` | 5 | … | / | `R-DIR-02~06` | 5 | … | / | `R-FRAUD-01` | 1 | … |
SEC113_KEY_RE = re.compile(r"^\|\s*`(R-[A-Z]+(?:-\d+(?:~\d+)?)?)`\s*\|\s*(\d+)\s*\|")
_SINGLE_ID_RE = re.compile(r"^R-[A-Z]+-\d+$")
_RANGE_RE = re.compile(r"^(R-[A-Z]+)-(\d+)~(\d+)$")

# §11.1 第 6 列「条款落点」：受控取值，禁自由文本（自由文本等于没判据）。
LANDING_EXEC = "执行项"          # 资产里同名执行项本身就是承载点（语义一致的 8 条）
LANDING_NONE = "无落点"          # 全仓找不到承载点（缺陷，必须锁基线）
LANDING_PORT = "端口:"           # 由该出站端口的适配器承载（含一期桩）
LANDING_ENGINE = "引擎:"         # 由引擎/领域类承载
# 端口存在、调用点也在，但**本仓没有实现者**（一期由接入方实现后再闭环）：
# 调用即 fail-closed，条款在一期不产生判定结论。与「端口:X」不能混为一值——
# 前者是"真的在判"，后者是"一律转人工/退补件"，对客户是两件事。
LANDING_PORT_PENDING = "端口待接:"

# 2026-11-08 逐条取证后的「无落点」基线（ratchet）：新增或静默消除都判红。
# 消除（改成有落点）必须先改基线，改基线就得写清取证依据——挡的是「嘴上说有」。
NO_LANDING_BASELINE = {"R-FRAUD-02", "R-FRAUD-03"}

# 「端口待接」基线（ratchet）：现实是 1 个（ItemCatalogPort）。消除=有人接了线，
# 必须改基线（并改文档）；新增=又有一条落点退化成 fail-closed，同样必须显式登记。
PORT_PENDING_BASELINE = {"ItemCatalogPort"}

# 落点声明的载体必须在源码树里真实存在（声明了不存在的类 = 与「配了不生效」同族）。
JAVA_ROOT = os.path.abspath(os.path.join(
    M.ROOT, "..", "..", "..", "bone-apps", "synap-claim", "src", "main", "java"))

# 规则引擎适配器：事实变量的唯一提供方（表达式里引用了没人给的变量 ⇒ 该执行项静默不命中）。
RULE_ADAPTER = os.path.join(
    JAVA_ROOT, "com", "bone", "claim", "infrastructure", "rule",
    "QlExpressRuleEnginePortAdapter.java")
FACT_PUT_RE = re.compile(r'facts\.put\(\s*"([A-Za-z0-9_]+)"')
_EXPR_STRING_RE = re.compile(r'"[^"]*"')
_EXPR_IDENT_RE = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")
# QLExpress 字面量/关键字；不剥离字符串字面量的话，"BATCH" / "UNKNOWN" 会被当成变量（实测假红 2 个）。
EXPR_KEYWORDS = {"true", "false", "null"}


def load_asset():
    with io.open(ASSET, encoding="utf-8") as f:
        data = json.load(f)
    items = data.get("rules") or data.get("items") or []
    out = {}
    for r in items:
        rid = r.get("code") or r.get("id")
        if rid:
            out[rid] = (str(r.get("conclusion", "")), str(r.get("risk", "")))
    return out


def load_expressions():
    """资产里每条规则的 QLExpress 表达式：{ID: expression}。"""
    with io.open(ASSET, encoding="utf-8") as f:
        data = json.load(f)
    items = data.get("rules") or data.get("items") or []
    return {(r.get("code") or r.get("id")): (r.get("expression") or "")
            for r in items if (r.get("code") or r.get("id"))}


def load_fact_keys():
    """规则引擎适配器实际下发的事实变量集合。

    这是**读取点取证**：表达式能不能命中，取决于 `facts.put(...)` 真的提供了哪个 key。
    类/端口存在但与事实接不上，规则就是静默不命中——比报错更难发现。
    """
    with io.open(RULE_ADAPTER, encoding="utf-8") as f:
        return set(FACT_PUT_RE.findall(f.read()))


def expr_variables(expression):
    """表达式引用的变量（先剥字符串字面量，再取标识符）。"""
    text = _EXPR_STRING_RE.sub('""', expression or "")
    return {v for v in _EXPR_IDENT_RE.findall(text) if v.lower() not in EXPR_KEYWORDS}


def _clean(cell):
    """表格单元格去壳：空格 / 反引号 / 加粗星号。"""
    return cell.strip().strip("`").replace("*", "").strip()


def java_types():
    """源码树里的类名集合（不含内部类）：落点声明的载体必须真实存在。"""
    names = set()
    for _dp, _dns, fns in os.walk(JAVA_ROOT):
        names.update(fn[:-5] for fn in fns if fn.endswith(".java"))
    return names


def java_sources():
    """{类名: 源码文本}——供端口「有没有实现者 / 有没有调用方」取证用。"""
    out = {}
    for dp, _dns, fns in os.walk(JAVA_ROOT):
        for fn in fns:
            if not fn.endswith(".java"):
                continue
            try:
                with io.open(os.path.join(dp, fn), encoding="utf-8") as f:
                    out[fn[:-5]] = f.read()
            except OSError:
                continue
    return out


def declared_ports(rows):
    """§11.1 里所有以端口形式声明的落点名集合。"""
    ports = set()
    for _con, _risk, _rel, landing in rows.values():
        for prefix in (LANDING_PORT, LANDING_PORT_PENDING):
            if landing.startswith(prefix):
                ports.add(landing.split(":", 1)[1].strip())
    return ports


def port_facts(ports, sources=None):
    """{端口: {"impl": {...}, "consumers": {...}}}——A9 的读取点取证。

    两个事实各自对应一类「配了不生效」：
      · **impl 为空**：端口没有任何实现者 ⇒ 调用即 fail-closed。文档写「由该端口承载」
        就成了过度声明，必须改标「端口待接:X」。
      · **consumers 为空**：端口有实现（或没有）但除实现者外没人引用它 ⇒ 死端口。
        本项目已有先例（类在、零调用方），故按**读取点**取证，不按**声明处**取证。
    """
    sources = java_sources() if sources is None else sources
    out = {}
    for port in sorted(ports):
        impl, consumers = set(), set()
        impl_re = re.compile(r"\b(?:implements|extends)\b[^{;]*\b%s\b" % re.escape(port))
        use_re = re.compile(r"\b%s\b" % re.escape(port))
        for name, text in sources.items():
            if name == port:
                continue
            if impl_re.search(text):
                impl.add(name)
            elif use_re.search(text):
                consumers.add(name)
        out[port] = {"impl": impl, "consumers": consumers}
    return out


def parse(text):
    """返回 (§11.1 rows, §11.2 仅资产登记行, 条款 ID 集合, §11.3 族级条数)。

    §11.1 每行解析为 ``(conclusion, risk, 关系, 落点)``——落点在第 6 列，
    由 A7 校（旧表格只有 5 列时该值为空串，A7 会 fail-closed 而不是静默放过）。
    """
    if SEC11 not in text:
        return {}, {}, set(), {}
    before, sec11 = text.split(SEC11, 1)
    clauses = {m.group(1) for l in before.splitlines()
               if (m := CLAUSE_RE.match(l))}
    # 按小节切片，避免 §11.2 / §11.3 的表格行串进彼此的解析
    part11_1 = sec11.split(SEC112, 1)[0]
    part11_2 = sec11.split(SEC112, 1)[1].split(SEC113, 1)[0] if SEC112 in sec11 else ""
    part11_3 = sec11.split(SEC113, 1)[1].split("### 11.4", 1)[0] if SEC113 in sec11 else ""
    rows = {}
    for l in part11_1.splitlines():
        m = ROW_RE.match(l)
        if m:
            cells = [_clean(c) for c in l.split("|")]
            rows[m.group(1)] = (m.group(2), m.group(3), m.group(4),
                                cells[6] if len(cells) > 6 else "")
    only = {}
    for l in part11_2.splitlines():
        m = ROW_ONLY_RE.match(l)
        if m:
            only[m.group(1)] = (m.group(2), m.group(3))
    families = {}
    for l in part11_3.splitlines():
        m = SEC113_KEY_RE.match(l)
        if m:
            families[m.group(1)] = int(m.group(2))
    return rows, only, clauses, families


def _family_members(key, only_book):
    """§11.3 的登记键 → 实际「仅本书有」的条款 ID 集合。

    三种键形态都要认（按语义取数，不按长相取数）：
      ``R-AREA``       族前缀
      ``R-DIR-02~06``  区间子集（族里有一部分已在 §11.1 登记）
      ``R-FRAUD-01``   单个条款
    """
    if _SINGLE_ID_RE.match(key):
        return {key} & only_book
    m = _RANGE_RE.match(key)
    if m:
        fam, lo, hi = m.group(1) + "-", int(m.group(2)), int(m.group(3))
        return {c for c in only_book
                if c.startswith(fam) and c[len(fam):].isdigit()
                and lo <= int(c[len(fam):]) <= hi}
    return {c for c in only_book if c.startswith(key + "-")}


def verdict(asset, rows, only, clauses, families=None, carriers=None,
            exprs=None, facts=None, ports=None):
    """纯函数判定，便于 --self-test 反证。返回错误列表。

    A1–A3 守共有 18 条；A5 是 2026-11-07 补的**全量覆盖率**判据：
    资产 33 条必须全部落在 §11.1 ∪ §11.2 里，否则门禁只守交集、静默丢覆盖。
    A6 守「仅本书有」的 28 条（族级条数不许写错/漏族）；A7 守「条款落点」声明；
    A8 守**表达式变量 ⊆ 事实提供方**（缺一个变量，该执行项就是静默不命中）；
    A9 守**端口落点的实现者与调用方**（无实现者 ⇒ 必须是「端口待接」，不得写成「端口」）。
    """
    errs = []
    if not asset:
        return ["A4 规则资产解析为空（路径或结构已变），fail-closed"]
    if not rows:
        return ["A4 §11.1 对照表解析为空（章节被删或表格格式已改），fail-closed"]
    if not clauses:
        return ["A4 Rulebook 条款 ID 解析为空，fail-closed"]

    common = set(asset) & clauses
    registered = set(rows)
    if common - registered:
        errs.append("A1 共有 ID 未在 §11.1 登记：%s" % sorted(common - registered))
    if registered - common:
        errs.append("A1 §11.1 登记了非共有 ID（或 ID 已从一侧删除）：%s"
                    % sorted(registered - common))
    for rid in sorted(registered & set(asset)):
        conclusion, risk, rel, _ = rows[rid]
        real_c, real_r = asset[rid]
        if conclusion != real_c:
            errs.append("A2 %s conclusion 文档=%s / 资产=%s" % (rid, conclusion, real_c))
        if risk != real_r:
            errs.append("A2 %s risk 文档=%s / 资产=%s" % (rid, risk, real_r))
    conflicts = {rid for rid, (_, _, rel, _) in rows.items() if rel == "同名不同义"}
    if conflicts - CONFLICT_BASELINE:
        errs.append("A3 新增「同名不同义」未登记基线：%s" % sorted(conflicts - CONFLICT_BASELINE))
    if CONFLICT_BASELINE - conflicts:
        errs.append("A3 基线中的冲突项被静默改成「一致」或删除：%s"
                    % sorted(CONFLICT_BASELINE - conflicts))

    covered = registered | set(only)
    if set(asset) - covered:
        errs.append("A5 资产中有 ID 未在 §11.1 / §11.2 登记（门禁覆盖缺口）：%s"
                    % sorted(set(asset) - covered))
    if covered - set(asset):
        errs.append("A5 登记了资产里已不存在的 ID：%s" % sorted(covered - set(asset)))
    if registered & set(only):
        errs.append("A5 同一个 ID 同时登记在 §11.1 与 §11.2：%s"
                    % sorted(registered & set(only)))
    for rid in sorted(set(only) & set(asset)):
        conclusion, risk = only[rid]
        real_c, real_r = asset[rid]
        if conclusion != real_c:
            errs.append("A5 §11.2 %s conclusion 文档=%s / 资产=%s" % (rid, conclusion, real_c))
        if risk != real_r:
            errs.append("A5 §11.2 %s risk 文档=%s / 资产=%s" % (rid, risk, real_r))

    # --- A6 §11.3 族级条数对账：数字必须由条款集合推出来，不许手写维护 ---
    only_book = clauses - set(asset)
    families = families or {}
    if not families and only_book:
        errs.append("A6 §11.3 族级登记表解析为空但仍有 %d 条仅本书有条款，fail-closed"
                    % len(only_book))
    elif families:
        fam_covered = set()
        for key in sorted(families):
            members = _family_members(key, only_book)
            fam_covered |= members
            if len(members) != families[key]:
                errs.append("A6 §11.3 %s 登记 %d 条 / 实际 %d 条 %s"
                            % (key, families[key], len(members), sorted(members)))
        total = sum(families.values())
        if total != len(only_book):
            errs.append("A6 §11.3 条数合计 %d / 实际仅本书有 %d 条（差 %d）"
                        % (total, len(only_book), total - len(only_book)))
        if only_book - fam_covered:
            errs.append("A6 仅本书有的条款未归入任何族：%s" % sorted(only_book - fam_covered))
        if fam_covered - only_book:
            errs.append("A6 §11.3 归入了资产里已存在的 ID：%s" % sorted(fam_covered - only_book))

    # --- A7 条款落点：声明必须可解析、载体必须真实存在、无落点锁基线 ---
    if carriers is None:
        carriers = java_types()
    if not carriers:
        errs.append("A7 源码树未扫到任何类（%s 已变），fail-closed" % JAVA_ROOT)
    no_landing = set()
    for rid in sorted(rows):
        _, _, rel, landing = rows[rid]
        if not landing:
            errs.append("A7 %s 未声明落点（§11.1 第 6 列），fail-closed" % rid)
        elif landing == LANDING_NONE:
            no_landing.add(rid)
        elif landing == LANDING_EXEC:
            if rel != "一致":
                errs.append("A7 %s 关系为「%s」却声称落点=执行项（不同义不得指认同一条执行项）"
                            % (rid, rel))
        elif (landing.startswith(LANDING_PORT)
              or landing.startswith(LANDING_PORT_PENDING)
              or landing.startswith(LANDING_ENGINE)):
            carrier = landing.split(":", 1)[1].strip()
            if carrier not in carriers:
                errs.append("A7 %s 落点声明的载体 %s 在源码树里不存在" % (rid, carrier))
            if rel == "一致":
                errs.append("A7 %s 关系为「一致」却把落点放在 %s（应为执行项）" % (rid, carrier))
        else:
            errs.append("A7 %s 落点取值非法：%s（只允许 %s / %s / %sX / %sX / %sX）"
                        % (rid, landing, LANDING_EXEC, LANDING_NONE,
                           LANDING_PORT, LANDING_PORT_PENDING, LANDING_ENGINE))
    if no_landing - NO_LANDING_BASELINE:
        errs.append("A7 新增「无落点」条款未登记基线：%s"
                    % sorted(no_landing - NO_LANDING_BASELINE))
    if NO_LANDING_BASELINE - no_landing:
        errs.append("A7 基线中的「无落点」条款被静默改成有落点或删除：%s"
                    % sorted(NO_LANDING_BASELINE - no_landing))

    # --- A8 表达式变量 ⊆ 事实提供方：缺变量 = 该执行项静默不命中（比报错更危险）---
    exprs = load_expressions() if exprs is None else exprs
    facts = load_fact_keys() if facts is None else facts
    if not facts:
        errs.append("A8 规则引擎适配器解析不到任何事实变量（路径或写法已变），fail-closed")
    elif not exprs:
        errs.append("A8 资产解析不到任何表达式，fail-closed")
    else:
        missing = {}
        for rid in sorted(exprs):
            if not (exprs[rid] or "").strip():
                errs.append("A8 %s 表达式为空（该执行项永远不会命中）" % rid)
                continue
            for var in sorted(expr_variables(exprs[rid]) - facts):
                missing.setdefault(var, []).append(rid)
        for var in sorted(missing):
            errs.append("A8 变量 %s 没有任何事实提供方，涉及 %s（这些执行项会静默不命中）"
                        % (var, missing[var]))

    # --- A9 端口落点：有实现者才算「端口承载」；无实现者必须显式标「端口待接」---
    # 为什么单列一类：`端口:X` 读起来是"这条在判"，而端口无实现者时实际是"调用即
    # fail-closed ⇒ 一律转人工/退补件"。两者对客户是完全不同的承诺，混在一个值里就是
    # 过度声明。这里按**读取点**取证（谁 implements / 谁引用），不按声明处取证。
    wanted = declared_ports(rows)
    if ports is None:
        ports = port_facts(wanted)
    elif not ports and wanted:
        errs.append("A9 端口取证结果为空（源码树未扫到或路径已变），fail-closed")
    pending, no_consumer = set(), set()
    for rid in sorted(rows):
        _con, _risk, _rel, landing = rows[rid]
        for prefix in (LANDING_PORT, LANDING_PORT_PENDING):
            if not landing.startswith(prefix):
                continue
            port = landing.split(":", 1)[1].strip()
            fact = ports.get(port)
            if fact is None:
                errs.append("A9 %s 的落点端口 %s 未做取证，fail-closed" % (rid, port))
                break
            if prefix == LANDING_PORT and not fact["impl"]:
                errs.append("A9 %s 声明「%s%s」承载，但源码树无任何实现者"
                            "（一期调用即 fail-closed，应改标「%s%s」）"
                            % (rid, LANDING_PORT, port, LANDING_PORT_PENDING, port))
            if prefix == LANDING_PORT_PENDING:
                pending.add(port)
                if fact["impl"]:
                    errs.append("A9 %s 标注「%s%s」，但已有实现者 %s"
                                "（未接线状态已变，须更新声明）"
                                % (rid, LANDING_PORT_PENDING, port,
                                   sorted(fact["impl"])))
            if not fact["consumers"]:
                no_consumer.add(port)
            break
    for port in sorted(no_consumer):
        errs.append("A9 端口 %s 除实现者外没有任何调用方（类存在≠接线）" % port)
    if pending - PORT_PENDING_BASELINE:
        errs.append("A9 新增「端口待接」未登记基线：%s"
                    % sorted(pending - PORT_PENDING_BASELINE))
    if PORT_PENDING_BASELINE - pending:
        errs.append("A9 基线中的「端口待接」已不可见（有人接了线或改了声明，须同步基线）：%s"
                    % sorted(PORT_PENDING_BASELINE - pending))
    return errs


def main():
    if "--self-test" in sys.argv:
        return self_test()
    try:
        asset = load_asset()
        text = io.open(RULEBOOK, encoding="utf-8").read()
    except (OSError, ValueError) as e:
        print("=== 规则 ID 命名空间对账 ===")
        print("【FAIL】读取失败：%s" % e)
        return 2
    rows, only, clauses, families = parse(text)
    exprs, facts = load_expressions(), load_fact_keys()
    ports = port_facts(declared_ports(rows))
    errs = verdict(asset, rows, only, clauses, families, None, exprs, facts, ports)
    landings = {}
    for _, _, _, lg in rows.values():
        landings[lg] = landings.get(lg, 0) + 1
    variables = set()
    for e in exprs.values():
        variables |= expr_variables(e)
    print("=== 规则 ID 命名空间对账 ===")
    print("资产 %d 条 / Rulebook 条款 %d 条 / 共有 %d 条 / §11.1 登记 %d 行 / §11.2 登记 %d 行"
          % (len(asset), len(clauses), len(set(asset) & clauses), len(rows), len(only)))
    print("全量覆盖：%d/%d 条资产已逐条登记" % (len((set(rows) | set(only)) & set(asset)), len(asset)))
    print("仅本书有：%d 条，按 %d 个族登记，合计 %d 条"
          % (len(clauses - set(asset)), len(families), sum(families.values())))
    print("条款落点：" + " / ".join("%s×%d" % (k or "未声明", v)
                                for k, v in sorted(landings.items())))
    print("表达式变量：引用 %d 个 / 事实提供方 %d 个 / 无提供方 %d 个"
          % (len(variables), len(facts), len(variables - facts)))
    for port in sorted(ports):
        fact = ports[port]
        print("端口取证：%s → 实现者 %s / 调用方 %s"
              % (port, sorted(fact["impl"]) or "无", sorted(fact["consumers"]) or "无"))
    if errs:
        for e in errs:
            print("【FAIL】" + e)
        print("共 %d 项失败" % len(errs))
        return 1
    print("【OK】资产全量覆盖 + 共有 ID 双向对账 + conclusion/risk 逐行一致 + "
          "10 条同名不同义基线未漂移 + 族级条数对账 + 落点载体真实存在 + "
          "表达式变量全部有提供方 + 端口实现者/调用方取证一致")
    return 0


def self_test():
    """反证：全绿不等于有判别力，改坏了必须判红。"""
    asset = {
        "R-FRAUD-02": ("NO_EVIDENCE", "True"),
        "R-FRAUD-06": ("VERIFY_NOT_PASSED", "True"),
        "R-AMOUNT-01": ("AMOUNT_OVER_LIMIT", "False"),
    }
    clauses = {"R-FRAUD-02", "R-FRAUD-06", "R-AREA-01"}
    rows = {
        "R-FRAUD-02": ("NO_EVIDENCE", "True", "同名不同义", "无落点"),
        "R-FRAUD-06": ("VERIFY_NOT_PASSED", "True", "一致", "执行项"),
    }
    only = {"R-AMOUNT-01": ("AMOUNT_OVER_LIMIT", "False")}
    fam = {"R-AREA": 1}
    carriers = {"GraphFraudPort", "InvoiceVerificationPort"}
    exprs = {
        "R-FRAUD-02": "hasEvidence == false",
        "R-FRAUD-06": "verifyPassed == false",
        "R-AMOUNT-01": "claimAmount >= 10000",
    }
    facts = {"hasEvidence", "verifyPassed", "claimAmount"}
    # 端口取证样本（纯内存）：一个有实现者、一个无实现者（一期待接入方实现）。
    ports = {
        "InvoiceVerificationPort": {"impl": {"ControlledInvoiceVerificationPortAdapter"},
                                    "consumers": {"RuleCheckNodeHandler"}},
        "ItemCatalogPort": {"impl": set(), "consumers": {"ItemCatalogGateway"}},
    }
    # 构造样本远小于真实规模，三条 ratchet 基线都要临时换成「与样本一致」，测完还原。
    global CONFLICT_BASELINE, NO_LANDING_BASELINE, PORT_PENDING_BASELINE
    saved = (CONFLICT_BASELINE, NO_LANDING_BASELINE, PORT_PENDING_BASELINE)
    CONFLICT_BASELINE = {"R-FRAUD-02"}
    NO_LANDING_BASELINE = {"R-FRAUD-02"}
    PORT_PENDING_BASELINE = set()
    passed, failed = 0, 0

    def V(a=None, r=None, o=None, c=None, f=None, ex=None, fa=None, p=None):
        return verdict(asset if a is None else a,
                       rows if r is None else r,
                       only if o is None else o,
                       clauses if c is None else c,
                       fam if f is None else f, carriers,
                       exprs if ex is None else ex,
                       facts if fa is None else fa,
                       ports if p is None else p)

    def V_with_pending_baseline(base):
        """临时替换「端口待接」基线跑一次，用于反证 ratchet 两个方向。"""
        global PORT_PENDING_BASELINE
        keep = PORT_PENDING_BASELINE
        PORT_PENDING_BASELINE = base
        try:
            return V()
        finally:
            PORT_PENDING_BASELINE = keep

    def expect(label, cond):
        nonlocal passed, failed
        if cond:
            passed += 1
            print("  ✅ " + label)
        else:
            failed += 1
            print("  ❌ " + label)

    try:
        print("=== _check_ruleset_alignment.py --self-test ===")
        expect("正则可解析含数字的 conclusion（OUT_OF_PHASE1_SCOPE）",
               bool(ROW_ONLY_RE.match(
                   "| `R-SCOPE-01` | `SCOPE` | `OUT_OF_PHASE1_SCOPE` | `True` | `x == false` |")))
        expect("§11.3 区间键 R-DIR-02~06 只认区间内的条款",
               _family_members("R-DIR-02~06",
                               {"R-DIR-01", "R-DIR-02", "R-DIR-06", "R-DIR-07"})
               == {"R-DIR-02", "R-DIR-06"})
        expect("§11.3 单条键 R-FRAUD-01 只认该条",
               _family_members("R-FRAUD-01", {"R-FRAUD-01", "R-FRAUD-02"})
               == {"R-FRAUD-01"})
        expect("合规正例（含 §11.2 仅资产登记 + 落点声明）", V() == [])
        expect("变异① 删掉 §11.1 一行 → A1 判红",
               any("A1" in e for e in V(r={"R-FRAUD-02": rows["R-FRAUD-02"]})))
        expect("变异② 改了资产 conclusion 没改文档 → A2 判红",
               any("A2" in e for e in V(a=dict(asset, **{"R-FRAUD-02": ("CHANGED", "True")}))))
        expect("变异③ 把同名不同义改成一致 → A3 判红",
               any("A3" in e for e in V(r=dict(
                   rows, **{"R-FRAUD-02": ("NO_EVIDENCE", "True", "一致", "执行项")}))))
        expect("变异④ 新增一个共有 ID 未登记 → A1 判红",
               any("A1" in e for e in V(
                   a=dict(asset, **{"R-FRAUD-99": ("X", "False")}),
                   c=clauses | {"R-FRAUD-99"})))
        expect("变异⑤ 表格整段被删 → fail-closed",
               any("A4" in e for e in V(r={})))
        expect("变异⑥ 资产读不出来 → fail-closed",
               any("A4" in e for e in V(a={})))
        expect("变异⑦ §11.2 删掉一条登记 → A5 判红（覆盖率缺口）",
               any("A5" in e for e in V(o={})))
        expect("变异⑧ §11.2 的 conclusion 与资产不符 → A5 判红",
               any("A5" in e for e in V(o={"R-AMOUNT-01": ("CHANGED", "False")})))
        expect("变异⑨ 资产新增一条未登记 → A5 判红",
               any("A5" in e for e in V(a=dict(asset, **{"R-NEW-01": ("X", "False")}))))
        expect("变异⑩ 同一 ID 同时登记在两节 → A5 判红",
               any("A5" in e for e in V(o=dict(only, **{"R-FRAUD-06": ("X", "False")}))))
        expect("变异⑪ §11.3 族级条数写错 → A6 判红",
               any("A6" in e for e in V(f={"R-AREA": 2})))
        expect("变异⑫ §11.3 整表被删但仍有仅本书有条款 → A6 fail-closed",
               any("A6" in e for e in V(f={})))
        expect("变异⑬ 落点声明了不存在的载体 → A7 判红",
               any("A7" in e for e in V(r=dict(
                   rows, **{"R-FRAUD-06": ("VERIFY_NOT_PASSED", "True", "一致", "端口:NoSuchPort")}))))
        expect("变异⑭ 新增「无落点」未登记基线 → A7 判红",
               any("A7" in e for e in V(r=dict(
                   rows, **{"R-FRAUD-06": ("VERIFY_NOT_PASSED", "True", "一致", "无落点")}))))
        expect("变异⑮ 落点列缺失 → A7 判红（不静默放过）",
               any("A7" in e for e in V(r=dict(
                   rows, **{"R-FRAUD-02": ("NO_EVIDENCE", "True", "同名不同义", "")}))))
        expect("变异⑯ 同名不同义却声称落点=执行项 → A7 判红",
               any("A7" in e for e in V(r=dict(
                   rows, **{"R-FRAUD-02": ("NO_EVIDENCE", "True", "同名不同义", "执行项")}))))
        expect("字符串字面量不被当成变量（sourceChannel == \"BATCH\"）",
               expr_variables('sourceChannel == "BATCH" || x == "UNKNOWN"')
               == {"sourceChannel", "x"})
        expect("变异⑰ 表达式引用无提供方变量 → A8 判红",
               any("A8" in e for e in V(ex=dict(
                   exprs, **{"R-FRAUD-06": "hospitalLevel > 1"}))))
        expect("变异⑱ 事实提供方解析为空 → A8 fail-closed",
               any("A8" in e for e in V(fa=set())))
        expect("变异⑲ 表达式为空 → A8 判红（永远不会命中）",
               any("A8" in e for e in V(ex=dict(exprs, **{"R-AMOUNT-01": "  "}))))
        # A9 端口落点：三条反证各锁一个方向（无实现者 / 有实现者却标待接 / 无调用方）
        row_pending = dict(rows, **{"R-FRAUD-06": ("VERIFY_NOT_PASSED", "True",
                                                   "同名不同义", "端口待接:ItemCatalogPort")})
        expect("变异⑳ 声明「端口:X」但源码树无实现者 → A9 判红（应改标端口待接）",
               any("A9" in e for e in V(r=dict(
                   rows, **{"R-FRAUD-06": ("VERIFY_NOT_PASSED", "True",
                                           "同名不同义", "端口:ItemCatalogPort")}))))
        expect("变异㉑ 标「端口待接:X」但已有实现者 → A9 判红（未接线状态已变）",
               any("A9" in e for e in V(r=dict(
                   rows, **{"R-FRAUD-06": ("VERIFY_NOT_PASSED", "True",
                                           "同名不同义", "端口待接:InvoiceVerificationPort")}))))
        expect("变异㉒ 端口除实现者外无调用方 → A9 判红（类存在≠接线）",
               any("A9" in e for e in V(r=row_pending, p=dict(
                   ports, **{"ItemCatalogPort": {"impl": set(), "consumers": set()}}))))
        expect("变异㉓ 端口取证结果为空 → A9 fail-closed",
               any("A9" in e for e in V(r=row_pending, p={})))
        expect("变异㉔ 基线中的「端口待接」不可见 → A9 判红（接线后必须同步基线）",
               any("A9" in e for e in V_with_pending_baseline({"ItemCatalogPort"})))
        expect("变异㉕ 新增「端口待接」未登记基线 → A9 判红",
               any("A9" in e for e in V(r=row_pending)))
    finally:
        CONFLICT_BASELINE, NO_LANDING_BASELINE, PORT_PENDING_BASELINE = saved
    print("  %d 通过 / %d 失败" % (passed, failed))
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
