#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""采购域 AI 评测集校验器（设计态 seed 草稿；已接 CI blocking 与本地 pre-commit）。

复刻 synap-claim check-claim-ai-evalset.py 的核心校验维度，并按 2026-11-10 复诊补齐：
  A1  schemaVersion / evalsetId 存在且类型正确
  A2  suites 数组非空、id 唯一
  A3  cases 数组非空、id 唯一
  A4  每个 case 必填字段齐全（含 capabilityCode）
  A5  每个 case.suite 必须命中 suites[].id
  A6  每个 case.piiFree 必须为 true（PII 禁入）
  A7  每个 case.groundTruthRef 非空且形如 path#anchor
  A8  负向用例占比须达到阈值（关键负向不可删，防假绿）
  A9  status 必须为 seededDraft 且 needsOwnerReview=true（防误当发布门禁终态）
  A10 capabilityCode 登记与硬编码防误删下界一致（脚本内独立锚点）
  A11 G15 算法公平偏见用例下界与关键 ID 不可删
  A12 关键负向用例 ID 集合不可删 / 不可改成正向
  A13 ≥200 样本规模路线 + **法规依据结构化**：每条 case.regulatoryBasis 为
      {type, source, clauseRef, truthRef}；type 区分 externalRegulation /
      internalContract，法源名须命中 catalog.sourceAliases，truthRef 须可解析；
      金融监管 §xx 编号残留独立锚点归零（第五轮：22 条债务二分收敛 + 防回流）；
      第六轮再加「外部原文真源」：解析 D3-v2 第 3.3 节核对表，13 条
      externalRegulation 的 basisVerification 须逐字一致 / URL / 日期 / 适用性对齐，
      internalContract 不得携带，ledger 不得伪造 fullyVerified；
      第七轮加「法源适用射程」：四类受控枚举、excludedByScope 条款不得作主依据、
      ledger.scopeCounts 与核对表实测四分类计数守恒
  A14 能力码**跨文档真源**比对：解析 D3-v2 §1.1 表第一列 → 与
      capabilityCodes / cases 三方比对（2026-11-10 第二轮：消除「脚本内常量自证」）
  A15 scalePlan 结构化：阶段 / 目标样本 / 标注规范 Kappa / 通过线占位 /
      交接条件 / 不得伪造 completed（2026-11-10 第二轮：消除「文本 token 判据」）
  A16 groundTruthRef **锚点可达**：path#anchor 必须解析到真源 Markdown 标题 slug
      或 JSON 字段路径（第三轮：消除「引用写了就算可追溯」的悬空引用）；
      第五轮再加「不得指向评测集自身字段」闭环自证守护；
      **第八轮扩展 clauseRef 章节解析守卫**：自由文本 clauseRef 中的「DocName §X.Y」
      须解析到真实文档的编号章节标题，悬空 / 指错即 fail-closed（逻辑自洽缺口）；
      第九轮补「第 X 节」写法与链式「+ §X」前缀继承；
      **第十一轮补「裸章节引用」兜底**：free text 中任何 §X.Y 左侧无已登记 DocName
      一律转红，否则删掉短路名即可让整个存在性守卫 fail-open
  A17 每能力码 ≥1 正向对照 + ≥1 负向用例，testSymbol 唯一；
      **所有正向用例**必须断言 tier=ADVISORY_ONLY + humanFinalDecision=true
      且不得出现自动决策结果码（第四轮：防从理赔域复制的 AUTO_APPROVE 反向放行断言）

重要边界（诚实标注，勿过度声称）：
  - 本脚本校验的是**评测集结构与治理骨架**，不是模型效果；模型效果评测仍待 owner
    按 DA-3 补齐 ≥200 样本 + 对抗样本 + 偏见审计集并给出通过线。
  - groundTruthRef 在设计态指向文档真源锚点，代码落地后须重定向到源码契约；
    A16 只守「锚点解析得到」，**不守期望值是否与真源语义一致**。第四轮已做 25 条
    逐条人工语义复核并修正 P0 反向放行断言，A17 把「正向用例不得越红线」固化为
    机器判据；第五轮把法规依据升级为结构化类型 + 可解析锚点；第六轮按公开一手
    原文核对 12 条条款并固化为 D3-v2 第 3.3 节核对表；第七轮按公开原文的适用对象 /
    发文抬头 / 总体目标再校准射程，扩为 15 行（含 2 行《暂行办法》明文排除证据、
    1 行《指南》伦理审查原文），A13 只守**主依据逐字精确性、射程判定与引用方向**。
    **主体适用性与场景射程的法务结论 / 辅助依据 / 25 条 clauseRef 与内部断言的
    逐条语义对应仍属 owner 项**（见 basisRebase.residual），语义一致性与法规强制
    适用性本身不能由门禁自证。
  - status=seededDraft / needsOwnerReview=true 是**刻意保留**的：CI 绿 ≠ 可上线。
  - A14 的真源是 D3-v2 §1.1 表格，不是本脚本常量；常量只作**独立防误删下界**，
    避免「期望值也来自被校验对象」的闭环自证。
  - **第八轮起 clauseRef 章节可达性已由 A16 机器守护**（DocName §X.Y 须命中真实章节），
    第九轮覆盖「第 X 节」与链式前缀继承、第十一轮覆盖裸章节引用兜底；
    但「该章节内容是否真正支持对应断言」的**语义对应**仍属 owner 项，不能由门禁自证；
    文档短路名映射与章节号索引见脚本顶部 CLAUSE_REF_DOC_MAP / SECTION_HEADING_RE，
    引用的章节号必须带已登记 DocName 前缀，未登记别名与裸引用均按 fail-closed 转红。
  - 退出码：校验通过=0；结构违规=1；--self-test 反证未全转红=1。
