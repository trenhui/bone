#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""采购域文档族机器门禁（Procura Alignment Gate）。

判据：
  A1 四份真源文档版本与 README 登记一致，且互相引用存在
  A2 跨文档 § 引用可解析，章节编号无重复（支持 ##/###/#### 与「4. 标题」点号写法）
  A3 相对链接 / assets 资产存在，SVG 可解析且不溢出 viewBox
  A4 废弃误判表述黑名单不得作为「决策」出现（允许出现在「为何改/已废弃/已校正」语境）
  A5 必备平台实证锚点存在（源码级能力校准结论）
  A6 空集合 / 缺文件 fail-closed（防恒绿）
  A7 模块详细设计族（LLD）7 卷 12 节模板、必备锚点与 10 模块覆盖
  A8 契约工件一致性：附录 B 事件注册表 ↔ AsyncAPI 零漂移、错误码/权限码唯一性与
     白名单、未登记 topic 拦截、OpenAPI/时序图资产与 v0.2 深度章节必备锚点
  A9 v0.3 逐卷可执行规格：各卷 §13 的 13.1–13.8 八子节、本卷专属锚点（防复制充数）、
     容量/SLO/FMEA/灰度回滚/保留/读模型/测试七类要素标记、§11 v0.3 探针、
     §12 引用总纲 §23，以及总纲 §23/附录 F/B12–B17/L38–L43 与上游索引登记
  A8.7/R5 外部锚点：12 个冻结事件独立硬编码，交叉比对总体 §7.1 / 领域 §6.2 /
     总纲附录 B.1 三张表（集合 + topic 一致），打破「同一渲染器重算」的闭环自证
  A10 资产裁决与收口守护（附录 F A10.1–A10.12）：README 9 行旧族状态列与作废引用、
     全族 md + 全部 SVG <text> 的废弃词与 AI 合规语境、D3-v2 唯一 payload 7 字段、
     Frozen 事件上游登记、契约工件/生成器/SVG 版本戳、一期机制表与 WBS 人力列、
     模块注册表 11 行 / ATAM ≥10 场景 ≥3 权衡点 / 答辩包 14 行 JD、
     A10.9 六卷 §6 端点 ↔ OpenAPI 方法级双向覆盖（R8 契约工件闭环）、
     A10.10 RA-2 可见性矩阵冻结安全基线（O4/R-4）、A10.11 ATAM 排序会议程+预排序（O6/R-6）、
     A10.12 一期边界最小切片骨架（O7/R-7）、
     A10.3.1 D3-v2 §2 ai_available 三态（C-B：与 §3 一致，禁 boolean）、
     A10.4.1 总体 §7.1 InvoiceMatched 字段 ↔ D3-v2 §2.1 一致（C-A：消除第三份漂移副本）

用法：
  python3 tools/check_procura_alignment.py              # 全套校验，退出码 0/1
  python3 tools/check_procura_alignment.py --self-test # 注入式负向反证：转红 + 失败消息含预期 key

