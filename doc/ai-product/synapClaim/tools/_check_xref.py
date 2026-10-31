#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
跨文档 § 引用完整性校验（防漂移）。

规则：
- 扫描根目录与 delivery/ 下所有 .md（含导航中枢 文档地图），提取 "文档名 §X" 形式的交叉引用，校验 X 是否在目标文档中真实存在（生成物 00-落地交付包清单 除外）。
- 裸 "synapClaim §X" 引用（用产品名简写代指章节）作为警告（须改为指名具体文档）。
- 契约-设计一致性：delivery/05 的 MVP 端点须是 工程落地版 §10 端点表的子集；其声明为枚举真源的令牌须被 工程落地版 引用（防交付漂移）。
- 库存一致性：delivery/NN-*.md ↔ delivery/README.md 索引；生产就绪完善/NN-*.md ↔ 00-总纲映射表。
  双向比对（磁盘有但未登记 / 登记了但无文件），防「新增分册无人登记」的静默漂移。

用法：python3 _check_xref.py
退出码：发现未解析引用 -> 1；否则 0。
"""
import re, os, sys

import _manifest as M

ROOT = M.ROOT

# 短名 → 路径，一律取自 docs.manifest.json；本文件不得再出现任何文档文件名。
DOC_MAP = M.short_names()

# 章节标题正则：## 3 / ### 3.2 / #### 11.7.1
SEC_RE = re.compile(r'^#{2,4}\s+(\d+(?:\.\d+)*)\b', re.M)
# 交叉引用：文档名 + § + 章节号，支持简写列表（"PRD §4、§14"、"工程落地版 §7.3/§11.6"）
# 允许书名号隔断："《设计方案》§6.2" 也要纳入校验（否则漏检失效引用）
# 后续 § 继承最近标签；连接器限 [、/，]，遇句号/；/其他标签即止（避免误归因跨句外部 §）
# 备选支按「名称越长越靠前」，避免短名把长名吃掉一半。
# 短名集合由 manifest 驱动：新增短名 = 给全仓新增断言（旧文本写错会立刻暴露，这是设计意图）。
_NAMES = "|".join(re.escape(s) for s in sorted(DOC_MAP, key=len, reverse=True))
REF_RE = re.compile(
    r'(' + _NAMES + r')[》]?\s*§\s*\d+(?:\.\d+)*(?:(?:[、/，])\s*§\s*\d+(?:\.\d+)*)*'
)
# 遗留裸引用
LEGACY_RE = re.compile(r'synapClaim\s*§\s*\d')


def sections(filename):
    path = os.path.join(ROOT, filename)
    if not os.path.exists(path):
        return set()
    return set(m.group(1) for m in SEC_RE.finditer(open(path, encoding="utf-8").read()))


SECS = {k: sections(v) for k, v in DOC_MAP.items()}

problems, warnings = [], []
# 扫描范围由 manifest 的层定义，不再硬编码目录名：L0–L7 参与 § 引用校验。
# L8 不纳入（尚未合入契约的草案包），L9/L10 不纳入（冻结的证据与归档件）——
# 纳入会导致"改历史文档才能过 CI"，把门禁变成负担后必然被绕过。
_SCAN_LAYERS = ["L0", "L1", "L2", "L3", "L4", "L5", "L6", "L7"]
_scan_files = sorted(os.path.join(ROOT, p) for p in M.in_layers(_SCAN_LAYERS))
for f in _scan_files:
    base = os.path.basename(f)
    if base.startswith("00-落地交付包清单"):
        continue  # 生成物清单（gen_delivery_list.js 产出），非手维护引用源
    text = open(f, encoding="utf-8").read()
    for m in REF_RE.finditer(text):
        label = m.group(1).replace(" ", "")
        key = "bone-ai 落地" if label == "bone-ai落地" else label
        if key not in SECS:
            continue
        for sec in re.findall(r'§\s*(\d+(?:\.\d+)*)', m.group(0)):
            if sec not in SECS[key]:
                problems.append((base, f"{label} §{sec}", "目标文档无此章节"))
    # 文档地图是治理中枢，其 § 引用须被守护；但其规约性提及"禁止再用 synapClaim §X"不计为遗留警告（避免误报）
    if not base.startswith("文档地图"):
        for m in LEGACY_RE.finditer(text):
            warnings.append((base, m.group(0), "裸 synapClaim § 引用（应以具体文档名代指章节）"))

# --- 契约-设计一致性（防交付漂移，P3 深化）---
# delivery/05 为 MVP 契约真源；其端点必须是工程落地版 §10 端点表的子集，
# 其声明为枚举真源的令牌必须被工程落地版引用，否则即交付漂移。
def _norm_path(p):
    return re.sub(r'\{[^}]+\}', '{}', p).lower().strip()

_t05 = M.read("delivery.05")
_tSSOT = M.read("contract.landing")

_e05 = {(m.group(1), _norm_path(m.group(2)))
        for m in re.finditer(r'\|\s*(POST|GET|PUT|DELETE)\s+(/api/v1/[^|]+)\s*\|', _t05)}
_eSSOT = {(m.group(1), _norm_path(m.group(2)))
          for m in re.finditer(r'\|\s*\d+\s*\|[^|]*\|\s*(POST|GET|PUT|DELETE)\s+(/api/v1/[^|]+)\s*\|', _tSSOT)}
for _ep in sorted(_e05 - _eSSOT):
    problems.append(("delivery/05", f"{_ep[0]} {_ep[1]}", "MVP 端点在工程落地版 §10 端点表中无对应（交付漂移）"))

_m = re.search(r'枚举真源[^（）]*（([^）]+)）', _t05)
if _m:
    for _tk in re.findall(r'[A-Z][A-Z_]{2,}', _m.group(1)):
        if _tk not in _tSSOT:
            problems.append(("delivery/05", f"枚举 {_tk}", "工程落地版未引用该契约枚举（交付漂移）"))

# --- 交付清单库存一致性（防交付物-文档漂移，#2 深化）---
# 磁盘上的内容交付包（NN-*.md，排除生成的 00-落地交付包清单）须与
# 07-delivery/README.md 索引表的编号严格一致：新增文件须登记、删除文件须移除行。
_deliv = os.path.join(ROOT, M.LAYER_DIRS["L7"])
_disk = set()
for _fn in os.listdir(_deliv):
    if re.match(r'^\d{2}-.*\.md$', _fn) and not _fn.startswith("00-落地交付包清单"):
        _disk.add(_fn[:2])
_readme = M.read("delivery.index")
_idx = set(re.findall(r'^\|(\d{2})\|', _readme, re.M))
for _p in sorted(_disk - _idx):
    problems.append(("delivery/", f"交付包 {_p}-*.md 存在但未在 README 索引登记", "交付物-文档漂移"))
for _p in sorted(_idx - _disk):
    problems.append(("delivery/README.md", f"索引登记 {_p} 但无对应交付包文件", "交付物-文档漂移"))

# --- 上线门禁分册库存一致性（与 delivery 同构，P9 补齐）---
# 分册由 00-总纲的「缺口 → 完善分册映射」表登记。此前只有 delivery 有库存校验，
# 本包新增分册不登记无人察觉——与 delivery 完全同类的漂移，只是没人给它上闸。
# P10 起 14–18 属实施记录，已移至 09-records/impl-log/，本目录只剩 00–13 规范分册。
_ready = os.path.join(ROOT, M.LAYER_DIRS["L8"])
_rtoc = M.path("golive.00")
if os.path.isdir(_ready) and os.path.exists(_rtoc):
    _rd = {
        f[:2] for f in os.listdir(_ready)
        if re.match(r'^\d{2}-.*\.md$', f) and not f.startswith("00-")
    }
    _ri = set(re.findall(r'`(\d{2})-[^`]+\.md`', open(_rtoc, encoding="utf-8").read()))
    for _p in sorted(_rd - _ri):
        problems.append(("08-go-live/", f"分册 {_p}-*.md 存在但未在 00-总纲映射表登记", "库存漂移"))
    for _p in sorted(_ri - _rd):
        problems.append(("08-go-live/00-总纲", f"总纲登记 {_p} 但无对应分册文件", "库存漂移"))

print("=== 跨文档引用校验 ===")
if problems:
    print("【错误】未解析引用 / 库存漂移：")
    for p in problems:
        print("  -", p)
else:
    print("【OK】所有指名文档的 § 引用均能在目标文档解析")
if warnings:
    print("【警告】遗留 synapClaim § 引用（待迁移）：")
    for w in warnings:
        print("  -", w)
else:
    print("【OK】无遗留 synapClaim § 引用")
sys.exit(1 if problems else 0)