"""
from __future__ import annotations  # py3.9 兼容：允许 X | Y 联合注解（仓库 Python=3.9.6）

import argparse
import copy
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_EVALSET = os.path.normpath(os.path.join(HERE, "..", "eval", "ai-decision-evalset.json"))
DEFAULT_D3V2 = os.path.normpath(os.path.join(HERE, "..", "采购AI-接口契约与数据字典-v2.md"))
# eval/ → procuraAI/ → ai-product/ → doc/ → 仓库根
REPO_ROOT = os.path.normpath(os.path.join(HERE, "..", "..", "..", ".."))

# 设计态种子下界（owner 按 DA-3 补全至 PRD ≥200 后由发布门禁接管）
SEED_MIN_CASES = 25
SCALE_TARGET = 200
# 关键负向占比下限（防假绿：负向用例不可低于此比例）
MIN_NEGATIVE_RATIO = 0.5
REQUIRED_CASE_FIELDS = [
    "id", "name", "suite", "kind", "expected",
    "groundTruthRef", "regulatoryBasis", "testClass", "testSymbol",
    "piiFree", "capabilityCode",
]
# 独立防误删下界：硬编码在脚本内，不从评测集、也不从 D3-v2 推导。
# A14 另解析 D3-v2 §1.1 真源表，两者互为外部锚点，任一漂移都转红。
CAPABILITY_CODES = frozenset({
    "M1", "M2", "M3", "M4", "M5", "M6", "M7", "ORCHESTRATION",
})
# D3-v2 §1.1「AI 能力码 ↔ 采购模块码」表的第一列（唯一结构化真源）
CAP_TRUTH_ROW_RE = re.compile(r"^\|\s*(M\d+|ORCHESTRATION)\s*\|", re.M)

# 金融监管条款号残留的独立锚点（不从被校验对象推导）。
# 第四轮为 22（债务只减不增）；第五轮完成「外部法规义务 / 内部 AI 契约断言」
# 二分收敛后归零：任何新增用例再引入「监管 §xx」银行业保险业专用编号都立即转红。
# 归零不等于法规原文核对完成，见 basisRebase.residual 与 A13 的 residual 守护。
LEGACY_BASIS_MAX = 0
# 第六轮 A13「外部法规原文核对表」：真源在 D3-v2 第 3.3 节（外部文档），
# 判据常量只作独立防误删下界，不从被校验对象推导，避免闭环自证。
BASIS_SECTION = "### 3.3 外部法规原文核对表"
BASIS_ROW_RE = re.compile(r"^\|\s*(B-\d+)\s*\|", re.M)
MIN_BASIS_CLAUSES = 15
MIN_BASIS_SOURCES = 4
MIN_NOTE_CHARS = 30
SCOPE_CLASSES = ("inScope", "conditionalBiddingScope",
                 "voluntaryAdoption", "excludedByScope")
EXCLUDED_SCOPE = "excludedByScope"
VERIFICATION_ENUM = frozenset({"directQuote", "principleExtension"})
APPLICABILITY_ENUM = frozenset(SCOPE_CLASSES) | {"pendingLegalReview"}
BV_REQUIRED_FIELDS = ("clauseId", "quote", "sourceUrl", "verifiedOn",
                      "verification", "applicability")
FINANCIAL_CLAUSE_NOS = frozenset({"4", "15", "17", "21", "22", "23", "24"})
LEGACY_BASIS_RE = re.compile(r"(?:监管\s*)?§\s*(\d{1,2})(?![\d.])")
# AI 不碰钱 / 人工终决红线：正向对照里不允许出现任何自动决策结果码
AUTO_DECISION_TOKENS = frozenset({
    "AUTO_APPROVE", "AUTO_PASS", "AUTO_FINALIZE", "AUTO_ARCHIVE",
    "AUTO_RELEASE", "AUTO_CLOSED", "AUTO_VERIFIED", "AUTO_SETTLED",
})

# A16 扩展：clauseRef 章节解析守卫（不新增主门禁号段，附属于 A16 锚点可达）。
# clauseRef 是自由文本，过去只校验「非空」，引用章节是否真实存在从未被机器守护，
# 导致指错 / 松散章节能一路绿灯（第八轮「逻辑自洽」核心缺口）。现要求自由文本中的
# 「DocName §X.Y」须解析到真实文档的编号章节标题，悬空 / 指错即 fail-closed。
# 短路名是设计态约定，须显式登记映射；未登记 / 文档不可达一律转红。
CLAUSE_REF_DOC_MAP = {
    "D3-v2": "doc/ai-product/procuraAI/采购AI-接口契约与数据字典-v2.md",
    "总体": "doc/ai-product/procuraAI/采购总体架构方案.md",
    "PRD": "doc/ai-product/procuraAI/采购AI-智能风险审查与合同智能处理-PRD.md",
    "03 卷": "doc/ai-product/procuraAI/detailed-design/03-合同风险绩效与AI模块详细设计.md",
    "一期边界": "doc/ai-product/procuraAI/一期边界与机制触发条件.md",
}
# clauseRef 自由文本解析：短路名位置与章节号位置分别扫描，再按「左侧最近前缀」配对，
# 使链式写法「D3-v2 §3.1 + §3.2」中无前缀的第二段 §3.2 继承 D3-v2，不得绕过存在性守卫。
# 章节号只取首个编号段，自动忽略「E-4 / C-5 / B-9」等规则 id 与「节」字；
# 「第 X 节」写法（如「D3-v2 第 3.3 节 B-2」）同样解析，避免该写法绕过守卫。
CLAUSE_SHORT_NAME_RE = re.compile(r"(D3-v2|总体|PRD|03\s*卷|一期边界)")
CLAUSE_SECTION_NUM_RE = re.compile(r"(?:[§#]\s*|第\s*)(\d+(?:\.\d+)*)\s*(?:节)?")
# 编号章节标题：行首 # 级标题，后跟「第? N[.N]*」章节号（如「### 3.1 人工最终决策」）。
SECTION_HEADING_RE = re.compile(
    r"^#{1,6}\s+(?:第\s*)?(\d+(?:\.\d+)*)\s*[\.\、:：]?\s*.+$")
CLAUSE_REF_INDEX: dict[str, set[str] | None] = {}  # path -> 章节号集合；None=不可达


def clause_ref_sections(path: str) -> set[str] | None:
    """解析 clauseRef 文档的全部编号章节标题；不可达返回 None（fail-closed）。"""
    if path in CLAUSE_REF_INDEX:
        return CLAUSE_REF_INDEX[path]
    try:
        with open(os.path.join(REPO_ROOT, path), encoding="utf-8") as f:
            body = f.read()
    except Exception:  # noqa: BLE001 - 真源不可达必须 fail-closed
        CLAUSE_REF_INDEX[path] = None
        return None
    secs: set[str] = set()
    for ln in body.splitlines():
        m = SECTION_HEADING_RE.match(ln)
        if m:
            secs.add(m.group(1))
    CLAUSE_REF_INDEX[path] = secs
    return secs


def parse_clause_refs(cref: str) -> list[tuple[str, str]]:
    """解析自由文本 clauseRef 中的（文档短路名, 章节号）引用列表。

    支持链式前缀继承：「D3-v2 §3.1 + §3.2」里第二段 §3.2 没有短路名前缀时，
    继承其左侧最近的短路名（D3-v2），避免「+ §X」写法绕过章节存在性守卫。
    无前序前缀的裸章节引用不在此返回（无法定位文档），由 bare_clause_sections()
    单独兜底转红——否则删掉短路名即可让整个存在性守卫 fail-open（第十一轮）。
    """
    shorts = [(m.start(), m.group(1)) for m in CLAUSE_SHORT_NAME_RE.finditer(cref)]
    out: list[tuple[str, str]] = []
    for m in CLAUSE_SECTION_NUM_RE.finditer(cref):
        pos = m.start()
        prefix = None
        for s_start, s_name in shorts:
            if s_start <= pos:
                prefix = s_name
            else:
                break
        if prefix is not None:
            out.append((prefix, m.group(1)))
    return out


def bare_clause_sections(cref: str) -> list[str]:
    """返回「左侧无已登记短路名」的裸章节号，这类引用无法确定归属文档。

    fail-closed 兜底：parse_clause_refs 出于历史兼容会忽略裸引用，若不拦截，
    只要把 clauseRef 里的「D3-v2 」等短路名删掉或改成未登记别名，悬空的
    「§99.9」就会一路绿灯（第十一轮真实文件注入实证的假绿）。
    """
    shorts = [m.start() for m in CLAUSE_SHORT_NAME_RE.finditer(cref)]
    return [m.group(1) for m in CLAUSE_SECTION_NUM_RE.finditer(cref)
            if not any(s <= m.start() for s in shorts)]


def has_legacy_basis(value: object) -> bool:
    """regulatoryBasis 是否仍挂金融监管条款号（D3-v2 §3 命名条款 / §1.1 等不算）。"""
    return any(g in FINANCIAL_CLAUSE_NOS
               for g in LEGACY_BASIS_RE.findall(str(value)))
FAIRNESS_MIN_CASES = 2
CRITICAL_FAIR_IDS = frozenset({"FAIR-01", "FAIR-02"})
CRITICAL_NEGATIVE_IDS = frozenset({
    "CONTRACT-02", "MATCH-02", "RISK-01", "ANOMALY-02", "DEGRADE-01",
    "DISCLOSE-01", "DISCLOSE-02", "FAIR-01", "FAIR-02", "LABEL-01",
    "PROMPT-INJ-01",
})
MIN_SCALE_PHASES = 3


def capability_truth_rows(d3_text: str) -> list[str]:
    """保留行序与重复：主键唯一性校验必须能看到重复行（set 会吞掉）。"""
    if "### 1.1" not in d3_text or "### 1.2" not in d3_text:
        return []
    sec = d3_text.split("### 1.1", 1)[1].split("### 1.2", 1)[0]
    return CAP_TRUTH_ROW_RE.findall(sec)


def capability_truth(d3_text: str) -> set[str]:
    """从 D3-v2 §1.1 表格解析能力码真源；解析不到返回空集（调用方 fail-closed）。"""
    return set(capability_truth_rows(d3_text))


def basis_truth(d3_text: str) -> tuple[dict[str, dict], list[str]]:
    """解析 D3-v2 第 3.3 节外部法规原文核对表（A13 的外部真源）。

    返回 {clauseId: {quote, sourceUrl, verifiedOn, applicability}} 与结构问题列表；
    章节不可达 / 行数不足 / 列数错 / 主键重复 / 摘录或 URL 为空一律 fail-closed。
    """
    out: dict[str, dict] = {}
    problems: list[str] = []
    if BASIS_SECTION not in d3_text:
        return out, ["A13 依据核验：D3-v2 第 3.3 节核对表章节不可达（外部原文真源缺失）"]
    sec = d3_text.split(BASIS_SECTION, 1)[1].split("## 4.", 1)[0]
    rows = [ln for ln in sec.splitlines() if BASIS_ROW_RE.match(ln)]
    if len(rows) < MIN_BASIS_CLAUSES:
        problems.append(f"A13 依据核验：核对表 B 行 {len(rows)} < 独立下界 "
                        f"{MIN_BASIS_CLAUSES}（条款行不可删）")
    seen: list[str] = []
    for ln in rows:
        cols = [c.strip() for c in ln.strip().strip("|").split("|")]
        rid = cols[0] if cols else "?"
        if len(cols) != 7:
            problems.append(f"A13 依据核验：核对表 {rid} 列数 {len(cols)} != 7")
            continue
        _rid, _src, quote, url, date, app, _maps = cols
        quote, url = quote.strip("「」"), url.strip("`")
        if rid in seen:
            problems.append(f"A13 依据核验：核对表主键 {rid} 重复")
        seen.append(rid)
        if (not quote or not url.startswith(("http://", "https://")) or not date
                or app not in APPLICABILITY_ENUM):
            problems.append(f"A13 依据核验：核对表 {rid} 摘录 / URL / 日期 / 适用性非法")
        out[rid] = {"quote": quote, "sourceUrl": url,
                    "verifiedOn": date, "applicability": app}
    return out, problems


def doc_anchor_slug(heading: str) -> str:
    """GitHub 风格锚点归一：小写、去标点、空白转连字符（CJK 保留）。"""
    x = re.sub(r"[^\w\u4e00-\u9fff\- ]", "", heading.strip().lower())
    return re.sub(r"\s+", "-", x.strip())


def load_ref_docs(data: dict) -> dict[str, str]:
    """把 groundTruthRef 与 regulatoryBasis.truthRef 引用到的真源读入内存。

    缺文件记空串由 A13 / A16 fail-closed 判红。两类引用共用同一解析器，
    避免「行为真源可达、法源真源悬空」的半绿。
    """
    out: dict[str, str] = {}
    cases = data.get("cases") if isinstance(data.get("cases"), list) else []
    for c in cases:
        refs = [c.get("groundTruthRef", "")]
        rb = c.get("regulatoryBasis")
        if isinstance(rb, dict):
            refs.append(rb.get("truthRef", ""))
        for ref in refs:
            if not isinstance(ref, str) or "#" not in ref:
                continue
            path = ref.partition("#")[0]
            if path in out:
                continue
            try:
                with open(os.path.join(REPO_ROOT, path), "r", encoding="utf-8") as f:
                    out[path] = f.read()
            except Exception:  # noqa: BLE001 - 真源不可达交 A13 / A16 判红
                out[path] = ""
    return out


def resolve_ref(ref: str, docs: dict[str, str]) -> bool:
    """path#anchor 可达：.md 解析标题 slug；.json 解析点分字段路径。"""
    path, _, anchor = ref.partition("#")
    body = docs.get(path)
    if not isinstance(body, str) or not body or not anchor:
        return False
    if path.endswith(".json"):
        try:
            cur = json.loads(body)
        except Exception:  # noqa: BLE001
            return False
        for seg in anchor.split("."):
            if not isinstance(cur, dict) or seg not in cur:
                return False
            cur = cur[seg]
        return True
    heads = re.findall(r"^#{1,6}\s+(.+?)\s*$", body, re.M)
    return doc_anchor_slug(anchor) in {doc_anchor_slug(h) for h in heads}