设计约定：纯 stdlib、零 DB / 零 JDK / 零网络；仿 synapClaim/tools/check_all.py 范式。
维护约定：新增判据必须同步扩充 --self-test 的负向探针，禁止只加判据不加反证。
"""

from __future__ import annotations

import argparse
import collections
import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "doc" / "ai-product" / "procuraAI"
ASSETS = BASE / "assets"

DESIGN_DIR = BASE / "detailed-design"
CONTRACTS = DESIGN_DIR / "contracts"
DOC_FILES = {
    "总体": BASE / "采购总体架构方案.md",
    "领域": BASE / "采购领域架构设计方案.md",
    "基线": BASE / "采购域-BONE框架能力对齐与落地基线.md",
    "README": BASE / "README.md",
}
# A7：模块详细设计族（LLD，Proposal）；键「总纲」同时用于 A2 的「总纲 §x」引用解析。
DESIGN_DOCS = {
    "总纲": DESIGN_DIR / "00-总纲与统一约定.md",
    "卷01": DESIGN_DIR / "01-供应商主数据模块详细设计.md",
    "卷02": DESIGN_DIR / "02-寻源与准入模块详细设计.md",
    "卷03": DESIGN_DIR / "03-合同风险绩效与AI模块详细设计.md",
    "卷04": DESIGN_DIR / "04-采购交易与成本模块详细设计.md",
    "卷05": DESIGN_DIR / "05-履约协同与门户模块详细设计.md",
    "卷06": DESIGN_DIR / "06-编排事件与平台接入详细设计.md",
}
ALL_DOCS = {**DOC_FILES, **DESIGN_DOCS}
SOURCE_DOCS = ("总体", "领域", "基线")
LLD_SECTIONS = tuple(str(i) for i in range(1, 13))
LLD_ANCHORS = (
    "Proposal", "t_proc_", "Idempotency-Key", "@PreAuthorize",
    "N1–N6", "负向探针", "必备列",
)
LLD_MODULES = (
    "bone-procurement-supplier-mdm", "bone-procurement-sourcing",
    "bone-procurement-qualification", "bone-procurement-contract",
    "bone-procurement-transaction", "bone-procurement-fulfillment",
    "bone-procurement-risk", "bone-procurement-portal",
    "bone-procurement-app", "bone-app-purchase",
)
EXPECTED_VERSIONS = {"总体": "v0.3", "领域": "v0.3", "基线": "v0.3"}
LLD_VERSION = "v0.3"

# A9：v0.3 逐卷可执行规格（各卷 §13）判据常量。
# 专属锚点逐卷不同，防止「把别卷专题复制过来充数」；下界写死防删条目恒绿。
V03_VOLUMES = tuple(n for n in DESIGN_DOCS if n != "总纲")
V03_SUBSECTIONS = tuple(f"13.{i}" for i in range(1, 9))
V03_ANCHORS: dict[str, tuple[str, ...]] = {
    "卷01": ("survivorship", "阈值三分区", "误合并"),
    "卷02": ("双密钥分持", "hash-chained", "服务器时钟"),
    "卷03": ("义务", "漂移复跑", "override"),
    "卷04": ("条件技术", "PPV", "预算承诺"),
    "卷05": ("TDS", "例外分类", "容差"),
    "卷06": ("full jitter", "retry budget", "毒丸", "redrive"),
}
# 七类要素：子节 → 必须在该子节正文出现的可机读标记
V03_ELEMENT_RULES = (
    ("13.2", ("QPS", "索引", "草案")),
    ("13.3", ("P99", "告警", "error budget")),
    ("13.4", ("降级", "恢复与对账", "不降级")),
    ("13.5", ("feature flag", "回滚", "灰度")),
    ("13.6", ("保留", "crypto-shred", "例外台账")),
    ("13.7", ("staleness", "REBUILD", "投影")),
    ("13.8", ("测试金字塔", "覆盖率", "性能", "压测")),
)
MIN_V03_ANCHORS_PER_VOL = 3
MIN_V03_PROBES = 2
CHARTER_V03_ANCHORS = (
    "## 23. 逐卷可执行规格基线", "## 附录 F", "A9",
    "B12", "B13", "B14", "B15", "B16", "B17",
    "L38", "L39", "L40", "L41", "L42", "L43",
)
KEY_SVGS = (
    "procuraAI-采购总体逻辑架构.svg",
    "procuraAI-采购域上下文地图.svg",
    "procuraAI-BONE能力对齐图.svg",
    "procuraAI-关键链路时序.svg",
)
SEQ_SVG = "procuraAI-关键链路时序.svg"

# A8：契约工件（内容注入 Ctx，保证 --self-test 的负向探针可注入、判据不读真实磁盘）
ARTIFACT_FILES = {
    "openapi.yaml": CONTRACTS / "openapi.yaml",
    "asyncapi.yaml": CONTRACTS / "asyncapi.yaml",
    "generate_asyncapi.py": CONTRACTS / "generate_asyncapi.py",
    "D3v2": BASE / "采购AI-接口契约与数据字典-v2.md",
    SEQ_SVG: ASSETS / SEQ_SVG,
}
# A10：新增真源（不进 ALL_DOCS，避免 A1/A7 的 12 节模板约束），由 A10 独立守护。
EXTRA_DOCS = {
    "资产登记": BASE / "文档资产登记与真源裁决.md",
    "D3v2": BASE / "采购AI-接口契约与数据字典-v2.md",
    "跨域契约": BASE / "跨域契约与外部域依赖.md",
    "一期边界": BASE / "一期边界与机制触发条件.md",
    "平台RFC": BASE / "平台能力共建RFC.md",
    "ATAM": BASE / "质量属性场景与ATAM.md",
    "答辩包": BASE / "架构师答辩包.md",
}
# 原有 POC 族（历史归档 / 受限引用），A10 扫描其失效口径仍带撤销标记。
LEGACY_DOCS = tuple(sorted(p.name for p in BASE.glob("采购AI-*.md")
                            if "-v2" not in p.name))

# R5 外部冻结锚点：12 个事件名独立硬编码自「领域 §6.2」这一冻结真源，
# 不从总纲附录 B 推导，避免「期望值也来自被校验文档」的闭环自证。
FROZEN_EVENT_TYPES = (
    "SupplierChanged", "VendorActivated", "PurchaseOrderCreated",
    "PurchaseOrderAcknowledged", "AsnSubmitted", "GoodsReceived",
    "InvoiceSubmitted", "InvoiceMatched", "PaymentRequested",
    "PaymentRecorded", "ContractReviewed", "RiskAlertRaised",
)
LLD_NEW_EVENTS_FLOOR = 17  # 附录 B.2 的 LLD 级新增事件数（B.1 12 + B.2 17 = 29）
EXPECTED_REGISTRY_SIZE = len(FROZEN_EVENT_TYPES) + LLD_NEW_EVENTS_FLOOR

# A10：SVG 文字层禁止残留的已判废口径（A3 只验 XML/viewBox，不读 <text>）。
SVG_BANNED_TEXTS = ("AuditBus", "A1–A6", "contract_no 主键", "全覆盖", "LLD v0.2")
# 「自动归档」只允许出现在撤销 / 禁止语境；其余一律判红。
REVOCATION_MARKERS = (
    "已撤销", "撤销", "撤回", "禁止", "不得", "失效", "否决", "废弃", "不再",
    "原为", "原「", "原口径", "历史", "误", "语境", "清零", "判废", "已删除",
    "不得再", "已停用", "不得实现", "取代", "已关闭",
)
# D3-v2 §2 定义的 ContractReviewed 唯一 payload 字段集（R3）。
CONTRACT_REVIEWED_FIELDS = (
    "review_id", "contract_no", "risk_level", "risk_items_hash",
    "review_snapshot_ref", "ai_available", "model_version_snapshot",
)

# 下界写死：防止「删条目让集合变小」造成恒绿（仿 synapClaim evalset 下界约定）。
# A6 额外校验 MIN_REGISTRY_EVENTS == EXPECTED_REGISTRY_SIZE（由外部冻结锚点推导）。
MIN_REGISTRY_EVENTS = 29
MIN_ERROR_CODES = 43
MIN_PERMISSION_RESOURCES = 33
OPENAPI_REQUIRED_PATHS = (
    "/procurement/suppliers:", "/procurement/vendor-activations:",
    "/procurement/purchase-orders:", "/portal/invoices:",
    "/procurement/webhooks/finance:", "/procurement/ops/events/{eventId}/replay:",
)
# A10.9（R8 契约工件闭环）：六卷 §6 是端点权威来源，OpenAPI 必须按 (method, path) 全覆盖。
# 下界与「仅允许存在于 OpenAPI 的投影端点」均写死在脚本里，作为独立于文档的外部锚点；
# 否则「LLD 删一行 + OpenAPI 删一行」会让覆盖率恒绿（同 R5 闭环自证问题）。
MIN_LLD_ENDPOINT_ROWS = 55
EXTRA_OPENAPI_ENDPOINTS = frozenset({
    ("get", "/procurement/transfers"),           # R8 只读列表投影
    ("get", "/procurement/rtvs"),                # R8 只读列表投影
    ("get", "/procurement/ops/items"),           # 跨域契约 §3 Item
    ("get", "/procurement/ops/inventory"),       # 跨域契约 §4 Inventory
    ("get", "/procurement/ops/warehouse-sites"), # 跨域契约 §5 Warehouse
})
LLD_ENDPOINT_ROW_RE = re.compile(
    r"^\|\s*`(GET|POST|PUT|PATCH|DELETE)`\s*\|\s*`(/(?:api/v1/)?[^`]+)`", re.M)
OA_PATH_RE = re.compile(r"^  (/[^\s:]*):\s*$", re.M)
OA_METHOD_RE = re.compile(r"^    (get|post|put|patch|delete):\s*$", re.M)
CHARTER_V02_ANCHORS = (
    "ISO 4217", "COMPENSATABLE", "REBUILD", "RFC 9457", "cursor",
    "aggregate_version", "tolerant reader", "A8", "FOR UPDATE SKIP LOCKED",
    "附录 A", "附录 B", "附录 C", "附录 D", "附录 E",
)
SEQ_SVG_ANCHORS = ("①", "②", "③", "REBUILD", "InvoiceMatched", "DLQ", "partition_key")
PERMISSION_ACTIONS = frozenset({"read", "write", "approve", "export", "admin"})

TOPIC_RE = re.compile(r"^domain\.procurement\.[a-z0-9_]+\.v1$")
DOMAIN_TOKEN_RE = re.compile(r"domain\.procurement\.[a-z0-9_.*]+")
ASYNC_CHANNEL_RE = re.compile(r"^  (domain\.procurement\.[a-z0-9_.]+):\s*$", re.M)
# A8.7 / A10.4：从三处真源表解析「事件 → 冻结 topic」映射（外部锚点比对用）。
FROZEN_ROW_RE = re.compile(
    r"^\|\s+`(?P<event>[A-Z][A-Za-z0-9]*)`\s+\|\s+`"
    r"(?P<topic>domain\.procurement\.[a-z0-9_.]+)`\s+\|", re.M)
ERROR_CODE_RE = re.compile(r"\b3\d{4}\b")
CATALOG_CODE_RE = re.compile(r"code: '(\d{5})'")
PERMISSION_RE = re.compile(r"\b(?:procurement|portal):([a-z][a-z0-9-]*):([a-z/]+)")
# A10：旧族状态表 / SVG 文字层 / 正文判废语境。
LEGACY_STATES = ("真源", "受限引用", "历史归档", "作废")
LEGACY_STATUS_ROW_RE = re.compile(
    r"^\|\s*\[[^\]]+\]\(\./(采购AI-[^)]+\.md)\)\s*\|\s*([^|]*?)\s*\|", re.M)
SVG_TEXT_RE = re.compile(r"<text\b[^>]*>([^<]*)</text>")
A10_MD_BANNED = ("AuditBus", "A1–A6", "LLD v0.2", "contract_no 主键")
A10_BANNED_NEG = (
    "废弃", "判废", "已删除", "删除", "原", "修订", "历史", "不得", "不新建",
    "拦截", "扫描", "清零", "已撤销", "撤销", "撤回", "失效", "取代", "校正",
    "修正", "已关闭", "禁用", "不得引用",
)

# A4：绝不允许出现的「v0.2 决策原文」，任何语境都判红。
HARD_FORBIDDEN = (
    "| **Audit** | 平台审计总线（ADR-0007 IAM 审计总线 / ADR-0009 审计日志拆分）",
    "| **EventBus/Outbox** | 事件规范 + `bp_outbox` | 跨部署单元写协作 |",
    "| 退回指定节点 | 驳回走「新建修订版本 + 重新发起」，不改写原实例 |",
    "| 委派 / 转办 / 加签 | 统一任务中心人工干预 + 平台运维兜底 |",
    "**v1 能力缺口与一期降级方案（v0.2 新增）**",
    "WF v1 实证缺子流程/退回/委派/SLA",
    "| 门禁总入口 | `scripts/check.sh` + CI | 机器可验证 gate 优先于人工 |",
    "三单匹配作为跨域强一致不变量，由采购域保证",
    "未结应付恒等于已收未付发票净额",
)

# A4：允许出现在「为何改 / 已废弃 / 已校正」语境，但不得作为决策独立出现。
CONTEXT_FORBIDDEN = (
    "平台审计总线（ADR-0007",
    "动作级细码授权（`RuntimeAccessGuard`）",
    "承接所有审批",
    "注解生成 DDL",
    "三单匹配作为跨域强一致不变量",
    "未结应付 = 已收未付发票净额",
    "结论回写 `contract_no`",
    "HC-001/006（零豁免）",
    "v1 未实现清单",
    "门禁总入口 = `scripts/check.sh`",
)
# 否定 / 修订语境标记（出现在命中点 ±70 字符内才放行）
DEPRECATION_MARKERS = (
    "废弃", "撤回", "已过时", "过时", "失真", "校正", "修正", "误用", "高估",
    "不得", "不再", "改为", "收窄", "推翻", "不成立", "并非", "不是", "否定",
    "v0.2 写法", "问题", "冲突", "已由", "→", "实为", "不存在", "清单过时",
    "禁止", "已改", "修订", "诊断", "依据", "校准", "证伪", "为何改", "已废弃",
)
EXTERNAL_REF_PREFIXES = ("合规", "规范", "指导意见", "PRD", "D3", "ADR", "195")

# A5：经源码实证校准后必须保留的能力锚点
REQUIRED_ANCHORS = {
    "总体": (
        "@PreAuthorize", "publishFrom", "TenantAbstractEntity", "bone-app-purchase",
        "ci.yml", "rejectToNode", "t_proc_outbox", "t_proc_processed_event",
        "check_procura_alignment.py", "子流程", "SLA",
    ),
    "领域": (
        "@PreAuthorize", "publishFrom", "TenantAbstractEntity", "bone-app-purchase",
        "rejectToNode", "t_proc_processed_event", "@Version", "@Sql",
        "WorkflowConditionEvaluator", "check_procura_alignment.py", "子流程", "SLA",
    ),
    "基线": (
        "@PreAuthorize", "publishFrom", "TenantAbstractEntity", "bone-app-purchase",
        "ci.yml", "t_proc_outbox", "--self-test", "rejectToNode", "子流程", "SLA",
    ),
    "README": (
        "bone-app-purchase", "check_procura_alignment.py", "真源约定", "v0.3",
        "采购域-BONE框架能力对齐与落地基线.md",
    ),
}

SECTION_RE = re.compile(r"^#{2,4}\s+(\d+(?:\.\d+)*)\.?\s", re.M)
# 总纲 v0.2 的专题子节采用「**16.8 标题**」粗体段落而非 ### 标题，
# 同样是可被 §x.y 引用的锚点；只并入可解析集合，不参与章节编号重复判据。
BOLD_SECTION_RE = re.compile(r"^\*\*(\d+(?:\.\d+)+)\s", re.M)
CROSS_REF_RE = re.compile(
    r"(总体|领域设计|领域|能力基线|基线|总纲)(?:\s*方案)?\s*§\s*(\d+(?:\.\d+)*)"
)
BARE_REF_RE = re.compile(r"(.{0,12})§\s*(\d+(?:\.\d+)*)")
LINK_RE = re.compile(r"\]\((\.{1,2}/[^)]+)\)")
VERSION_RE = re.compile(r">\s*\*\*版本\*\*：(v\d+\.\d+)")
REF_TARGET = {
    "总体": "总体", "领域": "领域", "领域设计": "领域",
    "能力基线": "基线", "基线": "基线", "总纲": "总纲",
}


@dataclass
class Ctx:
    texts: dict[str, str] = field(default_factory=dict)
    sections: dict[str, set[str]] = field(default_factory=dict)
    assets: list[Path] = field(default_factory=list)
    missing: list[str] = field(default_factory=list)
    artifacts: dict[str, str] = field(default_factory=dict)
    artifact_missing: list[str] = field(default_factory=list)
    # A10：新增真源 / SVG 文字层 / 旧 POC 族，全部随 Ctx 注入以支持负向反证。
    extra: dict[str, str] = field(default_factory=dict)
    svgs: dict[str, str] = field(default_factory=dict)
    legacy: dict[str, str] = field(default_factory=dict)
    extra_missing: list[str] = field(default_factory=list)


def _sections(text: str) -> set[str]:
    return set(SECTION_RE.findall(text)) | set(BOLD_SECTION_RE.findall(text))


def _numbered_headings(text: str) -> list[str]:
    return SECTION_RE.findall(text)


def _load_artifacts() -> tuple[dict[str, str], list[str]]:
    artifacts: dict[str, str] = {}
    missing: list[str] = []
    for key, path in ARTIFACT_FILES.items():
        if path.is_file():
            artifacts[key] = path.read_text(encoding="utf-8")
        else:
            missing.append(f"{key}: {path}")
    return artifacts, missing


def _load_a10_assets() -> tuple[dict[str, str], dict[str, str], dict[str, str], list[str]]:
    """A10 资产：新增真源 / SVG 全文 / 旧 POC 族。缺文件记入 extra_missing（fail-closed）。"""
    extra: dict[str, str] = {}
    svgs: dict[str, str] = {}
    legacy: dict[str, str] = {}
    missing: list[str] = []
    for name, path in EXTRA_DOCS.items():
        if path.is_file():
            extra[name] = path.read_text(encoding="utf-8")
        else:
            missing.append(f"extra:{name}: {path}")
    for path in sorted(ASSETS.glob("*.svg")):
        svgs[path.name] = path.read_text(encoding="utf-8")
    if not svgs:
        missing.append("svgs: assets/*.svg 为空")
    for name in LEGACY_DOCS:
        path = BASE / name
        if path.is_file():
            legacy[name] = path.read_text(encoding="utf-8")
        else:
            missing.append(f"legacy:{name}")
    return extra, svgs, legacy, missing


def load() -> Ctx:
    texts: dict[str, str] = {}
    missing: list[str] = []
    for name, path in ALL_DOCS.items():
        if path.is_file():
            texts[name] = path.read_text(encoding="utf-8")
        else:
            missing.append(f"{name}: {path}")
    assets = sorted(ASSETS.glob("*.svg")) if ASSETS.is_dir() else []
    artifacts, artifact_missing = _load_artifacts()
    extra, svgs, legacy, extra_missing = _load_a10_assets()
    return Ctx(
        texts=texts,
        sections={k: _sections(v) for k, v in texts.items()},
        assets=assets,
        missing=missing,
        artifacts=artifacts,
        artifact_missing=artifact_missing,
        extra=extra,
        svgs=svgs,
        legacy=legacy,
        extra_missing=extra_missing,
    )


def derive(texts: dict[str, str], assets: list[Path], missing: list[str] | None = None) -> Ctx:
    """由注入后的文本重建上下文（--self-test 用）。"""
    artifacts, artifact_missing = _load_artifacts()
    extra, svgs, legacy, extra_missing = _load_a10_assets()
    return Ctx(
        texts=dict(texts),
        sections={k: _sections(v) for k, v in texts.items()},
        assets=list(assets),
        missing=list(missing or []),
        artifacts=artifacts,
        artifact_missing=artifact_missing,
        extra=extra,
        svgs=svgs,
        legacy=legacy,
        extra_missing=extra_missing,
    )


# ---------------------------------------------------------------- A1
def check_a1(ctx: Ctx) -> tuple[bool, list[str]]:
    msgs: list[str] = []
    if ctx.missing:
        msgs.append(f"缺失文件：{ctx.missing}")
    readme = ctx.texts.get("README", "")
    for name in SOURCE_DOCS:
        text = ctx.texts.get(name, "")
        if not text:
            msgs.append(f"A1 缺少真源文档 {name}")
            continue
        m = VERSION_RE.search(text)
        if not m:
            msgs.append(f"A1 {name} 无版本头")
        elif m.group(1) != EXPECTED_VERSIONS[name]:
            msgs.append(f"A1 {name} 版本 {m.group(1)} != 期望 {EXPECTED_VERSIONS[name]}")
        filename = DOC_FILES[name].name
        rows = [ln for ln in readme.splitlines() if filename in ln]
        if not rows:
            msgs.append(f"A1 README 未登记 {filename}")
        elif not any(EXPECTED_VERSIONS[name] in ln for ln in rows):
            msgs.append(f"A1 README 登记行版本与 {name} 不一致")
        for other in SOURCE_DOCS:
            if other == name:
                continue
            if DOC_FILES[other].name not in text:
                msgs.append(f"A1 {name} 未互引 {other}")
        if "check_procura_alignment.py" not in text:
            msgs.append(f"A1 {name} 未登记文档族门禁脚本")
    if "真源约定" not in readme:
        msgs.append("A1 README 缺少真源约定")
    for name in DESIGN_DOCS:
        text = ctx.texts.get(name, "")
        if not text:
            msgs.append(f"A1 缺详细设计卷 {name}")
            continue
        m = VERSION_RE.search(text)
        if not m or m.group(1) != LLD_VERSION:
            msgs.append(f"A1 {name} 版本头缺失或不是 {LLD_VERSION}")
        if "**状态**：**Proposal" not in text:
            msgs.append(f"A1 {name} 状态头缺失或不是 Proposal（设计态不得写成已实现）")
        if ALL_DOCS[name].name not in readme:
            msgs.append(f"A1 README 未登记详细设计卷 {name}")
        if name != "总纲" and "00-总纲与统一约定.md" not in text:
            msgs.append(f"A1 {name} 未引用总纲")
    charter = ctx.texts.get("总纲", "")
    for src in ("采购总体架构方案.md", "采购领域架构设计方案.md",
                "采购域-BONE框架能力对齐与落地基线.md"):
        if src not in charter:
            msgs.append(f"A1 总纲未引用上游真源 {src}")
    return (not msgs), msgs


# ---------------------------------------------------------------- A2
def check_a2(ctx: Ctx) -> tuple[bool, list[str]]:
    msgs: list[str] = []
    for name in SOURCE_DOCS:
        text = ctx.texts.get(name, "")
        heads = _numbered_headings(text)
        dups = [h for h, c in collections.Counter(heads).items() if c > 1]
        if dups:
            msgs.append(f"A2 {name} 章节编号重复：{sorted(dups)}")
    union: set[str] = set()
    for s in ctx.sections.values():
        union |= s
    for name, text in ctx.texts.items():
        for prefix, num in CROSS_REF_RE.findall(text):
            target = REF_TARGET[prefix]
            if num not in ctx.sections.get(target, set()):
                msgs.append(f"A2 {name} 悬空跨文档引用 {prefix}§{num}")
        for m in BARE_REF_RE.finditer(text):
            lead, num = m.group(1), m.group(2)
            if num in ctx.sections.get(name, set()) or num in union:
                continue
            # 合规 §21–§24 / §29 等外部条文：跨行范围引用只看 12 字符 lead 会误判，
            # 因此按「所在整行」判定外部语境。
            line_start = text.rfind("\n", 0, m.start()) + 1
            line_end = text.find("\n", m.end())
            line = text[line_start: line_end if line_end >= 0 else len(text)]
            # 只有「整数条号」才可能是外部法规条文（合规 §24 / 指导意见 §29）；
            # 带小数的 §x.y 一律按内部锚点严格解析，防止借「合规」二字掩盖悬空引用。
            if (any(k in lead for k in EXTERNAL_REF_PREFIXES)
                    or (any(k in line for k in EXTERNAL_REF_PREFIXES) and "." not in num)):
                continue
            if any(k in lead for k in ("总体", "领域", "基线")):
                continue  # 已在跨文档引用判据中校验
            msgs.append(f"A2 {name} 未解析 §{num}（上下文：{lead.strip()!r}）")
    return (not msgs), msgs


# ---------------------------------------------------------------- A3
def check_a3(ctx: Ctx) -> tuple[bool, list[str]]:
    msgs: list[str] = []
    for name, text in ctx.texts.items():
        doc_dir = ALL_DOCS.get(name, BASE).parent  # LLD 卷在 detailed-design/ 子目录
        for rel in LINK_RE.findall(text):
            if not (doc_dir / rel).exists():
                msgs.append(f"A3 {name} 相对链接缺失：{rel}")
    for svg_name in KEY_SVGS:
        if not (ASSETS / svg_name).is_file():
            msgs.append(f"A3 关键架构图缺失：{svg_name}")
    for svg in ctx.assets:
        try:
            root = ET.parse(svg).getroot()
            vb = root.get("viewBox")
            if not vb:
                msgs.append(f"A3 {svg.name} 缺 viewBox")
                continue
            parts = [float(x) for x in vb.split()]
            if len(parts) != 4:
                msgs.append(f"A3 {svg.name} viewBox 非法")
                continue
            _, _, vb_w, vb_h = parts
            for el in root.iter():
                try:
                    y = float(el.get("y", "0"))
                    h = float(el.get("height", "0"))
                    x = float(el.get("x", "0"))
                    w = float(el.get("width", "0"))
                except (TypeError, ValueError):
                    continue  # 百分比 / 带单位坐标跳过
                if y > vb_h + 1 or y + h > vb_h + 1 or x > vb_w + 1 or x + w > vb_w + 1:
                    msgs.append(f"A3 {svg.name} 元素溢出 viewBox（x={x},y={y},w={w},h={h}）")
                    break
        except ET.ParseError as exc:
            msgs.append(f"A3 {svg.name} XML 解析失败：{exc}")
    return (not msgs), msgs


# ---------------------------------------------------------------- A4
def check_a4(ctx: Ctx) -> tuple[bool, list[str]]:
    msgs: list[str] = []
    for name, text in ctx.texts.items():
        for phrase in HARD_FORBIDDEN:
            if phrase in text:
                msgs.append(f"A4 {name} 出现已废弃决策原文：{phrase[:40]}…")
        for phrase in CONTEXT_FORBIDDEN:
            start = 0
            while True:
                idx = text.find(phrase, start)
                if idx < 0:
                    break
                window = text[max(0, idx - 70): idx + len(phrase) + 70]
                if not any(m in window for m in DEPRECATION_MARKERS):
                    msgs.append(f"A4 {name} 废弃表述脱离否定语境：{phrase[:36]}…")
                start = idx + len(phrase)
    return (not msgs), msgs


# ---------------------------------------------------------------- A5
def check_a5(ctx: Ctx) -> tuple[bool, list[str]]:
    msgs: list[str] = []
    for name, anchors in REQUIRED_ANCHORS.items():
        text = ctx.texts.get(name, "")
        for token in anchors:
            if token not in text:
                msgs.append(f"A5 {name} 缺平台实证锚点：{token}")
    return (not msgs), msgs


# ---------------------------------------------------------------- A6
def check_a6(ctx: Ctx) -> tuple[bool, list[str]]:
    msgs: list[str] = []
    constants = {
        "HARD_FORBIDDEN": HARD_FORBIDDEN,
        "CONTEXT_FORBIDDEN": CONTEXT_FORBIDDEN,
        "DEPRECATION_MARKERS": DEPRECATION_MARKERS,
        "REQUIRED_ANCHORS": REQUIRED_ANCHORS,
        "EXPECTED_VERSIONS": EXPECTED_VERSIONS,
        "KEY_SVGS": KEY_SVGS,
        "SOURCE_DOCS": SOURCE_DOCS,
        "DESIGN_DOCS": DESIGN_DOCS,
        "LLD_SECTIONS": LLD_SECTIONS,
        "LLD_ANCHORS": LLD_ANCHORS,
        "LLD_MODULES": LLD_MODULES,
        "ARTIFACT_FILES": ARTIFACT_FILES,
        "OPENAPI_REQUIRED_PATHS": OPENAPI_REQUIRED_PATHS,
        "EXTRA_OPENAPI_ENDPOINTS": EXTRA_OPENAPI_ENDPOINTS,
        "CHARTER_V02_ANCHORS": CHARTER_V02_ANCHORS,
        "CHARTER_V03_ANCHORS": CHARTER_V03_ANCHORS,
        "SEQ_SVG_ANCHORS": SEQ_SVG_ANCHORS,
        "PERMISSION_ACTIONS": PERMISSION_ACTIONS,
        "V03_VOLUMES": V03_VOLUMES,
        "V03_SUBSECTIONS": V03_SUBSECTIONS,
        "V03_ANCHORS": V03_ANCHORS,
        "V03_ELEMENT_RULES": V03_ELEMENT_RULES,
        "FROZEN_EVENT_TYPES": FROZEN_EVENT_TYPES,
        "EXTRA_DOCS": EXTRA_DOCS,
        "LEGACY_DOCS": LEGACY_DOCS,
        "SVG_BANNED_TEXTS": SVG_BANNED_TEXTS,
        "REVOCATION_MARKERS": REVOCATION_MARKERS,
        "CONTRACT_REVIEWED_FIELDS": CONTRACT_REVIEWED_FIELDS,
    }
    for key, value in constants.items():
        if not value:
            msgs.append(f"A6 常量集合为空（判据将恒绿）：{key}")
    for floor in (MIN_REGISTRY_EVENTS, MIN_ERROR_CODES, MIN_PERMISSION_RESOURCES,
                  MIN_V03_ANCHORS_PER_VOL, MIN_V03_PROBES, MIN_LLD_ENDPOINT_ROWS):
        if not isinstance(floor, int) or floor <= 0:
            msgs.append(f"A6 下界常量非法（判据将恒绿）：{floor!r}")
    # R5：下界必须与「外部冻结锚点 + LLD 新增下界」推导值一致，防止改小数字仍恒绿。
    if MIN_REGISTRY_EVENTS != EXPECTED_REGISTRY_SIZE:
        msgs.append(f"A6 下界常量 MIN_REGISTRY_EVENTS={MIN_REGISTRY_EVENTS} 与外部锚点推导值 "
                    f"{EXPECTED_REGISTRY_SIZE} 不一致（{len(FROZEN_EVENT_TYPES)} Frozen + "
                    f"{LLD_NEW_EVENTS_FLOOR} LLD）")
    if ctx.extra_missing or len(ctx.extra) != len(EXTRA_DOCS) or not ctx.svgs or not ctx.legacy:
        msgs.append(f"A6 A10 资产集合不完整：missing={ctx.extra_missing}, "
                    f"extra={sorted(ctx.extra)}, svgs={len(ctx.svgs)}, legacy={len(ctx.legacy)}")
    for key, text in {**ctx.extra, **ctx.svgs, **ctx.legacy}.items():
        if not text.strip():
            msgs.append(f"A6 A10 资产内容为空：{key}")
    if ctx.artifact_missing or len(ctx.artifacts) != len(ARTIFACT_FILES):
        msgs.append(f"A6 契约工件集合不完整：missing={ctx.artifact_missing}, "
                    f"got={sorted(ctx.artifacts)}")
    for key, text in ctx.artifacts.items():
        if not text.strip():
            msgs.append(f"A6 契约工件内容为空：{key}")
    if ctx.missing or len(ctx.texts) != len(ALL_DOCS):
        msgs.append(f"A6 文档集合不完整：missing={ctx.missing}, got={sorted(ctx.texts)}")
    for name, text in ctx.texts.items():
        if not text.strip():
            msgs.append(f"A6 {name} 内容为空")
    for name in SOURCE_DOCS + tuple(DESIGN_DOCS):
        if not ctx.sections.get(name):
            msgs.append(f"A6 {name} 章节集合为空")
    if not ctx.assets:
        msgs.append("A6 assets 集合为空")
    if not any(CROSS_REF_RE.findall(v) for v in ctx.texts.values()):
        msgs.append("A6 跨文档 § 引用集合为空")
    if not any(LINK_RE.findall(v) for v in ctx.texts.values()):
        msgs.append("A6 相对链接集合为空")
    return (not msgs), msgs


# ---------------------------------------------------------------- A7
def check_a7(ctx: Ctx) -> tuple[bool, list[str]]:
    """模块详细设计族（LLD）完整性与模板合规。"""
    msgs: list[str] = []
    charter = ctx.texts.get("总纲", "")
    for mod in LLD_MODULES:
        if mod not in charter:
            msgs.append(f"A7 总纲模块覆盖矩阵缺 {mod}")
    for name in DESIGN_DOCS:
        text = ctx.texts.get(name, "")
        if not text:
            msgs.append(f"A7 缺详细设计卷 {name}")
            continue
        heads = re.findall(r"^## (\d+)\. ", text, re.M)
        missing = [n for n in LLD_SECTIONS if n not in heads]
        if missing:
            msgs.append(f"A7 {name} 缺 LLD 章节：{missing}")
        dups = [h for h, c in collections.Counter(heads).items() if c > 1]
        if dups:
            msgs.append(f"A7 {name} LLD 章节编号重复：{sorted(dups)}")
        for token in LLD_ANCHORS:
            if token not in text:
                msgs.append(f"A7 {name} 缺必备锚点：{token}")
        if name != "总纲" and not re.search(r"\| L\d+ \|", text):
            msgs.append(f"A7 {name} 未登记 L 级待决项")
    return (not msgs), msgs


# ---------------------------------------------------------------- A8
# --- R5 外部冻结锚点 ---------------------------------------------------------
FROZEN_TABLE_SLICES = {
    "总体 §7.1": ("总体", "### 7.1 关键事件 payload 契约", "\n> **信封"),
    "领域 §6.2": ("领域", "### 6.2 领域事件契约", "### 6.3"),
    "总纲附录 B.1": ("总纲", "**B.1 已冻结", "**B.2 LLD 级新增"),
}


def _frozen_rows(text: str, start: str, end: str) -> dict[str, str]:
    """从上游真源表解析 eventType → topic；期望值不来自被校验的附录 B。"""
    if start not in text:
        return {}
    seg = text.split(start, 1)[1]
    if end in seg:
        seg = seg.split(end, 1)[0]
    return {e: t for e, t in FROZEN_ROW_RE.findall(seg)}


def _frozen_tables(ctx: Ctx) -> dict[str, dict[str, str]]:
    return {label: _frozen_rows(ctx.texts.get(name, ""), start, end)
            for label, (name, start, end) in FROZEN_TABLE_SLICES.items()}


def _generator_namespace(src: str) -> dict:
    """在隔离命名空间执行生成器源码（用注入内容，不读盘，保证负向探针可注入）。"""
    ns: dict = {
        "__name__": "procura_asyncapi_gen",
        "__file__": str(ARTIFACT_FILES["generate_asyncapi.py"]),
    }
    exec(compile(src, "generate_asyncapi.py", "exec"), ns)  # noqa: S102 - 仓库内可信工件
    return ns


def check_a8(ctx: Ctx) -> tuple[bool, list[str]]:
    """契约工件一致性：注册表 ↔ AsyncAPI 零漂移 / 码字唯一性 / topic 白名单 / 资产引用。"""
    msgs: list[str] = []
    charter = ctx.texts.get("总纲", "")
    vols = {n: ctx.texts.get(n, "") for n in DESIGN_DOCS if n != "总纲"}
    art = ctx.artifacts
    d3v2_text = art.get("D3v2", "")
    openapi = art.get("openapi.yaml", "")
    asyncapi = art.get("asyncapi.yaml", "")
    gen_src = art.get("generate_asyncapi.py", "")
    seq_svg = art.get(SEQ_SVG, "")
    for key in ARTIFACT_FILES:
        if key not in art:
            msgs.append(f"A8 缺契约工件：{key}")

    # --- 8.1 附录 B 注册表 ↔ AsyncAPI 零漂移 ---------------------------------
    gen: dict = {}
    if gen_src:
        try:
            gen = _generator_namespace(gen_src)
        except Exception as exc:  # noqa: BLE001 - 工件不可执行必须判红
            msgs.append(f"A8 生成器源码不可执行：{exc}")
    rows: list[dict[str, str]] = []
    if gen:
        try:
            rows = gen["parse_registry"](charter)
        except (SystemExit, Exception) as exc:  # noqa: B014 - 生成器主动 fail-closed
            msgs.append(f"A8 附录 B 注册表解析失败：{exc}")
    topics = [r["topic"] for r in rows]
    events = [r["event"] for r in rows]
    if len(rows) < MIN_REGISTRY_EVENTS:
        msgs.append(f"A8 注册表条目 {len(rows)} < 下界 {MIN_REGISTRY_EVENTS}（疑似被删或解析失败）")
    for label, seq in (("eventType", events), ("topic", topics)):
        for dup in sorted(k for k, c in collections.Counter(seq).items() if c > 1):
            msgs.append(f"A8 注册表 {label} 重复：{dup}")
    for t in topics:
        if not TOPIC_RE.match(t):
            msgs.append(f"A8 注册表 topic 不符命名规范：{t}")

    if gen and rows:
        try:
            fields = gen["parse_fields_from_texts"](rows, vols)
            schemas = gen["parse_payload_schemas"](d3v2_text) if d3v2_text else {}
            expected = gen["render"](rows, fields, schemas)
            if asyncapi != expected:
                exp_lines, act_lines = expected.splitlines(), asyncapi.splitlines()
                diff = next((i + 1 for i, (a, b) in enumerate(zip(exp_lines, act_lines))
                             if a != b), min(len(exp_lines), len(act_lines)) + 1)
                msgs.append(f"A8 asyncapi.yaml 与附录 B 漂移（首个差异行 {diff}；"
                            f"注册表 {len(rows)} 条），须重跑 generate_asyncapi.py")
        except (SystemExit, Exception) as exc:  # noqa: B014
            msgs.append(f"A8 零漂移重算失败：{exc}")

    # --- 8.6 核心 / INBOUND 事件 payload 非空壳（续诊 O1 闭合 payload 空壳）---
    if gen and d3v2_text:
        try:
            core_schemas = gen["parse_payload_schemas"](d3v2_text)
        except (SystemExit, Exception) as exc:  # noqa: B014
            core_schemas = {}
            msgs.append(f"A8 D3-v2 §2/§2.1 解析失败：{exc}")
        if not core_schemas:
            msgs.append("A8 未从 D3-v2 §2/§2.1 解析到核心事件 payload schema（O1 收口缺失）")
        for ev, flds in core_schemas.items():
            if len(flds) < 1:
                msgs.append(f"A8 核心事件 {ev} payload schema 字段数为 0（空壳，O1 未闭合）")

    channels = ASYNC_CHANNEL_RE.findall(asyncapi)
    for dup in sorted(k for k, c in collections.Counter(channels).items() if c > 1):
        msgs.append(f"A8 AsyncAPI channel 重复：{dup}")
    reg_set, ch_set = set(topics), set(channels)
    for t in sorted(reg_set - ch_set):
        msgs.append(f"A8 已登记事件未落 AsyncAPI channel：{t}")
    for t in sorted(ch_set - reg_set):
        msgs.append(f"A8 AsyncAPI channel 未登记注册表（禁止临时 Topic）：{t}")
    title_pairs = re.findall(
        r"^  (domain\.procurement\.[a-z0-9_.]+):\n    address: [^\n]*\n    title: (\w+)",
        asyncapi, re.M)
    want_titles = dict(zip(topics, events))
    for topic, event in title_pairs:
        if want_titles.get(topic) != event:
            msgs.append(f"A8 channel title 与注册表不符：{topic} → {event}")

    # --- 8.2 错误码唯一性与双向一致 -----------------------------------------
    a_sec = ""
    if "## 附录 A" in charter:
        a_sec = charter.split("## 附录 A", 1)[1].split("## 附录 B", 1)[0]
    else:
        msgs.append("A8 总纲缺附录 A 错误码目录")
    charter_codes = set(ERROR_CODE_RE.findall(a_sec))
    catalog = CATALOG_CODE_RE.findall(
        openapi.split("x-error-catalog:", 1)[1]) if "x-error-catalog:" in openapi else []
    if not catalog:
        msgs.append("A8 openapi.yaml 缺 x-error-catalog 或未解析到编号")
    for dup in sorted(k for k, c in collections.Counter(catalog).items() if c > 1):
        msgs.append(f"A8 错误码目录编号重复：{dup}")
    if len(catalog) < MIN_ERROR_CODES:
        msgs.append(f"A8 错误码目录 {len(set(catalog))} < 下界 {MIN_ERROR_CODES}（疑似被删）")
    catalog_set = set(catalog)
    for c in sorted(charter_codes - catalog_set):
        msgs.append(f"A8 附录 A 错误码未登记进 openapi catalog：{c}")
    for c in sorted(catalog_set - charter_codes):
        msgs.append(f"A8 openapi catalog 错误码未在附录 A 登记：{c}")
    for name in DESIGN_DOCS:
        for c in sorted(set(ERROR_CODE_RE.findall(ctx.texts.get(name, ""))) - catalog_set):
            msgs.append(f"A8 {name} 出现未登记错误码：{c}（新增须先改附录 A + catalog）")

    # --- 8.3 权限码白名单 ---------------------------------------------------
    whitelist: list[str] = []
    for line in charter.splitlines():
        if "resource 白名单" in line:
            # 同一行还有「v0.2 补齐的 6 项」短列表，必须取最长的那段才是全量白名单
            groups = re.findall(r"`([a-z-]+(?:\s/\s[a-z-]+)+)`", line)
            if groups:
                longest = max(groups, key=lambda g: len(g.split("/")))
                whitelist = [x.strip() for x in longest.split("/")]
            break
    if len(whitelist) < MIN_PERMISSION_RESOURCES:
        msgs.append(f"A8 权限码 resource 白名单 {len(whitelist)} < 下界 "
                    f"{MIN_PERMISSION_RESOURCES}（疑似被删或解析失败）")
    wl_set = set(whitelist)
    for name, text in vols.items():
        for res, acts in PERMISSION_RE.findall(text):
            if res not in wl_set:
                msgs.append(f"A8 {name} 使用白名单外 resource：{res}")
            for action in acts.split("/"):
                if action and action not in PERMISSION_ACTIONS:
                    msgs.append(f"A8 {name} 使用白名单外 action：{action}")
    c_sec = charter.split("## 附录 C", 1)[1].split("## 附录 D", 1)[0] if "## 附录 C" in charter else ""
    for n in range(1, 7):
        if not re.search(rf"^\| 0{n} \|", c_sec, re.M):
            msgs.append(f"A8 附录 C 权限码覆盖表缺 0{n} 卷")

    # --- 8.4 未登记 topic 拦截 ----------------------------------------------
    for name, text in ctx.texts.items():
        for token in DOMAIN_TOKEN_RE.findall(text):
            if token == "domain.procurement.*":
                continue  # 06 卷中继「全量通配」声明，非具体 topic
            if token.endswith((".v1", ".v2")) and token not in reg_set:
                msgs.append(f"A8 {name} 出现未登记临时 topic：{token}")
    if "platform.dead_letter.v1" not in charter or "platform.dead_letter.v1" not in asyncapi:
        msgs.append("A8 平台 DLQ 事件未在附录 B.3 与 AsyncAPI 同时登记")

    # --- 8.5 契约资产与时序图引用 -------------------------------------------
    for token in ("openapi: 3.1.0", "x-status: Proposal", "x-frozen: false",
                  "ProblemDetails", "Money", "x-error-catalog", "x-retry-policy"):
        if token not in openapi:
            msgs.append(f"A8 openapi.yaml 缺必备锚点：{token}")
    for path in OPENAPI_REQUIRED_PATHS:
        if path not in openapi:
            msgs.append(f"A8 openapi.yaml 缺关键端点：{path}")
    for token in ("asyncapi: 3.0.0", "x-generated-from", "x-gate",
                  "x-compatibility", "x-envelope", "additionalProperties: true",
                  "verb-less-topics"):
        if token not in asyncapi:
            msgs.append(f"A8 asyncapi.yaml 缺必备锚点：{token}")
    for token in ("--check", "parse_fields_from_texts", "禁止手改"):
        if token not in gen_src:
            msgs.append(f"A8 生成器缺必备能力：{token}")
    for token in SEQ_SVG_ANCHORS:
        if token not in seq_svg:
            msgs.append(f"A8 关键链路时序 SVG 缺必备锚点：{token}")
    for name, text in vols.items():
        if text.count("../assets/" + SEQ_SVG) < 2:
            msgs.append(f"A8 {name} 时序图引用不足 2 处（§2 组件视图 / §9 工程治理）")
    for token in ("contracts/openapi.yaml", "contracts/asyncapi.yaml",
                  "contracts/generate_asyncapi.py"):
        if token not in charter:
            msgs.append(f"A8 总纲未登记契约工件：{token}")

    # --- 8.6 v0.2 深度章节与上游索引 ----------------------------------------
    for n in range(14, 23):
        if str(n) not in ctx.sections.get("总纲", set()):
            msgs.append(f"A8 总纲缺 v0.2 深度章节 §{n}")
    for token in CHARTER_V02_ANCHORS:
        if token not in charter:
            msgs.append(f"A8 总纲缺 v0.2 必备锚点：{token}")
    for name in ("README", "总体", "领域", "基线"):
        if "A8" not in ctx.texts.get(name, ""):
            msgs.append(f"A8 {name} 未登记门禁 A8（索引失真）")

    # --- 8.7 R5 外部锚点：冻结事件双上游独立登记 + topic 三方一致 --------------
    frozen = _frozen_tables(ctx)
    want = set(FROZEN_EVENT_TYPES)
    for label, table in frozen.items():
        if not table:
            msgs.append(f"A8 外部锚点 {label} 解析为空（fail-closed）")
        for e in sorted(want - set(table)):
            msgs.append(f"A8 外部锚点 {label} 缺冻结事件：{e}")
        for e in sorted(set(table) - want):
            msgs.append(f"A8 外部锚点 {label} 出现硬编码清单外事件：{e}")
    for e in sorted(want):
        vals = {label: table.get(e) for label, table in frozen.items()}
        present = {v for v in vals.values() if v}
        if len(present) > 1:
            msgs.append(f"A8 外部锚点 topic 跨真源不一致：{e} → {vals}")
    reg_map = dict(zip(events, topics))
    for e in sorted(want):
        ref = frozen.get("领域 §6.2", {}).get(e)
        if ref and reg_map.get(e) and reg_map[e] != ref:
            msgs.append(f"A8 外部锚点 附录 B 注册表 {e} topic 与领域 §6.2 冻结值不一致："
                        f"{reg_map[e]} != {ref}")
    return (not msgs), msgs


# ---------------------------------------------------------------- A9
def check_a9(ctx: Ctx) -> tuple[bool, list[str]]:
    """v0.3 逐卷可执行规格：§13 八子节 / 专属锚点 / 七类要素 / 探针 / 总纲与索引。"""
    msgs: list[str] = []
    charter = ctx.texts.get("总纲", "")

    # --- 9.1 总纲 §23 / 附录 F / B12–B17 / L38–L43 -----------------------------
    for token in CHARTER_V03_ANCHORS:
        if token not in charter:
            msgs.append(f"A9 总纲缺 v0.3 必备锚点：{token}")

    # --- 9.2 常量集合与下界（防「删条目恒绿」） --------------------------------
    if len(V03_ANCHORS) != len(V03_VOLUMES):
        msgs.append(f"A9 专属锚点卷数 {len(V03_ANCHORS)} ≠ {len(V03_VOLUMES)}")
    for vol in V03_VOLUMES:
        anchors = V03_ANCHORS.get(vol, ())
        if len(anchors) < MIN_V03_ANCHORS_PER_VOL:
            msgs.append(f"A9 {vol} 专属锚点 {len(anchors)} < 下界 {MIN_V03_ANCHORS_PER_VOL}")
    if len(set(x for a in V03_ANCHORS.values() for x in a)) < sum(len(a) for a in V03_ANCHORS.values()):
        msgs.append("A9 专属锚点跨卷重复（差异化失效）")
    for floor in (MIN_V03_ANCHORS_PER_VOL, MIN_V03_PROBES):
        if not isinstance(floor, int) or floor <= 0:
            msgs.append(f"A9 下界常量非法：{floor!r}")

    # --- 9.3 每卷 §13 结构与差异化 ---------------------------------------------
    for name in V03_VOLUMES:
        text = ctx.texts.get(name, "")
        head = "## 13. 本卷专题深化（v0.3）"
        if head not in text:
            msgs.append(f"A9 {name} 缺 {head}")
            continue
        body = text.split(head, 1)[1]
        parts = re.split(r"^\*\*(13\.\d+)\s", body, flags=re.M)
        secs = {parts[i]: parts[i + 1] for i in range(1, len(parts) - 1, 2)}
        for sub in V03_SUBSECTIONS:
            if sub not in secs:
                msgs.append(f"A9 {name} §13 缺子节 {sub}")
        s131 = secs.get("13.1", "")
        for anchor in V03_ANCHORS.get(name, ()):
            if anchor not in s131:
                msgs.append(f"A9 {name} §13.1 缺本卷专属锚点：{anchor}")
        for sub, tokens in V03_ELEMENT_RULES:
            section = secs.get(sub, "")
            for token in tokens:
                if token not in section:
                    msgs.append(f"A9 {name} §{sub} 缺要素标记：{token}")

        # §11 v0.3 探针（按行计数，避免正文提及 v0.3 造成假绿）
        if "## 11. 测试与负向探针" in text:
            s11 = text.split("## 11. 测试与负向探针", 1)[1].split("## 12.", 1)[0]
            probes = [ln for ln in s11.splitlines()
                      if ln.startswith("| M") and "v0.3" in ln]
            if len(probes) < MIN_V03_PROBES:
                msgs.append(f"A9 {name} §11 v0.3 探针 {len(probes)} < {MIN_V03_PROBES}")
        else:
            msgs.append(f"A9 {name} 缺 §11 测试与负向探针")

        # §12 必须引用总纲 §23（v0.3 验收口径）
        s12 = (text.split("## 12. 验收与待决", 1)[1].split("## 13.", 1)[0]
               if "## 12. 验收与待决" in text else "")
        if "总纲 §23" not in s12:
            msgs.append(f"A9 {name} §12 未引用总纲 §23")

    # --- 9.4 上游索引登记 ------------------------------------------------------
    for name in ("README", "总体", "领域", "基线"):
        if "A9" not in ctx.texts.get(name, ""):
            msgs.append(f"A9 {name} 未登记门禁 A9（索引失真）")
    return (not msgs), msgs


def check_a10(ctx: Ctx) -> tuple[bool, list[str]]:
    """资产裁决与收口守护（总纲附录 F A10.1–A10.9），含 R5 冻结事件外部锚点复核。"""
    msgs: list[str] = []
    readme = ctx.texts.get("README", "")
    charter = ctx.texts.get("总纲", "")
    overall = ctx.texts.get("总体", "")
    art = ctx.artifacts
    for item in ctx.extra_missing:
        msgs.append(f"A10 新真源资产缺失：{item}")

    # --- 10.1 README 旧族状态列与作废引用 ------------------------------------
    sec = ""
    if "## 原有 POC 资产状态登记" in readme:
        sec = readme.split("## 原有 POC 资产状态登记", 1)[1].split("\n## ", 1)[0]
    else:
        msgs.append("A10 旧族状态表：README 缺资产状态登记章节")
    rows = LEGACY_STATUS_ROW_RE.findall(sec)
    if len(rows) != len(LEGACY_DOCS):
        msgs.append(f"A10 旧族状态表 {len(rows)} 行 != {len(LEGACY_DOCS)} 份（状态列缺失或重复）")
    statuses: dict[str, str] = {}
    for fname, status in rows:
        st = status.strip()
        if not st or st not in LEGACY_STATES:
            msgs.append(f"A10 旧族状态表 {fname} 状态为空或非法：{st!r}")
        statuses[fname] = st
    for name in LEGACY_DOCS:
        if name not in statuses:
            msgs.append(f"A10 旧族状态表缺登记：{name}")
    reg = ctx.extra.get("资产登记", "")
    reg_sec = (reg.split("### 3.2", 1)[1].split("## 4.", 1)[0]
               if "### 3.2" in reg else "")
    reg_names = re.findall(r"^\|\s*\d+\s*\|\s*`(采购AI-[^`]+\.md)`", reg_sec, re.M)
    if len(reg_names) != len(LEGACY_DOCS):
        msgs.append(f"A10 资产登记 §3.2 旧族 {len(reg_names)} 行 != {len(LEGACY_DOCS)} 份")
    deprecated = [f for f, s in statuses.items() if s == "作废"]
    for f in deprecated:
        for name, text in {**ctx.texts, **ctx.extra}.items():
            body = text.replace(sec, "") if name == "README" else text
            if name == "资产登记":
                body = body.replace(reg_sec, "")  # 资产登记表本身必须能列出作废资产
            if f in body:
                msgs.append(f"A10 作废文档 {f} 仍被 {name} 引用（禁止复活）")

    # --- 10.2 废弃词与 AI 合规语境 -------------------------------------------
    for name, text in {**ctx.texts, **ctx.extra, **ctx.legacy}.items():
        for i, line in enumerate(text.splitlines(), 1):
            if "自动归档" in line and not any(k in line for k in REVOCATION_MARKERS):
                msgs.append(f"A10 {name}:{i} 「自动归档」脱离撤销语境（R2 AI 合规裁决）")
            for bad in A10_MD_BANNED:
                if bad in line and not any(k in line for k in A10_BANNED_NEG):
                    msgs.append(f"A10 {name}:{i} 废弃词残留：{bad}")
    for svg_name, raw in ctx.svgs.items():
        nodes = SVG_TEXT_RE.findall(raw)
        if not nodes:
            msgs.append(f"A10 {svg_name} 未解析到 <text> 节点（SVG 文字层失效）")
        for node in nodes:
            for bad in SVG_BANNED_TEXTS:
                if bad in node:
                    msgs.append(f"A10 {svg_name} <text> 废弃词残留：{bad}")
            if "自动归档" in node and not any(k in node for k in REVOCATION_MARKERS):
                msgs.append(f"A10 {svg_name} <text> 「自动归档」脱离撤销语境")

    # --- 10.3 D3-v2 ContractReviewed 唯一 payload -----------------------------
    d3 = ctx.extra.get("D3v2", "")
    s2 = (d3.split("## 2. `ContractReviewed` 唯一 payload 定义", 1)[1].split("## 3.", 1)[0]
          if "## 2. `ContractReviewed` 唯一 payload 定义" in d3 else "")
    if not s2:
        msgs.append("A10 D3-v2 缺 §2 唯一 payload 定义")
    for f in CONTRACT_REVIEWED_FIELDS:
        if f"`{f}`" not in s2:
            msgs.append(f"A10 D3-v2 §2 唯一 payload 缺字段：{f}")
    v03_row = next((ln for ln in ctx.texts.get("卷03", "").splitlines()
                    if ln.startswith("|") and "ContractReviewed" in ln), "")
    for f in CONTRACT_REVIEWED_FIELDS:
        if f not in v03_row:
            msgs.append(f"A10 03 卷 §7 速查视图与唯一 payload 不一致，缺：{f}")
    v02_row = next((ln for ln in ctx.texts.get("卷02", "").splitlines()
                    if ln.startswith("|") and "ContractReviewed" in ln), "")
    for token in ("D3-v2 §2", "不重述", "子集"):
        if token not in v02_row:
            msgs.append(f"A10 02 卷 §7 未声明只消费子集 / 唯一 payload 引用：{token}")
    if not any("x-fields:" in ln and all(f in ln for f in CONTRACT_REVIEWED_FIELDS)
               for ln in art.get("asyncapi.yaml", "").splitlines()):
        msgs.append("A10 asyncapi.yaml 的 ContractReviewed x-fields 未投影唯一 payload 7 字段")

    # --- 10.3.1 ai_available 三态（续诊 C-B：D3-v2 §2 与 §3 一致，禁止 boolean）---
    avail_row = next((ln for ln in s2.splitlines() if "`ai_available`" in ln), "")
    if not avail_row:
        msgs.append("A10 C-B ai_available：D3-v2 §2 缺该字段行（三态校验基线缺失）")
    else:
        if "enum(" not in avail_row:
            msgs.append("A10 C-B ai_available：D3-v2 §2 未定义三态枚举（须 enum(AVAILABLE/UNAVAILABLE/UNKNOWN)，与 §3 一致）")
        if "boolean" in avail_row:
            msgs.append("A10 C-B ai_available：D3-v2 §2 仍为 boolean（与 §3 三态冲突，须改枚举）")

    # --- 10.4 Frozen 事件上游登记与陈旧表述 ----------------------------------
    b1 = (charter.split("**B.1 已冻结", 1)[1].split("**B.2", 1)[0]
          if "**B.1 已冻结" in charter else "")
    frozen_rows = [ln for ln in b1.splitlines() if FROZEN_ROW_RE.match(ln)]
    if len(frozen_rows) != len(FROZEN_EVENT_TYPES):
        msgs.append(f"A10 附录 B.1 Frozen 行 {len(frozen_rows)} != "
                    f"{len(FROZEN_EVENT_TYPES)}（外部锚点下界）")
    for ln in frozen_rows:
        if "Frozen" not in ln:
            msgs.append(f"A10 附录 B.1 行缺 Frozen 标记：{ln[:48]}")
    for phrase in ("总体 §7.1 未列", "未在总体 §7.1 登记"):
        if phrase in charter:
            msgs.append(f"A10 总纲残留 L37 陈旧表述：{phrase}")
    upstream = _frozen_rows(overall, "### 7.1 关键事件 payload 契约", "\n> **信封")
    for e in FROZEN_EVENT_TYPES:
        if e not in upstream:
            msgs.append(f"A10 总体 §7.1 未登记冻结事件：{e}")

    # --- 10.4.1 总体 §7.1 INBOUND payload 字段 ↔ D3-v2 §2.1 一致（续诊 C-A 收口，防第三份漂移副本）---
    s71 = overall.split("### 7.1 关键事件 payload 契约", 1)[1] if "### 7.1 关键事件 payload 契约" in overall else ""
    if "\n> **信封" in s71:
        s71 = s71.split("\n> **信封", 1)[0]
    inv_line = next((ln for ln in s71.splitlines()
                     if "InvoiceMatched" in ln and ln.strip().startswith("|")), "")
    if not inv_line:
        msgs.append("A10 C-A 总体 §7.1 缺 InvoiceMatched 行（INBOUND 事件登记缺失）")
    else:
        for must in ("match_status", "matched_at", "match_id"):
            if must not in inv_line:
                msgs.append(f"A10 C-A 总体 §7.1 InvoiceMatched 缺真源字段 {must}（须对齐 D3-v2 §2.1）")
        for drift in ("match_type", "tolerance_applied", "blocked_reason"):
            if drift in inv_line:
                msgs.append(f"A10 C-A 总体 §7.1 InvoiceMatched 残留漂移字段 {drift}（须删除，真源在 D3-v2 §2.1）")

    if "12 条全部补齐" not in ctx.texts.get("领域", ""):
        msgs.append("A10 领域 §6.2 缺「12 条全部补齐」收口说明")

    # --- 10.5 契约工件 / 生成器 / SVG 版本戳 --------------------------------
    for key in ("openapi.yaml", "asyncapi.yaml"):
        body = art.get(key, "")
        if "version: 0.3.0" not in body:
            msgs.append(f"A10 {key} 版本戳不是 0.3.0")
        if "version: 0.2.0" in body:
            msgs.append(f"A10 {key} 残留 0.2.0 版本戳")
    gen = art.get("generate_asyncapi.py", "")
    for token in ("版本 v0.3", '"  version: 0.3.0"'):
        if token not in gen:
            msgs.append(f"A10 生成器版本戳缺：{token}")
    for name in KEY_SVGS:
        svg = ctx.svgs.get(name, "")
        if "v0.3" not in svg:
            msgs.append(f"A10 {name} 缺 v0.3 版本戳")
        if "v0.2" in svg:
            msgs.append(f"A10 {name} 残留 v0.2 版本戳")

    # --- 10.6 一期边界 / 重试分层 / WBS / 新真源可达 -------------------------
    scope = ctx.extra.get("一期边界", "")
    t2 = (scope.split("## 2. 机制 → 一期状态 → 启用触发条件", 1)[1].split("## 3.", 1)[0]
          if "## 2. 机制 → 一期状态 → 启用触发条件" in scope else "")
    mech = [ln for ln in t2.splitlines() if re.match(r"^\| T\d+ \|", ln)]
    if len(mech) != 14:
        msgs.append(f"A10 一期机制表 {len(mech)} 行 != 14")
    for ln in mech:
        # 转义竖线（如 REBUILD\|REDELIVER）不是列分隔符
        cols = [c.strip() for c in re.split(r"(?<!\\)\|", ln.strip().strip("|"))]
        if len(cols) < 4 or not cols[3]:
            msgs.append(f"A10 一期机制表行缺触发条件：{ln[:40]}")
        elif not any(k in cols[2] for k in ("一期启用", "设计储备", "不实现", "分期启用")):
            msgs.append(f"A10 一期机制表行缺一期状态：{ln[:40]}")
    t3 = (scope.split("## 3. 重试预算分层表", 1)[1].split("## 4.", 1)[0]
          if "## 3. 重试预算分层表" in scope else "")
    layers = [ln for ln in t3.splitlines() if re.match(r"^\| L[1-4] \|", ln)]
    if len(layers) != 4:
        msgs.append(f"A10 重试分层表 {len(layers)} 行 != 4")
    if "不同对象" not in t3:
        msgs.append("A10 重试分层表缺「不同对象」显式说明（R6）")
    wbs = (overall.split("## 12. 实施路线与 WBS", 1)[1].split("### 12.1", 1)[0]
           if "## 12. 实施路线与 WBS" in overall else "")
    if "人力（草案" not in wbs:
        msgs.append("A10 WBS 缺人力列（R7 / B19）")
    if "专职率" not in wbs:
        msgs.append("A10 WBS 缺专职率列（R7 / B19）")
    for vol in V03_VOLUMES:
        if "一期状态（R6 / B20，A10 守护）" not in ctx.texts.get(vol, ""):
            msgs.append(f"A10 {vol} §13 缺一期状态标记（须指向机制表编号）")
    for name, path in EXTRA_DOCS.items():
        fn = path.name
        if fn not in readme and fn not in charter:
            msgs.append(f"A10 新真源 {name}（{fn}）未在 README / 总纲登记，索引不可达")

    # --- 10.7 模块注册表 / ATAM / 答辩包 ------------------------------------
    reg6 = (reg.split("## 6. 模块注册表", 1)[1].split("## 7.", 1)[0]
            if "## 6. 模块注册表" in reg else "")
    mods = [ln for ln in reg6.splitlines() if re.match(r"^\| M\d\d \|", ln)]
    if len(mods) != 11:
        msgs.append(f"A10 模块注册表 {len(mods)} 行 != 11（R13 编号唯一性）")
    atam = ctx.extra.get("ATAM", "")
    s3 = (atam.split("## 3. 质量属性场景", 1)[1].split("## 4.", 1)[0]
          if "## 3. 质量属性场景" in atam else "")
    scen = [ln for ln in s3.splitlines() if re.match(r"^\| S\d+ \|", ln)]
    if len(scen) < 10:
        msgs.append(f"A10 ATAM 质量属性场景 {len(scen)} < 10")
    for ln in scen:
        if "**H**" not in ln:
            continue
        cols = [c.strip() for c in re.split(r"(?<!\\)\|", ln.strip().strip("|"))]
        if len(cols) < 9 or any(c in ("", "—", "-", "TBD") for c in cols[-2:]):
            msgs.append(f"A10 ATAM H 场景缺架构决策 / 负向探针：{ln[:48]}")
    s4 = (atam.split("## 4. 敏感点", 1)[1].split("## 5.", 1)[0]
          if "## 4. 敏感点" in atam else "")
    tps = [ln for ln in s4.splitlines() if re.match(r"^\| \*\*TP-\d+\*\* \|", ln)]
    if len(tps) < 3:
        msgs.append(f"A10 ATAM 权衡点 {len(tps)} < 3")
    brief = ctx.extra.get("答辩包", "")
    b2 = (brief.split("## 2. JD 14 项逐条映射", 1)[1].split("## 3.", 1)[0]
          if "## 2. JD 14 项逐条映射" in brief else "")
    jd = [ln for ln in b2.splitlines() if re.match(r"^\| (?:职责|要求)\d \|", ln)]
    if len(jd) != 14:
        msgs.append(f"A10 答辩包 JD 14 项映射 {len(jd)} 行 != 14")

    # --- 10.8 跨域契约三域（R8「明示边界 + 最小契约」分支） -------------------
    cross = ctx.extra.get("跨域契约", "")
    for n, token in ((3, "Item"), (4, "Inventory"), (5, "Warehouse")):
        if not re.search(rf"^## {n}\. .*{token}", cross, re.M):
            msgs.append(f"A10 跨域契约缺 §{n} {token} 域章节")
    if "不生产、不裁决、不写入" not in cross:
        msgs.append("A10 跨域契约缺边界声明（R8 明示边界分支）")
    for path in ("/procurement/ops/items:", "/procurement/ops/inventory:",
                 "/procurement/ops/warehouse-sites:"):
        if path not in art.get("openapi.yaml", ""):
            msgs.append(f"A10 OpenAPI 缺跨域只读投影端点：{path}")

    # --- 10.9 六卷 §6 端点 ↔ OpenAPI 方法级双向覆盖（R8 契约工件闭环） ---------
    # 闭合诊断 R8 的验收判据「openapi 端点数 ≥ 各卷 §6 的 55」。
    # 判据读两处独立文本（LLD §6 表 / OpenAPI paths），下界与投影白名单写死在脚本，
    # 避免「两边同时删一行 → 覆盖率恒绿」的闭环自证。
    lld_pairs: set[tuple[str, str]] = set()
    lld_rows = 0
    for vol in V03_VOLUMES:
        vlines = ctx.texts.get(vol, "").splitlines()
        s6 = next((i for i, ln in enumerate(vlines) if re.match(r"^## 6\.", ln)), None)
        if s6 is None:
            msgs.append(f"A10 端点覆盖 {vol} 缺 §6 API 契约章节")
            continue
        e6 = next((i for i in range(s6 + 1, len(vlines))
                   if re.match(r"^## 7\.", vlines[i])), len(vlines))
        for m in LLD_ENDPOINT_ROW_RE.finditer("\n".join(vlines[s6:e6])):
            lld_rows += 1
            lld_pairs.add((m.group(1).lower(), re.sub(r"^/api/v1", "", m.group(2))))
    oa_text = art.get("openapi.yaml", "")
    oa_pairs: set[tuple[str, str]] = set()
    if "\npaths:\n" in oa_text:
        oa_body = oa_text.split("\npaths:\n", 1)[1].split("\ncomponents:\n", 1)[0]
        cur_path = ""
        for ln in oa_body.splitlines():
            pm = OA_PATH_RE.match(ln)
            if pm:
                cur_path = pm.group(1)
                continue
            mm = OA_METHOD_RE.match(ln)
            if mm and cur_path:
                oa_pairs.add((mm.group(1), cur_path))
    else:
        msgs.append("A10 端点覆盖 OpenAPI 缺 paths 段")
    if lld_rows < MIN_LLD_ENDPOINT_ROWS:
        msgs.append(f"A10 端点覆盖 LLD §6 行数 {lld_rows} < 外部锚点下界 "
                    f"{MIN_LLD_ENDPOINT_ROWS}（疑似删行 / 解析失败）")
    if not oa_pairs:
        msgs.append("A10 端点覆盖 OpenAPI paths 解析为空（契约工件不可读）")
    for method, path in sorted(lld_pairs - oa_pairs):
        msgs.append(f"A10 端点覆盖 OpenAPI 缺 {method.upper()} {path}（六卷 §6 已声明）")
    for method, path in sorted(oa_pairs - lld_pairs - EXTRA_OPENAPI_ENDPOINTS):
        msgs.append(f"A10 端点覆盖 OpenAPI 未登记端点 {method.upper()} {path}"
                    "（须先补六卷 §6 或登记为跨域只读投影）")
    for method, path in sorted(EXTRA_OPENAPI_ENDPOINTS - oa_pairs):
        msgs.append(f"A10 端点覆盖 缺跨域只读投影端点 {method.upper()} {path}")

    # --- 10.10 RA-2 多租户可见性矩阵冻结安全基线（续诊 O4 闭合 R-4 安全基线悬空）---
    o91 = (overall.split("### 9.1", 1)[1].split("\n## ", 1)[0]
           if "### 9.1" in overall else "")
    if not o91:
        msgs.append("A10 总体 §9.1 缺多租户维度模型章节（RA-2 载体）")
    else:
        if "Frozen-Draft" not in o91 and "Frozen v" not in o91:
            msgs.append("A10 RA-2 可见性矩阵未标冻结基线版本戳（R-4 安全基线悬空）")
        if "会签" not in o91 and "签名" not in o91:
            msgs.append("A10 RA-2 可见性矩阵缺审批签名栏（冻结基线须待会签生效）")
        for role in ("平台管理员", "集团采购", "法人采购", "供应商门户账号"):
            if role not in o91:
                msgs.append(f"A10 RA-2 可见性矩阵缺角色行：{role}")

    # --- 10.11 ATAM 正式排序会议程 + H/M 预排序（续诊 O6 闭合 R-6 排序会待做）---
    atam7 = (atam.split("## 7.", 1)[1] if "## 7." in atam else "")
    if not atam7:
        msgs.append("A10 ATAM 缺 §7 正式排序会议程（O6 / R-6 排序会待做）")
    else:
        if "排序会" not in atam7 or "预排序" not in atam7:
            msgs.append("A10 ATAM §7 缺排序会议程或 H/M 预排序（O6 须含二者）")
        if "S1" not in atam7 or "S12" not in atam7:
            msgs.append("A10 ATAM §7 预排序未覆盖全量场景（须含 S1–S12）")
        if "待评审会确认" not in atam7 and "待确认" not in atam7:
            msgs.append("A10 ATAM §7 未诚实标注排序为 owner 边界（须待评审会确认）")

    # --- 10.12 一期边界工程落地最小切片骨架（续诊 O7 闭合 R-7 开工蓝图）---
    scope7 = (scope.split("## 7.", 1)[1] if "## 7." in scope else "")
    if not scope7:
        msgs.append("A10 一期边界缺 §7 工程落地最小切片骨架（O7 / R-7 开工蓝图）")
    else:
        if "切片" not in scope7:
            msgs.append("A10 一期边界 §7 缺最小切片范围描述（O7）")
        if "t_proc_" not in scope7:
            msgs.append("A10 一期边界 §7 缺 t_proc_ DDL 草案块（O7 开工蓝图）")
        if "bone-init.sql" not in scope7 or "Proposal" not in scope7:
            msgs.append("A10 一期边界 §7 缺 Proposal / 不写 bone-init.sql 诚实边界标注（O7）")

    for name in ("README", "总体", "领域", "基线"):
        if "A10" not in ctx.texts.get(name, ""):
            msgs.append(f"A10 {name} 未登记门禁 A10（索引失真）")
    return (not msgs), msgs


CHECKS: dict[str, Callable[[Ctx], tuple[bool, list[str]]]] = {
    "A1": check_a1,
    "A2": check_a2,
    "A3": check_a3,
    "A4": check_a4,
    "A5": check_a5,
    "A6": check_a6,
    "A7": check_a7,
    "A8": check_a8,
    "A9": check_a9,
    "A10": check_a10,
}

DESCRIPTIONS = {
    "A1": "版本与真源互引",
    "A2": "§ 引用可解析 / 编号不重复",
    "A3": "链接与 SVG 资产",
    "A4": "废弃误判表述",
    "A5": "平台实证锚点",
    "A6": "空集合 fail-closed",
    "A7": "LLD 族 12 节模板与模块覆盖",
    "A8": "契约工件一致性 / 码字唯一性 / topic 白名单",
    "A9": "逐卷可执行规格 §13 八子节 / 专属锚点 / 七类要素 / v0.3 探针",
    "A10": "资产裁决 / AI 合规 / 外部锚点 / 版本戳 / 一期边界 / 新资产索引",
}


def run(ctx: Ctx) -> dict[str, tuple[bool, list[str]]]:
    return {name: fn(ctx) for name, fn in CHECKS.items()}


def report(results: dict[str, tuple[bool, list[str]]]) -> bool:
    ok = True
    for name, (passed, msgs) in results.items():
        print(f"[{'PASS' if passed else 'FAIL'}] {name} {DESCRIPTIONS[name]}")
        if not passed:
            ok = False
            for m in msgs:
                print(f"       - {m}")
    return ok


def _mutate(ctx: Ctx, texts: dict[str, str] | None = None,
            assets: list[Path] | None = None,
            sections: dict[str, set[str]] | None = None,
            missing: list[str] | None = None,
            artifacts: dict[str, str] | None = None,
            extra: dict[str, str] | None = None,
            svgs: dict[str, str] | None = None,
            legacy: dict[str, str] | None = None,
            extra_missing: list[str] | None = None) -> Ctx:
    new_texts = dict(ctx.texts if texts is None else texts)
    new_secs = {k: _sections(v) for k, v in new_texts.items()} if sections is None else sections
    return Ctx(
        texts=new_texts,
        sections=new_secs,
        assets=list(ctx.assets if assets is None else assets),
        missing=list(ctx.missing if missing is None else missing),
        artifacts=dict(ctx.artifacts if artifacts is None else artifacts),
        artifact_missing=list(ctx.artifact_missing),
        extra=dict(ctx.extra if extra is None else extra),
        svgs=dict(ctx.svgs if svgs is None else svgs),
        legacy=dict(ctx.legacy if legacy is None else legacy),
        extra_missing=list(ctx.extra_missing if extra_missing is None else extra_missing),
    )


def self_test() -> int:
    base = load()
    baseline = run(base)
    if not report(baseline):
        print("\n[SELF-TEST] 基线未全绿，负向反证无意义，先修复真实问题。")
        return 1
    print(f"\n[SELF-TEST] 基线 {len(CHECKS)}/{len(CHECKS)} 全绿，开始注入式负向反证：\n")

    def m_a1(c: Ctx) -> Ctx:
        t = dict(c.texts)
        t["总体"] = t["总体"].replace("> **版本**：v0.3", "> **版本**：v9.9", 1)
        return _mutate(c, texts=t)

    def m_a2(c: Ctx) -> Ctx:
        t = dict(c.texts)
        t["领域"] = t["领域"] + "\n\n> 注入探针：参见 总体 §99.99。\n"
        return _mutate(c, texts=t)

    def m_a2_bare(c: Ctx) -> Ctx:
        t = dict(c.texts)
        t["总纲"] = t["总纲"] + "\n> 注入探针：参见 §98.7（带小数的悬空内部锚点）。\n"
        return _mutate(c, texts=t)

    def m_a3(c: Ctx) -> Ctx:
        t = dict(c.texts)
        t["README"] = t["README"].replace(
            "](./assets/procuraAI-BONE能力对齐图.svg)",
            "](./assets/procuraAI-不存在的图.svg)", 1)
        return _mutate(c, texts=t)

    def m_a4(c: Ctx) -> Ctx:
        t = dict(c.texts)
        t["领域"] = t["领域"] + "\n\n> 注入探针：本方案采用承接所有审批的单一流程。\n"
        return _mutate(c, texts=t)

    def m_a5(c: Ctx) -> Ctx:
        t = dict(c.texts)
        t["基线"] = t["基线"].replace("publishFrom", "")  # 全量删除，避免残留导致判据假绿
        return _mutate(c, texts=t)

    def m_a6(c: Ctx) -> Ctx:
        t = dict(c.texts)
        t.pop("领域", None)
        return _mutate(c, texts=t, missing=["领域: injected"])

    def m_a1_lld(c: Ctx) -> Ctx:
        t = dict(c.texts)
        t["卷01"] = t["卷01"].replace(f"> **版本**：{LLD_VERSION}", "> **版本**：v9.9", 1)
        return _mutate(c, texts=t)

    def m_a3_lld(c: Ctx) -> Ctx:
        t = dict(c.texts)
        t["卷01"] = t["卷01"].replace(
            "](../采购总体架构方案.md)", "](../不存在的真源.md)", 1)
        return _mutate(c, texts=t)

    def m_a7_section(c: Ctx) -> Ctx:
        t = dict(c.texts)
        t["卷04"] = t["卷04"].replace("## 5. 状态机", "## 5X. 状态机", 1)
        return _mutate(c, texts=t)

    def m_a7_coverage(c: Ctx) -> Ctx:
        t = dict(c.texts)
        t["总纲"] = t["总纲"].replace("bone-procurement-risk", "", 1)
        return _mutate(c, texts=t)

    def m_a8_registry(c: Ctx) -> Ctx:
        t = dict(c.texts)
        t["总纲"] = t["总纲"].replace(
            "| `ProspectCreated` | `domain.procurement.prospect_created.v1` | 02 | 01 | "
            "`tenant_id + supplier_id` | — |\n", "", 1)
        return _mutate(c, texts=t)

    def m_a8_channel(c: Ctx) -> Ctx:
        a = dict(c.artifacts)
        a["asyncapi.yaml"] = a["asyncapi.yaml"].replace(
            "domain.procurement.recon_diff_found.v1:",
            "domain.procurement.ghost_event.v1:", 1)
        return _mutate(c, artifacts=a)

    def m_a8_error_code(c: Ctx) -> Ctx:
        t = dict(c.texts)
        t["卷04"] = t["卷04"] + "\n> 注入探针：错误码 39999 未登记。\n"
        return _mutate(c, texts=t)

    def m_a8_permission(c: Ctx) -> Ctx:
        t = dict(c.texts)
        t["卷01"] = t["卷01"] + "\n> 注入探针：权限 `procurement:ghost-resource:write`。\n"
        return _mutate(c, texts=t)

    def m_a8_topic(c: Ctx) -> Ctx:
        t = dict(c.texts)
        t["卷02"] = t["卷02"] + "\n> 注入探针：临时 topic `domain.procurement.temp_adhoc.v1`。\n"
        return _mutate(c, texts=t)

    def m_a8_seq_ref(c: Ctx) -> Ctx:
        t = dict(c.texts)
        t["卷03"] = t["卷03"].replace(
            "[关键链路时序图](../assets/procuraAI-关键链路时序.svg) 序列",
            "（时序图引用被删）序列", 1)
        return _mutate(c, texts=t)

    def m_a8_catalog(c: Ctx) -> Ctx:
        a = dict(c.artifacts)
        a["openapi.yaml"] = a["openapi.yaml"].replace(
            "    - { code: '31710', desc: '分页超 max_page_size', volume: '总纲 §17.4' }\n", "", 1)
        return _mutate(c, artifacts=a)

    def m_a8_openapi_path(c: Ctx) -> Ctx:
        a = dict(c.artifacts)
        a["openapi.yaml"] = a["openapi.yaml"].replace(
            "  /portal/invoices:", "  /portal/_invoices_removed:", 1)
        return _mutate(c, artifacts=a)

    def m_a9_subsection(c: Ctx) -> Ctx:
        t = dict(c.texts)
        t["卷01"] = re.sub(r"^\*\*13\.4 [^\n]*\n", "", t["卷01"], count=1, flags=re.M)
        return _mutate(c, texts=t)

    def m_a9_anchor(c: Ctx) -> Ctx:
        t = dict(c.texts)
        t["卷02"] = t["卷02"].replace("双密钥分持", "密钥托管")
        return _mutate(c, texts=t)

    def m_a9_probe(c: Ctx) -> Ctx:
        t = dict(c.texts)
        t["卷05"] = re.sub(r"^\| M5-1[23] \|[^\n]*\n", "", t["卷05"], flags=re.M)
        return _mutate(c, texts=t)

    def m_a8_external_topic(c: Ctx) -> Ctx:
        t = dict(c.texts)
        t["领域"] = t["领域"].replace(
            "| `SupplierChanged` | `domain.procurement.supplier_changed.v1` |",
            "| `SupplierChanged` | `domain.procurement.supplier_changed_alt.v1` |", 1)
        return _mutate(c, texts=t)

    def m_a8_external_missing(c: Ctx) -> Ctx:
        t = dict(c.texts)
        t["总体"] = "".join(ln for ln in t["总体"].splitlines(keepends=True)
                            if not ln.startswith("| `RiskAlertRaised` |"))
        return _mutate(c, texts=t)

    def m_a10_status(c: Ctx) -> Ctx:
        t = dict(c.texts)
        t["README"] = t["README"].replace("| 历史归档 | ADR-06 已撤销", "|  | ADR-06 已撤销", 1)
        return _mutate(c, texts=t)

    def m_a10_deprecated(c: Ctx) -> Ctx:
        t = dict(c.texts)
        t["README"] = t["README"].replace("| 历史归档 | ADR-06 已撤销", "| 作废 | ADR-06 已撤销", 1)
        t["总纲"] = t["总纲"] + "\n> 注入探针：参见 采购AI-智能风险审查与合同智能处理-PRD.md。\n"
        return _mutate(c, texts=t)

    def m_a10_svg_banned(c: Ctx) -> Ctx:
        s = dict(c.svgs)
        k = "procuraAI-技术架构.svg"
        s[k] = s[k].replace("</svg>", '<text x="1" y="1">AuditBus</text>\n</svg>', 1)
        return _mutate(c, svgs=s)

    def m_a10_svg_ai(c: Ctx) -> Ctx:
        s = dict(c.svgs)
        k = "procuraAI-业务闭环.svg"
        s[k] = s[k].replace("</svg>", '<text x="1" y="1">低风险自动归档</text>\n</svg>', 1)
        return _mutate(c, svgs=s)

    def m_a10_revocation(c: Ctx) -> Ctx:
        t = dict(c.texts)
        t["卷03"] = t["卷03"] + "\n> 注入探针：低风险条目自动归档，无需人工确认。\n"
        return _mutate(c, texts=t)

    def m_a10_payload(c: Ctx) -> Ctx:
        e = dict(c.extra)
        e["D3v2"] = re.sub(r"^\| `ai_available` \| .*\n", "", e["D3v2"], count=1, flags=re.M)
        return _mutate(c, extra=e)

    def m_a10_frozen_row(c: Ctx) -> Ctx:
        t = dict(c.texts)
        t["总纲"] = "".join(
            ln for ln in t["总纲"].splitlines(keepends=True)
            if not ln.startswith("| `SupplierChanged` | `domain.procurement.supplier_changed.v1` | 01 MDM"))
        return _mutate(c, texts=t)

    def m_a10_version(c: Ctx) -> Ctx:
        a = dict(c.artifacts)
        a["openapi.yaml"] = a["openapi.yaml"].replace("version: 0.3.0", "version: 0.2.0", 1)
        return _mutate(c, artifacts=a)

    def m_a10_mechanism(c: Ctx) -> Ctx:
        e = dict(c.extra)
        e["一期边界"] = "".join(ln for ln in e["一期边界"].splitlines(keepends=True)
                                if not ln.startswith("| T14 |"))
        return _mutate(c, extra=e)

    def m_a10_wbs(c: Ctx) -> Ctx:
        t = dict(c.texts)
        t["总体"] = t["总体"].replace("人力（草案，待评审冻结）", "范围（草案）", 1)
        return _mutate(c, texts=t)

    def m_a10_phase_marker(c: Ctx) -> Ctx:
        t = dict(c.texts)
        t["卷01"] = t["卷01"].replace("一期状态（R6 / B20，A10 守护）", "（标记被删）", 1)
        return _mutate(c, texts=t)

    def m_a10_newdoc(c: Ctx) -> Ctx:
        e = dict(c.extra)
        e.pop("ATAM", None)
        return _mutate(c, extra=e, extra_missing=["extra:ATAM: injected"])

    def m_a10_tradeoff(c: Ctx) -> Ctx:
        e = dict(c.extra)
        e["ATAM"] = "".join(
            ln for ln in e["ATAM"].splitlines(keepends=True)
            if not ln.startswith(("| **TP-3** |", "| **TP-4** |", "| **TP-5** |")))
        return _mutate(c, extra=e)

    def m_a10_jd(c: Ctx) -> Ctx:
        e = dict(c.extra)
        e["答辩包"] = "".join(ln for ln in e["答辩包"].splitlines(keepends=True)
                              if not ln.startswith("| 要求7 |"))
        return _mutate(c, extra=e)

    def m_a8_closed_loop(c: Ctx) -> Ctx:
        """R5 核心反证：注册表改错 + 同步重生成工件，零漂移仍绿，只有外部锚点能抓。"""
        t = dict(c.texts)
        t["总纲"] = t["总纲"].replace(
            "| `SupplierChanged` | `domain.procurement.supplier_changed.v1` | 01 MDM / OUT |",
            "| `SupplierChanged` | `domain.procurement.supplier_changed_alt.v1` | 01 MDM / OUT |", 1)
        gen = _generator_namespace(c.artifacts["generate_asyncapi.py"])
        rows = gen["parse_registry"](t["总纲"])
        vols = {n: t.get(n, "") for n in DESIGN_DOCS if n != "总纲"}
        fields = gen["parse_fields_from_texts"](rows, vols)
        a = dict(c.artifacts)
        a["asyncapi.yaml"] = gen["render"](rows, fields)
        return _mutate(c, texts=t, artifacts=a)

    def m_a9_s12(c: Ctx) -> Ctx:
        t = dict(c.texts)
        t["卷01"] = t["卷01"].replace(
            "（总纲 §23 / 门禁 A9）", "（引用被删 / 门禁 A9）", 1)
        return _mutate(c, texts=t)

    def m_a10_reg_sec(c: Ctx) -> Ctx:
        e = dict(c.extra)
        e["资产登记"] = "".join(
            ln for ln in e["资产登记"].splitlines(keepends=True)
            if not ln.startswith("| 1 | `采购AI-智能风险审查与合同智能处理-PRD.md`"))
        return _mutate(c, extra=e)

    def m_a10_v03_fields(c: Ctx) -> Ctx:
        t = dict(c.texts)
        t["卷03"] = t["卷03"].replace(
            "review_snapshot_ref, ai_available, model_version_snapshot",
            "review_snapshot_ref, model_version_snapshot", 1)
        return _mutate(c, texts=t)

    def m_a10_stale_l37(c: Ctx) -> Ctx:
        t = dict(c.texts)
        t["总纲"] = t["总纲"] + "\n> 注入探针：总体 §7.1 未列该事件。\n"
        return _mutate(c, texts=t)

    def m_a10_svg_version(c: Ctx) -> Ctx:
        s = dict(c.svgs)
        k = "procuraAI-采购总体逻辑架构.svg"
        s[k] = s[k].replace("v0.3", "（版本戳被删）", 1)
        return _mutate(c, svgs=s)

    def m_a10_retry(c: Ctx) -> Ctx:
        e = dict(c.extra)
        e["一期边界"] = "".join(ln for ln in e["一期边界"].splitlines(keepends=True)
                                if not ln.startswith("| L4 |"))
        return _mutate(c, extra=e)

    def m_a10_modules(c: Ctx) -> Ctx:
        e = dict(c.extra)
        e["资产登记"] = "".join(ln for ln in e["资产登记"].splitlines(keepends=True)
                                if not ln.startswith("| M11 |"))
        return _mutate(c, extra=e)

    def m_a10_scenarios(c: Ctx) -> Ctx:
        e = dict(c.extra)
        e["ATAM"] = "".join(
            ln for ln in e["ATAM"].splitlines(keepends=True)
            if not ln.startswith(("| S10 |", "| S11 |", "| S12 |")))
        return _mutate(c, extra=e)

    def m_a10_index(c: Ctx) -> Ctx:
        t = dict(c.texts)
        t["基线"] = t["基线"].replace("A10", "")
        return _mutate(c, texts=t)

    def m_a10_cross_chapter(c: Ctx) -> Ctx:
        e = dict(c.extra)
        e["跨域契约"] = e["跨域契约"].replace(
            "## 4. Inventory（库存）域", "## 4X. Inventory（库存）域", 1)
        return _mutate(c, extra=e)

    def m_a10_cross_endpoint(c: Ctx) -> Ctx:
        a = dict(c.artifacts)
        a["openapi.yaml"] = a["openapi.yaml"].replace(
            "  /procurement/ops/items:", "  /_items_removed:", 1)
        return _mutate(c, artifacts=a)

    def m_a10_scenario_probe(c: Ctx) -> Ctx:
        e = dict(c.extra)
        e["ATAM"] = e["ATAM"].replace(
            "| AD-1/AD-2 | `03 卷 §11` M3-13 |", "| AD-1/AD-2 | — |", 1)
        return _mutate(c, extra=e)

    def m_a10_endpoint_missing(c: Ctx) -> Ctx:
        a = dict(c.artifacts)
        a["openapi.yaml"] = a["openapi.yaml"].replace(
            "  /procurement/requisitions:\n", "  /_requisitions_removed:\n", 1)
        return _mutate(c, artifacts=a)

    def m_a10_endpoint_lld_row(c: Ctx) -> Ctx:
        t = dict(c.texts)
        t["卷01"] = "".join(ln for ln in t["卷01"].splitlines(keepends=True)
                            if "`/api/v1/procurement/vendors`" not in ln)
        return _mutate(c, texts=t)

    def m_a10_endpoint_stray(c: Ctx) -> Ctx:
        a = dict(c.artifacts)
        a["openapi.yaml"] = a["openapi.yaml"].replace(
            "\ncomponents:\n",
            "\n  /procurement/stray-probe:\n    get:\n      operationId: strayProbe\n"
            "      responses:\n        '200': { $ref: '#/components/responses/Ok' }\n"
            "\ncomponents:\n", 1)
        return _mutate(c, artifacts=a)

    def m_a8_core_payload(c: Ctx) -> Ctx:
        """续诊 O1 反证：清空 D3-v2 §2.1 中 GoodsReceived 子表字段行（保留标题），制造核心事件空壳。"""
        a = dict(c.artifacts)
        d3 = a["D3v2"]
        new = re.sub(
            r"(### GoodsReceived（INBOUND.*?\n)((?:^\|.*\n)+)",
            lambda m: m.group(1), d3, flags=re.M | re.S)
        a["D3v2"] = new
        return _mutate(c, artifacts=a)

    def m_a10_ra2(c: Ctx) -> Ctx:
        """续诊 O4 反证：删除 §9.1 整个 RA-2 冻结状态段（版本戳 + 会签栏），使 10.10 转红。"""
        t = dict(c.texts)
        t["总体"] = re.sub(r"> \*\*冻结状态：Frozen-Draft.*?安全基线悬空）。\n", "", t["总体"], flags=re.S)
        return _mutate(c, texts=t)

    def m_a10_atam7(c: Ctx) -> Ctx:
        """续诊 O6 反证：删除 ATAM §7 整个排序会议程段，使 10.11 转红。"""
        e = dict(c.extra)
        e["ATAM"] = re.sub(r"\n## 7\..*", "", e["ATAM"], flags=re.S)
        return _mutate(c, extra=e)

    def m_a10_slice(c: Ctx) -> Ctx:
        """续诊 O7 反证：删除一期边界 §7 整个最小切片骨架段，使 10.12 转红。"""
        e = dict(c.extra)
        e["一期边界"] = re.sub(r"\n## 7\..*", "", e["一期边界"], flags=re.S)
        return _mutate(c, extra=e)

    def m_a10_ca(c: Ctx) -> Ctx:
        """续诊 C-A 反证：把总体 §7.1 InvoiceMatched 行字段改回漂移版（match_type/tolerance_applied/blocked_reason），使 10.4.1 转红。"""
        t = dict(c.texts)
        good = ("match_id, invoice_no, po_no, match_status(enum MATCHED/EXCEPTION/REVIEW), "
                "matched_amount, exception_code?, matched_at")
        drifted = "invoice_no, match_type(2W/3W), tolerance_applied, exception_code?, blocked_reason?, matched_amount"
        t["总体"] = t["总体"].replace(good, drifted, 1)
        return _mutate(c, texts=t)

    def m_a10_cb(c: Ctx) -> Ctx:
        """续诊 C-B 反证：把 D3-v2 §2 ai_available 改回 boolean，使 10.3.1 转红。"""
        e = dict(c.extra)
        e["D3v2"] = e["D3v2"].replace(
            "| `ai_available` | enum(`AVAILABLE`/`UNAVAILABLE`/`UNKNOWN`) | ✅ |",
            "| `ai_available` | boolean | ✅ |", 1)
        return _mutate(c, extra=e)

    mutations = [
        ("A1", "版本号漂移（v0.3 → v9.9）", m_a1, "版本 v9.9"),
        ("A1", f"LLD 卷版本头漂移（{LLD_VERSION} → v9.9）", m_a1_lld, "版本头缺失"),
        ("A2", "注入悬空跨文档引用 总体§99.99", m_a2, "悬空跨文档引用"),
        ("A2", "注入带小数的悬空内部锚点 §98.7", m_a2_bare, "未解析 §98.7"),
        ("A3", "注入缺失 SVG 相对链接", m_a3, "相对链接缺失"),
        ("A3", "注入 LLD 卷缺失上游链接", m_a3_lld, "相对链接缺失"),
        ("A4", "注入脱离否定语境的废弃决策表述", m_a4, "废弃表述脱离否定语境"),
        ("A5", "删除平台实证锚点 publishFrom", m_a5, "缺平台实证锚点"),
        ("A6", "删除一份真源文档（空集合 / 缺文件）", m_a6, "文档集合不完整"),
        ("A7", "删除一卷 §5 状态机章节", m_a7_section, "缺 LLD 章节"),
        ("A7", "总纲模块覆盖矩阵删掉一个模块", m_a7_coverage, "模块覆盖矩阵缺"),
        ("A8", "附录 B 注册表删掉一条事件（零漂移 + 下界）", m_a8_registry, "注册表条目"),
        ("A8", "AsyncAPI 注入未登记 channel", m_a8_channel, "漂移"),
        ("A8", "卷内注入未登记错误码 39999", m_a8_error_code, "未登记错误码"),
        ("A8", "卷内注入白名单外权限 resource", m_a8_permission, "白名单外 resource"),
        ("A8", "卷内注入未登记临时 topic", m_a8_topic, "未登记临时 topic"),
        ("A8", "删除一卷的时序图引用（§2/§9 双引用）", m_a8_seq_ref, "时序图引用不足"),
        ("A8", "openapi catalog 删掉一条错误码（反向一致性）", m_a8_catalog, "错误码目录"),
        ("A8", "openapi.yaml 删掉一个关键端点", m_a8_openapi_path, "缺关键端点"),
        ("A9", "删掉一卷 §13.4 子节标题（八子节完整性）", m_a9_subsection, "缺子节 13.4"),
        ("A9", "删掉 02 卷专属锚点「双密钥分持」（防复制充数）", m_a9_anchor, "缺本卷专属锚点"),
        ("A9", "删掉 05 卷两条 v0.3 探针（§11 探针下界）", m_a9_probe, "v0.3 探针"),
        ("A8", "领域 §6.2 改一条冻结 topic（外部锚点三方比对）", m_a8_external_topic, "外部锚点"),
        ("A8", "总体 §7.1 删一条冻结事件（外部锚点下界）", m_a8_external_missing, "外部锚点"),
        ("A10", "README 旧族状态列删一个值", m_a10_status, "旧族状态"),
        ("A10", "把一份旧文档改判作废且仍被新族引用", m_a10_deprecated, "作废文档"),
        ("A10", "SVG <text> 注入已判废词 AuditBus", m_a10_svg_banned, "废弃词"),
        ("A10", "SVG <text> 注入无语境自动归档", m_a10_svg_ai, "撤销语境"),
        ("A10", "正文注入无语境自动归档", m_a10_revocation, "撤销语境"),
        ("A10", "D3-v2 唯一 payload 删一个字段", m_a10_payload, "唯一 payload"),
        ("A10", "附录 B.1 删一条 Frozen 行", m_a10_frozen_row, "B.1"),
        ("A10", "openapi 版本戳退回 0.2.0", m_a10_version, "版本戳"),
        ("A10", "一期机制表删一行", m_a10_mechanism, "机制表"),
        ("A10", "WBS 删人力列", m_a10_wbs, "WBS"),
        ("A10", "一卷删 §13 一期状态标记", m_a10_phase_marker, "一期状态"),
        ("A10", "删掉一份新增真源资产", m_a10_newdoc, "新真源"),
        ("A10", "ATAM 删到只剩 2 个权衡点", m_a10_tradeoff, "权衡点"),
        ("A10", "答辩包删一行 JD 映射", m_a10_jd, "JD 14"),
        ("A8", "注册表改错并重生成工件（R5 闭环自证反证）", m_a8_closed_loop, "外部锚点"),
        ("A9", "一卷 §12 删掉总纲 §23 引用", m_a9_s12, "§12 未引用"),
        ("A10", "资产登记 §3.2 删一行旧族", m_a10_reg_sec, "§3.2"),
        ("A10", "03 卷 §7 速查视图删一个 payload 字段", m_a10_v03_fields, "03 卷 §7"),
        ("A10", "总纲注入 L37 陈旧表述", m_a10_stale_l37, "陈旧表述"),
        ("A10", "一张新 SVG 删掉 v0.3 版本戳", m_a10_svg_version, "版本戳"),
        ("A10", "重试分层表删一行", m_a10_retry, "重试分层表"),
        ("A10", "模块注册表删一行", m_a10_modules, "模块注册表"),
        ("A10", "ATAM 场景删到 9 个", m_a10_scenarios, "质量属性场景"),
        ("A10", "ATAM 一个 H 场景删掉负向探针", m_a10_scenario_probe, "H 场景缺"),
        ("A10", "上游索引删掉 A10 登记", m_a10_index, "未登记门禁 A10"),
        ("A10", "跨域契约删一个域章节标题", m_a10_cross_chapter, "域章节"),
        ("A10", "OpenAPI 删跨域只读投影端点", m_a10_cross_endpoint, "跨域只读投影端点"),
        ("A10", "OpenAPI 删一条六卷 §6 已声明端点", m_a10_endpoint_missing, "端点覆盖 OpenAPI 缺"),
        ("A10", "六卷 §6 删一行端点（外部锚点下界）", m_a10_endpoint_lld_row, "外部锚点下界"),
        ("A10", "OpenAPI 注入未登记端点（双向覆盖）", m_a10_endpoint_stray, "未登记端点"),
        ("A8", "D3-v2 §2.1 核心事件 GoodsReceived 子表清空（O1 收口空壳）", m_a8_core_payload, "空壳"),
        ("A10", "总体 §9.1 RA-2 冻结基线版本戳 + 会签栏被抹（O4 回退）", m_a10_ra2, "冻结基线"),
        ("A10", "ATAM §7 排序会议程 + 预排序被删（O6 回退）", m_a10_atam7, "排序会议程"),
        ("A10", "一期边界 §7 最小切片骨架被删（O7 回退）", m_a10_slice, "切片骨架"),
        ("A10", "总体 §7.1 InvoiceMatched 改回漂移字段（C-A 第三份副本回退）", m_a10_ca, "C-A 总体 §7.1"),
        ("A10", "D3-v2 §2 ai_available 改回 boolean（C-B 三态回退）", m_a10_cb, "C-B ai_available"),
    ]
    all_ok = True
    for target, desc, mutator, expect in mutations:
        mutated = mutator(base)
        res = run(mutated)
        errs = res[target][1]
        turned_red = not res[target][0]
        key_hit = any(expect in m for m in errs)
        ok = turned_red and key_hit
        print(f"[{'PASS' if ok else 'FAIL'}] 负向 {target}：{desc}")
        if not turned_red:
            print(f"       - 注入后 {target} 仍为绿 = 判据假绿")
        elif not key_hit:
            print(f"       - 转红但失败消息未含预期 key {expect!r}：{errs[0] if errs else '(空)'}")
        else:
            print(f"       - 转红证据：{next(m for m in errs if expect in m)}")
        all_ok = all_ok and ok
    print(f"\n[SELF-TEST] {len(mutations)}/{len(mutations)} 负向探针全部转红且命中预期 key" if all_ok
          else "\n[SELF-TEST] 存在未转红或 key 未命中的判据，需修复")
    return 0 if all_ok else 1


def main() -> int:
    ap = argparse.ArgumentParser(description="采购域文档族机器门禁")
    ap.add_argument("--self-test", action="store_true", help="注入式负向反证")
    args = ap.parse_args()
    if args.self_test:
        return self_test()
    results = run(load())
    ok = report(results)
    print(f"\n=== {'全部通过 (' + str(len(CHECKS)) + '/' + str(len(CHECKS)) + ')' if ok else '未通过'} ===")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
