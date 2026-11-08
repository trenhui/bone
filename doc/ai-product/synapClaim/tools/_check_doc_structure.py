#!/usr/bin/env python3
"""方案文档结构机检 —— 落实《文档治理规约》§七。

判据分两档：
  必选四节（缺 → exit 1）：决策摘要 / 风险与回退 / 不做清单 / 待决策
  推荐五节（缺 → 仅提示）：目标与约束 / 现状与差距 / 方案 / 关键决策 / 质量与验收

适用范围：manifest 中 layer ∈ {L3,L4} 且 diataxis=reference 且 role=ssot。
豁免项见 EXEMPT（附理由），豁免后只要求「决策摘要 + 待决策」。

为什么按"标题语义同义词"而不是固定标题文本匹配：
  各文档历史措辞不同（「一页结论」/「决策摘要」/「TL;DR」），硬编码单一文本会
  对合规文档判红（误报比漏报更致命——作者会直接关掉门禁）。故按职责同义词组匹配，
  但同义词表本身必须有负向探针守护（--self-test），防止表被改宽到恒真。
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "docs.manifest.json"

# 必选：缺任一节即判红
REQUIRED: dict[str, tuple[str, ...]] = {
    "决策摘要": ("决策摘要", "一页结论", "一页方案", "tl;dr", "摘要"),
    "风险与回退": ("风险", "回退", "技术债"),
    "不做清单": ("不做", "不照搬", "非目标", "don't", "设计边界", "边界外"),
    "待决策": ("待决策", "待拍板", "待澄清", "未决", "开放问题"),
}

# 推荐：缺失只提示
ADVISED: dict[str, tuple[str, ...]] = {
    "目标与约束": ("目标", "约束", "范围", "定位", "原则"),
    "现状与差距": ("现状", "差距", "取证", "当前实现", "盘点", "断层", "缺口", "短板"),
    "方案": ("方案", "设计", "架构", "模型", "结构"),
    "关键决策": ("决策", "adr"),
    "质量与验收": ("质量", "验收", "非功能", "slo", "指标", "度量", "验证", "评测", "可观测"),
}

# 豁免：只校验「决策摘要 + 待决策」。理由必须写清，禁止无理由豁免。
EXEMPT: dict[str, str] = {
    "03-contract/智能理算引擎业务规则V1.md":
        "规则 ID 与公式清单，本身即待执行条目，无方案/风险/回退语义",
}

# 存量基线（ratchet，只减不增）：登记 §七 发布时已存在的必选节缺口。
# 规则：
#   实际缺口 − 基线 = 新增缺口 → 判红（防止新文档继续不合规）
#   基线 − 实际缺口 = 已补齐 → 提示"从基线删除该项"，不判红
# 补齐一份就把该项从下方删掉；**禁止为了让门禁变绿而往这里加条目**。
#
# 2026-11-07：5 份存量缺口全部补齐（工程落地版/AI技术方案/业务规则V1/风控与支付/BONE-AI 依赖图谱），
# 基线清空。此后任何方案文档缺必选四节都当场判红——这是"只减不增"的终态。
BASELINE: dict[str, set[str]] = {}

HEADING = re.compile(r"^##\s+(.+)$", re.MULTILINE)


def headings(text: str) -> list[str]:
    return [h.strip().lower() for h in HEADING.findall(text)]


def missing(heads: list[str], slots: dict[str, tuple[str, ...]]) -> list[str]:
    out = []
    for name, words in slots.items():
        if not any(w.lower() in h for h in heads for w in words):
            out.append(name)
    return out


def check(rel_path: str, text: str) -> tuple[list[str], list[str]]:
    heads = headings(text)
    exempt = rel_path in EXEMPT
    req = {"决策摘要": REQUIRED["决策摘要"], "待决策": REQUIRED["待决策"]} if exempt else REQUIRED
    return missing(heads, req), ([] if exempt else missing(heads, ADVISED))


def gap_class(rel_path: str, req: list[str]) -> tuple[list[str], list[str]]:
    """ratchet 判定：返回 (新增缺口, 已补齐的基线项)。"""
    base = BASELINE.get(rel_path, set())
    return sorted(set(req) - base), sorted(base - set(req))


def load_targets() -> list[str]:
    docs = json.loads(MANIFEST.read_text(encoding="utf-8"))["docs"]
    return [
        d["path"] for d in docs
        if d.get("layer") in ("L3", "L4")
        and d.get("diataxis") == "reference"
        and d.get("role") == "ssot"
    ]


def self_test() -> int:
    """负向探针：证明判据不恒真、同义词表不恒宽。"""
    ok = True

    def expect(label: str, cond: bool) -> None:
        nonlocal ok
        print(f"  {'PASS' if cond else 'FAIL'}  {label}")
        ok &= cond

    good = """## 0. 决策摘要