def validate(data: dict, d3_text: str = "", docs: dict[str, str] | None = None
             ) -> tuple[bool, list[str]]:
    """纯函数：只吃 dict + 真源文本 + 引用语料；不读写磁盘，便于注入反证。
    docs=None 时 A16 fail-closed（真源不可达不得静默跳过）。
    """
    msgs: list[str] = []

    # A1
    if not isinstance(data.get("schemaVersion"), int):
        msgs.append("A1 schemaVersion 缺失或非 int")
    if not isinstance(data.get("evalsetId"), str) or not data.get("evalsetId"):
        msgs.append("A1 evalsetId 缺失或非字符串")

    # A2
    suites = data.get("suites")
    suite_ids: list[str] = []
    if not isinstance(suites, list) or not suites:
        msgs.append("A2 suites 缺失或非空数组")
    else:
        suite_ids = [s.get("id") for s in suites]
        if len(suite_ids) != len(set(suite_ids)):
            msgs.append("A2 suites id 重复")
        for s in suites:
            if not isinstance(s.get("id"), str) or not s.get("id"):
                msgs.append("A2 存在 suite 缺 id")

    # A3
    cases = data.get("cases")
    if not isinstance(cases, list) or not cases:
        msgs.append("A3 cases 缺失或非空数组")
        cases = []
    case_ids = [c.get("id") for c in cases]
    if len(case_ids) != len(set(case_ids)):
        msgs.append("A3 case id 重复")

    # A4 + A5 + A6 + A7 + A10(逐例)
    suite_id_set = set(suite_ids)
    n_neg = 0
    by_id = {c.get("id"): c for c in cases}
    used_caps: set[str] = set()
    for c in cases:
        cid = c.get("id", "?")
        for field in REQUIRED_CASE_FIELDS:
            if field not in c:
                msgs.append(f"A4 case {cid} 缺字段 {field}")
        if c.get("suite") not in suite_id_set:
            msgs.append(f"A5 case {cid} 的 suite={c.get('suite')} 未命中 suites")
        if c.get("piiFree") is not True:
            msgs.append(f"A6 case {cid} piiFree 不为 true（PII 禁入）")
        gtr = c.get("groundTruthRef", "")
        if not isinstance(gtr, str) or "#" not in gtr:
            msgs.append(f"A7 case {cid} groundTruthRef 为空或缺少 '#' 锚点")
        if c.get("kind") == "negative":
            n_neg += 1
        cap = c.get("capabilityCode")
        if cap is not None:
            if cap not in CAPABILITY_CODES:
                msgs.append(f"A10 case {cid} capabilityCode={cap!r} 未登记在独立防误删锚点")
            else:
                used_caps.add(cap)

    # A8
    if cases:
        ratio = n_neg / len(cases)
        if ratio < MIN_NEGATIVE_RATIO:
            msgs.append(f"A8 负向占比 {ratio:.2f} 低于下限 {MIN_NEGATIVE_RATIO}")

    # 种子下界（输出取常量，不写死成功数字）
    if len(cases) < SEED_MIN_CASES:
        msgs.append(f"种子用例数 {len(cases)} 低于设计态下界 {SEED_MIN_CASES}")

    # A9
    if data.get("status") != "seededDraft":
        msgs.append("A9 status 必须为 seededDraft（CI 绿 ≠ 发布门禁终态）")
    if data.get("needsOwnerReview") is not True:
        msgs.append("A9 needsOwnerReview 必须为 true")

    # A10 能力码登记 ↔ 独立防误删下界
    declared = set(data.get("capabilityCodes", {}))
    if not declared:
        msgs.append("A10 评测集缺 capabilityCodes 登记（能力码↔用例无法双向追溯）")
    elif declared != CAPABILITY_CODES:
        msgs.append(f"A10 capabilityCodes 登记 {sorted(declared)} != 独立锚点 "
                    f"{sorted(CAPABILITY_CODES)}")
    missing_caps = CAPABILITY_CODES - used_caps
    if missing_caps:
        msgs.append(f"A10 已登记能力码无任何评测用例覆盖：{sorted(missing_caps)}（悬空登记）")

    # A16 groundTruthRef 锚点可达（引用必须解析到真源标题 / JSON 字段，不能只是写了个 #xxx）
    if docs is None:
        msgs.append("A16 未提供真源引用语料（fail-closed：锚点可达性不得跳过）")
    else:
        bad_refs: list[str] = []
        self_refs: list[str] = []
        for c in cases:
            ref = c.get("groundTruthRef", "")
            if not isinstance(ref, str) or "#" not in ref:
                continue  # A7 已判红
            if ref.partition("#")[0].endswith("eval/ai-decision-evalset.json"):
                self_refs.append(str(c.get("id", "?")))
            if not resolve_ref(ref, docs):
                bad_refs.append(f"{c.get('id', '?')}:{ref.partition('#')[2][:48]}")
        if bad_refs:
            msgs.append("A16 groundTruthRef 锚点悬空（" + "; ".join(bad_refs[:4]) +
                        "；path#anchor 须解析到真源标题 slug / JSON 字段）")
        if self_refs:
            msgs.append("A16 groundTruthRef 指向评测集自身字段（闭环自证）：" +
                        ", ".join(self_refs) +
                        "；期望值不得来自被校验对象，须挂 D3-v2 第 3.2 节等外部真源")

        # A16 扩展：clauseRef 章节解析守卫（自由文本中的 DocName §X.Y 须真实存在）
        # 与 groundTruthRef 共用「锚点可达」语义：引用写了 ≠ 可追溯。悬空 / 指错转红。
        # 链式写法「Doc §X + §Y」中第二段 §Y 继承左侧最近前缀（parse_clause_refs），
        # 不得因无前缀短路名而静默跳过。
        clause_problems: list[str] = []
        for c in cases:
            rb = c.get("regulatoryBasis")
            if not isinstance(rb, dict):
                continue
            cref = rb.get("clauseRef")
            if not isinstance(cref, str) or not cref.strip():
                continue
            bare = bare_clause_sections(cref)
            if bare:
                clause_problems.append(
                    f"{c.get('id', '?')}:clauseRef 裸章节引用 " +
                    ", ".join("§" + n for n in bare) +
                    " 缺少 DocName 前缀（章节号无法确定归属文档，不得静默跳过）")
            for short, num in parse_clause_refs(cref):
                key = short.replace(" ", "")
                path = CLAUSE_REF_DOC_MAP.get(short) or CLAUSE_REF_DOC_MAP.get(key)
                if not path:
                    clause_problems.append(f"{c.get('id', '?')}:clauseRef 文档别名 "
                                           f"{short!r} 未登记映射")
                    continue
                secs = clause_ref_sections(path)
                if secs is None:
                    clause_problems.append(f"{c.get('id', '?')}:clauseRef 文档 {short} 不可达")
                elif num not in secs:
                    clause_problems.append(f"{c.get('id', '?')}:clauseRef {short} §{num} 章节不存在")
        if clause_problems:
            msgs.append("A16 clauseRef 章节悬空（" + "; ".join(clause_problems[:4]) +
                        "；DocName §X.Y 须解析到真实章节标题）")

    # A17 正向对照 + 负向用例双向覆盖（防「恒拒绝」退化实现拿高分）+ testSymbol 唯一
    cap_kinds: dict[str, set[str]] = {}
    symbols: list[str] = []
    for c in cases:
        cap = c.get("capabilityCode")
        if cap in CAPABILITY_CODES:
            cap_kinds.setdefault(cap, set()).add(c.get("kind"))
        symbols.append(str(c.get("testSymbol", "")))
    for cap in sorted(CAPABILITY_CODES):
        kinds = cap_kinds.get(cap, set())
        if "positive" not in kinds or "negative" not in kinds:
            msgs.append(f"A17 能力码 {cap} 缺正向对照 / 负向用例（kinds={sorted(kinds)}；"
                        "只测 fail-closed 无法识别恒拒绝实现）")
    if len(symbols) != len(set(symbols)):
        msgs.append("A17 testSymbol 重复（用例不得复制粘贴同一测试符号充数）")
    # 第四轮：正向对照不仅要有，还必须断言「AI 只出建议 + 人工终决」。
    # 实证来源：CONTRACT-01 从 synap-claim 理赔域复制，断言 AUTO_APPROVE / tier=AUTO，
    # 与 D3-v2 §3「自动结案：禁止」直接对立，却因 A17 只查 kind 分布而一路绿灯。
    for c in cases:
        if c.get("kind") != "positive":
            continue
        cid = c.get("id", "?")
        exp = c.get("expected")
        if not isinstance(exp, dict):
            exp = {}
        if exp.get("tier") != "ADVISORY_ONLY":
            msgs.append(f"A17 正向用例 {cid} 缺 tier=ADVISORY_ONLY（正向对照不得断言自动决策档位）")
        if exp.get("humanFinalDecision") is not True:
            msgs.append(f"A17 正向用例 {cid} 缺 humanFinalDecision=true（AI 不碰钱 / 人工终决红线）")
        blob = " ".join(str(exp.get(k, "")) for k in ("result", "reason", "status", "tier")).upper()
        for tok in sorted(AUTO_DECISION_TOKENS):
            if tok in blob:
                msgs.append(f"A17 正向用例 {cid} 出现自动决策结果码 {tok}"
                            "（业务裁决权不得交给 AI；见 D3-v2 §3 决策权 / 自动结案）")

    # A14 跨文档真源：D3-v2 §1.1 表 ↔ capabilityCodes ↔ cases 三方比对
    rows = capability_truth_rows(d3_text)
    dupes = sorted({x for x in rows if rows.count(x) > 1})
    if dupes:
        msgs.append(f"A14 D3-v2 §1.1 真源表出现重复能力码行 {dupes}（主键唯一性）")
    truth = capability_truth(d3_text)
    if not truth:
        msgs.append("A14 无法从 D3-v2 §1.1 解析能力码真源表（缺文件 / 表被删 / 章节锚点漂移）")
    else:
        if truth != CAPABILITY_CODES:
            msgs.append(f"A14 D3-v2 §1.1 真源表 {sorted(truth)} != 独立锚点 "
                        f"{sorted(CAPABILITY_CODES)}（真源增删码未同步评测集与外部锚点）")
        if declared and declared != truth:
            msgs.append(f"A14 capabilityCodes {sorted(declared)} != D3-v2 §1.1 真源表 "
                        f"{sorted(truth)}（登记与真源漂移）")
        uncovered = truth - used_caps
        if uncovered:
            msgs.append(f"A14 D3-v2 §1.1 真源表中的能力码无评测用例覆盖：{sorted(uncovered)}")

    # A11 G15 算法公平偏见
    fair = [c for c in cases if c.get("suite") == "algorithmicFairness"]
    if len(fair) < FAIRNESS_MIN_CASES:
        msgs.append(f"A11 G15 公平性偏见用例 {len(fair)} < {FAIRNESS_MIN_CASES}（零偏见用例回退）")
    for fid in CRITICAL_FAIR_IDS:
        c = by_id.get(fid)
        if not c:
            msgs.append(f"A11 G15 关键偏见用例 {fid} 不得删除")
        elif c.get("kind") != "negative":
            msgs.append(f"A11 G15 关键偏见用例 {fid} 不得改成非负向")

    # A12 关键负向不可删 / 不可转正
    for kid in CRITICAL_NEGATIVE_IDS:
        c = by_id.get(kid)
        if not c:
            msgs.append(f"A12 关键负向用例 {kid} 不得删除")
        elif c.get("kind") != "negative":
            msgs.append(f"A12 关键负向用例 {kid} 不得改成非负向")

    # A13 规模路线 + 法规依据结构化（第五轮：不再是「债务计数 + 文本 token」）
    corpus = " ".join(str(data.get(k, "")) for k in ("scope", "seedNote"))
    if f"≥{SCALE_TARGET}" not in corpus:
        msgs.append(f"A13 缺 ≥{SCALE_TARGET} 样本规模路线登记（seed 不得被当作终态）")
    reg = data.get("regulatoryBasisCatalog", {})
    if not isinstance(reg, dict) or not reg.get("sources"):
        msgs.append("A13 缺 regulatoryBasisCatalog（上位合规依据不可达 / 行业适用性未重挂）")
    else:
        types = reg.get("basisTypes")
        aliases = reg.get("sourceAliases", {})
        na = reg.get("internalContractSource")
        if not (isinstance(types, list)
                and set(types) == {"externalRegulation", "internalContract"}):
            msgs.append("A13 缺 basisTypes 枚举（externalRegulation / internalContract，"
                        "依据类型不可自由文本化）")
        if not (isinstance(aliases, dict) and aliases):
            msgs.append("A13 缺 sourceAliases（外部法源名必须命中采购域公开文件清单，"
                        "不得臆造法规名 / 条款号）")
        if not (isinstance(na, str) and na.startswith("N/A")):
            msgs.append("A13 缺 internalContractSource（内部契约断言不得冒充外部法规义务）")

        legacy_ids = sorted(str(c.get("id", "?")) for c in cases
                            if has_legacy_basis(str(c.get("regulatoryBasis", ""))))
        if len(legacy_ids) > LEGACY_BASIS_MAX:
            msgs.append(f"A13 金融监管 §xx 编号回流：{len(legacy_ids)} > 独立锚点 "
                        f"{LEGACY_BASIS_MAX}（债务已归零，不得再引入银行业保险业专用条款号）")

        ext_ids: list[str] = []
        int_ids: list[str] = []
        basis_bad: list[str] = []
        for c in cases:
            cid = str(c.get("id", "?"))
            cb = c.get("regulatoryBasis")
            if not isinstance(cb, dict):
                basis_bad.append(f"{cid} regulatoryBasis 退化成非结构化文本"
                                 "（须 {type, source, clauseRef, truthRef}）")
                continue
            if not all(k in cb for k in ("type", "source", "clauseRef", "truthRef")):
                basis_bad.append(f"{cid} regulatoryBasis 缺必需字段")
                continue
            btype, bsrc, bref = cb.get("type"), str(cb.get("source", "")), str(cb.get("truthRef", ""))
            if btype == "externalRegulation":
                ext_ids.append(cid)
                if not any(a in bsrc for a in aliases):
                    basis_bad.append(f"{cid} externalRegulation.source 未命中 "
                                     "catalog.sourceAliases（不得臆造法源名）")
            elif btype == "internalContract":
                int_ids.append(cid)
                if bsrc != na:
                    basis_bad.append(f"{cid} internalContract.source 须为 "
                                     "catalog.internalContractSource（不得冒充外部法规）")
            else:
                basis_bad.append(f"{cid} regulatoryBasis.type 非法：{btype!r}")
                continue
            if not cb.get("clauseRef"):
                basis_bad.append(f"{cid} 缺 clauseRef（法源 / 契约落点不可空）")
            if not resolve_ref(bref, docs or {}):
                basis_bad.append(f"{cid} regulatoryBasis.truthRef 不可解析：{bref[:72]}")
        for problem in basis_bad:
            msgs.append(f"A13 法规依据：{problem}")

        # --- 第六轮 A13 原地加固：外部原文真源（D3-v2 第 3.3 节）-----------------
        truth_map, truth_problems = basis_truth(d3_text)
        for p in truth_problems:
            msgs.append(p)
        bv_bad: list[str] = []
        ext_with_bv = 0
        for c in cases:
            cid = str(c.get("id", "?"))
            cb = c.get("regulatoryBasis")
            if not isinstance(cb, dict):
                continue  # 上面 basis_bad 已判红，避免重复噪声
            btype = cb.get("type")
            bv = cb.get("basisVerification")
            if btype == "internalContract":
                if "basisVerification" in cb:
                    bv_bad.append(f"{cid} internalContract 携带 basisVerification"
                                  "（内部契约断言不得冒充外部法规原文）")
                continue
            if btype != "externalRegulation":
                continue
            if not isinstance(bv, dict):
                bv_bad.append(f"{cid} 缺 basisVerification（externalRegulation "
                              "主依据须挂第 3.3 节核对表）")
                continue
            ext_with_bv += 1
            missing = [k for k in BV_REQUIRED_FIELDS if not bv.get(k)]
            if missing:
                bv_bad.append(f"{cid} basisVerification 缺字段 {missing}")
                continue
            row = truth_map.get(bv.get("clauseId"))
            if row is None:
                bv_bad.append(f"{cid} clauseId={bv.get('clauseId')!r} 不在第 3.3 节核对表"
                              "（不得臆造条款 id）")
            else:
                for k in ("quote", "sourceUrl", "verifiedOn", "applicability"):
                    if bv.get(k) != row[k]:
                        bv_bad.append(f"{cid} basisVerification.{k} 与核对表逐字不一致"
                                      "（原文 / URL / 日期 / 适用性不得改写）")
                        break
                if row["applicability"] == EXCLUDED_SCOPE:
                    msgs.append(f"A13 射程判定：{cid} 把 excludedByScope 条款 "
                                f"{bv.get('clauseId')} 当作主依据（法源第 2 条明文排除"
                                "企业内部应用，只可作排除性证据）")
            if bv.get("verification") not in VERIFICATION_ENUM:
                bv_bad.append(f"{cid} verification={bv.get('verification')!r} 非合法枚举"
                              "（只许 directQuote / principleExtension）")
            need_note = (bv.get("verification") == "principleExtension"
                         or bv.get("applicability") != "inScope")
            if need_note and len(str(bv.get("applicabilityNote", "")).strip()) < MIN_NOTE_CHARS:
                bv_bad.append(f"{cid} principleExtension / 非 inScope 须附 "
                              f"applicabilityNote（≥{MIN_NOTE_CHARS} 字符，说明非逐字义务或主体边界）")
        for problem in bv_bad:
            msgs.append(f"A13 依据核验：{problem}")
        led = reg.get("basisVerificationLedger")
        if not isinstance(led, dict):
            msgs.append("A13 依据核验：缺 basisVerificationLedger（原文核对台账不可省略）")
        else:
            if led.get("status") == "fullyVerified":
                msgs.append("A13 依据核验：ledger.status 伪造 fullyVerified"
                            "（主体适用性 / 未取得条款 / 语义对应仍属 owner）")
            if led.get("clauseCount") != len(truth_map):
                msgs.append(f"A13 依据核验：ledger.clauseCount={led.get('clauseCount')} "
                            f"!= 核对表实测 {len(truth_map)}")
            if led.get("caseCount") != len(ext_ids) or ext_with_bv != len(ext_ids):
                msgs.append("A13 依据核验：ledger.caseCount / 实际挂接数与外部用例数不一致")
            dates = {r["verifiedOn"] for r in truth_map.values()}
            if led.get("verifiedOn") not in dates:
                msgs.append("A13 依据核验：ledger.verifiedOn 与核对表核验日期不一致")
            if not (isinstance(led.get("sources"), list)
                    and len(led["sources"]) >= MIN_BASIS_SOURCES):
                msgs.append(f"A13 依据核验：ledger.sources 少于独立下界 {MIN_BASIS_SOURCES}")
            if "第 3.3 节" not in str(led.get("truthSource", "")):
                msgs.append("A13 依据核验：ledger.truthSource 未指向 D3-v2 第 3.3 节核对表")
            # 第七轮：射程分类守恒（真源侧改枚举 / 台账侧腐烂都转红）
            expected = {k: sum(1 for r in truth_map.values()
                               if r["applicability"] == k) for k in SCOPE_CLASSES}
            if led.get("scopeCounts") != expected:
                msgs.append(f"A13 射程判定：ledger.scopeCounts={led.get('scopeCounts')} "
                            f"!= 核对表实测 {expected}（射程四分类计数须守恒）")
            if led.get("sourceCount") != len(led.get("sources", [])):
                msgs.append("A13 射程判定：ledger.sourceCount 与 sources 实际长度不一致")
            sc = led.get("scopeCalibration")
            if not isinstance(sc, dict) or sc.get("status") != "scopeClassified":
                msgs.append("A13 射程判定：缺 scopeCalibration.status=scopeClassified"
                            "（射程判定依据不可省略）")
            elif set(sc.get("classes", {})) != set(SCOPE_CLASSES):
                msgs.append("A13 射程判定：scopeCalibration.classes 未覆盖四类射程")

        rb = reg.get("basisRebase")
        if not isinstance(rb, dict) or not all(
                rb.get(k) for k in ("owner", "exitCriteria", "method", "residual")):
            msgs.append("A13 缺 basisRebase 结构化台账（owner / exitCriteria / method / "
                        "residual 不可省略；不得把结构化收敛伪称为法规原文核对完成）")
        else:
            status = rb.get("status")
            if status not in ("rebased", "pendingOwnerRebase"):
                msgs.append(f"A13 basisRebase.status={status!r} 非法（不得伪造 completed / 其它终态）")
            if legacy_ids and status != "pendingOwnerRebase":
                msgs.append(f"A13 仍有 {len(legacy_ids)} 条金融监管编号残留却标 rebased"
                            "（不得伪造完成）")
            if not legacy_ids and status != "rebased":
                msgs.append("A13 债务已清零但 status 仍 pendingOwnerRebase（须如实标 rebased，"
                            "同时保留 residual 说明法规原文核对未完成）")
            if rb.get("affectedCaseCount") != len(legacy_ids):
                msgs.append(f"A13 basisRebase.affectedCaseCount={rb.get('affectedCaseCount')} "
                            f"!= 实测残留 {len(legacy_ids)}（计数不得腐烂）")
            if rb.get("affectedCaseIds") != legacy_ids:
                msgs.append("A13 basisRebase.affectedCaseIds 与实测残留不一致")
            declared = sorted(rb.get("externalRegulationCaseIds", [])
                              + rb.get("internalContractCaseIds", []))
            if declared != sorted(str(c.get("id", "?")) for c in cases):
                msgs.append("A13 basisRebase 分类编号集合未恰好覆盖全部用例"
                            "（不得遗漏 / 重复 / 伪造分类）")
            if (sorted(rb.get("externalRegulationCaseIds", [])) != sorted(ext_ids)
                    or sorted(rb.get("internalContractCaseIds", [])) != sorted(int_ids)):
                msgs.append("A13 basisRebase 分类编号与 case.regulatoryBasis.type 实测不一致")

    # A15 scalePlan 结构化（防止「写一句 ≥200 就算有路线」）
    sp = data.get("scalePlan")
    if not isinstance(sp, dict):
        msgs.append("A15 缺 scalePlan 结构化规模路线（文本 token 不足以作为可验收路线）")
    else:
        if sp.get("targetCases") != SCALE_TARGET:
            msgs.append(f"A15 scalePlan.targetCases={sp.get('targetCases')} != {SCALE_TARGET}")
        if sp.get("currentCases") != len(cases):
            msgs.append(f"A15 scalePlan.currentCases={sp.get('currentCases')} != 实际用例数 {len(cases)}")
        if sp.get("status") == "completed":
            msgs.append("A15 scalePlan.status 不得标 completed（seed 期禁止伪造扩容完成）")
        phases = sp.get("phases")
        if not isinstance(phases, list) or len(phases) < MIN_SCALE_PHASES:
            msgs.append(f"A15 scalePlan.phases 少于 {MIN_SCALE_PHASES} 阶段")
        else:
            for i, ph in enumerate(phases, 1):
                if not isinstance(ph, dict) or not all(
                        str(ph.get(k, "")).strip() for k in ("phase", "cases", "gate", "exit")):
                    msgs.append(f"A15 scalePlan.phases[{i}] 缺阶段 / 样本数 / 门禁 / exit 判据")
        spec = str(sp.get("annotationSpec", ""))
        if "双人标注" not in spec or "Kappa" not in spec:
            msgs.append("A15 scalePlan.annotationSpec 缺双人标注 / Kappa 一致性判据（D6 标注规范未承接）")
        pc = sp.get("passCriteria")
        if not isinstance(pc, dict) or not pc:
            msgs.append("A15 scalePlan.passCriteria 缺指标与通过线占位（不得伪造数值）")
        if "发布门禁" not in str(sp.get("handover", "")):
            msgs.append("A15 scalePlan.handover 缺发布门禁接管条件（seed 不得被当作终态）")


    # A18 推理期不可信输入防御（第十二轮 / N4）：必须 1 正 + 1 负，且挂 ORCHESTRATION。
    # 只查「结构完整」不够：注入/越狱用例是唯一能证明推理期 fail-closed 的可执行证据。
    inj = {str(c.get("id")): c for c in cases
           if str(c.get("id", "")).startswith("PROMPT-INJ-")}
    if set(inj) != {"PROMPT-INJ-01", "PROMPT-INJ-02"}:
        msgs.append("A18 推理期注入防御用例缺失（须 PROMPT-INJ-01 负向 + PROMPT-INJ-02 正向，不得只留其一）")
    else:
        if (inj["PROMPT-INJ-01"].get("kind") != "negative"
                or inj["PROMPT-INJ-02"].get("kind") != "positive"):
            msgs.append("A18 注入防御用例正负档位错误（不得把负向写成正向以稀释 fail-closed）")
        if any(c.get("capabilityCode") != "ORCHESTRATION" for c in inj.values()):
            msgs.append("A18 注入防御用例能力码须挂 ORCHESTRATION（编排治理域）")
        exp = json.dumps(inj["PROMPT-INJ-01"].get("expected", {}), ensure_ascii=False)
        if "injection_detected" not in exp or "finalized" not in exp:
            msgs.append("A18 负向用例缺 injection_detected / finalized 期望（无法验证检出与不放行）")

    return (not msgs), msgs