## 1. 目标与约束
## 2. 现状与差距
## 3. 方案
## 4. 关键决策
## 5. 质量与验收
## 6. 不做清单
## 7. 风险与回退
## 8. 待决策
"""
    req, adv = check("x.md", good)
    expect("全合规样本零缺失（证明不是恒红）", not req and not adv)

    expect("摘要标题改成无职责名 → 必判红",
           "决策摘要" in check("x.md", good.replace("## 0. 决策摘要", "## 0. 总体情况"))[0])

    expect("删掉待决策 → 必判红",
           "待决策" in check("x.md", good.replace("## 8. 待决策\n", ""))[0])

    expect("删掉风险 → 必判红",
           "风险与回退" in check("x.md", good.replace("## 7. 风险与回退\n", ""))[0])

    expect("豁免项仅缺推荐节 → 不判红",
           not check("03-contract/智能理算引擎业务规则V1.md", "## 决策摘要\n## 待决策\n")[0])

    expect("豁免项缺摘要 → 仍判红（豁免不等于放行）",
           "决策摘要" in check("03-contract/智能理算引擎业务规则V1.md", "## 待决策\n")[0])

    # ratchet 三态用注入基线的样本证明，不依赖真实 BASELINE 当前内容
    # （否则基线清空后这几条探针会失效——门禁自身也会失去判别力）。
    saved = dict(BASELINE)
    BASELINE["__probe__.md"] = {"决策摘要", "不做清单", "待决策"}
    try:
        new, _ = gap_class("__probe__.md", ["决策摘要", "不做清单", "待决策"])
        expect("基线内缺口不判红（存量放行，否则门禁上线即被关）", not new)

        new2, _ = gap_class("__probe__.md", ["决策摘要", "不做清单", "待决策", "风险与回退"])
        expect("基线外新增缺口判红（ratchet 只减不增）", "风险与回退" in new2)

        _, stale = gap_class("__probe__.md", ["决策摘要"])
        expect("补齐后基线项转为 stale 提示（防基线永久留白）", len(stale) == 2)
    finally:
        BASELINE.clear()
        BASELINE.update(saved)

    expect("真实基线已清空（终态：无存量放行，任何缺口当场判红）", BASELINE == {})
    expect("基线为空时无 stale 可报（终态不存在永久留白）",
           not gap_class("04-design/AI技术方案.md", [])[1])

    print(f"\n{'【OK】自检通过' if ok else '【FAIL】自检未通过'}")
    return 0 if ok else 1


def main() -> int:
    if "--self-test" in sys.argv:
        return self_test()

    targets = load_targets()
    bad = 0
    stale_notes: list[str] = []
    print("=== 方案文档结构机检（治理规约 §七）===")
    for rel in targets:
        path = ROOT / rel
        if not path.exists():
            print(f"  [MISS]    {rel}  ← manifest 登记但文件不存在")
            bad += 1
            continue
        req, adv = check(rel, path.read_text(encoding="utf-8"))
        new_gaps, stale = gap_class(rel, req)
        if new_gaps:
            bad += 1
        if new_gaps:
            tag = "NEW-GAP "
        elif req:
            tag = "BASELINE"
        elif rel in EXEMPT:
            tag = "EXEMPT  "
        else:
            tag = "OK      "

        parts = [
            f"新增缺口: {'/'.join(new_gaps)}" if new_gaps
            else (f"存量缺口: {'/'.join(sorted(req))}（基线内，待补齐）" if req else "必选齐备")
        ]
        if stale:
            parts.append(f"已补齐、请从 BASELINE 删除: {'/'.join(stale)}")
            stale_notes.append(rel)
        if adv:
            parts.append(f"缺推荐: {'/'.join(adv)}")
        print(f"  [{tag}] {rel}\n           " + "｜".join(parts))

    print()
    if bad:
        print(f"【GAP】{bad} 份文档出现**新增**必选节缺口。方案文档必须含 §七之二 的必选四节。")
        return 1
    print(f"【OK】{len(targets)} 份方案文档无新增结构缺口"
          f"（存量 {len(BASELINE)} 份基线内缺口待补齐，推荐节提示不判红）")
    if stale_notes:
        print(f"【提醒】{len(stale_notes)} 份已补齐，请同步从 BASELINE 删除对应条目")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