def read_truth(path: str) -> str:
    try:
        with open(path, "r", encoding="utf-8") as f:
            return f.read()
    except Exception:  # noqa: BLE001 - 真源不可达必须 fail-closed，交由 A14 判红
        return ""


def check(evalset_path: str, d3v2_path: str) -> bool:
    try:
        with open(evalset_path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except Exception as e:
        print(f"[FAIL] 无法解析 JSON: {e}")
        return False
    ok, msgs = validate(data, read_truth(d3v2_path), load_ref_docs(data))
    for m in msgs:
        print(f"[FAIL] {m}")

    cases = data.get("cases") if isinstance(data.get("cases"), list) else []
    n_neg = sum(1 for c in cases if c.get("kind") == "negative")
    suits = data.get("suites") if isinstance(data.get("suites"), list) else []
    truth = capability_truth(read_truth(d3v2_path))
    print(f"[INFO] evalset={data.get('evalsetId')} status={data.get('status')} "
          f"suites={len(suits)} cases={len(cases)} negative={n_neg} "
          f"negRatio={(n_neg / len(cases)) if cases else 0:.2f} "
          f"caps={len({c.get('capabilityCode') for c in cases})}/{len(CAPABILITY_CODES)} "
          f"truthCaps={len(truth)} scaleTarget={data.get('scalePlan', {}).get('targetCases')} "
          f"refDocs={len(load_ref_docs(data))}")
    if data.get("status") == "seededDraft":
        print("=" * 68)
        print(" [SEED DRAFT] 本评测集为设计态种子草稿，非发布门禁终态。")
        print("  - 代码未落地：groundTruthRef 指向文档真源锚点，非源码 path。")
        print(f"  - 规模/通过线待 owner 按 DA-3 补全至 PRD ≥{SCALE_TARGET} + 对抗样本 + 偏见审计集。")
        print("  - 本脚本已接 CI blocking 与本地 pre-commit，只守结构 / 能力码真源 /")
        print("    正向断言红线 / 锚点可达 / 法规依据类型 / 关键负向 / scalePlan 路线，")
        print("    **不守模型效果**；CI 绿不等于 AI 能力可上线。")
        print("=" * 68)
    print("RESULT:", "PASS" if ok else "FAIL")
    return ok


def self_test(evalset_path: str, d3v2_path: str) -> int:
    with open(evalset_path, "r", encoding="utf-8") as f:
        base = json.load(f)
    d3_base = read_truth(d3v2_path)
    ok0, msgs0 = validate(base, d3_base, load_ref_docs(base))
    if not ok0:
        print("[SELF-TEST] 基线未全绿，反证无意义：", msgs0[:3])
        return 1

    def mut(fn):
        box = {"d": copy.deepcopy(base), "d3": d3_base, "docs": load_ref_docs(base)}
        fn(box)
        return box["d"], box["d3"], box["docs"]


    def drop_injection_case(b):
        b["d"]["cases"] = [c for c in b["d"]["cases"] if c["id"] != "PROMPT-INJ-01"]

    def injection_negative_to_positive(b):
        for c in b["d"]["cases"]:
            if c["id"] == "PROMPT-INJ-01":
                c["kind"] = "positive"


    def drop_cap(b):
        b["d"]["cases"][0].pop("capabilityCode", None)

    def bad_cap(b):
        b["d"]["cases"][0]["capabilityCode"] = "M99"

    def drop_cap_registry(b):
        b["d"]["capabilityCodes"].pop("M6", None)

    def drop_fair(b):
        b["d"]["cases"] = [c for c in b["d"]["cases"] if c["id"] != "FAIR-01"]

    def fair_to_positive(b):
        for c in b["d"]["cases"]:
            if c["id"] == "FAIR-02":
                c["kind"] = "positive"

    def drop_critical_negative(b):
        b["d"]["cases"] = [c for c in b["d"]["cases"] if c["id"] != "MATCH-02"]

    def pii_leak(b):
        b["d"]["cases"][0]["piiFree"] = False

    def status_released(b):
        b["d"]["status"] = "released"

    def drop_scale_route(b):
        b["d"]["scope"] = b["d"]["scope"].replace(f"≥{SCALE_TARGET}", "")
        b["d"]["seedNote"] = b["d"]["seedNote"].replace(f"≥{SCALE_TARGET}", "")

    def drop_catalog(b):
        b["d"].pop("regulatoryBasisCatalog", None)

    def shrink_cases(b):
        b["d"]["cases"] = b["d"]["cases"][:SEED_MIN_CASES - 1]

    # --- A14 跨文档真源探针 ---------------------------------------------------
    def truth_add_cap(b):
        b["d3"] = b["d3"].replace("| ORCHESTRATION |", "| M8 | 幽灵能力 | M02 | 寻源 | 02 卷 |\n| ORCHESTRATION |", 1)

    def truth_drop_cap(b):
        b["d3"] = b["d3"].replace("| M6 | 投标响应助手", "| ~~M6~~ | 投标响应助手", 1)

    def truth_delete_section(b):
        # 整行换掉章节号：只改标题后缀仍会被 split("### 1.1") 命中，必须让锚点真正消失
        b["d3"] = re.sub(r"^### 1\.1 .*$", "### 1.9 能力码章节已废止",
                         b["d3"], count=1, flags=re.M)

    # --- A15 scalePlan 结构化探针 ---------------------------------------------
    def drop_scale_plan(b):
        b["d"].pop("scalePlan", None)

    def shrink_target(b):
        b["d"]["scalePlan"]["targetCases"] = 50

    def fake_completed(b):
        b["d"]["scalePlan"]["status"] = "completed"

    def drop_annotation_spec(b):
        b["d"]["scalePlan"]["annotationSpec"] = "待补"

    def drop_phase(b):
        b["d"]["scalePlan"]["phases"] = b["d"]["scalePlan"]["phases"][:1]

    # --- A16 锚点可达探针 -----------------------------------------------------
    def dangling_anchor(b):
        b["d"]["cases"][0]["groundTruthRef"] = b["d"]["cases"][0]["groundTruthRef"].split("#")[0] + "#臆造锚点"

    def missing_ref_file(b):
        b["d"]["cases"][0]["groundTruthRef"] = "doc/ai-product/procuraAI/__不存在__.md#x"

    def drop_docs(b):
        b["docs"] = None

    def self_referential_ground_truth(b):
        # 让 JSON 锚点「真的可解析」，否则只会命中通用悬空消息，测不到自引用守护本身。
        by(b, "ANOMALY-02")["groundTruthRef"] = (
            "doc/ai-product/procuraAI/eval/ai-decision-evalset.json"
            "#groundTruthPolicy.adversarialSamplePolicy")
        b["docs"]["doc/ai-product/procuraAI/eval/ai-decision-evalset.json"] = json.dumps(
            b["d"], ensure_ascii=False)

    def clause_ref_dangling(b):
        # 把 MATCH-01 中合法的「总体 §3.2」改成文档里不存在的「总体 §99.9」，
        # 验证 clauseRef 章节解析守卫本身会转红（而非只靠人工核对）。
        c = by(b, "MATCH-01")
        c["regulatoryBasis"]["clauseRef"] = (
            c["regulatoryBasis"]["clauseRef"].replace("总体 §3.2", "总体 §99.9"))

    def clause_ref_dangling_legacy(b):
        # 把 CONTRACT-01 中「D3-v2 第 3.3 节 B-2」的「第 X 节」写法换成不存在的
        # 「第 9.9 节」，验证 clauseRef 守卫在「第 X 节」写法下同样转红——
        # 闭合第八轮守卫只匹配 §/# 前缀、对「第 X 节」写法放过的盲区。
        c = by(b, "CONTRACT-01")
        c["regulatoryBasis"]["clauseRef"] = (
            c["regulatoryBasis"]["clauseRef"].replace("第 3.3 节", "第 9.9 节"))

    def clause_ref_dangling_chained(b):
        # 链式写法「D3-v2 §3.1 + §3.2 E-4」的第二段 §3.2 无前缀，历史上被守卫
        # 静默跳过（findall 只抓带前缀段）；把第二段改成不存在的 §99.9，验证
        # parse_clause_refs 继承前缀后守卫仍转红——闭合「+ §X」fail-open 盲区。
        c = by(b, "CONTRACT-01")
        c["regulatoryBasis"]["clauseRef"] = (
            c["regulatoryBasis"]["clauseRef"].replace("+ §3.2 E-4", "+ §99.9"))

    def clause_ref_bare_ref(b):
        # 删掉 CONTRACT-02 clauseRef 里的全部「D3-v2 」短路名并把首个章节号改成
        # 不存在的 §99.9：裸章节引用无法定位文档，历史上被 parse_clause_refs 静默
        # 忽略 ⇒ 整个存在性守卫 fail-open（第十一轮真实文件注入实证的假绿）。
        # 验证 bare_clause_sections() 兜底后此类写法必红。
        c = by(b, "CONTRACT-02")
        cr = c["regulatoryBasis"]["clauseRef"]
        assert cr.count("D3-v2 ") >= 1, cr
        c["regulatoryBasis"]["clauseRef"] = (
            cr.replace("D3-v2 ", "").replace("§3 决策权", "§99.9 决策权", 1))

    # --- A17 正负向对照探针 ---------------------------------------------------
    def drop_all_positive(b):
        b["d"]["cases"] = [c for c in b["d"]["cases"] if c.get("kind") != "positive"]

    def one_cap_positive_to_negative(b):
        for c in b["d"]["cases"]:
            if c["id"] == "POS-M1-01":
                c["kind"] = "negative"

    def dupe_test_symbol(b):
        b["d"]["cases"][1]["testSymbol"] = b["d"]["cases"][0]["testSymbol"]

    # --- 第四轮：A17 正向断言红线 / A13 债务台账探针 --------------------------
    def by(b, cid):
        return next(c for c in b["d"]["cases"] if c["id"] == cid)

    def contract01_revert_auto_approve(b):
        by(b, "CONTRACT-01")["expected"] = {
            "result": "AUTO_APPROVE", "tier": "AUTO", "reason": "ALL_GATES_PASSED"}

    def positive_drop_human_final(b):
        by(b, "POS-M5-01")["expected"].pop("humanFinalDecision", None)

    def positive_drop_tier(b):
        by(b, "RECON-01")["expected"].pop("tier", None)

    def positive_auto_token(b):
        by(b, "POS-M1-01")["expected"]["result"] = "AUTO_PASS"

    def legacy_basis_spread(b):
        by(b, "LABEL-01")["regulatoryBasis"]["source"] += "；监管 §22 人工最终决策"

    def drop_basis_rebase(b):
        b["d"]["regulatoryBasisCatalog"].pop("basisRebase", None)

    def fake_rebase_done(b):
        b["d"]["regulatoryBasisCatalog"]["basisRebase"]["status"] = "completed"

    def wrong_rebase_count(b):
        b["d"]["regulatoryBasisCatalog"]["basisRebase"]["affectedCaseCount"] = 3

    # --- 第五轮：法规依据结构化探针 --------------------------------------------
    def basis_to_plain_text(b):
        by(b, "CONTRACT-01")["regulatoryBasis"] = "195 号文辅助性定位"

    def basis_drop_type(b):
        by(b, "CONTRACT-01")["regulatoryBasis"].pop("type", None)

    def basis_bad_type(b):
        by(b, "CONTRACT-01")["regulatoryBasis"]["type"] = "unknownBasis"

    def basis_fake_source(b):
        by(b, "LABEL-01")["regulatoryBasis"]["source"] = "某不存在的采购法规第 99 条"

    def basis_dangling_truth_ref(b):
        by(b, "LABEL-01")["regulatoryBasis"]["truthRef"] = (
            "doc/ai-product/procuraAI/采购AI-接口契约与数据字典-v2.md#不存在的锚点")

    def basis_internal_fake_source(b):
        by(b, "CONTRACT-02")["regulatoryBasis"]["source"] = "《标识办法》第 4/5 条"

    def drop_basis_types(b):
        b["d"]["regulatoryBasisCatalog"].pop("basisTypes", None)

    def basis_misclassified_ids(b):
        rb = b["d"]["regulatoryBasisCatalog"]["basisRebase"]
        rb["externalRegulationCaseIds"] = rb["externalRegulationCaseIds"][1:]

    def drop_rebase_residual(b):
        b["d"]["regulatoryBasisCatalog"]["basisRebase"].pop("residual", None)

    # --- A14 真源表主键唯一探针 -----------------------------------------------
    def truth_duplicate_row(b):
        row = next(ln for ln in b["d3"].splitlines() if re.match(r"^\|\s*M1\s*\|", ln))
        b["d3"] = b["d3"].replace(row, row + "\n" + row, 1)


    # --- 第六轮：A13 外部原文真源探针 ----------------------------------------
    def bv_drop(b):
        by(b, "CONTRACT-01")["regulatoryBasis"].pop("basisVerification", None)

    def bv_quote_typo(b):
        v = by(b, "CONTRACT-01")["regulatoryBasis"]["basisVerification"]
        v["quote"] = v["quote"].replace("不替代", "可替代", 1)

    def bv_fake_clause(b):
        by(b, "CONTRACT-01")["regulatoryBasis"]["basisVerification"]["clauseId"] = "B-99"

    def bv_bad_url(b):
        by(b, "CONTRACT-01")["regulatoryBasis"]["basisVerification"]["sourceUrl"] = "http://example.com/fake"

    def bv_bad_date(b):
        by(b, "CONTRACT-01")["regulatoryBasis"]["basisVerification"]["verifiedOn"] = "2020-01-01"

    def bv_bad_verification(b):
        by(b, "CONTRACT-01")["regulatoryBasis"]["basisVerification"]["verification"] = "selfDeclared"

    def bv_bad_applicability(b):
        by(b, "LABEL-01")["regulatoryBasis"]["basisVerification"]["applicability"] = "inScope"

    def bv_drop_note(b):
        by(b, "LABEL-01")["regulatoryBasis"]["basisVerification"].pop("applicabilityNote", None)

    def bv_internal_fake(b):
        by(b, "CONTRACT-02")["regulatoryBasis"]["basisVerification"] = {"clauseId": "B-2"}

    def bv_delete_section(b):
        b["d3"] = re.sub(r"^### 3\.3 .*$", "### 3.9 核对表已废止",
                         b["d3"], count=1, flags=re.M)

    def bv_truth_quote_typo(b):
        b["d3"] = b["d3"].replace("坚持技术的辅助性定位", "坚持技术的主导性定位", 1)

    def bv_ledger_fake_done(b):
        b["d"]["regulatoryBasisCatalog"]["basisVerificationLedger"]["status"] = "fullyVerified"

    def bv_ledger_wrong_count(b):
        b["d"]["regulatoryBasisCatalog"]["basisVerificationLedger"]["clauseCount"] = 3

    def bv_drop_ledger(b):
        b["d"]["regulatoryBasisCatalog"].pop("basisVerificationLedger", None)


    # --- 第七轮：法源适用射程探针 --------------------------------------------
    def scope_excluded_as_basis(b):
        # 让 B-13 的 quote / URL / 日期 / 适用性都逐字对齐真源，否则只会命中
        # 通用「逐字不一致」，测不到「明文排除条款不得作主依据」这条新守护本身。
        row = next(ln for ln in b["d3"].splitlines() if ln.startswith("| B-13 |"))
        cols = [c.strip() for c in row.strip().strip("|").split("|")]
        bv = by(b, "CONTRACT-01")["regulatoryBasis"]["basisVerification"]
        bv.update({"clauseId": "B-13", "quote": cols[2].strip("「」"),
                   "sourceUrl": cols[3].strip("`"), "verifiedOn": cols[4],
                   "applicability": cols[5]})

    def scope_truth_revert(b):
        old = "| 2026-11-11 | excludedByScope | C-2 排除性证据（不得作主依据） |"
        assert old in b["d3"]
        b["d3"] = b["d3"].replace(old, "| 2026-11-11 | inScope | C-2 排除性证据（不得作主依据） |", 1)

    def scope_counts_rot(b):
        b["d"]["regulatoryBasisCatalog"]["basisVerificationLedger"]["scopeCounts"]["inScope"] = 99

    def scope_drop_calibration(b):
        b["d"]["regulatoryBasisCatalog"]["basisVerificationLedger"].pop("scopeCalibration", None)

    def scope_source_count_rot(b):
        b["d"]["regulatoryBasisCatalog"]["basisVerificationLedger"]["sourceCount"] = 3

    probes = [
        ("删一条用例的 capabilityCode", drop_cap, "A4"),
        ("注入未登记能力码 M99", bad_cap, "A10"),
        ("删 capabilityCodes 中 M6 登记", drop_cap_registry, "A10"),
        ("删 G15 关键偏见用例 FAIR-01", drop_fair, "A11"),
        ("FAIR-02 改成正向", fair_to_positive, "A11"),
        ("删关键负向 MATCH-02", drop_critical_negative, "A12"),
        ("piiFree 改 false", pii_leak, "A6"),
        ("status 改成 released", status_released, "A9"),
        ("抹掉 ≥200 规模路线文本", drop_scale_route, "A13"),
        ("删合规依据目录", drop_catalog, "A13"),
        ("用例数压到种子下界以下", shrink_cases, "种子用例数"),
        ("D3-v2 §1.1 真源表新增未登记能力码 M8", truth_add_cap, "A14"),
        ("D3-v2 §1.1 真源表删 M6 行", truth_drop_cap, "A14"),
        ("D3-v2 §1.1 章节锚点被抹除", truth_delete_section, "A14"),
        ("删 scalePlan 整块", drop_scale_plan, "A15"),
        ("scalePlan.targetCases 压到 50", shrink_target, "A15"),
        ("scalePlan.status 伪造 completed", fake_completed, "A15"),
        ("scalePlan.annotationSpec 抹掉标注规范", drop_annotation_spec, "A15"),
        ("scalePlan.phases 删到只剩 1 阶段", drop_phase, "A15"),
        ("groundTruthRef 改成臆造锚点", dangling_anchor, "A16"),
        ("groundTruthRef 指向不存在的文件", missing_ref_file, "A16"),
        ("不提供真源引用语料（A16 fail-closed）", drop_docs, "A16"),
        ("groundTruthRef 指向评测集自身字段（闭环自证）",
         self_referential_ground_truth, "闭环自证"),
        ("clauseRef 注入不存在的 §99.9 章节（A16 扩展守卫）",
         clause_ref_dangling, "A16"),
        ("删掉全部正向对照（恒拒绝退化基线）", drop_all_positive, "A17"),
        ("POS-M1-01 改成负向（M1 缺正向对照）", one_cap_positive_to_negative, "A17"),
        ("两条用例复用同一 testSymbol", dupe_test_symbol, "A17"),
        ("删掉 PROMPT-INJ-01 推理期注入负向用例", drop_injection_case, "A18"),
        ("注入负向用例改成正向（稀释 fail-closed）", injection_negative_to_positive, "A18"),
        ("D3-v2 §1.1 真源表复制一行 M1", truth_duplicate_row, "A14"),
        ("CONTRACT-01 回退成 AUTO_APPROVE 反向放行（第四轮 P0 修复回退）",
         contract01_revert_auto_approve, "A17"),
        ("正向用例删掉 humanFinalDecision", positive_drop_human_final, "A17"),
        ("正向用例删掉 tier=ADVISORY_ONLY", positive_drop_tier, "A17"),
        ("正向用例结果码改成 AUTO_PASS", positive_auto_token, "A17"),
        ("已收敛用例回流金融监管 §22 编号（债务回流）", legacy_basis_spread, "A13"),
        ("删掉 basisRebase 台账", drop_basis_rebase, "A13"),
        ("basisRebase.status 伪造 completed", fake_rebase_done, "A13"),
        ("basisRebase.affectedCaseCount 腐烂成 3", wrong_rebase_count, "A13"),
        ("regulatoryBasis 退化成自由文本", basis_to_plain_text, "A13"),
        ("regulatoryBasis 删 type 字段", basis_drop_type, "A13"),
        ("regulatoryBasis.type 改成非法枚举", basis_bad_type, "A13"),
        ("外部法源名改成 catalog 外臆造法规", basis_fake_source, "A13"),
        ("regulatoryBasis.truthRef 改成臆造锚点", basis_dangling_truth_ref, "A13"),
        ("internalContract 冒充外部法规依据", basis_internal_fake_source, "A13"),
        ("删 catalog.basisTypes 枚举", drop_basis_types, "A13"),
        ("basisRebase 分类编号漏登记一条", basis_misclassified_ids, "A13"),
        ("删 basisRebase.residual（伪称法规原文已核对）", drop_rebase_residual, "A13"),
        ("externalRegulation 删掉 basisVerification", bv_drop, "A13 依据核验"),
        ("basisVerification.quote 改一个字（原文逐字性）", bv_quote_typo, "A13 依据核验"),
        ("clauseId 臆造 B-99", bv_fake_clause, "A13 依据核验"),
        ("sourceUrl 换成臆造站点", bv_bad_url, "A13 依据核验"),
        ("verifiedOn 换成旧日期", bv_bad_date, "A13 依据核验"),
        ("verification 改成非法枚举", bv_bad_verification, "A13 依据核验"),
        ("voluntaryAdoption 被改成 inScope", bv_bad_applicability, "A13 依据核验"),
        ("非 inScope 用例删掉 applicabilityNote", bv_drop_note, "A13 依据核验"),
        ("internalContract 冒充携带原文核验", bv_internal_fake, "A13 依据核验"),
        ("D3-v2 第 3.3 节核对表整节被抹除", bv_delete_section, "A13 依据核验"),
        ("核对表 B-2 原文被改字（真源侧篡改）", bv_truth_quote_typo, "A13 依据核验"),
        ("ledger.status 伪造 fullyVerified", bv_ledger_fake_done, "A13 依据核验"),
        ("ledger.clauseCount 腐烂成 3", bv_ledger_wrong_count, "A13 依据核验"),
        ("删掉 basisVerificationLedger", bv_drop_ledger, "A13 依据核验"),
        ("excludedByScope 条款被当作主依据", scope_excluded_as_basis, "A13 射程判定"),
        ("核对表 B-13 射程枚举真源侧回退 inScope", scope_truth_revert, "A13 射程判定"),
        ("ledger.scopeCounts 计数腐烂", scope_counts_rot, "A13 射程判定"),
        ("删掉 ledger.scopeCalibration", scope_drop_calibration, "A13 射程判定"),
        ("ledger.sourceCount 与 sources 长度不一致", scope_source_count_rot, "A13 射程判定"),
        ("clauseRef 『第 X 节』写法注入不存在章节（A16 盲区闭合）",
         clause_ref_dangling_legacy, "A16"),
        ("clauseRef 链式第二段『+ §X』注入不存在章节（A16 盲区闭合）",
         clause_ref_dangling_chained, "A16"),
        ("clauseRef 删光 DocName 前缀的裸 §99.9 引用（A16 fail-open 兜底）",
         clause_ref_bare_ref, "裸章节引用"),
    ]
    all_ok = True
    for desc, fn, expect in probes:
        d3, truth, docs = mut(fn)
        ok, msgs = validate(d3, truth, docs)
        hit = any(expect in m for m in msgs)
        good = (not ok) and hit
        print(f"[{'PASS' if good else 'FAIL'}] 负向：{desc}")
        if good:
            print(f"       - 转红证据：{next(m for m in msgs if expect in m)}")
        else:
            print(f"       - 未转红或 key {expect!r} 未命中：{msgs[:2]}")
        all_ok = all_ok and good
    print(f"\n[SELF-TEST] {len(probes)}/{len(probes)} 负向探针全部转红且命中预期 key"
          if all_ok else "\n[SELF-TEST] 存在未转红的判据，需修复")
    return 0 if all_ok else 1


def main() -> int:
    ap = argparse.ArgumentParser(description="采购域 AI 评测集结构与治理门禁")
    ap.add_argument("evalset", nargs="?", default=DEFAULT_EVALSET, help="评测集 JSON 路径")
    ap.add_argument("--d3v2", default=DEFAULT_D3V2, help="能力码真源 D3-v2 §1.1 文档路径")
    ap.add_argument("--self-test", action="store_true", help="注入式负向反证")
    args = ap.parse_args()
    if not os.path.exists(args.evalset):
        print(f"[FAIL] 评测集文件不存在: {args.evalset}")
        return 1
    if args.self_test:
        return self_test(args.evalset, args.d3v2)
    return 0 if check(args.evalset, args.d3v2) else 1


if __name__ == "__main__":
    sys.exit(main())
